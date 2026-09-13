package tf.fog.kasnota.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tf.fog.kasnota.R
import tf.fog.kasnota.data.Prefs
import tf.fog.kasnota.data.backupFileName
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.BukuEntity
import tf.fog.kasnota.data.exportBukuJson
import tf.fog.kasnota.data.importBukuJson

/** Pengaturan berdiri sendiri (route "pengaturan") — dipakai saat belum ada buku sama sekali. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PengaturanScreen(nav: NavController, db: AppDatabase) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
                navigationIcon = {
                    if (nav.previousBackStackEntry != null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PengaturanBody(nav, db, bukuId = null, embedded = false)
        }
    }
}

/**
 * Isi Pengaturan — dipakai baik sebagai tab di dalam buku ([bukuId] terisi) maupun layar
 * berdiri sendiri saat belum ada buku ([bukuId] null). [embedded] true = dipanggil di dalam
 * Column yang sudah scroll (tab), jadi tidak menambah scroll sendiri.
 */
@Composable
fun PengaturanBody(nav: NavController, db: AppDatabase, bukuId: Int?, embedded: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var daftar by remember { mutableStateOf<List<BukuEntity>>(emptyList()) }
    var showDownload by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<BukuEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<BukuEntity?>(null) }
    var pendingBackup by remember { mutableStateOf<String?>(null) }

    suspend fun reload() { daftar = db.bukuDao().getAll() }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(nav.currentBackStackEntry) { reload() }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingBackup
        pendingBackup = null
        if (uri != null && json != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                Toast.makeText(ctx, ctx.getString(R.string.backup_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val newId = try {
                    withContext(Dispatchers.IO) { importBukuJson(db, ctx, uri) }
                } catch (e: Exception) {
                    Toast.makeText(ctx, ctx.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                Prefs.activeBukuId = newId
                Toast.makeText(ctx, ctx.getString(R.string.import_done), Toast.LENGTH_SHORT).show()
                nav.navigate("splash") { popUpTo(0) }
            }
        }
    }

    val content: @Composable () -> Unit = {
        if (bukuId != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDownload = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.download_report), maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val json = withContext(Dispatchers.IO) { exportBukuJson(db, bukuId) }
                            val nama = daftar.firstOrNull { it.id == bukuId }?.nama ?: "buku"
                            pendingBackup = json
                            exporter.launch(backupFileName(nama))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.export_backup), maxLines = 1)
                }
                OutlinedButton(
                    onClick = { importer.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.import_backup), maxLines = 1)
                }
            }
            Spacer(Modifier.height(20.dp))
        } else {
            OutlinedButton(
                onClick = { importer.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.import_backup))
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = { nav.navigate("buku_baru") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(stringResource(R.string.add_cashbook))
        }

        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.your_cashbooks), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (daftar.isEmpty()) {
            Text(
                stringResource(R.string.empty_cashbooks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        daftar.forEach { b ->
            val aktif = b.id == bukuId
            Card(
                onClick = {
                    if (!aktif) {
                        Prefs.activeBukuId = b.id
                        nav.navigate("splash") { popUpTo(0) }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (aktif) Primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(b.nama, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (aktif) {
                            Text(
                                stringResource(R.string.cashbook_open),
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                            )
                        }
                    }
                    IconButton(onClick = { renameTarget = b }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename_cashbook))
                    }
                    IconButton(onClick = { deleteTarget = b }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_cashbook), tint = KeluarColor)
                    }
                }
            }
        }
    }

    if (embedded) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) { content() }
    } else {
        content()
    }

    if (showDownload && bukuId != null) {
        DownloadDialog(db, bukuId, onDismiss = { showDownload = false })
    }

    renameTarget?.let { target ->
        var nama by remember(target.id) { mutableStateOf(target.nama) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_cashbook)) },
            text = {
                OutlinedTextField(
                    value = nama, onValueChange = { nama = it }, singleLine = true,
                    label = { Text(stringResource(R.string.cashbook_name_label)) },
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = nama.trim()
                    if (v.isNotEmpty()) scope.launch {
                        db.bukuDao().update(target.copy(nama = v))
                        reload()
                    }
                    renameTarget = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_cashbook_q)) },
            text = { Text(stringResource(R.string.delete_cashbook_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            for (k in db.kelompokDao().getAll(target.id)) {
                                tf.fog.kasnota.data.softDeleteKelompokLocal(db, k)
                            }
                            for (t in db.transaksiDao().getAllForSaldo(target.id)) {
                                tf.fog.kasnota.data.softDeleteTransaksiLocal(db, t)
                            }
                            db.bukuDao().deleteById(target.id)
                        }
                        if (Prefs.activeBukuId == target.id) Prefs.activeBukuId = null
                        deleteTarget = null
                        if (target.id == bukuId) {
                            nav.navigate("splash") { popUpTo(0) }
                        } else {
                            reload()
                        }
                    }
                }) { Text(stringResource(R.string.action_delete), color = KeluarColor) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
