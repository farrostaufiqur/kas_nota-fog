package tf.fog.kasnota.ui

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import tf.fog.kasnota.R
import tf.fog.kasnota.data.MAX_BUKTI_PER_TRANSAKSI
import tf.fog.kasnota.data.createCameraCaptureUri
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.BukuEntity
import tf.fog.kasnota.data.db.BuktiEntity
import tf.fog.kasnota.data.db.TransaksiEntity
import tf.fog.kasnota.data.saveBuktiLocal
import tf.fog.kasnota.data.saveTransaksiLocal
import tf.fog.kasnota.data.softDeleteBuktiLocal
import tf.fog.kasnota.data.softDeleteTransaksiLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

/**
 * Tambah (localId=0) / Edit transaksi — sekaligus tempat "Hapus" (tidak ada halaman detail
 * terpisah lagi). Field sama seperti web: tanggal default hari ini (tidak bisa masa depan),
 * switch masuk/keluar, kegiatan/jenis teks bebas dengan saran, deskripsi wajib (dinamis,
 * tumbuh sesuai isi), jumlah format Rupiah. Untuk role auditor, form dibuka read-only
 * (semua field terkunci, tanpa tombol Simpan/Hapus) — tap card di daftar transaksi tetap
 * membuka layar ini, cuma tidak bisa diedit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(nav: NavController, db: AppDatabase, bukuId: Int, localId: Long) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val todayStr = remember { LocalDate.now().toString() }

    var buku by remember { mutableStateOf<BukuEntity?>(null) }
    var existing by remember { mutableStateOf<TransaksiEntity?>(null) }
    var loaded by remember { mutableStateOf(localId == 0L) }

    var tanggal by remember { mutableStateOf(todayStr) }
    var tipe by remember { mutableStateOf("masuk") }
    var kegiatan by remember { mutableStateOf("") }
    var jenis by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var jumlahRaw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmHapusBukti by remember { mutableStateOf<BuktiItem.Saved?>(null) }
    var daftarBukti by remember { mutableStateOf<List<BuktiEntity>>(emptyList()) }
    // Bukti yang dipilih SEBELUM transaksi baru disimpan — belum ada localId transaksi utk
    // dilekatkan, jadi ditahan di sini dulu dan baru dilekatkan (saveBuktiLocal) setelah
    // saveTransaksiLocal sukses membuat baris transaksinya.
    var pendingBukti by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(bukuId) { buku = db.bukuDao().getById(bukuId) }

    LaunchedEffect(localId) {
        if (localId != 0L) {
            val t = db.transaksiDao().findByLocalId(localId)
            existing = t
            if (t != null) {
                tanggal = t.tanggal
                tipe = if (t.masuk > 0) "masuk" else "keluar"
                kegiatan = t.kegiatan ?: ""
                jenis = t.jenis ?: ""
                deskripsi = t.deskripsi
                jumlahRaw = (if (t.masuk > 0) t.masuk else t.keluar).toString()
                daftarBukti = db.buktiDao().listForTransaksi(t.localId)
            }
        }
        loaded = true
    }

    val readOnly = false

    val jumlahSlotTerpakai = if (existing != null) daftarBukti.size else pendingBukti.size

    /** Tambah 1 uri bukti (dari galeri atau kamera) — langsung disimpan ke Room kalau transaksi
     * sudah ada (edit), atau ditahan dulu di [pendingBukti] kalau transaksi masih baru/belum
     * disimpan (localId belum ada untuk dilekatkan). */
    fun tambahBukti(uri: Uri) {
        val e = existing
        if (e != null) {
            scope.launch { saveBuktiLocal(db, context, bukuId, e.localId, uri)?.let { daftarBukti = daftarBukti + it } }
        } else {
            pendingBukti = pendingBukti + uri
        }
    }

    val fotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        val sisaSlot = MAX_BUKTI_PER_TRANSAKSI - jumlahSlotTerpakai
        uris.take(sisaSlot.coerceAtLeast(0)).forEach { tambahBukti(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) tambahBukti(uri)
        pendingCameraUri = null
    }

    fun launchCamera() {
        if (jumlahSlotTerpakai >= MAX_BUKTI_PER_TRANSAKSI) return
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
        if (tanggal.isBlank()) return context.getString(R.string.err_date_required)
        if (tanggal > todayStr) return context.getString(R.string.err_date_future)
        if (deskripsi.isBlank()) return context.getString(R.string.err_description_required)
        if ((jumlahRaw.toIntOrNull() ?: 0) <= 0) return context.getString(R.string.err_amount_min)
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
                            localId == 0L -> stringResource(R.string.title_add_transaction)
                            else -> stringResource(R.string.title_edit_transaction)
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = tanggal,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.field_date)) },
                trailingIcon = {
                    IconButton(onClick = { openDatePicker() }, enabled = !readOnly) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Pilih tanggal")
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
                    value = kegiatan.ifBlank { "-" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.field_activity)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OpsiComboBox(db, bukuId, "kegiatan", stringResource(R.string.field_activity_optional), kegiatan, { kegiatan = it })
            }

            Spacer(Modifier.height(12.dp))
            if (readOnly) {
                OutlinedTextField(
                    value = jenis.ifBlank { "-" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.field_type)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OpsiComboBox(db, bukuId, "jenis", stringResource(R.string.field_type_optional), jenis, { jenis = it })
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
            Text(stringResource(R.string.receipts), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            val buktiItems = if (existing != null) {
                daftarBukti.map { BuktiItem.Saved(it) }
            } else {
                pendingBukti.map { BuktiItem.Pending(it) }
            }
            BuktiSection(
                items = buktiItems,
                readOnly = readOnly,
                onTambahKamera = { launchCamera() },
                onTambahGaleri = {
                    fotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onHapus = { item ->
                    when (item) {
                        // Bukti tersimpan = foto struk/nota sungguhan, dihapus permanen —
                        // wajib konfirmasi dulu (lihat confirmHapusBukti di bawah).
                        is BuktiItem.Saved -> confirmHapusBukti = item
                        // Bukti pending = baru dipilih, belum disimpan ke mana pun — hapus dari
                        // daftar sementara ini tidak butuh konfirmasi, tinggal pilih ulang.
                        is BuktiItem.Pending -> pendingBukti = pendingBukti.filter { it != item.uri }
                    }
                },
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KeluarColor),
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
                            error = null
                            saving = true
                            scope.launch {
                                val t = saveTransaksiLocal(
                                    db, bukuId, existing, tanggal, kegiatan, jenis, deskripsi, tipe,
                                    jumlahRaw.toInt(),
                                )
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
                title = { Text(stringResource(R.string.delete_transaction_q)) },
                text = { Text(stringResource(R.string.undo_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        val e = existing ?: return@TextButton
                        scope.launch {
                            softDeleteTransaksiLocal(db, e)
                            playDeleteHintSound()
                            confirmDelete = false
                            nav.popBackStack()
                        }
                    }) { Text(stringResource(R.string.action_delete), color = KeluarColor) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
                },
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
                            softDeleteBuktiLocal(db, item.entity)
                            playDeleteHintSound()
                            daftarBukti = daftarBukti.filter { it.localId != item.entity.localId }
                            confirmHapusBukti = null
                        }
                    }) { Text(stringResource(R.string.action_delete), color = KeluarColor) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmHapusBukti = null }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}

/** Satu thumbnail bukti — baik yang sudah tersimpan di Room (transaksi lama) maupun yang baru
 * dipilih/difoto tapi masih menunggu transaksi baru selesai disimpan (lihat [pendingBukti]
 * di [FormScreen]). */
sealed class BuktiItem {
    abstract val key: Any
    data class Saved(val entity: BuktiEntity) : BuktiItem() { override val key get() = entity.localId }
    data class Pending(val uri: Uri) : BuktiItem() { override val key get() = uri }
}

/** Grid horizontal thumbnail bukti + tombol kamera & galeri (kalau masih ada slot & bukan auditor). */
@Composable
fun BuktiSection(
    items: List<BuktiItem>,
    readOnly: Boolean,
    onTambahKamera: () -> Unit,
    onTambahGaleri: () -> Unit,
    onHapus: (BuktiItem) -> Unit,
    maxBukti: Int = MAX_BUKTI_PER_TRANSAKSI,
) {
    LazyRow {
        items(items, key = { it.key }) { item ->
            Box(modifier = Modifier.padding(end = 8.dp)) {
                BuktiThumb(item)
                if (!readOnly) {
                    IconButton(
                        onClick = { onHapus(item) },
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopEnd),
                    ) {
                        Icon(
                            Icons.Filled.Close, contentDescription = stringResource(R.string.remove_receipt),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }
            }
        }
        if (!readOnly && items.size < maxBukti) {
            item {
                IconButton(
                    onClick = onTambahKamera,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.take_receipt_photo))
                }
            }
            item {
                IconButton(
                    onClick = onTambahGaleri,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = stringResource(R.string.pick_receipt_gallery))
                }
            }
        }
    }
}

/** Baca gambar bukti di background thread lalu tampilkan — tanpa dependency Coil. Sumbernya
 * bisa file lokal (bukti tersimpan) atau content:// uri (bukti baru dipilih/difoto, belum
 * tersimpan ke Room). Bukti lama yang tidak diunduh karena kena batas storage
 * ([tf.fog.kasnota.data.SyncManager], MAX_BUKTI_STORAGE_BYTES) localFilePath-nya
 * memang null — ditampilkan sebagai placeholder "Web saja", bukan dianggap masih loading. */
@Composable
fun BuktiThumb(item: BuktiItem) {
    val context = LocalContext.current
    val webOnly = item is BuktiItem.Saved && item.entity.localFilePath == null
    val bitmap by produceState<ImageBitmap?>(null, item) {
        if (webOnly) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                when (item) {
                    is BuktiItem.Saved -> item.entity.localFilePath?.let { BitmapFactory.decodeFile(it) }
                    is BuktiItem.Pending -> context.contentResolver.openInputStream(item.uri)
                        ?.use { BitmapFactory.decodeStream(it) }
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            webOnly -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.CloudOff, contentDescription = null,
                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Web saja", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            bitmap != null -> Image(
                bitmap!!, contentDescription = stringResource(R.string.receipt_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
