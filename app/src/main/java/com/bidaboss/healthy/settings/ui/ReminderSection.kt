package com.bidaboss.healthy.settings.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.ui.dayName
import com.bidaboss.healthy.ui.healthyViewModelFactory
import java.time.LocalTime

/** Réglage du rappel des jours de séance (notification). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSection(
    viewModel: ReminderViewModel = viewModel(
        factory = healthyViewModelFactory {
            ReminderViewModel(it.workoutRepository, it.reminderSettings, it.reminderScheduler)
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionRefused by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    // Android 13 et plus demandent l'accord avant d'afficher une notification.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefused = !granted
        viewModel.setEnabled(granted)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rappel de séance", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Me rappeler les jours de séance", modifier = Modifier.weight(1f))
            Switch(
                checked = state.settings.enabled,
                onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setEnabled(on)
                    }
                },
            )
        }
        if (permissionRefused) {
            Text(
                "Sans l'autorisation des notifications, le rappel ne peut pas s'afficher. " +
                    "Tu peux l'accorder dans les réglages Android de Healthy.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.settings.enabled) {
            OutlinedButton(onClick = { pickingTime = true }) {
                Text("Heure du rappel : ${formatTime(state.settings.time)}")
            }
            Text(daysText(state), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (pickingTime) {
        val picker = rememberTimePickerState(
            initialHour = state.settings.time.hour,
            initialMinute = state.settings.time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Heure du rappel") },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTime(LocalTime.of(picker.hour, picker.minute))
                    pickingTime = false
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Annuler") } },
        )
    }
}

private fun formatTime(time: LocalTime): String = "%d h %02d".format(time.hour, time.minute)

private fun daysText(state: ReminderUiState): String = when {
    !state.hasPlan -> "Importe d'abord un plan sportif : le rappel suit ses jours d'entraînement."
    state.trainingDays.isEmpty() -> "Ton plan n'indique pas de jours d'entraînement, donc pas de rappel pour l'instant."
    else -> "Jours : " + state.trainingDays.joinToString(", ") { dayName(it).lowercase() } +
        ". Pas de rappel si la séance du jour est déjà faite."
}
