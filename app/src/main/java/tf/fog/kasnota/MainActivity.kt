package tf.fog.kasnota

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tf.fog.kasnota.data.Prefs
import tf.fog.kasnota.data.db.AppDatabase
import tf.fog.kasnota.ui.BukuFormScreen
import tf.fog.kasnota.ui.BukuScaffold
import tf.fog.kasnota.ui.DaftarBukuScreen
import tf.fog.kasnota.ui.FormScreen
import tf.fog.kasnota.ui.KelompokAnggotaFormScreen
import tf.fog.kasnota.ui.KelompokBuatScreen
import tf.fog.kasnota.ui.KelompokDetailScreen
import tf.fog.kasnota.ui.KeuanganTheme
import tf.fog.kasnota.ui.PengaturanScreen
import tf.fog.kasnota.ui.SplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp >= 600) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        Prefs.init(this)
        val db = AppDatabase.get(this)
        setContent {
            KeuanganTheme {
                AppNav(db)
            }
        }
    }
}

/**
 * Aplikasi 100% lokal (Room), tanpa server/login. Start = "splash": baca buku aktif
 * terakhir dari [Prefs] lalu routing — ada buku aktif → buka; belum ada tapi ada buku
 * lain → Daftar Buku; belum ada buku sama sekali → Pengaturan (tombol Tambah Buku).
 */
@Composable
fun AppNav(db: AppDatabase) {
    val nav = rememberNavController()
    val dur = 160
    NavHost(
        navController = nav,
        startDestination = "splash",
        enterTransition = { slideIntoContainer(SlideDirection.Left, tween(dur)) + fadeIn(tween(dur)) },
        exitTransition = { slideOutOfContainer(SlideDirection.Left, tween(dur)) + fadeOut(tween(dur)) },
        popEnterTransition = { slideIntoContainer(SlideDirection.Right, tween(dur)) + fadeIn(tween(dur)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.Right, tween(dur)) + fadeOut(tween(dur)) },
    ) {
        composable(
            "splash",
            enterTransition = { fadeIn(tween(dur)) },
            exitTransition = { fadeOut(tween(dur)) },
            popEnterTransition = { fadeIn(tween(dur)) },
            popExitTransition = { fadeOut(tween(dur)) },
        ) { SplashScreen(nav, db) }
        composable("daftar_buku") { DaftarBukuScreen(nav, db) }
        composable("pengaturan") { PengaturanScreen(nav, db) }
        composable("buku_baru") { BukuFormScreen(nav, db) }
        composable(
            "buku/{bukuId}",
            arguments = listOf(navArgument("bukuId") { type = NavType.IntType }),
        ) { backStack ->
            BukuScaffold(nav, db, backStack.arguments!!.getInt("bukuId"))
        }
        composable(
            "form/{bukuId}/{localId}",
            arguments = listOf(
                navArgument("bukuId") { type = NavType.IntType },
                navArgument("localId") { type = NavType.LongType },
            ),
        ) { backStack ->
            FormScreen(
                nav, db,
                bukuId = backStack.arguments!!.getInt("bukuId"),
                localId = backStack.arguments!!.getLong("localId"),
            )
        }
        composable(
            "kelompok_baru/{bukuId}",
            arguments = listOf(navArgument("bukuId") { type = NavType.IntType }),
        ) { backStack ->
            KelompokBuatScreen(nav, db, bukuId = backStack.arguments!!.getInt("bukuId"))
        }
        composable(
            "kelompok/{bukuId}/{kelompokLocalId}",
            arguments = listOf(
                navArgument("bukuId") { type = NavType.IntType },
                navArgument("kelompokLocalId") { type = NavType.LongType },
            ),
        ) { backStack ->
            KelompokDetailScreen(
                nav, db,
                bukuId = backStack.arguments!!.getInt("bukuId"),
                kelompokLocalId = backStack.arguments!!.getLong("kelompokLocalId"),
            )
        }
        composable(
            "kelompok_anggota/{bukuId}/{kelompokLocalId}/{localId}",
            arguments = listOf(
                navArgument("bukuId") { type = NavType.IntType },
                navArgument("kelompokLocalId") { type = NavType.LongType },
                navArgument("localId") { type = NavType.LongType },
            ),
        ) { backStack ->
            KelompokAnggotaFormScreen(
                nav, db,
                bukuId = backStack.arguments!!.getInt("bukuId"),
                kelompokLocalId = backStack.arguments!!.getLong("kelompokLocalId"),
                localId = backStack.arguments!!.getLong("localId"),
            )
        }
    }
}
