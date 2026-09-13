package tf.fog.kasnota.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import tf.fog.kasnota.R
import tf.fog.kasnota.data.Prefs
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.BukuEntity

/** Daftar buku lokal — muncul saat ada >1 buku dan belum ada yang dipilih sebagai aktif. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftarBukuScreen(nav: NavController, db: AppDatabase) {
    var daftar by remember { mutableStateOf<List<BukuEntity>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        daftar = db.bukuDao().getAll()
        loaded = true
    }
    LaunchedEffect(nav.currentBackStackEntry) { daftar = db.bukuDao().getAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_cashbooks)) },
                actions = {
                    IconButton(onClick = { nav.navigate("buku_baru") }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_cashbook))
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        if (daftar.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.empty_cashbooks),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 16.dp),
        ) {
            items(daftar) { buku ->
                Card(
                    onClick = {
                        Prefs.activeBukuId = buku.id
                        nav.navigate("buku/${buku.id}")
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(buku.nama, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
