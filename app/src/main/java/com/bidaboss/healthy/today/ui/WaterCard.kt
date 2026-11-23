package com.bidaboss.healthy.today.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bidaboss.healthy.ui.WaterFeedback
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.body.data.MealDrink
import com.bidaboss.healthy.body.data.WaterIntake
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.ui.theme.healthyTonalButtonColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Carte « Eau » d'Aujourd'hui : progression et ajouts rapides. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterCard(state: WaterUiState, onAdd: (Int) -> Unit, onUndo: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    // Petite vibration à chaque ajout (fil Design C7).
    val add: (Int) -> Unit = { ml ->
        WaterFeedback.tick(context)
        onAdd(ml)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Eau", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpen) { Text("Détails") }
            }
            val target = state.targetMl
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WaterGlass(state.todayMl.toFloat() / (target ?: Hydration.MIN_ML))
                Text(
                    Hydration.formatLiters(state.todayMl) + (target?.let { " sur ${Hydration.formatLiters(it)}" }.orEmpty()),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (target != null) {
                LinearProgressIndicator(
                    progress = { (state.todayMl.toFloat() / target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (state.sessionsToday > 0) {
                    Text(
                        "Objectif augmenté de ${Hydration.formatLiters(Hydration.PER_SESSION_ML * state.sessionsToday)} pour ta séance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Pour savoir si le verre qu'on a sous les yeux est déjà noté (demande du propriétaire, 13/11/2026).
            state.todayIntakes.firstOrNull()?.let { last ->
                val count = state.todayIntakes.size
                Text(
                    "Dernier ajout : ${intakeText(last)} à ${intakeTime(last)}" +
                        if (count > 1) " · $count ajouts aujourd'hui" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Cinq boutons : retour à la ligne selon la largeur de l'écran.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalButton(colors = healthyTonalButtonColors(), onClick = { add(Hydration.GLASS_ML) }) {
                    Text("+ verre ${Hydration.formatLiters(Hydration.GLASS_ML)}")
                }
                FilledTonalButton(colors = healthyTonalButtonColors(), onClick = { add(Hydration.COFFEE_ML) }) {
                    Text("+ café ${Hydration.formatLiters(Hydration.COFFEE_ML)}")
                }
                FilledTonalButton(colors = healthyTonalButtonColors(), onClick = { add(Hydration.SMALL_CUP_ML) }) {
                    Text("+ petite tasse ${Hydration.formatLiters(Hydration.SMALL_CUP_ML)}")
                }
                FilledTonalButton(colors = healthyTonalButtonColors(), onClick = { add(Hydration.LARGE_CUP_ML) }) {
                    Text("+ grande tasse ${Hydration.formatLiters(Hydration.LARGE_CUP_ML)}")
                }
                FilledTonalButton(colors = healthyTonalButtonColors(), onClick = { add(Hydration.BOTTLE_ML) }) {
                    Text("+ bouteille ${Hydration.formatLiters(Hydration.BOTTLE_ML)}")
                }
            }
            if (state.todayIntakes.isNotEmpty()) TextButton(onClick = onUndo) { Text("Annuler le dernier ajout") }
        }
    }
}

/** Verre qui se remplit en douceur à chaque ajout ; il reste plein une fois l'objectif atteint. */
@Composable
private fun WaterGlass(fraction: Float) {
    val level by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "verre",
    )
    val water = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(width = 32.dp, height = 42.dp).semantics { contentDescription = "Verre rempli à ${(level * 100).roundToInt()} %" }) {
        val inset = size.width * 0.14f
        // Verre légèrement évasé : plus large en haut qu'en bas.
        fun glass() = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - inset, size.height)
            lineTo(inset, size.height)
            close()
        }
        clipPath(glass()) {
            val top = size.height * (1f - level)
            drawRect(water.copy(alpha = 0.85f), topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
        }
        drawPath(glass(), outline, style = Stroke(width = 2.dp.toPx()))
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

/** « Verre 20 cL », ou juste « 33 cL » pour une quantité libre. */
internal fun intakeText(intake: WaterIntake): String {
    val amount = Hydration.formatLiters(intake.ml)
    return Hydration.quickLabel(intake.ml)?.let { "$it $amount" } ?: amount
}

/** Heure de l'ajout, à l'heure du téléphone (ex. « 08:05 »). */
internal fun intakeTime(intake: WaterIntake): String =
    Instant.ofEpochMilli(intake.addedAtEpochMs).atZone(ZoneId.systemDefault()).format(timeFormat)

/** Heure du repas d'une boisson, à l'heure du téléphone. */
internal fun mealDrinkTime(drink: MealDrink): String =
    Instant.ofEpochMilli(drink.eatenAtEpochMs).atZone(ZoneId.systemDefault()).format(timeFormat)
