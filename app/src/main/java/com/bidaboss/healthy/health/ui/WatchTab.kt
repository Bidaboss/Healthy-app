package com.bidaboss.healthy.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.health.data.WatchDayRecord
import com.bidaboss.healthy.health.data.WatchStatus
import com.bidaboss.healthy.ui.formatThousands

/**
 * « Montre » : la journée la plus récente reçue (calories brûlées, pas, distance, sommeil, cœur), puis les tendances
 * sur 7 ou 30 jours. Le stress, le score de sommeil Garmin, la Batterie corporelle et la SpO2 ne passent pas par
 * Health Connect : ils ne sont jamais affichés.
 */
@Composable
internal fun WatchTab(state: HealthUiState, trends: WatchTrendsViewModel, onConnect: () -> Unit) {
    val latest = state.watchWeek.filterNotNull().lastOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        latest?.let { Latest(it, state) }
        if (state.status == WatchStatus.READY && !state.distanceGranted && state.extrasGranted) {
            MetricCard("Distance") {
                Muted("La distance n'est pas autorisée (facultatif).")
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Ajouter la distance") }
            }
        }
        WatchTrendsContent(trends, onConnect)
    }
}

@Composable
private fun Latest(day: WatchDayRecord, state: HealthUiState) {
    val tiles = buildList {
        day.activeKcal?.let { add("Calories brûlées" to "≈ ${formatThousands(it)} kcal") }
        day.steps?.let { add("Pas" to formatThousands(it)) }
        day.distanceM?.let { add("Distance" to km(it / 1000.0)) }
        day.sleepMin?.let { add("Sommeil" to hoursText(it)) }
        if (day.avgBpm != null && day.minBpm != null) add("Cœur" to "${day.avgBpm} en moyenne, ${day.minBpm} au plus bas")
    }
    MetricCard(if (day.date == state.today) "Aujourd'hui" else chartDate(day.date).replaceFirstChar(Char::uppercase)) {
        if (tiles.isEmpty()) Muted("Rien reçu de ta montre pour l'instant.")
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Muted(label)
                    }
                }
                if (pair.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
        day.activeKcal?.let { Muted("Les calories brûlées sont indicatives : elles ne s'ajoutent pas à ton objectif.") }
    }
}
