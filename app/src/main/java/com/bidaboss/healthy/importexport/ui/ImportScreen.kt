package com.bidaboss.healthy.importexport.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bidaboss.healthy.core.importformat.DrinkKind
import com.bidaboss.healthy.core.importformat.ImportDocument
import com.bidaboss.healthy.core.importformat.ImportedMeal
import com.bidaboss.healthy.core.importformat.ImportedPlan
import com.bidaboss.healthy.ui.DateTimeButtons
import com.bidaboss.healthy.ui.MealTypeChips
import com.bidaboss.healthy.ui.dayName
import com.bidaboss.healthy.ui.formatGrams
import com.bidaboss.healthy.ui.formatKcal
import java.time.format.DateTimeFormatter
import java.util.Locale

private val mealDateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", Locale.FRENCH)

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onPickFile: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Importer", style = MaterialTheme.typography.headlineMedium)

        when (val current = state) {
            ImportUiState.Idle -> SourceButtons(
                intro = "Copie le bloc JSON envoyé par ton IA (repas ou plan sportif), puis appuie sur Coller.",
                onPaste = { pasteFromClipboard(context, viewModel) },
                onPickFile = onPickFile,
            )

            is ImportUiState.Invalid -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Import impossible",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        current.errors.forEach {
                            Text("• $it", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                SourceButtons(
                    intro = "Tu peux réessayer, ou envoyer ce message d'erreur à Claude pour qu'il corrige le fichier.",
                    onPaste = { pasteFromClipboard(context, viewModel) },
                    onPickFile = onPickFile,
                )
            }

            is ImportUiState.Preview -> {
                when (val document = current.document) {
                    is ImportDocument.Meal -> {
                        MealPreview(document.meal)
                        if (!current.alreadyImported) {
                            Text("Tu peux corriger le type de repas, la date et l'heure :")
                            MealTypeChips(
                                selected = document.meal.type,
                                onSelect = { viewModel.editMeal(it, document.meal.eatenAt.toLocalDateTime()) },
                            )
                            DateTimeButtons(
                                dateTime = document.meal.eatenAt.toLocalDateTime(),
                                onChange = { viewModel.editMeal(document.meal.type, it) },
                            )
                        }
                        when (val replacement = current.replacement) {
                            Replacement.None -> Unit
                            is Replacement.Found -> Text(
                                "Ce repas remplace : ${replacement.description}.",
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Replacement.NotFound -> Text(
                                "Le repas à remplacer est introuvable : celui-ci sera ajouté.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    is ImportDocument.WorkoutPlan -> PlanPreview(document.plan)
                    is ImportDocument.Backup -> BackupPreview(document)
                }
                if (current.warnings.isNotEmpty()) {
                    Text(
                        "Le fichier peut être importé. L'app a juste laissé de côté ${
                            if (current.warnings.size == 1) "ce détail" else "ces ${current.warnings.size} détails"
                        } :",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    current.warnings.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (current.alreadyImported) {
                    Text(
                        "Ce fichier a déjà été importé. Il ne sera pas ajouté une deuxième fois.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) { Text("Retour") }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = viewModel::reset,
                            enabled = !current.saving,
                            modifier = Modifier.weight(1f),
                        ) { Text("Annuler") }
                        Button(
                            onClick = viewModel::confirm,
                            enabled = !current.saving,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (current.document is ImportDocument.Backup) "Restaurer" else "Enregistrer") }
                    }
                }
            }

            is ImportUiState.Saved -> {
                Text(current.message, style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        viewModel.reset()
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Terminer") }
                OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                    Text("Importer autre chose")
                }
            }
        }
    }
}

@Composable
private fun SourceButtons(intro: String, onPaste: () -> Unit, onPickFile: () -> Unit) {
    Text(intro, style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onPaste, modifier = Modifier.fillMaxWidth()) { Text("Coller") }
    OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) { Text("Choisir un fichier") }
}

@Composable
private fun MealPreview(meal: ImportedMeal) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(meal.title ?: meal.type.label, style = MaterialTheme.typography.titleLarge)
            Text(
                "${meal.type.label} · ${meal.eatenAt.format(mealDateFormat)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatKcal(meal.totalKcal),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Protéines ${formatGrams(meal.totalProteinG)} · Glucides ${formatGrams(meal.totalCarbsG)} · Lipides ${formatGrams(meal.totalFatG)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            meal.items.forEach { food ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val quantity = food.quantityG?.let { " (${formatGrams(it)})" }.orEmpty()
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        when (food.drink) {
                            DrinkKind.DRINK -> Icon(Icons.Rounded.WaterDrop, contentDescription = "Boisson, comptée dans l'eau", modifier = Modifier.size(16.dp))
                            DrinkKind.ALCOHOL -> Icon(Icons.Rounded.LocalBar, contentDescription = "Alcool, pas compté dans l'eau", modifier = Modifier.size(16.dp))
                            null -> Unit
                        }
                        Text("${food.name}$quantity", modifier = Modifier.padding(start = if (food.drink != null) 4.dp else 0.dp))
                    }
                    Text(formatKcal(food.kcal))
                }
            }
            meal.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlanPreview(plan: ImportedPlan) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(plan.name, style = MaterialTheme.typography.titleLarge)
            plan.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            plan.weeks?.let { Text("Durée : $it semaines") }
            plan.sessions.forEach { session ->
                val day = session.dayOfWeek?.let { "${dayName(it)} : " }.orEmpty()
                val durations = session.variants.joinToString(" / ") { "${it.durationMin} min" }
                Text("$day${session.title} ($durations)")
            }
            Text(
                "Ce plan remplacera ton plan actuel. Ton historique de séances est conservé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun pasteFromClipboard(context: Context, viewModel: ImportViewModel) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    if (text.isNullOrBlank()) {
        viewModel.reportError("Le presse-papiers est vide. Copie d'abord le bloc envoyé par Claude.")
    } else {
        viewModel.submitText(text)
    }
}

private val backupDateFormat = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", java.util.Locale.FRENCH)

@Composable
private fun BackupPreview(document: ImportDocument.Backup) {
    val content = document.content
    Text("Sauvegarde Healthy", style = MaterialTheme.typography.titleLarge)
    Text("Faite le ${document.createdAt.toLocalDateTime().format(backupDateFormat)}.")
    Text(
        "${content.rowCount("meals")} repas, ${content.rowCount("workout_logs")} séances, " +
            "${content.rowCount("activities")} activités, ${content.rowCount("weigh_ins")} pesées.",
    )
    Text(
        "Attention : restaurer remplace toutes les données de ce téléphone (repas, plan, séances, profil, pesées) " +
            "par celles de la sauvegarde.",
        color = MaterialTheme.colorScheme.error,
    )
}
