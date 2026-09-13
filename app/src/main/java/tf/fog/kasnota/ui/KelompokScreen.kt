package tf.fog.kasnota.ui

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import tf.fog.kasnota.R
import tf.fog.kasnota.data.KelompokPage
import tf.fog.kasnota.data.MAX_BUKTI_PER_KELOMPOK_ANGGOTA
import tf.fog.kasnota.data.createCameraCaptureUri
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.BukuEntity
import tf.fog.kasnota.data.db.KelompokEntity
import tf.fog.kasnota.data.db.TransaksiEntity
import tf.fog.kasnota.data.deleteKelompokAnggotaLocal
import tf.fog.kasnota.data.listKelompokPage
import tf.fog.kasnota.data.saveBuktiLocal
import tf.fog.kasnota.data.saveKelompokAnggotaLocal
import tf.fog.kasnota.data.saveKelompokLocal
import tf.fog.kasnota.data.softDeleteKelompokLocal
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

/** Isi tab "Kelompok": daftar SEMUA kelompok buku ini (khusus, tanpa transaksi biasa
 * bercampur) — filter tanggal + cari nama kegiatan + paging (sama gaya dgn tab Transaksi) +
 * FAB tambah kelompok baru untuk editor. Beda dari kartu kelompok yang muncul digabung di
 * tab Transaksi — di sini murni daftar kelompok saja. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KelompokListBody(nav: NavController, db: AppDatabase, bukuId: Int, buku: BukuEntity?) {
    val context = LocalContext.current
    val isEditor = true
    var page by remember { mutableIntStateOf(1) }
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(KelompokPage(emptyList(), 1, 1, 0)) }

    suspend fun reload() {
        result = listKelompokPage(
            db, bukuId,
            dateFrom.ifBlank { null }, dateTo.ifBlank { null }, q.ifBlank { null }, page,
        )
    }

    LaunchedEffect(page, dateFrom, dateTo, q) { reload() }
    LaunchedEffect(nav.currentBackStackEntry) { reload() }

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
                label = { Text(stringResource(R.string.search_groups)) },
                placeholder = { Text("Nama kegiatan") },
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
stringResource(R.string.count_groups, result.total),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            if (result.items.isEmpty()) {
                Text(
                    stringResource(R.string.empty_groups),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = if (result.pages > 1 || isEditor) 88.dp else 12.dp),
                ) {
                    items(result.items, key = { it.kelompok.localId }) { s ->
                        Card(
                            onClick = { nav.navigate("kelompok/$bukuId/${s.kelompok.localId}") },
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
                                    Text(
                                        s.kelompok.kegiatan ?: stringResource(R.string.group_default_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
stringResource(R.string.group_members_count, s.count),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF6B7280),
                                    )
                                    Text(s.kelompok.tanggal, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    if (s.masuk > 0) {
                                        Text(formatRupiah(s.masuk), color = MasukColor, fontWeight = FontWeight.Bold)
                                    }
                                    if (s.keluar > 0) {
                                        Text(formatRupiah(s.keluar), color = KeluarColor, fontWeight = FontWeight.Bold)
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
                onClick = { nav.navigate("kelompok_baru/$bukuId") },
                modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_group))
            }
        }
    }
}

/** Buat kelompok baru — cuma tanggal (anggota pertama) & kegiatan (tetap sejak dibuat). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KelompokBuatScreen(nav: NavController, db: AppDatabase, bukuId: Int) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ctx = context
    val todayStr = remember { LocalDate.now().toString() }

    var tanggal by remember { mutableStateOf(todayStr) }
    var kegiatan by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun openDatePicker() {
        val parts = tanggal.split("-").mapNotNull { it.toIntOrNull() }
        val cal = Calendar.getInstance()
        if (parts.size == 3) cal.set(parts[0], parts[1] - 1, parts[2])
        DatePickerDialog(
            context,
            { _, year, month, day -> tanggal = "%04d-%02d-%02d".format(year, month + 1, day) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_add_group)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                stringResource(R.string.group_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = tanggal,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.field_date)) },
                trailingIcon = {
                    IconButton(onClick = { openDatePicker() }) {
                        Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.pick_date))
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OpsiComboBox(db, bukuId, "kegiatan", stringResource(R.string.field_activity), kegiatan, { kegiatan = it })

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            LoadingButton(
                text = stringResource(R.string.create_group),
                loading = saving,
                onClick = {
                    if (kegiatan.isBlank()) {
                        error = ctx.getString(R.string.err_activity_required)
                        return@LoadingButton
                    }
                    error = null
                    saving = true
                    scope.launch {
                        val k = saveKelompokLocal(db, bukuId, tanggal, kegiatan)
                        saving = false
                        nav.navigate("kelompok/$bukuId/${k.localId}") {
                            popUpTo("kelompok_baru/$bukuId") { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}

/** Detail 1 kelompok: total masuk/keluar + daftar anggota (cari + paging spt tab Transaksi),
 * tambah/edit/hapus anggota, hapus kelompok. FAB "Tambah Anggota" & pagination sengaja
 * diposisikan manual (bukan slot floatingActionButton bawaan Scaffold) supaya bisa diberi
 * jarak dari tombol "Hapus Kelompok" di bawahnya — kalau pakai slot bawaan, FAB melayang di
 * atas SELURUH body tanpa peduli isi Column, jadi menutupi tombol itu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KelompokDetailScreen(nav: NavController, db: AppDatabase, bukuId: Int, kelompokLocalId: Long) {
    val scope = rememberCoroutineScope()
    var buku by remember { mutableStateOf<BukuEntity?>(null) }
    var kelompok by remember { mutableStateOf<KelompokEntity?>(null) }
    var anggota by remember { mutableStateOf<List<TransaksiEntity>>(emptyList()) }
    var confirmHapusKelompok by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    val perPage = 20

    suspend fun reload() {
        kelompok = db.kelompokDao().findByLocalId(kelompokLocalId)
        anggota = db.transaksiDao().listForKelompok(kelompokLocalId)
    }

    LaunchedEffect(bukuId) { buku = db.bukuDao().getById(bukuId) }
    LaunchedEffect(kelompokLocalId) { reload() }
    LaunchedEffect(nav.currentBackStackEntry) { reload() }

    val isEditor = true
    val totalMasuk = anggota.sumOf { it.masuk }
    val totalKeluar = anggota.sumOf { it.keluar }

    val filteredAnggota = remember(anggota, q) {
        val qLower = q.trim().lowercase()
        if (qLower.isEmpty()) {
            anggota
        } else {
            anggota.filter { a ->
                listOf(a.jenis ?: "", a.deskripsi, a.masuk.toString(), a.keluar.toString())
                    .any { it.lowercase().contains(qLower) }
            }
        }
    }
    val pages = if (filteredAnggota.isEmpty()) 1 else (filteredAnggota.size + perPage - 1) / perPage
    val safePage = page.coerceIn(1, pages)
    val pageItems = filteredAnggota.drop((safePage - 1) * perPage).take(perPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kelompok?.kegiatan ?: stringResource(R.string.group_default_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                // Masuk & Keluar kartu terpisah, sejajar kiri-kanan — masing2 dapat lebar penuh
                // 1/2 kolom (bukan berbagi 1 kartu) supaya nominal besar tidak terpotong.
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Masuk", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                        Text(
                            formatRupiah(totalMasuk),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MasukColor,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Keluar", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                        Text(
                            formatRupiah(totalKeluar),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = KeluarColor,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = q,
                onValueChange = { q = it; page = 1 },
                label = { Text(stringResource(R.string.search_members)) },
                placeholder = { Text(stringResource(R.string.search_members_hint)) },
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
stringResource(R.string.count_members, filteredAnggota.size),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = if (isEditor) 160.dp else if (pages > 1) 88.dp else 12.dp),
            ) {
                items(pageItems, key = { it.localId }) { a ->
                    val isMasuk = a.masuk > 0
                    val isKosong = a.masuk == 0 && a.keluar == 0
                    val amount = if (isMasuk) a.masuk else a.keluar
                    val tint = if (isKosong) Color(0xFF6B7280) else if (isMasuk) MasukColor else KeluarColor
                    Card(
                        onClick = { nav.navigate("kelompok_anggota/$bukuId/$kelompokLocalId/${a.localId}") },
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
                                    a.jenis?.takeIf { it.isNotBlank() } ?: stringResource(R.string.label_umum),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(a.deskripsi, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(a.tanggal, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(formatRupiah(amount), color = tint, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (pageItems.isEmpty()) {
                    item {
                        Text(
                            if (anggota.isEmpty()) stringResource(R.string.empty_members) else stringResource(R.string.no_matching_members),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
            }
        }

        if (pages > 1) {
            FloatingPagination(
                page = safePage,
                pages = pages,
                onPageChange = { page = it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (isEditor) 88.dp else 16.dp),
            )
        }

        if (isEditor) {
            FloatingActionButton(
                onClick = { nav.navigate("kelompok_anggota/$bukuId/$kelompokLocalId/0") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_member))
            }
        }

        // Layar ini tidak punya bottom navbar (beda dari BukuScaffold) — tombol "Hapus Kelompok"
        // sengaja MENGGANTIKAN posisi itu: bar melayang flush di dasar layar (spt NavigationBar),
        // BUKAN sibling Column setelah LazyColumn yg di-weight (itu penyebab wilayah kosong
        // sebelumnya — box LazyColumn dipaksa mengisi sisa layar, jadi jarak ke tombol jadi besar
        // kalau anggotanya sedikit). Polanya sama dgn FAB & FloatingPagination di atas: overlay
        // pd Box, posisi tetap di layar berapa pun panjang isi list (persis spt tab Transaksi).
        if (isEditor) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                tonalElevation = 3.dp,
            ) {
                OutlinedButton(
                    onClick = { confirmHapusKelompok = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KeluarColor),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(stringResource(R.string.delete_group))
                }
            }
        }
        }

        if (confirmHapusKelompok) {
            AlertDialog(
                onDismissRequest = { confirmHapusKelompok = false },
                title = { Text(stringResource(R.string.delete_group_q)) },
                text = { Text(stringResource(R.string.delete_group_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        val k = kelompok ?: return@TextButton
                        scope.launch {
                            softDeleteKelompokLocal(db, k)
                            playDeleteHintSound()
                            confirmHapusKelompok = false
                            nav.popBackStack()
                        }
                    }) { Text(stringResource(R.string.action_delete), color = KeluarColor) }
                },
                dismissButton = { TextButton(onClick = { confirmHapusKelompok = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

/** Tambah (localId=0) / edit 1 anggota kelompok — tanggal, tipe, jenis, deskripsi, jumlah,
 * maks 3 foto bukti. Tanpa kegiatan (ikut kelompok induk). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KelompokAnggotaFormScreen(nav: NavController, db: AppDatabase, bukuId: Int, kelompokLocalId: Long, localId: Long) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ctx = context
    val todayStr = remember { LocalDate.now().toString() }

    var kelompok by remember { mutableStateOf<KelompokEntity?>(null) }
    var buku by remember { mutableStateOf<BukuEntity?>(null) }
    var existing by remember { mutableStateOf<TransaksiEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }

    var tanggal by remember { mutableStateOf(todayStr) }
    var tipe by remember { mutableStateOf("masuk") }
    var jenis by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var jumlahRaw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmHapusBukti by remember { mutableStateOf<BuktiItem.Saved?>(null) }
    var daftarBukti by remember { mutableStateOf<List<tf.fog.kasnota.data.db.BuktiEntity>>(emptyList()) }
    var pendingBukti by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(bukuId) { buku = db.bukuDao().getById(bukuId) }

    LaunchedEffect(kelompokLocalId, localId) {
        kelompok = db.kelompokDao().findByLocalId(kelompokLocalId)
        if (localId != 0L) {
            val t = db.transaksiDao().findByLocalId(localId)
            existing = t
            if (t != null) {
                tanggal = t.tanggal
                tipe = if (t.masuk > 0) "masuk" else "keluar"
                jenis = t.jenis ?: ""
                deskripsi = t.deskripsi
                jumlahRaw = (if (t.masuk > 0) t.masuk else t.keluar).toString()
                daftarBukti = db.buktiDao().listForTransaksi(t.localId)
            }
        } else {
            kelompok?.let { tanggal = it.tanggal }
        }
        loaded = true
    }

    val readOnly = false
    val jumlahSlotTerpakai = if (existing != null) daftarBukti.size else pendingBukti.size

    fun tambahBukti(uri: Uri) {
        val e = existing
        if (e != null) {
            scope.launch { saveBuktiLocal(db, context, bukuId, e.localId, uri)?.let { daftarBukti = daftarBukti + it } }
        } else {
            pendingBukti = pendingBukti + uri
        }
    }

    val fotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val sisaSlot = MAX_BUKTI_PER_KELOMPOK_ANGGOTA - jumlahSlotTerpakai
        uris.take(sisaSlot.coerceAtLeast(0)).forEach { tambahBukti(it) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) tambahBukti(uri)
        pendingCameraUri = null
    }
    fun launchCamera() {
        if (jumlahSlotTerpakai >= MAX_BUKTI_PER_KELOMPOK_ANGGOTA) return
        val uri = createCameraCaptureUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    fun openDatePicker() {
        val parts = tanggal.split("-").mapNotNull { it.toIntOrNull() }
        val cal = Calendar.getInstance()
        if (parts.size == 3) cal.set(parts[0], parts[1] - 1, parts[2])
        DatePickerDialog(
            context,
            { _, year, month, day -> tanggal = "%04d-%02d-%02d".format(year, month + 1, day) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    fun validate(): String? {
        if (tanggal.isBlank()) return ctx.getString(R.string.err_date_required)
        if (tanggal > todayStr) return ctx.getString(R.string.err_date_future)
        if (deskripsi.isBlank()) return ctx.getString(R.string.err_description_required)
        // Anggota kelompok boleh jumlah 0/kosong (mis. belum bayar) — beda dari transaksi biasa.
        return null
    }

    val pageTint = if (tipe == "masuk") MasukColor.copy(alpha = 0.08f) else KeluarColor.copy(alpha = 0.08f)

    Scaffold(
        containerColor = pageTint,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            localId == 0L -> stringResource(R.string.title_add_member)
                            else -> stringResource(R.string.title_edit_member)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            kelompok?.kegiatan?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = Primary)
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = tanggal,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.field_date)) },
                trailingIcon = {
                    IconButton(onClick = { openDatePicker() }, enabled = !readOnly) {
                        Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.pick_date))
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.field_type), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            SwitchTipe(tipe, { tipe = it }, enabled = !readOnly)

            Spacer(Modifier.height(12.dp))
            if (readOnly) {
                OutlinedTextField(
                    value = jenis.ifBlank { "-" }, onValueChange = {}, readOnly = true,
                    label = { Text("Jenis") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OpsiComboBox(db, bukuId, "jenis", "Jenis (opsional)", jenis, { jenis = it })
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = deskripsi,
                onValueChange = { deskripsi = it },
                readOnly = readOnly,
                label = { Text(stringResource(R.string.field_description)) },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            RupiahField(jumlahRaw, { jumlahRaw = it }, readOnly = readOnly)

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.receipts_max, MAX_BUKTI_PER_KELOMPOK_ANGGOTA), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            val buktiItems = if (existing != null) daftarBukti.map { BuktiItem.Saved(it) } else pendingBukti.map { BuktiItem.Pending(it) }
            BuktiSection(
                items = buktiItems,
                readOnly = readOnly,
                onTambahKamera = { launchCamera() },
                onTambahGaleri = { fotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onHapus = { item ->
                    when (item) {
                        is BuktiItem.Saved -> confirmHapusBukti = item
                        is BuktiItem.Pending -> pendingBukti = pendingBukti.filter { it != item.uri }
                    }
                },
                maxBukti = MAX_BUKTI_PER_KELOMPOK_ANGGOTA,
            )

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (!readOnly) {
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (existing != null) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = KeluarColor),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text(stringResource(R.string.action_delete)) }
                        Spacer(Modifier.width(12.dp))
                    }
                    LoadingButton(
                        text = stringResource(R.string.action_save),
                        loading = saving,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val err = validate()
                            if (err != null) {
                                error = err
                                return@LoadingButton
                            }
                            val k = kelompok ?: return@LoadingButton
                            error = null
                            saving = true
                            scope.launch {
                                val t = saveKelompokAnggotaLocal(db, k, existing, tanggal, jenis, deskripsi, tipe, jumlahRaw.toIntOrNull() ?: 0)
                                if (existing == null) {
                                    for (uri in pendingBukti) saveBuktiLocal(db, context, bukuId, t.localId, uri)
                                }
                                saving = false
                                nav.popBackStack()
                            }
                        },
                    )
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.delete_member_q)) },
                text = { Text(stringResource(R.string.undo_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        val e = existing ?: return@TextButton
                        scope.launch {
                            deleteKelompokAnggotaLocal(db, e)
                            playDeleteHintSound()
                            confirmDelete = false
                            nav.popBackStack()
                        }
                    }) { Text(stringResource(R.string.action_delete), color = KeluarColor) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }

        confirmHapusBukti?.let { item ->
            AlertDialog(
                onDismissRequest = { confirmHapusBukti = null },
                title = { Text(stringResource(R.string.delete_receipt_q)) },
                text = { Text(stringResource(R.string.delete_receipt_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            tf.fog.kasnota.data.softDeleteBuktiLocal(db, item.entity)
                            playDeleteHintSound()
                            daftarBukti = daftarBukti.filter { it.localId != item.entity.localId }
                            confirmHapusBukti = null
                        }
                    }) { Text(stringResource(R.string.action_delete), color = KeluarColor) }
                },
                dismissButton = { TextButton(onClick = { confirmHapusBukti = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}
