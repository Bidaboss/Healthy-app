package com.bidaboss.healthy.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.bidaboss.healthy.ui.Glyphs
import com.bidaboss.healthy.ui.SvgIcon
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text("Ma montre", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Voir tout", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
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

                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Quatre tuiles en grille 2 x 2, chacune avec sa pastille d'icône colorée (prototype).
                    // Calories actives : indicatives, elles ne s'ajoutent pas à l'objectif (docs/decisions.md).
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WatchTile("Calories", day.activeKcal?.let { formatThousands(it) }, "kcal", Glyphs.FLAME, Color(0xFFF07A4A), Modifier.weight(1f))
                        WatchTile("Pas", formatThousands(day.steps.toInt()), null, Glyphs.SHOE, Color(0xFF3F7CE0), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Distance du jour (lot 4) : « – » si la montre n'en envoie pas ou si elle n'est pas autorisée.
                        WatchTile("Distance", day.distanceM?.let { "%.1f".format(java.util.Locale.FRENCH, it / 1000.0) }, "km", Glyphs.ROUTE, Color(0xFF2FA37A), Modifier.weight(1f))
                        WatchTile("Cœur", day.avgBpm?.toString(), "bpm", Glyphs.HEART, Color(0xFFE0566B), Modifier.weight(1f))
                    }
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

/** Une mesure de la montre : pastille d'icône colorée, nom, valeur en gros ; « – » quand elle manque. */
@Composable
private fun WatchTile(label: String, value: String?, unit: String?, glyph: String, badge: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            SvgIcon(glyph, Color.White, size = 18.dp, strokeWidth = 2.4f)
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    value ?: "–",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (value != null && unit != null) {
                    Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
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
