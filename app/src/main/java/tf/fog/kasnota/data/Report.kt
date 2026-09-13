package tf.fog.kasnota.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import tf.fog.kasnota.R
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.BukuEntity
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ReportFormat { PDF, XLSX }

data class ReportBundle(
    val buku: BukuEntity,
    val data: ReportData,
    val from: String,
    val to: String,
    /** localId transaksi -> daftar path file bukti (untuk lampiran PDF). */
    val buktiPaths: Map<Long, List<String>>,
)

private fun rp(n: Int): String {
    val sign = if (n < 0) "-" else ""
    val g = kotlin.math.abs(n).toString().reversed().chunked(3).joinToString(".").reversed()
    return "${sign}Rp $g"
}

suspend fun gatherReport(db: AppDatabase, bukuId: Int, from: String, to: String): ReportBundle {
    val buku = db.bukuDao().getById(bukuId) ?: error("buku tidak ditemukan")
    val data = flatTransaksiForRange(db, bukuId, from, to)
    val buktiPaths = HashMap<Long, List<String>>()
    for (row in data.rows) {
        val paths = db.buktiDao().listForTransaksi(row.localId).mapNotNull { it.localFilePath }
        if (paths.isNotEmpty()) buktiPaths[row.localId] = paths
    }
    return ReportBundle(buku, data, from, to, buktiPaths)
}

/** Buat file laporan di cache app ({cacheDir}/exports/) — pemanggil menyalinnya ke lokasi
 * pilihan user lewat SAF. */
fun buildReportFile(context: Context, bundle: ReportBundle, format: ReportFormat): File {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val safeName = bundle.buku.nama.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").replace(Regex("\\s+"), "_")
        .ifBlank { "buku" }
    val base = "${safeName}_${bundle.from}_${bundle.to}"
    return when (format) {
        ReportFormat.PDF -> File(dir, "$base.pdf").also { writePdf(context, bundle, it) }
        ReportFormat.XLSX -> File(dir, "$base.xlsx").also { writeXlsx(context, bundle, it) }
    }
}

// ---------------------------------------------------------------------------- PDF

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 28f

private fun writePdf(context: Context, bundle: ReportBundle, out: File) {
    val doc = PdfDocument()
    val title = Paint().apply { color = Color.BLACK; textSize = 15f; isFakeBoldText = true; isAntiAlias = true }
    val meta = Paint().apply { color = Color.DKGRAY; textSize = 10f; isAntiAlias = true }
    val head = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true; isAntiAlias = true }
    val cell = Paint().apply { color = Color.BLACK; textSize = 9f; isAntiAlias = true }
    val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }

    // kolom: tanggal, kegiatan, jenis, deskripsi, masuk, keluar, saldo
    val cols = floatArrayOf(62f, 86f, 62f, 143f, 62f, 62f, 62f) // total ~539 (usable 539)
    val labels = listOf(
        context.getString(R.string.report_col_date),
        context.getString(R.string.report_col_activity),
        context.getString(R.string.report_col_type),
        context.getString(R.string.report_col_desc),
        context.getString(R.string.report_col_in),
        context.getString(R.string.report_col_out),
        context.getString(R.string.report_col_balance),
    )
    val colX = FloatArray(cols.size + 1)
    colX[0] = MARGIN
    for (i in cols.indices) colX[i + 1] = colX[i] + cols[i]

    val rowH = 16f
    val topAfterHeader = 116f
    val bottom = PAGE_H - MARGIN

    var pageNo = 0
    var totalPages = 1
    // hitung total halaman tabel dulu
    run {
        val perPage = ((bottom - topAfterHeader) / rowH).toInt()
        totalPages = maxOf(1, (bundle.data.rows.size + perPage - 1) / perPage)
    }
    // + halaman lampiran (1 gambar per halaman)
    val attachmentCount = bundle.buktiPaths.values.sumOf { it.size }
    val grandPages = totalPages + attachmentCount

    fun ellipsize(text: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var s = text
        while (s.isNotEmpty() && paint.measureText("$s…") > maxW) s = s.dropLast(1)
        return "$s…"
    }

    var idx = 0
    while (idx < bundle.data.rows.size || pageNo == 0) {
        pageNo++
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        val c = page.canvas
        c.drawText(context.getString(R.string.report_title_table), MARGIN, 42f, title)
        c.drawText(bundle.buku.nama, MARGIN, 62f, meta)
        c.drawText(
            "${context.getString(R.string.report_period)}: ${bundle.from} – ${bundle.to}",
            MARGIN, 78f, meta,
        )
        c.drawText(
            "${context.getString(R.string.report_total)}: ${context.getString(R.string.report_col_in)} ${rp(bundle.data.totalMasuk)}   " +
                "${context.getString(R.string.report_col_out)} ${rp(bundle.data.totalKeluar)}   " +
                "${context.getString(R.string.report_col_balance)} ${rp(bundle.data.saldoAkhir)}",
            MARGIN, 94f, meta,
        )
        c.drawText(
            context.getString(R.string.report_page, pageNo, grandPages),
            PAGE_W - MARGIN - 90f, 42f, meta,
        )

        // header tabel
        var y = topAfterHeader
        for (i in labels.indices) c.drawText(labels[i], colX[i] + 2f, y, head)
        y += 4f
        c.drawLine(MARGIN, y, colX.last(), y, line)
        y += rowH

        while (idx < bundle.data.rows.size && y <= bottom) {
            val r = bundle.data.rows[idx]
            val vals = listOf(
                r.tanggal, r.kegiatan, r.jenis, r.deskripsi,
                if (r.masuk > 0) rp(r.masuk) else "", if (r.keluar > 0) rp(r.keluar) else "", rp(r.saldo),
            )
            for (i in vals.indices) {
                c.drawText(ellipsize(vals[i], cell, cols[i] - 4f), colX[i] + 2f, y, cell)
            }
            c.drawLine(MARGIN, y + 3f, colX.last(), y + 3f, line)
            y += rowH
            idx++
        }
        doc.finishPage(page)
        if (idx >= bundle.data.rows.size) break
    }

    // lampiran bukti
    var attachNo = totalPages
    val capPaint = Paint().apply { color = Color.DKGRAY; textSize = 10f; isAntiAlias = true }
    val headPaint = Paint().apply { color = Color.BLACK; textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
    for (row in bundle.data.rows) {
        val paths = bundle.buktiPaths[row.localId] ?: continue
        for (path in paths) {
            attachNo++
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, attachNo).create())
            val c = page.canvas
            c.drawText(context.getString(R.string.report_attachments), MARGIN, 40f, headPaint)
            c.drawText("${row.tanggal} — ${ellipsize(row.deskripsi, capPaint, PAGE_W - 2 * MARGIN)}", MARGIN, 58f, capPaint)
            c.drawText(
                context.getString(R.string.report_page, attachNo, grandPages),
                PAGE_W - MARGIN - 90f, 40f, capPaint,
            )
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
            if (bmp != null) {
                val availW = PAGE_W - 2 * MARGIN
                val availH = PAGE_H - 80f - MARGIN
                val scale = minOf(availW / bmp.width, availH / bmp.height, 1f)
                val w = bmp.width * scale
                val h = bmp.height * scale
                val left = MARGIN + (availW - w) / 2f
                c.drawBitmap(bmp, null, android.graphics.RectF(left, 74f, left + w, 74f + h), null)
                bmp.recycle()
            }
            doc.finishPage(page)
        }
    }

    out.outputStream().use { doc.writeTo(it) }
    doc.close()
}

// ---------------------------------------------------------------------------- XLSX

private fun xmlEsc(s: String): String = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&apos;")

private fun writeXlsx(context: Context, bundle: ReportBundle, out: File) {
    val headers = listOf(
        context.getString(R.string.report_col_date),
        context.getString(R.string.report_col_activity),
        context.getString(R.string.report_col_type),
        context.getString(R.string.report_col_desc),
        context.getString(R.string.report_col_in),
        context.getString(R.string.report_col_out),
        context.getString(R.string.report_col_balance),
    )

    fun colName(i: Int): String {
        var n = i
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        }
        return sb.toString()
    }

    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

    fun rowXml(rowIdx: Int, cells: List<Pair<Boolean, String>>) {
        // Pair<isNumber, value>
        sb.append("<row r=\"${rowIdx + 1}\">")
        cells.forEachIndexed { ci, (isNum, v) ->
            val ref = "${colName(ci)}${rowIdx + 1}"
            if (isNum) {
                sb.append("<c r=\"$ref\"><v>${xmlEsc(v)}</v></c>")
            } else {
                sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlEsc(v)}</t></is></c>")
            }
        }
        sb.append("</row>")
    }

    var r = 0
    rowXml(r++, listOf(false to "${bundle.buku.nama} (${bundle.from} – ${bundle.to})"))
    rowXml(r++, headers.map { false to it })
    for (row in bundle.data.rows) {
        rowXml(
            r++,
            listOf(
                false to row.tanggal,
                false to row.kegiatan,
                false to row.jenis,
                false to row.deskripsi,
                true to row.masuk.toString(),
                true to row.keluar.toString(),
                true to row.saldo.toString(),
            ),
        )
    }
    rowXml(
        r++,
        listOf(
            false to context.getString(R.string.report_total), false to "", false to "", false to "",
            true to bundle.data.totalMasuk.toString(),
            true to bundle.data.totalKeluar.toString(),
            true to bundle.data.saldoAkhir.toString(),
        ),
    )
    sb.append("</sheetData></worksheet>")

    val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
        """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
        """<Default Extension="xml" ContentType="application/xml"/>""" +
        """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
        """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
        """</Types>"""

    val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
        """</Relationships>"""

    val sheetLabel = context.getString(R.string.report_title_table).take(28)
    val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
        """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
        """<sheets><sheet name="${xmlEsc(sheetLabel)}" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
        """</Relationships>"""

    ZipOutputStream(out.outputStream()).use { zip ->
        fun put(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        put("[Content_Types].xml", contentTypes)
        put("_rels/.rels", rootRels)
        put("xl/workbook.xml", workbook)
        put("xl/_rels/workbook.xml.rels", workbookRels)
        put("xl/worksheets/sheet1.xml", sb.toString())
    }
}
