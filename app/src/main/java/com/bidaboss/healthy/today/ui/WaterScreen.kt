package com.bidaboss.healthy.today.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.ui.healthyViewModelFactory
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/** Détail de l'eau : quantité libre, ajouts du jour (supprimables un par un) et 7 derniers jours. */
@Composable
fun WaterScreen(
    onClose: () -> Unit,
    viewModel: WaterViewModel = viewModel(
        factory = healthyViewModelFactory { WaterViewModel(it.bodyRepository, it.workoutLogRepository) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val snackbar = remember { SnackbarHostState() }
    state.lastDeleted?.let { gone ->
        LaunchedEffect(gone) {
            val result = snackbar.showSnackbar("Ajout supprimé : ${intakeText(gone)}", "Annuler", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreDeleted() else viewModel.deletedMessageShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Eau", style = MaterialTheme.typography.headlineMedium)
        WaterCard(state, onAdd = viewModel::add, onUndo = viewModel::undo, onOpen = {})
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.freeAmount,
                onValueChange = viewModel::setFreeAmount,
                label = { Text("Autre quantité (cL)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::addFreeAmount) { Text("Ajouter") }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "Comptent : eau, thé, café, tisanes. L'alcool ne compte pas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Ajouts d'aujourd'hui", style = MaterialTheme.typography.titleLarge)
        if (state.todayIntakes.isEmpty()) {
            Text("Rien de noté pour l'instant.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.todayIntakes.forEach { intake ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    intakeTime(intake),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                Text(intakeText(intake), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.delete(intake) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Supprimer cet ajout (${intakeText(intake)} à ${intakeTime(intake)})")
                }
            }
        }

        if (state.mealDrinks.isNotEmpty()) {
            Text("Dans les repas", style = MaterialTheme.typography.titleLarge)
            state.mealDrinks.forEach { drink ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mealDrinkTime(drink),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(
                        "${drink.name} · ${Hydration.formatLiters(drink.ml)} · ${drink.mealType.label.lowercase()}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "Ces boissons sont comptées dans ton eau. Pour les changer, modifie le repas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text("7 derniers jours", style = MaterialTheme.typography.titleLarge)
        state.lastDays.reversed().forEach { day ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(day.date.format(dayFormat).replaceFirstChar(Char::uppercase), modifier = Modifier.weight(1f))
                Text(Hydration.formatLiters(day.ml), style = MaterialTheme.typography.titleMedium)
            }
        }
        TextButton(onClick = onClose) { Text("Retour") }
    }
    SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
