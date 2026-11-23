package com.bidaboss.healthy.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.body.WaterCheck
import com.bidaboss.healthy.core.body.WaterReminder
import com.bidaboss.healthy.core.body.WaterReminderMessage
import com.bidaboss.healthy.settings.data.MotivationRepository
import com.bidaboss.healthy.settings.data.WaterReminderStore
import com.bidaboss.healthy.settings.reminder.WaterReminderNotifier
import com.bidaboss.healthy.settings.reminder.WaterReminderScheduler
import com.bidaboss.healthy.ui.healthyViewModelFactory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class WaterReminderViewModel(
    private val store: WaterReminderStore,
    private val scheduler: WaterReminderScheduler,
    private val repository: MotivationRepository,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = store.enabled

    fun setEnabled(enabled: Boolean) {
        store.setEnabled(enabled)
        scheduler.apply(enabled)
    }

    /**
     * Un exemple tiré de la journée en cours, pour le prochain point de vérification. Rend null si
     * l'on est dans les temps : la vraie notification ne s'afficherait pas non plus.
     */
    fun sample(onReady: (WaterReminderMessage?) -> Unit) {
        viewModelScope.launch {
            val now = LocalTime.now()
            val check = WaterCheck.entries.lastOrNull { !LocalTime.of(it.hour, it.minute).isAfter(now) } ?: WaterCheck.LATE_MORNING
            val day = repository.snapshot(LocalDate.now())
            onReady(WaterReminder.message(check, day.waterMl, day.waterTargetMl))
        }
    }
}

/** Rappels pour boire : désactivés par défaut ; quatre vérifications, notification seulement en retard. */
@Composable
fun WaterReminderSection(
    viewModel: WaterReminderViewModel = viewModel(
        factory = healthyViewModelFactory {
            WaterReminderViewModel(it.waterReminderStore, it.waterReminderScheduler, it.motivationRepository)
        },
    ),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionRefused by remember { mutableStateOf(false) }
    var sampleNote by remember { mutableStateOf<String?>(null) }

    // Android 13 et plus demandent l'accord avant d'afficher une notification.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefused = !granted
        viewModel.setEnabled(granted)
    }

    fun toggle(on: Boolean) {
        val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.setEnabled(on)
        sampleNote = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = ::toggle),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Rappels pour boire", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Vers 11 h 30, 14 h, 17 h 30 et 20 h 30, l'app regarde où tu en es de ton objectif d'eau. " +
                        "Elle ne te prévient que si tu es en retard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
        if (permissionRefused) {
            Text(
                "Sans l'autorisation des notifications, rien ne peut s'afficher. " +
                    "Tu peux l'accorder dans les réglages Android de Healthy.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (enabled) {
            OutlinedButton(onClick = {
                viewModel.sample { message ->
                    if (message == null) {
                        sampleNote = "Tu es dans les temps : pas de rappel pour l'instant."
                    } else {
                        sampleNote = null
                        WaterReminderNotifier.show(context, message)
                    }
                }
            }) { Text("Essayer maintenant") }
            sampleNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
