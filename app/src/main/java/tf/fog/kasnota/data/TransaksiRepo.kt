package tf.fog.kasnota.data

import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.KelompokEntity
import tf.fog.kasnota.data.db.TransaksiEntity
import java.time.LocalDate
import java.time.YearMonth

/** Baris daftar transaksi utama — [Standalone] = transaksi biasa, [Kelompok] = ringkasan
 * (total masuk/keluar semua anggota) 1 kelompok, sama seperti dashboard.html backend. */
sealed class DisplayRow {
    abstract val tanggal: String
    abstract val sortId: Long
    abstract val saldo: Int

    data class Standalone(val entity: TransaksiEntity, override val saldo: Int) : DisplayRow() {
        override val tanggal get() = entity.tanggal
        override val sortId get() = entity.localId
    }

    data class Kelompok(
        val kelompok: KelompokEntity,
        val masuk: Int,
        val keluar: Int,
        val count: Int,
        override val saldo: Int,
        override val sortId: Long,
    ) : DisplayRow() {
        override val tanggal get() = kelompok.tanggal
    }
}

data class LocalPage(val items: List<DisplayRow>, val page: Int, val pages: Int, val total: Int)

data class KelompokSummary(val kelompok: KelompokEntity, val masuk: Int, val keluar: Int, val count: Int)

/** Semua kelompok buku ini + total masuk/keluar/anggota masing-masing — dipakai tab Kelompok
 * (daftar khusus kelompok, terpisah dari daftar transaksi utama). */
suspend fun listKelompokSummaries(db: AppDatabase, bukuId: Int): List<KelompokSummary> {
    return db.kelompokDao().getAll(bukuId).map { k ->
        val anggota = db.transaksiDao().listForKelompok(k.localId)
        KelompokSummary(k, anggota.sumOf { it.masuk }, anggota.sumOf { it.keluar }, anggota.size)
    }
}

data class KelompokPage(val items: List<KelompokSummary>, val page: Int, val pages: Int, val total: Int)

/** Versi terfilter (rentang tanggal + cari nama kegiatan) & terpaginasi dari
 * [listKelompokSummaries] — dasar tab Kelompok, sama pola paginasi dgn [listTransaksiWithSaldo]. */
suspend fun listKelompokPage(
    db: AppDatabase,
    bukuId: Int,
    dateFrom: String?,
    dateTo: String?,
    q: String?,
    page: Int,
    perPage: Int = 20,
): KelompokPage {
    val all = listKelompokSummaries(db, bukuId)
    val qLower = q?.trim()?.lowercase().takeUnless { it.isNullOrEmpty() }
    val filtered = all.filter { s ->
        val matchesDate = (dateFrom == null || s.kelompok.tanggal >= dateFrom) &&
            (dateTo == null || s.kelompok.tanggal <= dateTo)
        val matchesQ = qLower == null || (s.kelompok.kegiatan?.lowercase()?.contains(qLower) == true)
        matchesDate && matchesQ
    }
    val total = filtered.size
    val pages = if (total == 0) 1 else (total + perPage - 1) / perPage
    val safePage = page.coerceIn(1, pages)
    val fromIdx = (safePage - 1) * perPage
    return KelompokPage(filtered.drop(fromIdx).take(perPage), safePage, pages, total)
}

data class PeriodSummary(val masuk: Int, val keluar: Int)
data class BerandaSummary(
    val saldo: Int,
    val pekanIni: PeriodSummary,
    val bulanIni: PeriodSummary,
    val tahunIni: PeriodSummary,
)

enum class Period { PEKAN, BULAN, TAHUN }

/** Rentang tanggal [dari, sampai] (inklusif), rolling relatif terhadap hari ini — BUKAN
 * periode kalender (mis. "bulan ini" bukan tanggal 1 s/d hari ini, tapi 30 hari terakhir). */
private fun periodRange(period: Period): Pair<LocalDate, LocalDate> {
    val today = LocalDate.now()
    return when (period) {
        Period.PEKAN -> today.minusDays(6) to today // 7 hari terakhir
        Period.BULAN -> today.minusDays(29) to today // 30 hari terakhir
        Period.TAHUN -> today.minusMonths(11).withDayOfMonth(1) to today // 12 bulan terakhir
    }
}

/** Ringkasan Beranda: saldo total + rekap masuk/keluar 7 hari, 30 hari, 12 bulan terakhir. */
suspend fun computeBerandaSummary(db: AppDatabase, bukuId: Int): BerandaSummary {
    val all = db.transaksiDao().getAllForSaldo(bukuId)
    val saldo = all.sumOf { it.masuk - it.keluar }

    fun sumForRange(dari: LocalDate, sampai: LocalDate): PeriodSummary {
        val dariStr = dari.toString()
        val sampaiStr = sampai.toString()
        var masuk = 0
        var keluar = 0
        all.forEach {
            if (it.tanggal >= dariStr && it.tanggal <= sampaiStr) { masuk += it.masuk; keluar += it.keluar }
        }
        return PeriodSummary(masuk, keluar)
    }

    val (pekanDari, pekanSampai) = periodRange(Period.PEKAN)
    val (bulanDari, bulanSampai) = periodRange(Period.BULAN)
    val (tahunDari, tahunSampai) = periodRange(Period.TAHUN)

    return BerandaSummary(
        saldo = saldo,
        pekanIni = sumForRange(pekanDari, pekanSampai),
        bulanIni = sumForRange(bulanDari, bulanSampai),
        tahunIni = sumForRange(tahunDari, tahunSampai),
    )
}

data class JenisSlice(val jenis: String, val nominal: Int)

/** Distribusi total nominal (masuk+keluar) per jenis, dalam periode terpilih — data pie chart Beranda. */
suspend fun computeJenisDistribution(db: AppDatabase, bukuId: Int, period: Period): List<JenisSlice> {
    val all = db.transaksiDao().getAllForSaldo(bukuId)
    val (dari, sampai) = periodRange(period)
    val dariStr = dari.toString()
    val sampaiStr = sampai.toString()
    return all
        .filter { it.tanggal >= dariStr && it.tanggal <= sampaiStr }
        .groupBy { it.jenis?.takeIf { j -> j.isNotBlank() } ?: "Umum" }
        .map { (jenis, list) -> JenisSlice(jenis, list.sumOf { it.masuk + it.keluar }) }
        .filter { it.nominal > 0 }
        .sortedByDescending { it.nominal }
}

data class BucketCount(val label: String, val count: Int)

// Label hari/bulan mengikuti bahasa perangkat (Locale.getDefault()).
private fun dowLabel(dow: java.time.DayOfWeek): String =
    dow.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
private fun monthLabel(month: Int): String =
    java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())

/** Banyaknya transaksi per hari (7/30 hari terakhir) atau per bulan (12 bulan terakhir) —
 * data diagram garis Beranda. */
suspend fun computeBucketCounts(db: AppDatabase, bukuId: Int, period: Period): List<BucketCount> {
    val all = db.transaksiDao().getAllForSaldo(bukuId)
    val (dari, sampai) = periodRange(period)
    return when (period) {
        Period.PEKAN -> (0..6).map { i ->
            val d = dari.plusDays(i.toLong())
            BucketCount(dowLabel(d.dayOfWeek), all.count { it.tanggal == d.toString() })
        }
        Period.BULAN -> {
            val days = java.time.temporal.ChronoUnit.DAYS.between(dari, sampai).toInt()
            (0..days).map { i ->
                val d = dari.plusDays(i.toLong())
                BucketCount(d.dayOfMonth.toString(), all.count { it.tanggal == d.toString() })
            }
        }
        Period.TAHUN -> {
            val startYm = YearMonth.from(dari)
            (0..11).map { i ->
                val ym = startYm.plusMonths(i.toLong())
                BucketCount(monthLabel(ym.monthValue), all.count { it.tanggal.startsWith(ym.toString()) })
            }
        }
    }
}

/**
 * Saldo berjalan dihitung on-the-fly di Kotlin (bukan window function SQL — SQLite bawaan
 * Android di minSdk 26 belum tentu dukung window function), persis semantik
 * `query_transaksi_with_saldo` di backend: kumulatif berurutan tanggal+id, baru difilter+paginasi.
 */
suspend fun listTransaksiWithSaldo(
    db: AppDatabase,
    bukuId: Int,
    dateFrom: String?,
    dateTo: String?,
    q: String?,
    page: Int,
    perPage: Int = 20,
): LocalPage {
    val all = db.transaksiDao().getAllForSaldo(bukuId) // ASC tanggal, localId
    var running = 0
    val withSaldo = all.map {
        running += it.masuk - it.keluar
        it to running
    }

    // Anggota kelompok DIGABUNG jadi 1 baris ringkasan (total masuk/keluar + saldo dari
    // anggota yang paling akhir secara kronologis) — sama semantik dgn query_transaksi_with_saldo
    // di backend (services.py), lihat docstring model di sana.
    data class KelompokAcc(
        var masuk: Int = 0, var keluar: Int = 0, var count: Int = 0,
        var saldo: Int = 0, var sortTanggal: String = "", var sortId: Long = 0,
    )
    val grouped = LinkedHashMap<Long, KelompokAcc>()
    val rows = mutableListOf<DisplayRow>()

    for ((e, saldo) in withSaldo) {
        val kid = e.kelompokLocalId
        if (kid == null) {
            rows.add(DisplayRow.Standalone(e, saldo))
            continue
        }
        val acc = grouped.getOrPut(kid) { KelompokAcc() }
        acc.masuk += e.masuk
        acc.keluar += e.keluar
        acc.count += 1
        if (e.tanggal > acc.sortTanggal || (e.tanggal == acc.sortTanggal && e.localId >= acc.sortId)) {
            acc.saldo = saldo
            acc.sortTanggal = e.tanggal
            acc.sortId = e.localId
        }
    }
    for ((kid, acc) in grouped) {
        val k = db.kelompokDao().findByLocalId(kid) ?: continue
        rows.add(DisplayRow.Kelompok(k, acc.masuk, acc.keluar, acc.count, acc.saldo, acc.sortId))
    }

    val qLower = q?.trim()?.lowercase().takeUnless { it.isNullOrEmpty() }
    val filtered = rows.filter { row ->
        val matchesDate = (dateFrom == null || row.tanggal >= dateFrom) && (dateTo == null || row.tanggal <= dateTo)
        val matchesQ = qLower == null || when (row) {
            is DisplayRow.Standalone -> listOfNotNull(
                row.entity.deskripsi, row.entity.kegiatan, row.entity.jenis,
                row.entity.masuk.toString(), row.entity.keluar.toString(),
            ).any { it.lowercase().contains(qLower) }
            is DisplayRow.Kelompok -> listOfNotNull(
                row.kelompok.kegiatan, row.masuk.toString(), row.keluar.toString(),
            ).any { it.lowercase().contains(qLower) }
        }
        matchesDate && matchesQ
    }.sortedWith(compareByDescending<DisplayRow> { it.tanggal }.thenByDescending { it.sortId })

    val total = filtered.size
    val pages = if (total == 0) 1 else (total + perPage - 1) / perPage
    val safePage = page.coerceIn(1, pages)
    val fromIdx = (safePage - 1) * perPage
    val pageItems = filtered.drop(fromIdx).take(perPage)
    return LocalPage(pageItems, safePage, pages, total)
}

/** Satu baris laporan (PDF/Excel) — transaksi biasa & anggota kelompok sama-sama jadi
 * baris tersendiri, urut kronologis, dengan saldo berjalan. */
data class ReportRow(
    val localId: Long,
    val tanggal: String,
    val kegiatan: String,
    val jenis: String,
    val deskripsi: String,
    val masuk: Int,
    val keluar: Int,
    val saldo: Int,
)

data class ReportData(
    val rows: List<ReportRow>,
    val totalMasuk: Int,
    val totalKeluar: Int,
    val saldoAkhir: Int,
)

/** Tabel keuangan penuh untuk rentang [from]..[to] (inklusif, yyyy-MM-dd). Saldo dihitung
 * kumulatif sejak transaksi paling awal lalu difilter rentang — sama semantik dgn
 * [listTransaksiWithSaldo]. */
suspend fun flatTransaksiForRange(db: AppDatabase, bukuId: Int, from: String, to: String): ReportData {
    val all = db.transaksiDao().getAllForSaldo(bukuId) // urut tanggal ASC, localId ASC
    val kelompokKegiatan = db.kelompokDao().getAll(bukuId).associate { it.localId to (it.kegiatan ?: "") }
    var running = 0
    val rows = mutableListOf<ReportRow>()
    for (t in all) {
        running += t.masuk - t.keluar
        if (t.tanggal < from || t.tanggal > to) continue
        val keg = t.kegiatan?.takeUnless { it.isBlank() }
            ?: t.kelompokLocalId?.let { kelompokKegiatan[it] }.orEmpty()
        rows.add(
            ReportRow(
                localId = t.localId,
                tanggal = t.tanggal,
                kegiatan = keg,
                jenis = t.jenis.orEmpty(),
                deskripsi = t.deskripsi,
                masuk = t.masuk,
                keluar = t.keluar,
                saldo = running,
            )
        )
    }
    return ReportData(rows, rows.sumOf { it.masuk }, rows.sumOf { it.keluar }, rows.lastOrNull()?.saldo ?: running)
}
