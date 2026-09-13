package tf.fog.kasnota.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.BuktiEntity
import tf.fog.kasnota.data.db.KelompokEntity
import tf.fog.kasnota.data.db.OpsiEntity
import tf.fog.kasnota.data.db.TransaksiEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Maks bukti per transaksi — samakan dengan MAX_BUKTI_PER_TRANSAKSI di backend
 * (keuangan/utils/bukti_utils.py) supaya pesan penuh di app konsisten dengan penolakan server. */
const val MAX_BUKTI_PER_TRANSAKSI = 6

/** Anggota kelompok maks 3 bukti — samakan dgn MAX_BUKTI_PER_KELOMPOK_ANGGOTA backend. */
const val MAX_BUKTI_PER_KELOMPOK_ANGGOTA = 3
private const val BUKTI_COMPRESS_THRESHOLD = 1_000_000 // 1 MB, sama seperti backend

private val ISO_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

/** Timestamp lokal format ISO 6-digit mikrodetik — kompatibel dengan `datetime.fromisoformat()` di server. */
fun nowIso(): String = LocalDateTime.now().format(ISO_FMT)

fun newUuid(): String = UUID.randomUUID().toString()

/**
 * Get-or-create teks kegiatan/jenis ke vocabulary lokal (sama seperti `_register_opsi_usage`
 * di backend) — murni saran combobox berikutnya, tidak pernah direferensikan balik oleh transaksi.
 */
suspend fun registerOpsiUsage(db: AppDatabase, bukuId: Int, kategori: String, nama: String?) {
    val trimmed = nama?.trim().orEmpty()
    if (trimmed.isEmpty()) return
    val existing = db.opsiDao().findByNama(bukuId, kategori, trimmed)
    if (existing != null) return
    db.opsiDao().insert(
        OpsiEntity(
            bukuId = bukuId, kategori = kategori, nama = trimmed,
            updatedAt = nowIso(), clientUuid = newUuid(), dirty = true,
        )
    )
}

/** Simpan transaksi SELALU ke Room dulu (offline-first) — terkirim ke server nanti lewat SyncScreen. */
suspend fun saveTransaksiLocal(
    db: AppDatabase,
    bukuId: Int,
    existing: TransaksiEntity?,
    tanggal: String,
    kegiatan: String?,
    jenis: String?,
    deskripsi: String,
    tipe: String,
    jumlah: Int,
): TransaksiEntity {
    val now = nowIso()
    val masuk = if (tipe == "masuk") jumlah else 0
    val keluar = if (tipe == "keluar") jumlah else 0
    val kegiatanTrim = kegiatan?.trim().takeUnless { it.isNullOrEmpty() }
    val jenisTrim = jenis?.trim().takeUnless { it.isNullOrEmpty() }

    val entity = existing?.copy(
        tanggal = tanggal, kegiatan = kegiatanTrim, jenis = jenisTrim,
        deskripsi = deskripsi.trim(), masuk = masuk, keluar = keluar,
        updatedAt = now, dirty = true,
    ) ?: TransaksiEntity(
        bukuId = bukuId, tanggal = tanggal, kegiatan = kegiatanTrim, jenis = jenisTrim,
        deskripsi = deskripsi.trim(), masuk = masuk, keluar = keluar, origin = "android",
        updatedAt = now, clientUuid = newUuid(), dirty = true,
    )

    val savedEntity = if (existing != null) {
        db.transaksiDao().update(entity)
        entity
    } else {
        val newLocalId = db.transaksiDao().insert(entity)
        entity.copy(localId = newLocalId)
    }
    registerOpsiUsage(db, bukuId, "kegiatan", kegiatanTrim)
    registerOpsiUsage(db, bukuId, "jenis", jenisTrim)
    return savedEntity
}

/** Offline: hapus PERMANEN transaksi + semua file/baris buktinya (tak ada sync/tombstone). */
suspend fun softDeleteTransaksiLocal(db: AppDatabase, entity: TransaksiEntity) {
    for (b in db.buktiDao().listAllForTransaksi(entity.localId)) {
        b.localFilePath?.let { runCatching { File(it).delete() } }
        db.buktiDao().deleteHard(b.localId)
    }
    db.transaksiDao().deleteHard(entity.localId)
}

/** Buat kelompok baru — cuma tanggal (utk anggota pertama) & kegiatan (tetap sejak dibuat),
 * sama seperti create_kelompok backend. */
suspend fun saveKelompokLocal(db: AppDatabase, bukuId: Int, tanggal: String, kegiatan: String): KelompokEntity {
    val entity = KelompokEntity(
        bukuId = bukuId, tanggal = tanggal, kegiatan = kegiatan.trim(),
        updatedAt = nowIso(), clientUuid = newUuid(), dirty = true,
    )
    val newLocalId = db.kelompokDao().insert(entity)
    registerOpsiUsage(db, bukuId, "kegiatan", kegiatan)
    return entity.copy(localId = newLocalId)
}

/** Tambah/edit 1 anggota kelompok (tanpa kegiatan sendiri — ikut kelompok). Tanggal
 * kelompok ikut anggota BARU ini (hanya saat tambah, bukan edit) — sama seperti
 * add_kelompok_member vs update_transaksi di backend. */
suspend fun saveKelompokAnggotaLocal(
    db: AppDatabase,
    kelompok: KelompokEntity,
    existing: TransaksiEntity?,
    tanggal: String,
    jenis: String?,
    deskripsi: String,
    tipe: String,
    jumlah: Int,
): TransaksiEntity {
    val now = nowIso()
    val masuk = if (tipe == "masuk") jumlah else 0
    val keluar = if (tipe == "keluar") jumlah else 0
    val jenisTrim = jenis?.trim().takeUnless { it.isNullOrEmpty() }

    val entity = existing?.copy(
        tanggal = tanggal, jenis = jenisTrim, deskripsi = deskripsi.trim(),
        masuk = masuk, keluar = keluar, updatedAt = now, dirty = true,
    ) ?: TransaksiEntity(
        bukuId = kelompok.bukuId, kelompokLocalId = kelompok.localId,
        tanggal = tanggal, kegiatan = null, jenis = jenisTrim,
        deskripsi = deskripsi.trim(), masuk = masuk, keluar = keluar, origin = "android",
        updatedAt = now, clientUuid = newUuid(), dirty = true,
    )

    val saved = if (existing != null) {
        db.transaksiDao().update(entity)
        entity
    } else {
        val newLocalId = db.transaksiDao().insert(entity)
        entity.copy(localId = newLocalId)
    }
    registerOpsiUsage(db, kelompok.bukuId, "jenis", jenisTrim)
    if (existing == null) {
        db.kelompokDao().update(kelompok.copy(tanggal = tanggal, updatedAt = now, dirty = true))
    }
    return saved
}

/** Hapus 1 anggota kelompok (staging isDeleted+dirty spt softDeleteTransaksiLocal), lalu
 * recompute tanggal kelompok ke anggota MASIH ADA yang terakhir (lihat lastForKelompok). */
suspend fun deleteKelompokAnggotaLocal(db: AppDatabase, entity: TransaksiEntity) {
    softDeleteTransaksiLocal(db, entity)
    val kid = entity.kelompokLocalId ?: return
    val kelompok = db.kelompokDao().findByLocalId(kid) ?: return
    val last = db.transaksiDao().lastForKelompok(kid)
    if (last != null && last.tanggal != kelompok.tanggal) {
        db.kelompokDao().update(kelompok.copy(tanggal = last.tanggal, updatedAt = nowIso(), dirty = true))
    }
}

/** Hapus PERMANEN kelompok + SEMUA anggota & buktinya. */
suspend fun softDeleteKelompokLocal(db: AppDatabase, kelompok: KelompokEntity) {
    for (t in db.transaksiDao().listAllForKelompok(kelompok.localId)) {
        softDeleteTransaksiLocal(db, t)
    }
    db.kelompokDao().deleteHard(kelompok.localId)
}

/** Kompres bila > 1MB (mis. quality 80/60/40, sama seperti backend) — hasil langsung
 * dipakai apa adanya tanpa cek ulang ukuran setelah kompres. Selalu keluar sebagai JPEG
 * kalau sempat dikompres; kalau sudah <= 1MB, bytes asli disimpan apa adanya. */
private fun compressBuktiIfNeeded(data: ByteArray, mime: String): Pair<ByteArray, String> {
    if (data.size <= BUKTI_COMPRESS_THRESHOLD) return data to mime
    return try {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return data to mime
        var result = data
        for (quality in intArrayOf(80, 60, 40)) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            result = out.toByteArray()
            if (result.size <= BUKTI_COMPRESS_THRESHOLD) break
        }
        result to "image/jpeg"
    } catch (e: Exception) {
        data to mime
    }
}

/** Buat file kosong di cache app + Uri content:// via FileProvider, dipakai sebagai target
 * ActivityResultContracts.TakePicture() — app kamera eksternal nulis hasil jepretan ke situ
 * tanpa perlu expose path file:// langsung (dilarang API 24+ / StrictMode). */
fun createCameraCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "bukti_tmp").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Simpan 1 bukti transaksi (foto) ke penyimpanan privat app (bukan external storage —
 * supaya foto struk/nota tidak bisa diakses app lain) + baris Room, ditandai dirty
 * (belum diupload) supaya diproses SyncManager saat sync manual berikutnya.
 * Return null kalau uri tidak bisa dibaca / bukan gambar valid.
 */
suspend fun saveBuktiLocal(
    db: AppDatabase,
    context: Context,
    bukuId: Int,
    transaksiLocalId: Long,
    uri: Uri,
): BuktiEntity? {
    val resolver = context.contentResolver
    val rawBytes = try {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    } ?: return null
    if (rawBytes.isEmpty()) return null

    // Selalu decode ulang buat pastikan ini gambar valid (bukan cuma percaya Content-Type).
    if (BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) == null) return null

    val rawMime = resolver.getType(uri) ?: "image/jpeg"
    val (finalBytes, finalMime) = compressBuktiIfNeeded(rawBytes, rawMime)
    val ext = when (finalMime) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        else -> ".jpg"
    }

    val dir = File(context.filesDir, "bukti/$transaksiLocalId").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}$ext")
    file.writeBytes(finalBytes)

    val entity = BuktiEntity(
        bukuId = bukuId,
        transaksiLocalId = transaksiLocalId,
        localFilePath = file.absolutePath,
        mimeType = finalMime,
        dirty = true,
        clientUuid = newUuid(),
        createdAt = nowIso(),
    )
    val localId = db.buktiDao().insert(entity)
    return entity.copy(localId = localId)
}

/** Hapus bukti: kalau belum pernah diupload, langsung hapus lokal; kalau sudah, tandai
 * dirty supaya SyncManager memanggil DELETE ke server saat sync berikutnya. */
suspend fun softDeleteBuktiLocal(db: AppDatabase, bukti: BuktiEntity) {
    if (bukti.remoteId == null) {
        bukti.localFilePath?.let { runCatching { File(it).delete() } }
        db.buktiDao().deleteHard(bukti.localId)
    } else {
        db.buktiDao().update(bukti.copy(isDeleted = true, dirty = true))
    }
}
