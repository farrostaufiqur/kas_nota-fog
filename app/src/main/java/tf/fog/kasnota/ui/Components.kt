package tf.fog.kasnota.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tf.fog.kasnota.R
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.OpsiEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Nada pendek sebagai penanda audio ("hint") saat konfirmasi hapus & setiap penghapusan
 * berhasil (transaksi, bukti, kelompok, anggota kelompok) — dipakai di semua dialog konfirmasi
 * "Hapus" di app ini. ToneGenerator dibuat sekali per panggilan lalu dilepas otomatis
 * (Handler.postDelayed) setelah nada selesai, supaya tidak menahan resource audio lama-lama. */
fun playDeleteHintSound() {
    runCatching {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { tg.release() } }, 300)
    }
}

/** Format "Rp 1.234.567" — sama seperti formatter JS di web. */
fun formatRupiah(amount: Int): String {
    val sign = if (amount < 0) "-" else ""
    val grouped = kotlin.math.abs(amount).toString()
        .reversed().chunked(3).joinToString(".").reversed()
    return "${sign}Rp $grouped"
}

/** Saat fokus (sedang diketik): angka polos, gampang diedit. Saat blur atau tekan Enter/Done:
 * diformat jadi "Rp x.xxx.xxx" — supaya tidak mengganggu posisi kursor/mengetik selagi aktif. */
@Composable
fun RupiahField(
    rawDigits: String,
    onRawDigitsChange: (String) -> Unit,
    label: String = stringResource(R.string.field_amount),
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val displayValue = when {
        focused -> rawDigits
        rawDigits.isEmpty() -> ""
        else -> formatRupiah(rawDigits.toLongOrNull()?.toInt() ?: 0)
    }
    OutlinedTextField(
        value = displayValue,
        onValueChange = { newText -> onRawDigitsChange(newText.filter { it.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        readOnly = readOnly,
        placeholder = { Text("Rp 0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
    )
}

/** Button dengan CircularProgressIndicator rapi di dalam, sebelum label — bukan di tengah layar. */
@Composable
fun LoadingButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = colors,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
fun SwitchTipe(tipe: String, onTipeChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier = modifier) {
        Button(
            onClick = { onTipeChange("masuk") },
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (tipe == "masuk") MasukColor else Color(0xFFE5E7EB),
                contentColor = if (tipe == "masuk") Color.White else Color(0xFF374151),
                disabledContainerColor = if (tipe == "masuk") MasukColor else Color(0xFFE5E7EB),
                disabledContentColor = if (tipe == "masuk") Color.White else Color(0xFF374151),
            ),
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp),
        ) { Text(stringResource(R.string.amount_in)) }
        Button(
            onClick = { onTipeChange("keluar") },
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (tipe == "keluar") KeluarColor else Color(0xFFE5E7EB),
                contentColor = if (tipe == "keluar") Color.White else Color(0xFF374151),
                disabledContainerColor = if (tipe == "keluar") KeluarColor else Color(0xFFE5E7EB),
                disabledContentColor = if (tipe == "keluar") Color.White else Color(0xFF374151),
            ),
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp, topStart = 0.dp, bottomStart = 0.dp),
        ) { Text(stringResource(R.string.amount_out)) }
    }
}

/**
 * Combobox kegiatan/jenis — teks bebas, sama seperti web: klik langsung tampilkan saran
 * (tanpa perlu ketik dulu), tidak ada "lengkapi kata otomatis". Vocabulary baru didaftarkan
 * otomatis saat transaksi disimpan (lihat LocalRepo.registerOpsiUsage), jadi combobox ini
 * murni pembantu — tidak ada langkah "buat baru" terpisah yang wajib.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpsiComboBox(
    db: AppDatabase,
    bukuId: Int,
    kategori: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<OpsiEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Debounce (spt combobox web, 250ms) + BATALKAN pencarian sebelumnya yg belum selesai —
    // tanpa ini, ketik/backspace cepat (apalagi kategori dgn banyak opsi spt "jenis") numpuk
    // banyak coroutine paralel yg masing2 nyalain `expanded=true` gantian, bikin dropdown
    // kedip2/popup kebuka-tutup terus pas user lagi ngetik cepat — kesannya field "gagal
    // dihapus" padahal `value` sendiri sudah benar, cuma UI dropdown-nya yg ganggu.
    fun search(q: String, debounce: Boolean = true) {
        searchJob?.cancel()
        searchJob = scope.launch {
            if (debounce) delay(250)
            suggestions = db.opsiDao().search(bukuId, kategori, q.trim())
            expanded = true
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                search(it)
            },
            label = { Text(label) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color(0xFFF1F2F7),
                focusedContainerColor = Color(0xFFF1F2F7),
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) search(value, debounce = false) },
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            suggestions.forEach { opsi ->
                DropdownMenuItem(
                    text = { Text(opsi.nama) },
                    onClick = {
                        onValueChange(opsi.nama)
                        expanded = false
                    },
                )
            }
        }
    }
}
