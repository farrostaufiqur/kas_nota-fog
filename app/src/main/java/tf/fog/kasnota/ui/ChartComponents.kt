package tf.fog.kasnota.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tf.fog.kasnota.R
import tf.fog.kasnota.data.BucketCount
import tf.fog.kasnota.data.JenisSlice
import kotlin.random.Random

/**
 * Warna per label (jenis) — di-generate dari hash nama-nya, BUKAN dipilih manual, tapi stabil
 * (label yang sama selalu dapat warna yang sama, tidak berubah tiap recomposition).
 */
fun colorForLabel(label: String): Color {
    val rnd = Random(label.hashCode())
    val hue = rnd.nextFloat() * 360f
    return Color.hsv(hue, 0.55f, 0.85f)
}

/** Pie chart distribusi nominal per jenis, dengan legenda warna di sampingnya — legenda dibuat
 * "chip" yang mengalir (wrap) beberapa label per baris (2 kolom, bukan 1 label per baris) supaya
 * tidak makin tinggi kalau jenis-nya banyak; tinggi menyesuaikan jumlah baris chip secara dinamis
 * (tanpa scroll) — TIDAK dibatasi heightIn, karena itu justru memicu scroll di dalam kartu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PieChartWithLegend(slices: List<JenisSlice>, modifier: Modifier = Modifier) {
    if (slices.isEmpty()) {
        Text(
            stringResource(R.string.chart_empty),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7280),
            modifier = modifier,
        )
        return
    }
    val total = slices.sumOf { it.nominal }.coerceAtLeast(1)

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Canvas(modifier = Modifier.size(110.dp)) {
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = 360f * slice.nominal / total
                drawArc(
                    color = colorForLabel(slice.jenis),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                )
                startAngle += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            slices.forEach { slice ->
                val pct = slice.nominal * 100 / total
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(colorForLabel(slice.jenis), CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${slice.jenis} ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Diagram garis: banyaknya transaksi per bucket (hari/bulan) — lebar total SELALU mengisi
 * lebar layar (tidak scroll), lebar per titik menyesuaikan (menyusut) kalau titiknya banyak
 * (mis. 30 hari di periode bulan) — supaya pekan/bulan/tahun ukurannya konsisten. */
@Composable
fun LineChartCount(buckets: List<BucketCount>, modifier: Modifier = Modifier) {
    if (buckets.isEmpty() || buckets.all { it.count == 0 }) {
        Text(
            stringResource(R.string.chart_empty),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7280),
            modifier = modifier,
        )
        return
    }
    val maxCount = buckets.maxOf { it.count }.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            val step = size.width / buckets.size
            val topMargin = 12f
            val usableHeight = size.height - topMargin
            val points = buckets.mapIndexed { i, b ->
                val x = step * i + step / 2
                val y = topMargin + usableHeight * (1f - b.count.toFloat() / maxCount)
                Offset(x, y)
            }
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = Primary,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
            points.forEach { p -> drawCircle(color = Primary, radius = 5f, center = p) }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            buckets.forEach { b ->
                Text(
                    b.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
