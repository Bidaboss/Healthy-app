package com.bidaboss.healthy.health.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.core.watch.BedtimeRegularity
import com.bidaboss.healthy.core.watch.SleepSegment
import com.bidaboss.healthy.core.watch.SleepStage
import com.bidaboss.healthy.core.watch.SleepStages
import com.bidaboss.healthy.health.data.WatchDayRecord
import kotlin.math.roundToInt

/** Couleurs des phases : jamais seules, chaque phase est aussi écrite avec sa durée. */
@Composable
private fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.DEEP -> MaterialTheme.colorScheme.primary
    SleepStage.LIGHT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    SleepStage.REM -> MaterialTheme.colorScheme.tertiary
    SleepStage.AWAKE -> MaterialTheme.colorScheme.outline
}

/**
 * « Sommeil » : la dernière nuit reçue de la montre, puis les 7 dernières nuits. On ne montre que ce que la montre
 * envoie : sans phases, pas de frise ; sans cœur de nuit, pas de ligne cœur. Aucun seuil ni jugement.
 */
@Composable
internal fun SleepSection(state: HealthUiState, onConnect: () -> Unit) {
    val nights = state.watchWeek.filterNotNull().filter { it.sleepMin != null }
    val last = nights.lastOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!state.extrasGranted && state.status == com.bidaboss.healthy.health.data.WatchStatus.READY) {
            MetricCard("Sommeil") {
                Muted("Le sommeil et le cœur ne sont pas autorisés (facultatif).")
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Ajouter le sommeil et le cœur") }
            }
            return@Column
        }
        if (last == null) {
            MetricCard("Sommeil") { Muted("Pas de nuit reçue de ta montre ces 7 derniers jours. Ouvre Garmin Connect pour synchroniser.") }
            return@Column
        }
        LastNight(last, nights)
        Last7Nights(state, nights)
    }
}

@Composable
private fun LastNight(night: WatchDayRecord, nights: List<WatchDayRecord>) {
    val detail = night.sleep
    MetricCard(if (night.date == nights.last().date && nights.size >= 1) "Ta dernière nuit" else "Nuit") {
        Muted(chartDate(night.date))
        BigNumber(hoursText(night.sleepMin!!))
        if (detail != null) {
            Text("${clockText(detail.start.hour, detail.start.minute)} → ${clockText(detail.end.hour, detail.end.minute)}", style = MaterialTheme.typography.titleMedium)
        }
        val segments = detail?.segments.orEmpty()
        if (SleepStages.hasPhases(segments)) {
            Frieze(segments)
            Phases(segments)
        }
        if (detail != null) {
            val rows = buildList {
                add("Endormissement" to clockText(detail.start.hour, detail.start.minute))
                add("Réveil" to clockText(detail.end.hour, detail.end.minute))
                if (SleepStages.hasPhases(segments)) {
                    val n = SleepStages.awakenings(segments)
                    add("Réveils dans la nuit" to if (n == 0) "aucun" else "$n")
                }
                if (detail.nightAvgBpm != null && detail.nightMinBpm != null) {
                    add("Cœur pendant la nuit" to "${detail.nightAvgBpm} en moyenne, ${detail.nightMinBpm} au plus bas")
                }
                val spread = BedtimeRegularity.spreadMinutes(nights.mapNotNull { it.sleep?.start })
                if (spread != null) add("Régularité du coucher" to if (spread < 5) "à peu près à la même heure" else "écart d'environ $spread min")
            }
            rows.forEach { (label, value) -> Fact(label, value) }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
}

/** La nuit en une frise : un morceau par phase, dans l'ordre, largeur proportionnelle à la durée. */
@Composable
private fun Frieze(segments: List<SleepSegment>) {
    val total = segments.sumOf { it.minutes }.coerceAtLeast(1)
    val summary = SleepStages.totals(segments).entries.joinToString(", ") { "${it.key.label} ${hoursText(it.value)}" }
    Row(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(10.dp)).clearAndSetSemantics { contentDescription = "Phases de la nuit : $summary" },
    ) {
        segments.forEach { Box(Modifier.weight(it.minutes.toFloat() / total).height(28.dp).background(stageColor(it.stage))) }
    }
}

@Composable
private fun Phases(segments: List<SleepSegment>) {
    val totals = SleepStages.totals(segments)
    val total = totals.values.sum().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SleepStage.entries.filter { it in totals }.forEach { stage ->
            val minutes = totals.getValue(stage)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).background(stageColor(stage), CircleShape))
                Text(stage.label, modifier = Modifier.weight(1f))
                Text("${hoursText(minutes)} · ${(minutes * 100f / total).roundToInt()} %", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun Last7Nights(state: HealthUiState, nights: List<WatchDayRecord>) {
    val dates = state.watchWeek.indices.map { state.today.minusDays((state.watchWeek.size - 1 - it).toLong()) }
    val values = state.watchWeek.map { it?.sleepMin?.toDouble() }
    MetricCard("Les 7 dernières nuits") {
        val avg = values.averageOrNull()
        if (avg != null) BigNumber("${hoursText(avg.roundToInt())} par nuit en moyenne")
        WeekBars(dates, values, weekDescription("Sommeil par nuit", dates, values) { hoursText(it.roundToInt()) })
        nights.reversed().forEach { n ->
            Fact(chartDate(n.date).replaceFirstChar(Char::uppercase), hoursText(n.sleepMin!!))
        }
    }
}
