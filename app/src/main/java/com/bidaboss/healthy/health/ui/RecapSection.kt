package com.bidaboss.healthy.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.core.progress.WeeklyRecap
import com.bidaboss.healthy.ui.Pousse
import com.bidaboss.healthy.ui.ThemeTexts
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

/**
 * « Bilan » : le résumé de la semaine qu'on peut partager. Sport, série, Pousse et badge seulement : rien sur les
 * calories, le poids ni la santé (règle du bilan, choix des fils Design et Nutrition, 13/11/2026). Le bouton ouvre
 * le bilan en plein écran, qui sait le partager en image.
 */
@Composable
internal fun RecapSection(recap: WeeklyRecap?, onOpenRecap: () -> Unit) {
    MetricCard("Ta semaine") {
        if (recap == null) {
            Muted("Chargement…")
            return@MetricCard
        }
        Muted("Du ${recap.weekStart.format(dayMonth)} au ${recap.weekEnd.format(dayMonth)}")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Pousse(Mood.PROUD, size = 96.dp, growth = recap.growth)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BigNumber(sportLine(recap))
                Text(ThemeTexts.current.streakDescription(recap.streakWeeks).replaceFirstChar(Char::uppercase))
                recap.badge?.let { Text(if (recap.badgeIsNew) "Nouveau badge : ${it.title}" else "Badge : ${it.title}") }
            }
        }
        Muted("Rien sur tes calories, ton poids ni ta santé n'est partagé.")
        Button(onClick = onOpenRecap, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Voir et partager ma semaine") }
    }
}

internal fun sportLine(recap: WeeklyRecap): String {
    val parts = listOfNotNull(
        recap.sessions.takeIf { it > 0 }?.let { "$it ${if (it > 1) "séances" else "séance"}" },
        recap.activities.takeIf { it > 0 }?.let { "$it ${if (it > 1) "activités" else "activité"}" },
    )
    return if (parts.isEmpty()) "Pas de sport cette semaine" else parts.joinToString(" et ")
}
