package com.bidaboss.healthy.meals.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.FoodHistory
import com.bidaboss.healthy.meals.data.HistoryFood
import com.bidaboss.healthy.meals.data.SharePortion
import com.bidaboss.healthy.ui.formatGrams
import com.bidaboss.healthy.ui.formatKcal
import com.bidaboss.healthy.ui.healthyViewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Ce que choisit l'utilisateur dans la feuille « Ajouter » de l'onglet Repas. */
enum class AddAction(val label: String, val hint: String, val icon: ImageVector) {
    IMPORT_FROM_AI("Importer depuis mon IA", "Colle le bloc envoyé par Claude", Icons.Rounded.ContentPaste),
    SCAN("Scanner un code-barres", "Un produit en un geste", Icons.Rounded.QrCodeScanner),
    SEARCH("Chercher un aliment", "Dans Open Food Facts", Icons.Rounded.Search),
    QUICK_ENTRY("Saisie rapide", "Juste les calories", Icons.Rounded.Bolt),
    HISTORY("Déjà mangés", "Tes aliments habituels", Icons.Rounded.History),
    SAVED_FOODS("Mes aliments", "Tes favoris", Icons.Rounded.Bookmarks),
    CREATE_FOOD("Créer un aliment", "Pour le retrouver ensuite", Icons.Rounded.NoteAdd),
    RECIPES("Mes plats", "Tes recettes, en parts ou en grammes", Icons.AutoMirrored.Rounded.MenuBook),
    NEW_MEAL("Composer un repas", "Choisir le type, l'heure et les aliments", Icons.Rounded.EditNote),
}

/** Feuille « Ajouter » : toutes les façons d'ajouter quelque chose au journal, au même endroit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(actions: List<AddAction>, onChoose: (AddAction) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                "Ajouter",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChoose(action) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column {
                        Text(action.label, style = MaterialTheme.typography.titleMedium)
                        Text(action.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** « Déjà mangés » : les aliments déjà notés, filtrés à chaque lettre, sans réseau. */
class FoodHistoryViewModel(history: Flow<List<HistoryFood>>) : ViewModel() {
    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query

    val items: StateFlow<List<HistoryFood>> = combine(history, query) { all, q ->
        FoodHistory.filter(all, q).take(MAX_SHOWN)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(text: String) {
        query.value = text
    }

    private companion object {
        const val MAX_SHOWN = 40
    }
}

@Composable
internal fun FoodHistoryDialog(
    onFoodChosen: (Food) -> Unit,
    onDismiss: () -> Unit,
    viewModel: FoodHistoryViewModel = viewModel(
        key = "food-history",
        factory = healthyViewModelFactory { FoodHistoryViewModel(it.mealRepository.observeFoodHistory()) },
    ),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Déjà mangés") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Chercher dans ce que j'ai déjà mangé") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (items.isEmpty()) {
                    Text(
                        if (query.isBlank()) "Rien encore : tes aliments apparaîtront ici après quelques repas." else "Rien de déjà mangé ne correspond.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(items, key = { it.key }) { item ->
                        val food = item.food
                        Column(modifier = Modifier.fillMaxWidth().clickable { onFoodChosen(food) }.padding(vertical = 8.dp)) {
                            Text(food.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(food.quantityG?.let { formatGrams(it) } ?: food.portion, formatKcal(food.kcal)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/**
 * « Partager ce repas » : la portion de l'autre personne (Pareil, La moitié, Un tiers, Autre), puis l'envoi par le
 * partage Android d'un fichier au format d'import. L'autre l'ouvre dans son app Healthy, sans compte.
 */
@Composable
internal fun ShareMealDialog(onShare: (SharePortion) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf<SharePortion>(SharePortion.Same) }
    var custom by remember { mutableStateOf(false) }
    var percent by remember { mutableStateOf("") }
    val choices = listOf(SharePortion.Same, SharePortion.Half, SharePortion.Third)
    val customPortion = SharePortion.parsePercent(percent)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partager ce repas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Quelle portion pour l'autre personne ?", style = MaterialTheme.typography.bodyMedium)
                choices.forEach { portion ->
                    PortionRow(portion.label, selected = !custom && chosen == portion) {
                        custom = false
                        chosen = portion
                    }
                }
                PortionRow("Autre", selected = custom) { custom = true }
                if (custom) {
                    OutlinedTextField(
                        value = percent,
                        onValueChange = { percent = it },
                        label = { Text("Pourcentage du repas (1 à 100)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Les quantités et les calories sont ajustées. L'autre personne l'ouvre dans Healthy : le repas s'ajoute à son journal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            val portion = if (custom) customPortion else chosen
            TextButton(onClick = { portion?.let(onShare) }, enabled = portion != null) { Text("Envoyer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun PortionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
