package com.bidaboss.healthy.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.bidaboss.healthy.importexport.data.MissingProfileException
import com.bidaboss.healthy.importexport.data.PromptRepository
import com.bidaboss.healthy.ui.healthyViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PromptsViewModel(private val repository: PromptRepository) : ViewModel() {
    suspend fun meal() = repository.mealPrompt()
    suspend fun sport() = repository.sportPrompt()
    suspend fun adjustment() = repository.adjustmentPrompt()
}

private enum class PersonalPrompt { SPORT, ADJUSTMENT }

/**
 * Prompts à copier pour se servir de sa propre IA, puis coller sa réponse dans « Importer ».
 * Le prompt repas ne contient rien de personnel ; les prompts sport, oui : on prévient avant de copier.
 */
@Composable
fun PromptsSection(
    onOpenSportProfile: () -> Unit,
    viewModel: PromptsViewModel = viewModel(factory = healthyViewModelFactory { PromptsViewModel(it.promptRepository) }),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<PersonalPrompt?>(null) }

    fun copy(label: String, build: suspend () -> String) {
        scope.launch {
            message = try {
                copyToClipboard(context, label, build())
                "Copié. Colle-le dans ton IA, puis colle sa réponse dans « Importer »."
            } catch (e: CancellationException) {
                throw e
            } catch (_: MissingProfileException) {
                "Remplis d'abord ton profil (Réglages, « Mon profil »)."
            } catch (_: Exception) {
                "La copie n'a pas marché. Réessaie."
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Ces textes expliquent à ton IA (ChatGPT, Gemini, Claude…) le format attendu par Healthy.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { copy("Prompt repas", viewModel::meal) }, modifier = Modifier.fillMaxWidth()) {
            Text("Copier le prompt repas")
        }
        OutlinedButton(onClick = { confirming = PersonalPrompt.SPORT }, modifier = Modifier.fillMaxWidth()) {
            Text("Copier le prompt programme sport")
        }
        OutlinedButton(onClick = { confirming = PersonalPrompt.ADJUSTMENT }, modifier = Modifier.fillMaxWidth()) {
            Text("Copier le prompt d'ajustement (avec mon bilan)")
        }
        TextButton(onClick = onOpenSportProfile) { Text("Modifier mon profil sportif") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }

    confirming?.let { which ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Informations personnelles") },
            text = {
                Text(
                    "Ce texte contient ton prénom, ton âge, ta taille, ton poids, tes douleurs" +
                        (if (which == PersonalPrompt.ADJUSTMENT) " et tes séances des 28 derniers jours" else "") +
                        ". Elles seront partagées avec l'IA où tu le colles.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    if (which == PersonalPrompt.SPORT) copy("Prompt sport", viewModel::sport) else copy("Prompt ajustement", viewModel::adjustment)
                }) { Text("Copier") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Annuler") } },
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
