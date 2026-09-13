package tf.fog.kasnota.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.BukuEntity
import tf.fog.kasnota.data.db.BuktiEntity
import tf.fog.kasnota.data.db.KelompokEntity
import tf.fog.kasnota.data.db.OpsiEntity
import tf.fog.kasnota.data.db.TransaksiEntity
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val SCHEMA = 1
private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

/** Nama file cadangan: {namabuku}_backup_{yyyyMMdd}_{HHmm}.json */
fun backupFileName(namaBuku: String): String {
    val safe = namaBuku.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").replace(Regex("\\s+"), "_")
        .ifBlank { "buku" }
    return "${safe}_backup_${LocalDateTime.now().format(FILE_STAMP)}.json"
}

/** Serialisasi 1 buku (opsi + kelompok + transaksi + bukti, gambar di-embed base64). */
suspend fun exportBukuJson(db: AppDatabase, bukuId: Int): String {
    val buku = db.bukuDao().getById(bukuId) ?: error("buku tidak ditemukan")
    val opsiList = db.opsiDao().let { dao ->
        (dao.search(bukuId, "kegiatan", "", 100000) + dao.search(bukuId, "jenis", "", 100000))
    }
    val kelompokList = db.kelompokDao().getAll(bukuId)
    val transaksiList = db.transaksiDao().getAllForSaldo(bukuId)

    val root = JSONObject()
    root.put("schema", SCHEMA)
    root.put("buku", JSONObject().put("nama", buku.nama))

    val opsiArr = JSONArray()
    opsiList.forEach { o ->
        opsiArr.put(JSONObject().put("kategori", o.kategori).put("nama", o.nama))
    }
    root.put("opsi", opsiArr)

    val kelArr = JSONArray()
    kelompokList.forEach { k ->
        kelArr.put(
            JSONObject().put("tmpId", k.localId).put("tanggal", k.tanggal)
                .put("kegiatan", k.kegiatan ?: JSONObject.NULL),
        )
    }
    root.put("kelompok", kelArr)

    val trxArr = JSONArray()
    transaksiList.forEach { t ->
        trxArr.put(
            JSONObject()
                .put("tmpId", t.localId)
                .put("kelompokTmpId", t.kelompokLocalId ?: JSONObject.NULL)
                .put("tanggal", t.tanggal)
                .put("kegiatan", t.kegiatan ?: JSONObject.NULL)
                .put("jenis", t.jenis ?: JSONObject.NULL)
                .put("deskripsi", t.deskripsi)
                .put("masuk", t.masuk)
                .put("keluar", t.keluar)
                .put("origin", t.origin),
        )
    }
    root.put("transaksi", trxArr)

    val buktiArr = JSONArray()
    transaksiList.forEach { t ->
        for (b in db.buktiDao().listForTransaksi(t.localId)) {
            val path = b.localFilePath ?: continue
            val bytes = runCatching { File(path).readBytes() }.getOrNull() ?: continue
            buktiArr.put(
                JSONObject()
                    .put("transaksiTmpId", t.localId)
                    .put("mimeType", b.mimeType)
                    .put("createdAt", b.createdAt)
                    .put("dataB64", Base64.encodeToString(bytes, Base64.NO_WRAP)),
            )
        }
    }
    root.put("bukti", buktiArr)

    return root.toString()
}

/** Impor jadi BUKU BARU (id lokal baru). Return id buku baru. Lempar Exception kalau file
 * bukan cadangan yang valid. */
suspend fun importBukuJson(db: AppDatabase, context: Context, uri: Uri): Int {
    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        ?: throw IllegalArgumentException("file kosong")
    val root = JSONObject(text)
    require(root.optInt("schema", -1) == SCHEMA) { "skema tidak dikenal" }

    var nama = root.getJSONObject("buku").getString("nama").trim().ifBlank { "Buku" }
    if (db.bukuDao().countByNama(nama) > 0) {
        nama = "$nama ${context.getString(tf.fog.kasnota.R.string.imported_suffix)}"
    }
    val bukuId = db.bukuDao().insert(BukuEntity(nama = nama)).toInt()

    val now = nowIso()

    root.optJSONArray("opsi")?.let { arr ->
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            db.opsiDao().insert(
                OpsiEntity(
                    bukuId = bukuId, kategori = o.getString("kategori"), nama = o.getString("nama"),
                    updatedAt = now, clientUuid = newUuid(),
                ),
            )
        }
    }

    val kelompokIdMap = HashMap<Long, Long>()
    root.optJSONArray("kelompok")?.let { arr ->
        for (i in 0 until arr.length()) {
            val k = arr.getJSONObject(i)
            val newId = db.kelompokDao().insert(
                KelompokEntity(
                    bukuId = bukuId,
                    tanggal = k.getString("tanggal"),
                    kegiatan = k.optStringOrNull("kegiatan"),
                    updatedAt = now, clientUuid = newUuid(),
                ),
            )
            kelompokIdMap[k.getLong("tmpId")] = newId
        }
    }

    val transaksiIdMap = HashMap<Long, Long>()
    root.optJSONArray("transaksi")?.let { arr ->
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            val kelompokTmp = if (t.isNull("kelompokTmpId")) null else t.getLong("kelompokTmpId")
            val newId = db.transaksiDao().insert(
                TransaksiEntity(
                    bukuId = bukuId,
                    kelompokLocalId = kelompokTmp?.let { kelompokIdMap[it] },
                    tanggal = t.getString("tanggal"),
                    kegiatan = t.optStringOrNull("kegiatan"),
                    jenis = t.optStringOrNull("jenis"),
                    deskripsi = t.getString("deskripsi"),
                    masuk = t.optInt("masuk"),
                    keluar = t.optInt("keluar"),
                    origin = t.optString("origin", "android"),
                    updatedAt = now, clientUuid = newUuid(),
                ),
            )
            transaksiIdMap[t.getLong("tmpId")] = newId
        }
    }

    root.optJSONArray("bukti")?.let { arr ->
        for (i in 0 until arr.length()) {
            val b = arr.getJSONObject(i)
            val trxId = transaksiIdMap[b.getLong("transaksiTmpId")] ?: continue
            val bytes = runCatching { Base64.decode(b.getString("dataB64"), Base64.NO_WRAP) }.getOrNull() ?: continue
            val mime = b.optString("mimeType", "image/jpeg")
            val ext = when (mime) {
                "image/png" -> ".png"; "image/webp" -> ".webp"; else -> ".jpg"
            }
            val dir = File(context.filesDir, "bukti/$trxId").apply { mkdirs() }
            val file = File(dir, "${newUuid()}$ext")
            file.writeBytes(bytes)
            db.buktiDao().insert(
                BuktiEntity(
                    bukuId = bukuId,
                    transaksiLocalId = trxId,
                    localFilePath = file.absolutePath,
                    mimeType = mime,
                    clientUuid = newUuid(),
                    createdAt = b.optString("createdAt", now),
                ),
            )
        }
    }

    return bukuId
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
