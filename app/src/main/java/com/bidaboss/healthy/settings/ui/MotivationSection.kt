package com.bidaboss.healthy.settings.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.motivation.Motivation
import com.bidaboss.healthy.core.motivation.MotivationMessage
import com.bidaboss.healthy.core.motivation.MotivationSlot
import com.bidaboss.healthy.core.motivation.MotivationTone
import com.bidaboss.healthy.settings.data.MotivationRepository
import com.bidaboss.healthy.settings.data.MotivationStore
import com.bidaboss.healthy.settings.reminder.MotivationNotifier
import com.bidaboss.healthy.settings.reminder.MotivationScheduler
import com.bidaboss.healthy.ui.healthyViewModelFactory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class MotivationViewModel(
    private val store: MotivationStore,
    private val scheduler: MotivationScheduler,
    private val repository: MotivationRepository,
) : ViewModel() {
    val tone: StateFlow<MotivationTone?> = store.tone

    fun setTone(tone: MotivationTone?) {
        store.setTone(tone)
        scheduler.apply(tone)
    }

    /** Un exemple tiré de la journée en cours : celle du midi avant 16 h, sinon celle du soir. */
    fun sample(onReady: (MotivationMessage) -> Unit) {
        val tone = store.tone.value ?: return
        viewModelScope.launch {
            val slot = if (LocalTime.now().hour < 16) MotivationSlot.NOON else MotivationSlot.EVENING
            onReady(Motivation.message(tone, slot, repository.snapshot(LocalDate.now())))
        }
    }
}

private fun MotivationTone?.label() = when (this) {
    null -> "Désactivées"
    MotivationTone.GENTLE -> "Gentil"
    MotivationTone.TRASH -> "Trash"
    MotivationTone.SUPER_TRASH -> "Super trash"
}

private fun MotivationTone?.description() = when (this) {
    null -> "Aucune notification de motivation."
    MotivationTone.GENTLE -> "Encouragements tout doux."
    MotivationTone.TRASH -> "Cash et vachard, sans filtre, physique compris."
    MotivationTone.SUPER_TRASH -> "Insultes et sarcasme, pour rire. À ne choisir que si tu as le cuir épais."
}

/** Notifications de motivation : désactivées par défaut, deux par jour selon la journée. */
@Composable
fun MotivationSection(
    viewModel: MotivationViewModel = viewModel(
        factory = healthyViewModelFactory {
            MotivationViewModel(it.motivationStore, it.motivationScheduler, it.motivationRepository)
        },
    ),
) {
    val current by viewModel.tone.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionRefused by remember { mutableStateOf(false) }
    var wanted by remember { mutableStateOf<MotivationTone?>(null) }

    // Android 13 et plus demandent l'accord avant d'afficher une notification.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefused = !granted
        viewModel.setTone(if (granted) wanted else null)
    }

    fun choose(tone: MotivationTone?) {
        if (tone != null && current == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted = tone
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setTone(tone)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Notifications de motivation", style = MaterialTheme.typography.titleMedium)
        Text(
            "Deux par jour, vers 12 h 30 et 19 h, selon ta journée : repas, eau et séance.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(Modifier.selectableGroup()) {
            (listOf(null) + MotivationTone.entries).forEach { tone ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = tone == current, role = Role.RadioButton) { choose(tone) }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(selected = tone == current, onClick = null)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(tone.label(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tone.description(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (permissionRefused) {
            Text(
                "Sans l'autorisation des notifications, rien ne peut s'afficher. " +
                    "Tu peux l'accorder dans les réglages Android de Healthy.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (current != null) {
            OutlinedButton(onClick = { viewModel.sample { MotivationNotifier.show(context, it) } }) {
                Text("Essayer maintenant")
            }
        }
    }
}
