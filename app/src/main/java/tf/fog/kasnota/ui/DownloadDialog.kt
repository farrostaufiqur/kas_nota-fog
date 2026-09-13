package tf.fog.kasnota.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tf.fog.kasnota.R
import tf.fog.kasnota.data.ReportFormat
import tf.fog.kasnota.data.buildReportFile
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.gatherReport
import java.io.File
import java.time.LocalDate
import java.util.Calendar

private const val PDF_MIME = "application/pdf"
private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Modal: rentang tanggal + pilih format (PDF/Excel) → buat laporan, simpan via SAF, lalu buka. */
@Composable
fun DownloadDialog(db: AppDatabase, bukuId: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now().toString() }

    var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var to by remember { mutableStateOf(today) }
    var format by remember { mutableStateOf(ReportFormat.PDF) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    fun openViewer(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { ctx.startActivity(view) }
    }

    // Kontrak dibuat sekali; ekstensi nyata ikut nama file yang di-launch (.pdf/.xlsx),
    // jadi mime generik di sini aman.
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val src = pendingFile
        pendingFile = null
        if (uri != null && src != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { os -> src.inputStream().use { it.copyTo(os) } }
                }
                Toast.makeText(ctx, ctx.getString(R.string.report_saved), Toast.LENGTH_SHORT).show()
                openViewer(src, if (format == ReportFormat.PDF) PDF_MIME else XLSX_MIME)
                onDismiss()
            }
        }
    }

    fun openPicker(current: String, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        current.split("-").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }
            ?.let { cal.set(it[0], it[1] - 1, it[2]) }
        DatePickerDialog(
            ctx,
            { _, y, m, d -> onPicked("%04d-%02d-%02d".format(y, m + 1, d)) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.download_report)) },
        text = {
            Column {
                OutlinedTextField(
                    value = from, onValueChange = {}, readOnly = true,
                    label = { Text(stringResource(R.string.report_start_date)) },
                    trailingIcon = {
                        IconButton(onClick = { openPicker(from) { from = it; error = null } }) {
                            Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.pick_date))
                        }
                    },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = to, onValueChange = {}, readOnly = true,
                    label = { Text(stringResource(R.string.report_end_date)) },
                    trailingIcon = {
                        IconButton(onClick = { openPicker(to) { to = it; error = null } }) {
                            Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.pick_date))
                        }
                    },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.report_format), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row {
                    FilterChip(
                        selected = format == ReportFormat.PDF,
                        onClick = { format = ReportFormat.PDF },
                        label = { Text("PDF") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = format == ReportFormat.XLSX,
                        onClick = { format = ReportFormat.XLSX },
                        label = { Text("Excel") },
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (to < from) {
                        error = ctx.getString(R.string.report_range_invalid)
                        return@TextButton
                    }
                    error = null
                    busy = true
                    scope.launch {
                        try {
                            val bundle = withContext(Dispatchers.IO) { gatherReport(db, bukuId, from, to) }
                            if (bundle.data.rows.isEmpty()) {
                                error = ctx.getString(R.string.report_empty)
                                busy = false
                                return@launch
                            }
                            val file = withContext(Dispatchers.IO) { buildReportFile(ctx, bundle, format) }
                            pendingFile = file
                            busy = false
                            saver.launch(file.name)
                        } catch (e: Exception) {
                            error = ctx.getString(R.string.report_failed)
                            busy = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.action_generate)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
