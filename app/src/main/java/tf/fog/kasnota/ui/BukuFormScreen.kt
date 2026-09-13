package tf.fog.kasnota.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import tf.fog.kasnota.R
import tf.fog.kasnota.data.Prefs
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.BukuEntity

/** Tambah buku baru: satu field nama. Setelah simpan → jadikan buku aktif → kembali ke
 * splash supaya buku itu dimuat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BukuFormScreen(nav: NavController, db: AppDatabase) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var nama by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_cashbook_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = nama,
                onValueChange = { nama = it; error = null },
                label = { Text(stringResource(R.string.cashbook_name_label)) },
                singleLine = true,
                isError = error != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            LoadingButton(
                text = stringResource(R.string.action_save),
                loading = saving,
                onClick = {
                    if (nama.isBlank()) {
                        error = ctx.getString(R.string.cashbook_name_required)
                        return@LoadingButton
                    }
                    saving = true
                    scope.launch {
                        val id = db.bukuDao().insert(BukuEntity(nama = nama.trim())).toInt()
                        Prefs.activeBukuId = id
                        saving = false
                        nav.navigate("splash") { popUpTo(0) }
                    }
                },
            )
        }
    }
}
