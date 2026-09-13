package tf.fog.kasnota.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BukuDao {
    @Query("SELECT * FROM buku ORDER BY nama")
    suspend fun getAll(): List<BukuEntity>

    @Query("SELECT * FROM buku WHERE id = :id")
    suspend fun getById(id: Int): BukuEntity?

    @Query("SELECT COUNT(*) FROM buku WHERE lower(nama) = lower(:nama)")
    suspend fun countByNama(nama: String): Int

    @Insert
    suspend fun insert(entity: BukuEntity): Long

    @Update
    suspend fun update(entity: BukuEntity)

    @Query("DELETE FROM buku WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface OpsiDao {
    @Query(
        """
        SELECT * FROM opsi WHERE bukuId = :bukuId AND kategori = :kategori AND isDeleted = 0
        AND (:q = '' OR nama LIKE '%' || :q || '%')
        ORDER BY nama LIMIT :limit
        """
    )
    suspend fun search(bukuId: Int, kategori: String, q: String, limit: Int = 10): List<OpsiEntity>

    @Query("SELECT * FROM opsi WHERE bukuId = :bukuId AND kategori = :kategori AND lower(nama) = lower(:nama) AND isDeleted = 0 LIMIT 1")
    suspend fun findByNama(bukuId: Int, kategori: String, nama: String): OpsiEntity?

    @Query("SELECT * FROM opsi WHERE bukuId = :bukuId AND dirty = 1")
    suspend fun getDirty(bukuId: Int): List<OpsiEntity>

    @Query("SELECT * FROM opsi WHERE bukuId = :bukuId AND serverId = :serverId LIMIT 1")
    suspend fun findByServerId(bukuId: Int, serverId: Int): OpsiEntity?

    @Query("SELECT * FROM opsi WHERE clientUuid = :uuid LIMIT 1")
    suspend fun findByClientUuid(uuid: String): OpsiEntity?

    @Insert
    suspend fun insert(entity: OpsiEntity): Long

    @Update
    suspend fun update(entity: OpsiEntity)

    @Query("UPDATE opsi SET serverId = :serverId, dirty = 0 WHERE localId = :localId")
    suspend fun markSynced(localId: Long, serverId: Int)
}

@Dao
interface TransaksiDao {
    /** Urut kronologis (tanggal lalu localId) — dasar perhitungan saldo berjalan di Kotlin. */
    @Query("SELECT * FROM transaksi WHERE bukuId = :bukuId AND isDeleted = 0 ORDER BY tanggal ASC, localId ASC")
    suspend fun getAllForSaldo(bukuId: Int): List<TransaksiEntity>

    @Query("SELECT * FROM transaksi WHERE bukuId = :bukuId AND dirty = 1")
    suspend fun getDirty(bukuId: Int): List<TransaksiEntity>

    @Query("SELECT * FROM transaksi WHERE bukuId = :bukuId AND serverId = :serverId LIMIT 1")
    suspend fun findByServerId(bukuId: Int, serverId: Int): TransaksiEntity?

    @Query("SELECT * FROM transaksi WHERE clientUuid = :uuid LIMIT 1")
    suspend fun findByClientUuid(uuid: String): TransaksiEntity?

    @Query("SELECT * FROM transaksi WHERE localId = :localId LIMIT 1")
    suspend fun findByLocalId(localId: Long): TransaksiEntity?

    /** Anggota kelompok ini, urut kronologis — dasar tampilan detail kelompok + total. */
    @Query("SELECT * FROM transaksi WHERE kelompokLocalId = :kelompokLocalId AND isDeleted = 0 ORDER BY tanggal ASC, localId ASC")
    suspend fun listForKelompok(kelompokLocalId: Long): List<TransaksiEntity>

    /** Anggota terakhir yang MASIH ADA (urut localId) — dasar recompute tanggal kelompok
     * setelah 1 anggota dihapus, sama semantik dgn _recompute_kelompok_tanggal di backend. */
    @Query("SELECT * FROM transaksi WHERE kelompokLocalId = :kelompokLocalId AND isDeleted = 0 ORDER BY localId DESC LIMIT 1")
    suspend fun lastForKelompok(kelompokLocalId: Long): TransaksiEntity?

    /** SEMUA anggota kelompok ini, tanpa filter isDeleted — dipakai cascade-hapus kelompok
     * (tombstone), sama pola dgn BuktiDao.listAllForTransaksi. */
    @Query("SELECT * FROM transaksi WHERE kelompokLocalId = :kelompokLocalId")
    suspend fun listAllForKelompok(kelompokLocalId: Long): List<TransaksiEntity>

    @Insert
    suspend fun insert(entity: TransaksiEntity): Long

    @Update
    suspend fun update(entity: TransaksiEntity)

    @Query("UPDATE transaksi SET serverId = :serverId, dirty = 0 WHERE localId = :localId")
    suspend fun markSynced(localId: Long, serverId: Int)

    @Query("DELETE FROM transaksi WHERE localId = :localId")
    suspend fun deleteHard(localId: Long)
}

@Dao
interface KelompokDao {
    @Query("SELECT * FROM kelompok WHERE bukuId = :bukuId AND isDeleted = 0 ORDER BY tanggal DESC, localId DESC")
    suspend fun getAll(bukuId: Int): List<KelompokEntity>

    @Query("SELECT * FROM kelompok WHERE bukuId = :bukuId AND dirty = 1")
    suspend fun getDirty(bukuId: Int): List<KelompokEntity>

    @Query("SELECT * FROM kelompok WHERE bukuId = :bukuId AND serverId = :serverId LIMIT 1")
    suspend fun findByServerId(bukuId: Int, serverId: Int): KelompokEntity?

    @Query("SELECT * FROM kelompok WHERE clientUuid = :uuid LIMIT 1")
    suspend fun findByClientUuid(uuid: String): KelompokEntity?

    @Query("SELECT * FROM kelompok WHERE localId = :localId LIMIT 1")
    suspend fun findByLocalId(localId: Long): KelompokEntity?

    @Insert
    suspend fun insert(entity: KelompokEntity): Long

    @Update
    suspend fun update(entity: KelompokEntity)

    @Query("UPDATE kelompok SET serverId = :serverId, dirty = 0 WHERE localId = :localId")
    suspend fun markSynced(localId: Long, serverId: Int)

    @Query("DELETE FROM kelompok WHERE localId = :localId")
    suspend fun deleteHard(localId: Long)
}

@Dao
interface BuktiDao {
    @Query("SELECT * FROM bukti WHERE transaksiLocalId = :transaksiLocalId AND isDeleted = 0 ORDER BY createdAt")
    suspend fun listForTransaksi(transaksiLocalId: Long): List<BuktiEntity>

    @Query("SELECT COUNT(*) FROM bukti WHERE transaksiLocalId = :transaksiLocalId AND isDeleted = 0")
    suspend fun countForTransaksi(transaksiLocalId: Long): Int

    @Query("SELECT * FROM bukti WHERE bukuId = :bukuId AND dirty = 1")
    suspend fun getDirty(bukuId: Int): List<BuktiEntity>

    @Query("SELECT * FROM bukti WHERE localFilePath IS NOT NULL AND isDeleted = 0")
    suspend fun listWithLocalFile(): List<BuktiEntity>

    /** SEMUA bukti milik transaksi ini, tanpa filter isDeleted — dipakai saat cascade-hapus
     * transaksi (tombstone) supaya tidak ada bukti yang tersisa jadi yatim. */
    @Query("SELECT * FROM bukti WHERE transaksiLocalId = :transaksiLocalId")
    suspend fun listAllForTransaksi(transaksiLocalId: Long): List<BuktiEntity>

    @Query("SELECT * FROM bukti WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: Int): BuktiEntity?

    @Query("SELECT * FROM bukti WHERE clientUuid = :uuid LIMIT 1")
    suspend fun findByClientUuid(uuid: String): BuktiEntity?

    @Insert
    suspend fun insert(entity: BuktiEntity): Long

    @Update
    suspend fun update(entity: BuktiEntity)

    @Query("DELETE FROM bukti WHERE localId = :localId")
    suspend fun deleteHard(localId: Long)

    @Query("UPDATE bukti SET remoteId = :remoteId, dirty = 0 WHERE localId = :localId")
    suspend fun markUploaded(localId: Long, remoteId: Int)
}
