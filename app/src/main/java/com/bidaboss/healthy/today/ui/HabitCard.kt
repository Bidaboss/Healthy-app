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
import com.bidaboss.healthy.meals.data.HabitualMeal
import com.bidaboss.healthy.ui.formatKcal
import com.bidaboss.healthy.ui.theme.healthyTonalButtonColors

/** « Ton petit-déjeuner habituel ? » : le titre ou les aliments, les calories, et deux boutons. */
@Composable
fun HabitCard(suggestion: HabitualMeal?, onAdd: (HabitualMeal) -> Unit, onDismiss: () -> Unit) {
    if (suggestion == null) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ton ${suggestion.type.label.lowercase()} habituel ?", style = MaterialTheme.typography.titleMedium)
            Text(
                suggestion.title ?: suggestion.foods.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${formatKcal(suggestion.kcal)} · noté ${suggestion.occurrences} fois ces 4 dernières semaines",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Pas aujourd'hui") }
                FilledTonalButton(colors = healthyTonalButtonColors(), onClick = { onAdd(suggestion) }) { Text("Ajouter") }
            }
        }
    }
}
