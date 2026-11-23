package com.bidaboss.healthy.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.health.data.WatchStatus
import com.bidaboss.healthy.ui.formatThousands
import kotlin.math.roundToInt

/**
 * « 7 jours » : une carte par thème (calories, eau, sport, pas, sommeil) avec la moyenne et sept barres. Un jour
 * sans donnée est un trou ; une moyenne ne compte que les jours notés. Sans montre connectée, Pas et Sommeil sont
 * masqués et une carte invite à en ajouter une.
 */
@Composable
internal fun WeekSection(state: HealthUiState, onOpenWater: () -> Unit, onConnect: () -> Unit) {
    val dates = state.week.map { it.date }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Calories
        val kcal = state.week.map { it.kcal?.toDouble() }
        MetricCard("Calories") {
            val avg = kcal.averageOrNull()
            if (avg == null) {
                Muted("Aucun repas noté cette semaine.")
            } else {
                BigNumber("${formatThousands(avg.roundToInt())} kcal par jour")
                Muted("En moyenne sur ${kcal.count { it != null }} ${if (kcal.count { it != null } > 1) "jours notés" else "jour noté"}.")
            }
            val kcalText: (Double) -> String = { "${formatThousands(it.roundToInt())} kcal" }
            WeekBars(
                dates, kcal, weekDescription("Calories par jour", dates, kcal, kcalText),
                goal = state.week.lastOrNull()?.kcalGoal?.toDouble(), format = kcalText,
            )
        }

        // Eau
        val water = state.week.map { it.waterMl?.toDouble() }
        MetricCard("Eau", onClick = onOpenWater) {
            val avg = water.averageOrNull()
            if (avg == null) Muted("Aucune eau notée cette semaine.") else {
                BigNumber("${liters(avg)} par jour")
                Muted("En moyenne sur les jours notés. Touche pour le détail.")
            }
            WeekBars(
                dates, water, weekDescription("Eau par jour", dates, water) { liters(it) },
                color = MaterialTheme.colorScheme.tertiary,
                goal = state.week.lastOrNull()?.waterGoalMl?.toDouble(), format = { liters(it) },
            )
        }

        // Sport
        val sport = state.week.map { if (it.sportMinutes > 0) it.sportMinutes.toDouble() else if (it.sessions > 0) 1.0 else null }
        MetricCard("Sport") {
            val count = state.week.sumOf { it.sessions }
            val minutes = state.week.sumOf { it.sportMinutes }
            if (count == 0) Muted("Aucune séance ni activité cette semaine.") else {
                BigNumber("$count ${if (count > 1) "séances ou activités" else "séance ou activité"}")
                if (minutes > 0) Muted("${hoursText(minutes)} au total.")
            }
            WeekBars(
                dates, sport, weekDescription("Sport par jour", dates, sport) { if (it <= 1.0) "une séance" else hoursText(it.roundToInt()) },
                color = MaterialTheme.colorScheme.secondary,
                format = { if (it <= 1.0) "une séance" else hoursText(it.roundToInt()) },
            )
        }

        if (state.watchConnected) {
            // Pas
            val steps = state.watchWeek.map { it?.steps?.toDouble() }
            MetricCard("Pas") {
                val avg = steps.averageOrNull()
                if (avg == null) Muted("Pas de données cette semaine.") else {
                    BigNumber("${formatThousands(avg.roundToInt())} pas par jour")
                    Muted("En moyenne sur les jours reçus de ta montre.")
                }
                WeekBars(dates, steps, weekDescription("Pas par jour", dates, steps) { "${formatThousands(it.roundToInt())} pas" }, format = { "${formatThousands(it.roundToInt())} pas" })
            }

            // Sommeil
            val sleep = state.watchWeek.map { it?.sleepMin?.toDouble() }
            MetricCard("Sommeil") {
                val avg = sleep.averageOrNull()
                when {
                    avg != null -> {
                        BigNumber("${hoursText(avg.roundToInt())} par nuit")
                        Muted("En moyenne sur les nuits reçues de ta montre.")
                    }
                    !state.extrasGranted -> {
                        Muted("Le sommeil n'est pas autorisé (facultatif).")
                        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Ajouter le sommeil et le cœur") }
                    }
                    else -> Muted("Pas de données cette semaine.")
                }
                WeekBars(dates, sleep, weekDescription("Sommeil par nuit", dates, sleep) { hoursText(it.roundToInt()) }, format = { hoursText(it.roundToInt()) })
            }
        } else if (state.status != null) {
            AddWatchCard(state.status, onConnect)
        }
    }
}

/** Carte « Ajouter une montre » : la connexion est facultative, en lecture seule, rien ne quitte le téléphone. */
@Composable
internal fun AddWatchCard(status: WatchStatus?, onConnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ajouter une montre", style = MaterialTheme.typography.titleMedium)
            when (status) {
                WatchStatus.UNAVAILABLE -> Muted("Health Connect n'est pas disponible sur ce téléphone, donc une montre ne peut pas être lue.")
                WatchStatus.UPDATE_REQUIRED -> Muted("Health Connect doit être mis à jour : ouvre le Play Store, puis reviens ici.")
                else -> {
                    Text("Connecte ta montre pour suivre tes pas, ton sommeil et ton cœur. C'est en lecture seule et rien ne quitte ton téléphone.")
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Connecter ma montre") }
                }
            }
        }
    }
}
