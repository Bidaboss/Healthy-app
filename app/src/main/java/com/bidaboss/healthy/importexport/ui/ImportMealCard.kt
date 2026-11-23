package com.bidaboss.healthy.importexport.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bidaboss.healthy.core.importformat.ImportDocument

/**
 * Carte « Importer un repas de mon IA » en tête de l'onglet Repas : coller ou choisir un fichier, aperçu du repas
 * reconnu (plats, calories, macros), « Ajouter au journal ». Même moteur que l'écran Importer : un fichier qui n'est
 * pas un repas (plan, sauvegarde) ou qui est refusé s'ouvre dans l'écran Importer ([onOpenImport]).
 */
@Composable
fun ImportMealCard(
    viewModel: ImportViewModel,
    onPickFile: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
    /** Second bouton de la rangée au repos (« Ajouter une boisson »), posé par l'écran Repas. */
    idleExtra: @Composable RowScope.() -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val current = state
    val preview = current as? ImportUiState.Preview
    val meal = (preview?.document as? ImportDocument.Meal)?.meal

    val idle = meal == null && preview == null && current !is ImportUiState.Invalid && current !is ImportUiState.Saved
    if (idle) {
        // Au repos : une rangée de deux boutons (retouches du 23/11/2026), pas une carte entière.
        var menuOpen by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                Button(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Importer d'une IA", maxLines = 1, softWrap = false, textAlign = TextAlign.Center) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Coller le bloc copié") },
                        onClick = { menuOpen = false; pasteFromClipboard(context, viewModel) },
                    )
                    DropdownMenuItem(
                        text = { Text("Choisir un fichier") },
                        onClick = { menuOpen = false; onPickFile() },
                    )
                }
            }
            idleExtra()
        }
        return
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Importer un repas de mon IA", style = MaterialTheme.typography.titleMedium)
            when {
                meal != null && preview != null -> {
                    MealPreview(meal)
                    if (preview.alreadyImported) {
                        Text("Ce repas a déjà été ajouté. Il ne le sera pas deux fois.", color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                    } else {
                        if (preview.replacement is Replacement.Found) {
                            Text("Ce repas remplace : ${preview.replacement.description}.", color = MaterialTheme.colorScheme.primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = viewModel::reset, enabled = !preview.saving, modifier = Modifier.weight(1f)) {
                                Text("Annuler")
                            }
                            Button(onClick = viewModel::confirm, enabled = !preview.saving, modifier = Modifier.weight(1f)) {
                                Text("Ajouter au journal")
                            }
                        }
                        TextButton(onClick = onOpenImport) { Text("Corriger le type ou l'heure") }
                    }
                }
                preview != null -> {
                    Text("Ce fichier n'est pas un repas (plan ou sauvegarde).")
                    Button(onClick = onOpenImport, modifier = Modifier.fillMaxWidth()) { Text("Voir dans Importer") }
                    TextButton(onClick = viewModel::reset) { Text("Annuler") }
                }
                current is ImportUiState.Invalid -> {
                    Text(current.errors.firstOrNull() ?: "Import impossible.", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = viewModel::reset, modifier = Modifier.weight(1f)) { Text("Fermer") }
                        Button(onClick = onOpenImport, modifier = Modifier.weight(1f)) { Text("Détails") }
                    }
                }
                current is ImportUiState.Saved -> {
                    Text(current.message)
                    Button(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) { Text("OK") }
                }
                else -> {
                    Text(
                        "Copie le bloc envoyé par ton IA puis appuie sur Coller, ou choisis le fichier.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { pasteFromClipboard(context, viewModel) }, modifier = Modifier.weight(1f)) { Text("Coller") }
                        OutlinedButton(onClick = onPickFile, modifier = Modifier.weight(1f)) { Text("Choisir un fichier") }
                    }
                }
            }
        }
    }
}
