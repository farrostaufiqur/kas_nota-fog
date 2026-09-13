package tf.fog.kasnota.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import tf.fog.kasnota.R
import tf.fog.kasnota.data.BerandaSummary
import tf.fog.kasnota.data.BucketCount
import tf.fog.kasnota.data.JenisSlice
import tf.fog.kasnota.data.Period
import tf.fog.kasnota.data.PeriodSummary
import tf.fog.kasnota.data.computeBerandaSummary
import tf.fog.kasnota.data.computeBucketCounts
import tf.fog.kasnota.data.computeJenisDistribution
import tf.fog.kasnota.data.db.AppDatabase

/** Isi tab "Beranda": saldo total, tombol tambah transaksi, ringkasan (sekaligus pemilih periode diagram), pie chart & diagram batang. */
@Composable
fun BerandaBody(nav: NavController, db: AppDatabase, bukuId: Int) {
    var summary by remember { mutableStateOf<BerandaSummary?>(null) }
    var period by remember { mutableStateOf(Period.PEKAN) }
    var slices by remember { mutableStateOf<List<JenisSlice>>(emptyList()) }
    var buckets by remember { mutableStateOf<List<BucketCount>>(emptyList()) }

    suspend fun reloadSummary() { summary = computeBerandaSummary(db, bukuId) }
    suspend fun reloadCharts() {
        slices = computeJenisDistribution(db, bukuId, period)
        buckets = computeBucketCounts(db, bukuId, period)
    }

    LaunchedEffect(bukuId) { reloadSummary() }
    LaunchedEffect(nav.currentBackStackEntry) { reloadSummary() }
    LaunchedEffect(bukuId, period, nav.currentBackStackEntry) { reloadCharts() }

    val s = summary ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.current_balance), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatRupiah(s.saldo),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { nav.navigate("form/$bukuId/0") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_transaction))
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.summary_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7280),
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PeriodBox(stringResource(R.string.period_7d), s.pekanIni, period == Period.PEKAN, { period = Period.PEKAN }, Modifier.weight(1f))
            PeriodBox(stringResource(R.string.period_30d), s.bulanIni, period == Period.BULAN, { period = Period.BULAN }, Modifier.weight(1f))
            PeriodBox(stringResource(R.string.period_12m), s.tahunIni, period == Period.TAHUN, { period = Period.TAHUN }, Modifier.weight(1f))
        }

        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.distribution_by_type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        PieChartWithLegend(slices)

        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.transaction_count), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LineChartCount(buckets)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PeriodBox(
    label: String,
    p: PeriodSummary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Primary.copy(alpha = 0.12f) else Color(0xFFF7F9FC),
        ),
        modifier = if (selected) modifier.border(1.5.dp, Primary, RoundedCornerShape(12.dp)) else modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.amount_in), style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
            Text(
                formatRupiah(p.masuk), color = MasukColor, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.amount_out), style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
            Text(
                formatRupiah(p.keluar), color = KeluarColor, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
