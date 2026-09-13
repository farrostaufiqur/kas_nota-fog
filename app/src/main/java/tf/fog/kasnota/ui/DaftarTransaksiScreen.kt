package tf.fog.kasnota.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import tf.fog.kasnota.R
import tf.fog.kasnota.data.DisplayRow
import tf.fog.kasnota.data.LocalPage
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.BukuEntity
import tf.fog.kasnota.data.listTransaksiWithSaldo
import java.util.Calendar

/** Isi tab "Transaksi": filter tanggal (date picker) + cari teks, daftar kartu berwarna hijau/merah. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftarTransaksiBody(nav: NavController, db: AppDatabase, bukuId: Int, buku: BukuEntity?) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(1) }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(LocalPage(emptyList(), 1, 1, 0)) }

    suspend fun reload() {
        result = listTransaksiWithSaldo(
            db, bukuId,
            dateFrom.ifBlank { null }, dateTo.ifBlank { null }, q.ifBlank { null }, page,
        )
    }

    LaunchedEffect(page, dateFrom, dateTo, q) { reload() }
    LaunchedEffect(nav.currentBackStackEntry) { reload() }

    val isEditor = true

    fun openPicker(current: String, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val parts = current.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size == 3) cal.set(parts[0], parts[1] - 1, parts[2])
        DatePickerDialog(
            context,
            { _, year, month, day -> onPicked("%04d-%02d-%02d".format(year, month + 1, day)) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = dateFrom,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.date_from)) },
                    trailingIcon = {
                        if (dateFrom.isNotEmpty()) {
                            IconButton(onClick = { dateFrom = ""; page = 1 }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_filter))
                            }
                        } else {
                            IconButton(onClick = { openPicker(dateFrom) { dateFrom = it; page = 1 } }) {
                                Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.pick_date))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = dateTo,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.date_to)) },
                    trailingIcon = {
                        if (dateTo.isNotEmpty()) {
                            IconButton(onClick = { dateTo = ""; page = 1 }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_filter))
                            }
                        } else {
                            IconButton(onClick = { openPicker(dateTo) { dateTo = it; page = 1 } }) {
                                Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.pick_date))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = q,
                onValueChange = { q = it; page = 1 },
                label = { Text(stringResource(R.string.search_transactions)) },
                placeholder = { Text(stringResource(R.string.search_transactions_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (q.isNotEmpty()) {
                        IconButton(onClick = { q = ""; page = 1 }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )

            Text(
                stringResource(R.string.count_transactions, result.total),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = if (result.pages > 1) 88.dp else 12.dp),
            ) {
                items(result.items) { row ->
                    when (row) {
                        is DisplayRow.Standalone -> {
                            val e = row.entity
                            val isMasuk = e.masuk > 0
                            val amount = if (isMasuk) e.masuk else e.keluar
                            val tint = if (isMasuk) MasukColor else KeluarColor
                            Card(
                                onClick = { nav.navigate("form/$bukuId/${e.localId}") },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f)),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            e.jenis?.takeIf { it.isNotBlank() } ?: stringResource(R.string.label_umum),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tint,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        val nama = if (!e.kegiatan.isNullOrBlank()) "${e.kegiatan} - ${e.deskripsi}" else e.deskripsi
                                        Text(
                                            nama,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            e.tanggal,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF6B7280),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        formatRupiah(amount),
                                        color = tint,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        is DisplayRow.Kelompok -> {
                            Card(
                                onClick = { nav.navigate("kelompok/$bukuId/${row.kelompok.localId}") },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val judul = row.kelompok.kegiatan ?: stringResource(R.string.group_default_title)
                                            val jml = stringResource(R.string.count_transactions_short, row.count)
                                            Text(
                                                "$judul — $jml",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            row.tanggal,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF6B7280),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (row.masuk > 0) {
                                            Text(formatRupiah(row.masuk), color = MasukColor, fontWeight = FontWeight.Bold)
                                        }
                                        if (row.keluar > 0) {
                                            Text(formatRupiah(row.keluar), color = KeluarColor, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

        if (result.pages > 1) {
            FloatingPagination(
                page = result.page,
                pages = result.pages,
                onPageChange = { page = it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }

        if (isEditor) {
            FloatingActionButton(
                onClick = { nav.navigate("form/$bukuId/0") },
                modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_transaction))
            }
        }
    }
}

/** Bar pagination melayang: panah bulat (« pertama, ‹ sebelumnya, › berikutnya, » terakhir —
 * yang tak relevan di ujung disembunyikan) + pil elips angka halaman yang bisa ditekan untuk
 * lompat langsung ke halaman tertentu. Dipakai juga di tab Kelompok & daftar anggota kelompok
 * (lihat KelompokScreen.kt) — bukan private lagi supaya bisa dipakai ulang di file lain. */
@Composable
fun FloatingPagination(
    page: Int,
    pages: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showJumpDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (page > 1) {
            PageNavButton(Icons.Filled.FirstPage, stringResource(R.string.page_first)) { onPageChange(1) }
            PageNavButton(Icons.Filled.ChevronLeft, stringResource(R.string.page_prev)) { onPageChange(page - 1) }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Primary)
                .clickable { showJumpDialog = true }
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                "$page / $pages",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (page < pages) {
            PageNavButton(Icons.Filled.ChevronRight, stringResource(R.string.page_next)) { onPageChange(page + 1) }
            PageNavButton(Icons.AutoMirrored.Filled.LastPage, stringResource(R.string.page_last)) { onPageChange(pages) }
        }
    }

    if (showJumpDialog) {
        JumpToPageDialog(
            currentPage = page,
            totalPages = pages,
            onDismiss = { showJumpDialog = false },
            onJump = { onPageChange(it); showJumpDialog = false },
        )
    }
}

@Composable
fun PageNavButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/** Modal lompat ke halaman tertentu — input divalidasi, tidak boleh di luar rentang 1..totalPages. */
@Composable
fun JumpToPageDialog(currentPage: Int, totalPages: Int, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    var text by remember { mutableStateOf(currentPage.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val outOfRange = stringResource(R.string.page_out_of_range, totalPages)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_goto)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }; error = null },
                    label = { Text(stringResource(R.string.page_number_label, totalPages)) },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = text.toIntOrNull()
                if (n == null || n < 1 || n > totalPages) {
                    error = outOfRange
                } else {
                    onJump(n)
                }
            }) { Text(stringResource(R.string.page_goto)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
