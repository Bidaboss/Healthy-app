package com.bidaboss.healthy.health.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.health.data.HealthConnectSource
import com.bidaboss.healthy.health.data.WatchRepository
import com.bidaboss.healthy.health.data.WatchStatus
import com.bidaboss.healthy.ui.healthyViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WatchSettingsViewModel(private val watch: WatchRepository) : ViewModel() {
    private val _status = MutableStateFlow<WatchStatus?>(null)
    val status: StateFlow<WatchStatus?> = _status.asStateFlow()
    private val _extras = MutableStateFlow(true)

    /** Sommeil et cœur autorisés ? Faux : on propose de les ajouter. */
    val extras: StateFlow<Boolean> = _extras.asStateFlow()

    private val _historyDays = MutableStateFlow(0)

    /** Nombre de jours gardés dans l'historique de la montre (0 : rien à effacer). */
    val historyDays: StateFlow<Int> = _historyDays.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _status.value = watch.status()
            _extras.value = watch.extrasGranted()
            _historyDays.value = watch.historyCount()
        }
    }

    /** « Effacer l'historique de la montre » : ne touche ni aux repas, ni aux séances, ni aux activités. */
    fun clearHistory() {
        viewModelScope.launch {
            watch.clearHistory()
            _historyDays.value = 0
        }
    }
}

/** Réglages, carte « Ma montre » : autoriser la lecture de Health Connect (lecture seule). */
@Composable
fun WatchSection(
    viewModel: WatchSettingsViewModel = viewModel(
        factory = healthyViewModelFactory { WatchSettingsViewModel(it.watchRepository) },
    ),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val historyDays by viewModel.historyDays.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectSource.permissionContract()) { viewModel.refresh() }

    // Au retour de Health Connect ou des réglages d'Android, l'état peut avoir changé.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (status) {
            null -> Unit

            WatchStatus.UNAVAILABLE -> Text(
                "Health Connect n'est pas disponible sur ce téléphone. Il est fourni avec Android 14, " +
                    "et s'installe depuis le Play Store sur les versions plus anciennes.",
            )

            WatchStatus.UPDATE_REQUIRED -> Text("Health Connect doit être mis à jour : ouvre le Play Store, puis reviens ici.")

            WatchStatus.NEEDS_PERMISSION -> {
                Text(
                    "Healthy peut lire les pas, les calories actives, les séances, le sommeil et le cœur de ta montre (Garmin par exemple) " +
                        "dans Health Connect. C'est en lecture seule et rien ne quitte ton téléphone.",
                )
                Text(
                    "Dans l'app Garmin Connect, active aussi le partage vers Health Connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { permissionLauncher.launch(HealthConnectSource.PERMISSIONS) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connecter ma montre") }
            }

            WatchStatus.READY -> {
                Text(
                    "Connectée. Healthy lit tes pas, tes calories actives, tes séances" +
                        (if (extras) ", ton sommeil et ton cœur" else "") +
                        " dans Health Connect, en lecture seule. Tu les retrouves sur Aujourd'hui.",
                )
                if (!extras) {
                    Text(
                        "Tu peux aussi autoriser la lecture du sommeil et du cœur (facultatif).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { permissionLauncher.launch(HealthConnectSource.PERMISSIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Ajouter le sommeil et le cœur") }
                }
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(HealthConnectSource.manageIntent(context)) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Gérer l'accès dans Health Connect") }
            }
        }
        if (historyDays > 0) {
            Text(
                "Healthy garde sur ton téléphone un relevé par jour (pas, calories actives, sommeil, cœur) pour tes tendances : " +
                    "$historyDays jours pour l'instant. Il est dans la sauvegarde.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Effacer l'historique de la montre")
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer l'historique de la montre ?") },
            text = {
                Text(
                    "Les relevés gardés par jour seront supprimés. Tes repas, tes séances et tes activités ne changent pas. " +
                        "Les 30 derniers jours pourront être relus depuis ta montre.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearHistory()
                    },
                ) { Text("Effacer") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}
