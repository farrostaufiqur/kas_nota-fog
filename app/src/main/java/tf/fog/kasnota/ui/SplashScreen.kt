package tf.fog.kasnota.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import tf.fog.kasnota.R
import tf.fog.kasnota.data.Prefs
import tf.fog.kasnota.data.db.AppDatabase

/**
 * Logo + nama aplikasi, sekaligus router awal. Tanpa jaringan: baca buku aktif terakhir
 * dari [Prefs] lalu tentukan tujuan. Dipakai ulang setiap ganti/tambah/impor buku supaya
 * data buku yang dipilih dimuat dari nol.
 */
@Composable
fun SplashScreen(nav: NavController, db: AppDatabase) {
    LaunchedEffect(Unit) {
        delay(300)
        val active = Prefs.activeBukuId
        val books = db.bukuDao().getAll()
        val dest = when {
            active != null && books.any { it.id == active } -> "buku/$active"
            books.isNotEmpty() -> "daftar_buku"
            else -> "pengaturan"
        }
        nav.navigate(dest) { popUpTo("splash") { inclusive = true } }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Primary),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.pibt_logo),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.app_name),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
