package tf.fog.kasnota.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF1F4E8C)
val Secondary = Color(0xFF3C7DD9)
val MasukColor = Color(0xFF13C296)
val KeluarColor = Color(0xFFF23030)

private val AppColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Secondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE9FF),
    onSecondaryContainer = Color(0xFF001A40),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun KeuanganTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        content = content,
    )
}
