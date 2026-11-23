package com.bidaboss.healthy.today.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bidaboss.healthy.ui.WaterFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import com.bidaboss.healthy.ui.Glyphs
import com.bidaboss.healthy.ui.SvgIcon
import com.bidaboss.healthy.ui.theme.healthyTonalColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Carte « Eau » d'Aujourd'hui, allégée comme le prototype : titre et total, barre, quatre boutons à icône. */
@Composable
fun WaterCard(state: WaterUiState, onAdd: (Int) -> Unit, onUndo: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    // Petite vibration à chaque ajout (fil Design C7).
    val add: (Int) -> Unit = { ml ->
        WaterFeedback.tick(context)
        onAdd(ml)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val target = state.targetMl
            // Toucher le titre ouvre le détail de l'eau.
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Voir le détail de l'eau", onClick = onOpen),
            ) {
                Text("Eau", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    Hydration.formatLiters(state.todayMl) + (target?.let { " sur ${Hydration.formatLiters(it)}" }.orEmpty()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (target != null) {
                LinearProgressIndicator(
                    progress = { (state.todayMl.toFloat() / target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
                if (state.sessionsToday > 0) {
                    Text(
                        "Objectif augmenté de ${Hydration.formatLiters(Hydration.PER_SESSION_ML * state.sessionsToday)} pour ta séance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Quatre boutons de même largeur : un appui ajoute la quantité.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                DrinkButton("Verre", Hydration.GLASS_ML, Glyphs.GLASS, add, Modifier.weight(1f))
                DrinkButton("Café", Hydration.COFFEE_ML, Glyphs.COFFEE, add, Modifier.weight(1f))
                DrinkButton("Thé", Hydration.TEA_ML, Glyphs.TEA, add, Modifier.weight(1f))
                DrinkButton("Bouteille", Hydration.BOTTLE_ML, Glyphs.BOTTLE, add, Modifier.weight(1f))
            }
            Text(
                "Café, thé et boissons sans alcool comptent dans l'eau du jour.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // « Annuler » reste à portée de main ; il dit aussi quel est le dernier ajout (le verre sous les yeux
            // est-il déjà noté ? demande du 13/11/2026).
            state.todayIntakes.firstOrNull()?.let { last ->
                TextButton(onClick = onUndo, modifier = Modifier.align(Alignment.End)) {
                    Text("Annuler : ${intakeText(last)} à ${intakeTime(last)}")
                }
            }
        }
    }
}

/** Un bouton d'ajout rapide : icône, nom, quantité (« + 20 cL »). */
@Composable
private fun DrinkButton(name: String, ml: Int, glyph: String, onAdd: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tonal = healthyTonalColors()
    val amount = Hydration.formatLiters(ml)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tonal.container)
            .clickable(role = Role.Button) { onAdd(ml) }
            .semantics { contentDescription = "Ajouter $name $amount" }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        SvgIcon(glyph, tonal.content, size = 22.dp)
        Text(name, style = MaterialTheme.typography.labelLarge, color = tonal.content, maxLines = 1)
        Text("+ ${ml / 10} cL", style = MaterialTheme.typography.labelSmall, color = tonal.content, maxLines = 1)
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
