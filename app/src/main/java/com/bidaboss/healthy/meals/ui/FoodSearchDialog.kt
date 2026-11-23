package com.bidaboss.healthy.meals.ui

import com.bidaboss.healthy.ui.parseNumber
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.openfoodfacts.OffProduct
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.HistoryFood
import com.bidaboss.healthy.ui.formatGrams
import com.bidaboss.healthy.ui.formatKcal
import com.bidaboss.healthy.ui.healthyViewModelFactory

/** Recherche plein écran d'un aliment dans Open Food Facts, puis choix de la quantité. */
@Composable
fun FoodSearchDialog(
    onFoodChosen: (Food) -> Unit,
    onDismiss: () -> Unit,
    viewModel: FoodSearchViewModel = viewModel(
        key = "food-search",
        factory = healthyViewModelFactory { FoodSearchViewModel(it.foodSearchRepository, it.mealRepository.observeFoodHistory()) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.historyState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reset() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Chercher un aliment", style = MaterialTheme.typography.headlineSmall)
                    if (history.hasHistory && state.selected == null) TextButton(onClick = onDismiss) { Text("Fermer") }
                }

                val selected = state.selected
                if (selected == null && history.hasHistory) {
                    // « Déjà mangés » d'abord : sans réseau, la liste se filtre à chaque lettre. Open Food Facts
                    // ne part que sur le bouton (10 recherches par minute au plus).
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        label = { Text("Nom de l'aliment ou du produit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "history-title") {
                            Text(
                                if (history.items.isEmpty()) "Rien de déjà mangé ne correspond." else "Déjà mangés",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(history.items, key = { "h-${it.key}" }) { item ->
                            HistoryCard(item, onClick = { onFoodChosen(item.food) })
                        }
                        item(key = "off-button") {
                            Button(
                                onClick = viewModel::search,
                                enabled = !state.searching,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (state.searching) "Recherche…" else "Chercher aussi sur Open Food Facts") }
                        }
                        state.message?.let { message ->
                            item(key = "off-message") { Text(message, color = MaterialTheme.colorScheme.error) }
                        }
                        state.detail?.let { detail ->
                            item(key = "off-detail") {
                                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(state.results.orEmpty(), key = { it.code }) { product ->
                            ProductCard(product, onClick = { viewModel.select(product) })
                        }
                        if (state.results != null) {
                            item(key = "off-credit") {
                                Text(
                                    "Données : Open Food Facts (base libre, valeurs indicatives).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else if (selected == null) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        label = { Text("Nom de l'aliment ou du produit") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Fermer") }
                        Button(
                            onClick = viewModel::search,
                            enabled = !state.searching,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (state.searching) "Recherche…" else "Chercher") }
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    state.detail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.results.orEmpty(), key = { it.code }) { product ->
                            ProductCard(product, onClick = { viewModel.select(product) })
                        }
                        item {
                            Text(
                                "Données : Open Food Facts (base libre, valeurs indicatives).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(selected.name, style = MaterialTheme.typography.titleLarge)
                    selected.brand?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("Pour 100 g : ${formatKcal(selected.kcalPer100g)}")
                    OutlinedTextField(
                        value = state.quantityG,
                        onValueChange = viewModel::setQuantity,
                        label = { Text("Quantité mangée (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    parseNumber(state.quantityG)?.takeIf { it > 0 }?.let { quantity ->
                        val nutrients = selected.forQuantity(quantity)
                        Text(
                            formatKcal(nutrients.kcal),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Protéines ${formatGrams(nutrients.proteinG ?: 0.0)} · " +
                                "Glucides ${formatGrams(nutrients.carbsG ?: 0.0)} · " +
                                "Lipides ${formatGrams(nutrients.fatG ?: 0.0)}",
                        )
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = viewModel::clearSelection, modifier = Modifier.weight(1f)) {
                            Text("Retour")
                        }
                        Button(
                            onClick = { viewModel.buildFood()?.let(onFoodChosen) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Ajouter") }
                    }
                }
            }
        }
    }
}

/** Un aliment déjà mangé : sa dernière quantité et ses calories ; un toucher l'ajoute. */
@Composable
private fun HistoryCard(item: HistoryFood, onClick: () -> Unit) {
    val food = item.food
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(food.name, style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(food.quantityG?.let { formatGrams(it) } ?: food.portion, formatKcal(food.kcal))
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProductCard(product: OffProduct, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(product.brand, "${formatKcal(product.kcalPer100g)} pour 100 g")
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
