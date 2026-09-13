package tf.fog.kasnota.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Buku (ledger) — dibuat lokal oleh pengguna, tidak ada server. */
@Entity(tableName = "buku")
data class BukuEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nama: String,
)

/**
 * Vocabulary/saran kegiatan atau jenis, per buku — SAMA seperti KeuanganOpsi di web:
 * tidak pernah direferensikan balik oleh TransaksiEntity (kegiatan/jenis di sana teks bebas).
 */
@Entity(tableName = "opsi")
data class OpsiEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Int? = null,
    val bukuId: Int,
    val kategori: String, // "kegiatan" | "jenis"
    val nama: String,
    val isDeleted: Boolean = false,
    val updatedAt: String,
    val clientUuid: String,
    val dirty: Boolean = false, // true = ada perubahan lokal yang belum di-push
)

/**
 * Wadah transaksi berulang/serial (mis. iuran per siswa) — SAMA seperti KeuanganKelompok
 * di web: cuma punya tanggal (ikut anggota terakhir yang ditambahkan) & kegiatan (tetap
 * sejak dibuat). Anggotanya adalah TransaksiEntity biasa dengan kelompokLocalId terisi.
 */
@Entity(tableName = "kelompok")
data class KelompokEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Int? = null,
    val bukuId: Int,
    val tanggal: String, // yyyy-MM-dd, ikut anggota terakhir
    val kegiatan: String? = null,
    val isDeleted: Boolean = false,
    val updatedAt: String,
    val clientUuid: String,
    val dirty: Boolean = false,
)

@Entity(tableName = "transaksi")
data class TransaksiEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Int? = null,
    val bukuId: Int,
    val kelompokLocalId: Long? = null, // isi = anggota kelompok (lihat KelompokEntity), null = transaksi biasa
    val tanggal: String, // yyyy-MM-dd
    val kegiatan: String? = null,
    val jenis: String? = null,
    val deskripsi: String,
    val masuk: Int = 0,
    val keluar: Int = 0,
    val origin: String = "android",
    val isDeleted: Boolean = false,
    val updatedAt: String,
    val clientUuid: String,
    val dirty: Boolean = false,
)

/**
 * Bukti transaksi (foto struk/nota, opsional, banyak per transaksi). Beda dari opsi/transaksi:
 * TIDAK lewat sync/push JSON (isinya biner) — upload/hapus dipanggil langsung oleh
 * SyncManager lewat endpoint multipart terpisah, tapi tetap HANYA dari sync manual
 * (bukan dari FormScreen), konsisten dengan aturan "layar lain murni Room" di modul ini.
 *
 * localFilePath: file gambar di penyimpanan privat app (context.filesDir), null kalau
 * baris ini hasil pull dari server dan file-nya belum sempat diunduh.
 * remoteId null = belum pernah diupload. dirty = ada perubahan lokal (upload/hapus
 * baru) yang belum dikirim ke server.
 */
@Entity(tableName = "bukti")
data class BuktiEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Int? = null,
    val bukuId: Int,
    val transaksiLocalId: Long,
    val localFilePath: String? = null,
    val mimeType: String,
    val isDeleted: Boolean = false,
    val dirty: Boolean = false,
    val clientUuid: String,
    val createdAt: String,
)
