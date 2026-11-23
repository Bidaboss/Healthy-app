package com.bidaboss.healthy.today.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.health.data.WatchSession
import com.bidaboss.healthy.ui.dayName
import com.bidaboss.healthy.ui.formatThousands
import com.bidaboss.healthy.ui.theme.healthyTonalButtonColors
import java.time.LocalDate

/** Nombre de séances proposées d'un coup : au-delà, on les verra après avoir traité les premières. */
private const val MAX_PROPOSALS = 3

/** Carte « Ma montre » : pas, calories actives (indicatives) et séances à ajouter au journal. */
@Composable
fun WatchCard(state: WatchUiState, onAdd: (WatchSession) -> Unit, onIgnore: (WatchSession) -> Unit, onOpen: () -> Unit) {
    if (!state.visible) return
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ma montre", style = MaterialTheme.typography.titleMedium)
            val day = state.day
            when {
                day == null -> Text(
                    "Impossible de lire ta montre pour l'instant.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                day.isEmpty -> Text(
                    "Rien reçu aujourd'hui. Ouvre Garmin Connect pour synchroniser ta montre.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Calories actives : indicatives, elles ne s'ajoutent pas à l'objectif (docs/decisions.md).
                    WatchStat("Calories", day.activeKcal?.let { formatThousands(it) }, "kcal", Modifier.weight(1f))
                    WatchStat("Pas", formatThousands(day.steps.toInt()), null, Modifier.weight(1f))
                    // La distance du jour arrive avec le lot 4 (Santé) : « – » d'ici là.
                    WatchStat("Distance", null, "km", Modifier.weight(1f))
                    WatchStat("Cœur", day.avgBpm?.toString(), "bpm", Modifier.weight(1f))
                }
            }
            if (state.proposals.isNotEmpty()) {
                Text("Séances à ajouter à ton journal", style = MaterialTheme.typography.titleSmall)
                state.proposals.take(MAX_PROPOSALS).forEach { session ->
                    ProposalRow(session, state.date, onAdd, onIgnore)
                }
            }
        }
    }
}

/** Une mesure de la montre : valeur en gros, nom dessous ; « – » quand elle manque. */
@Composable
private fun WatchStat(label: String, value: String?, unit: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value ?: "–",
            style = MaterialTheme.typography.titleLarge,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            if (value != null && unit != null) "$label ($unit)" else label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProposalRow(session: WatchSession, today: LocalDate, onAdd: (WatchSession) -> Unit, onIgnore: (WatchSession) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${session.type} · ${session.durationMin} min", style = MaterialTheme.typography.titleSmall)
            Text(
                whenText(session, today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onIgnore(session) }) { Text("Ignorer") }
        FilledTonalButton(colors = healthyTonalButtonColors(), onClick = { onAdd(session) }) { Text("Ajouter") }
    }
}

/** « hier à 18 h 05 », « mardi à 7 h 30 ». */
internal fun whenText(session: WatchSession, today: LocalDate): String {
    val day = when (session.date) {
        today -> "aujourd'hui"
        today.minusDays(1) -> "hier"
        else -> dayName(session.date.dayOfWeek.value).lowercase()
    }
    return "$day à ${session.startedAt.hour} h %02d".format(session.startedAt.minute)
}

/** « 7 h 30 », « 45 min ». */
internal fun durationText(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h %02d".format(minutes % 60)
