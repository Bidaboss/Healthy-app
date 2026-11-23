package com.bidaboss.healthy.meals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.meals.data.ScannedProduct
import com.bidaboss.healthy.ui.EmptyState
import com.bidaboss.healthy.ui.formatKcal
import com.bidaboss.healthy.ui.healthyViewModelFactory

/** « Mes produits scannés » : les produits retenus sur le téléphone. */
@Composable
fun ScannedProductsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScannedProductsViewModel = viewModel(
        factory = healthyViewModelFactory { ScannedProductsViewModel(it.scannedProductRepository) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Mes produits scannés", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Les produits que tu as scannés restent sur ton téléphone : tu peux les ajouter même sans internet. " +
                    "Corriger ou supprimer un produit ne change pas les repas déjà notés.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.message?.let { message ->
            item(key = "message") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(start = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(message, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                    }
                }
            }
        }
        val list = state.products
        if (list != null && list.isEmpty()) {
            item { EmptyState("Pas encore de produit scanné. Utilise « Scanner » dans un repas.") }
        }
        items(list.orEmpty(), key = { it.product.code }) { scanned ->
            ProductRow(
                scanned = scanned,
                refreshing = state.refreshingCode == scanned.product.code,
                onEdit = { viewModel.edit(scanned) },
                onDelete = { viewModel.askDelete(scanned) },
                onRefresh = { viewModel.refresh(scanned) },
            )
        }
        item { TextButton(onClick = onClose) { Text("Retour") } }
    }

    state.deleting?.let { product ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Supprimer ce produit ?") },
            text = { Text("« ${product.product.name} » sera oublié. Il faudra le scanner de nouveau pour le retrouver.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Annuler") } },
        )
    }

    state.editing?.let { edit ->
        AlertDialog(
            onDismissRequest = viewModel::cancelEdit,
            title = { Text("Corriger le produit") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Code : ${edit.code}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val form = edit.form
                    EditField("Nom du produit", form.name, KeyboardType.Text) { viewModel.setForm(form.copy(name = it)) }
                    EditField("Marque (facultatif)", form.brand, KeyboardType.Text) { viewModel.setForm(form.copy(brand = it)) }
                    EditField("Calories pour 100 g (kcal)", form.kcal, KeyboardType.Decimal) { viewModel.setForm(form.copy(kcal = it)) }
                    EditField("Protéines pour 100 g (g)", form.protein, KeyboardType.Decimal) { viewModel.setForm(form.copy(protein = it)) }
                    EditField("Glucides pour 100 g (g)", form.carbs, KeyboardType.Decimal) { viewModel.setForm(form.copy(carbs = it)) }
                    EditField("Lipides pour 100 g (g)", form.fat, KeyboardType.Decimal) { viewModel.setForm(form.copy(fat = it)) }
                    EditField("Fibres pour 100 g (g)", form.fiber, KeyboardType.Decimal) { viewModel.setForm(form.copy(fiber = it)) }
                    EditField("Une portion (g)", form.serving, KeyboardType.Decimal) { viewModel.setForm(form.copy(serving = it)) }
                    edit.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = viewModel::saveEdit) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = viewModel::cancelEdit) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ProductRow(scanned: ScannedProduct, refreshing: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onRefresh: () -> Unit) {
    val product = scanned.product
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(
                product.brand,
                "${formatKcal(product.kcalPer100g)} pour 100 g",
                if (scanned.fromOpenFoodFacts) "Open Food Facts" else "Saisi depuis l'étiquette",
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Corriger") }
                OutlinedButton(onClick = onDelete) { Text("Supprimer") }
            }
            if (scanned.fromOpenFoodFacts) {
                TextButton(onClick = onRefresh, enabled = !refreshing) {
                    Text(if (refreshing) "Mise à jour…" else "Mettre à jour depuis Open Food Facts")
                }
            }
        }
    }
}

@Composable
private fun EditField(label: String, value: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}
