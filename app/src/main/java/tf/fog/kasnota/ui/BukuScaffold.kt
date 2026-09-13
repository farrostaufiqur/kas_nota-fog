package tf.fog.kasnota.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import tf.fog.kasnota.R
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.data.db.BukuEntity

private const val TAB_BERANDA = 0
private const val TAB_TRANSAKSI = 1
private const val TAB_KELOMPOK = 2
private const val TAB_PENGATURAN = 3

/** Destinasi utama per buku: 4 tab (Beranda, Transaksi, Kelompok, Pengaturan). Tanpa
 * tombol sinkron/reload di title bar (aplikasi 100% lokal). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BukuScaffold(nav: NavController, db: AppDatabase, bukuId: Int) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_BERANDA) }
    var buku by remember { mutableStateOf<BukuEntity?>(null) }

    LaunchedEffect(bukuId) { buku = db.bukuDao().getById(bukuId) }
    LaunchedEffect(nav.currentBackStackEntry) { buku = db.bukuDao().getById(bukuId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            TAB_BERANDA -> buku?.nama ?: stringResource(R.string.tab_home)
                            TAB_TRANSAKSI -> stringResource(R.string.tab_transactions)
                            TAB_KELOMPOK -> stringResource(R.string.tab_groups)
                            else -> stringResource(R.string.tab_settings)
                        },
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == TAB_BERANDA,
                    onClick = { tab = TAB_BERANDA },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_TRANSAKSI,
                    onClick = { tab = TAB_TRANSAKSI },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_transactions)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_KELOMPOK,
                    onClick = { tab = TAB_KELOMPOK },
                    icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_groups)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_PENGATURAN,
                    onClick = { tab = TAB_PENGATURAN },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                TAB_BERANDA -> BerandaBody(nav, db, bukuId)
                TAB_TRANSAKSI -> DaftarTransaksiBody(nav, db, bukuId, buku)
                TAB_KELOMPOK -> KelompokListBody(nav, db, bukuId, buku)
                else -> PengaturanBody(nav, db, bukuId)
            }
        }
    }
}
