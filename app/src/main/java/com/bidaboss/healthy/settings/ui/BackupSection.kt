package com.bidaboss.healthy.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.importexport.data.BackupRepository
import com.bidaboss.healthy.ui.healthyViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class BackupViewModel(private val repository: BackupRepository) : ViewModel() {
    suspend fun exportText(): String = repository.export()
}

/**
 * « Exporter mes données » : écrit une sauvegarde complète dans un fichier choisi par l'utilisateur
 * (Téléchargements, Drive…). La restauration passe par « Importer », comme un repas ou un plan.
 */
@Composable
fun BackupSection(
    viewModel: BackupViewModel = viewModel(factory = healthyViewModelFactory { BackupViewModel(it.backupRepository) }),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                val text = viewModel.exportText()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("Fichier impossible à ouvrir")
                }
                "Sauvegarde enregistrée. Garde ce fichier hors du téléphone (Drive, e-mail) : il sert à tout retrouver."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "La sauvegarde a échoué. Réessaie, ou choisis un autre endroit."
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { createFile.launch("healthy-sauvegarde-${LocalDate.now()}.json") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Exporter mes données") }
        Text(
            "La sauvegarde contient tes données de santé : repas, poids, séances et, si ta montre est connectée, ses relevés " +
                "(pas, sommeil, cœur). Garde le fichier pour toi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Pour restaurer sur ce téléphone ou un nouveau : « Importer », puis choisis le fichier de sauvegarde.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
