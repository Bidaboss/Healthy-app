package com.bidaboss.healthy.meals.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.Recipe
import com.bidaboss.healthy.meals.data.RecipeRepository
import com.bidaboss.healthy.ui.EmptyState
import com.bidaboss.healthy.ui.formatGrams
import com.bidaboss.healthy.ui.formatKcal
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.parseNumber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Liste de « Mes plats ». */
class RecipesViewModel(recipes: RecipeRepository) : ViewModel() {
    val recipes: StateFlow<List<Recipe>?> =
        recipes.observeRecipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Résumé d'un plat : poids cuit, parts, calories par part ou pour 100 g. */
internal fun recipeSummary(recipe: Recipe): String {
    val per = recipe.servingWeightG?.let { w ->
        "${recipe.servings} parts de ${formatGrams(w)} · ${formatKcal(recipe.portion(w).kcal)} la part"
    } ?: "${formatKcal(recipe.portion(100.0).kcal)} pour 100 g"
    return "${formatGrams(recipe.cookedWeightG)} cuit · $per"
}

@Composable
fun RecipesScreen(
    onOpenRecipe: (Long?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipesViewModel = viewModel(factory = healthyViewModelFactory { RecipesViewModel(it.recipeRepository) }),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Mes plats", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Une recette faite maison : note ses ingrédients et le poids du plat cuit, puis ajoute-la à un repas en parts ou en grammes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(onClick = { onOpenRecipe(null) }, modifier = Modifier.fillMaxWidth()) { Text("Nouveau plat") }
        }
        val list = recipes
        if (list != null && list.isEmpty()) {
            item { EmptyState("Pas encore de plat enregistré.") }
        }
        items(list.orEmpty(), key = { it.id }) { recipe ->
            Card(onClick = { onOpenRecipe(recipe.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                    Text(recipeSummary(recipe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { TextButton(onClick = onClose) { Text("Retour") } }
    }
}

@Composable
fun RecipeEditorScreen(
    recipeId: Long?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeEditorViewModel = viewModel(
        factory = healthyViewModelFactory { RecipeEditorViewModel(it.recipeRepository, recipeId) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.finished) { if (state.finished) onClose() }

    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (state.isNew) "Nouveau plat" else "Modifier le plat", style = MaterialTheme.typography.headlineMedium)
        if (state.loading) {
            Text("Chargement…")
            return@Column
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            label = { Text("Nom du plat") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.ingredients.forEachIndexed { index, food ->
            FoodCard(
                position = index + 1,
                food = food,
                onChange = viewModel::updateIngredient,
                onRemove = { viewModel.removeIngredient(food.key) },
                label = "Ingrédient",
                showPortions = false,
                showKind = false,
            )
        }
        Button(onClick = { showSearch = true }, modifier = Modifier.fillMaxWidth()) { Text("Chercher un ingrédient") }
        OutlinedButton(onClick = viewModel::addIngredient, modifier = Modifier.fillMaxWidth()) { Text("Ajouter un ingrédient à la main") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.cookedWeightG,
                onValueChange = viewModel::setCookedWeight,
                label = { Text("Poids cuit total (g)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.servings,
                onValueChange = viewModel::setServings,
                label = { Text("Parts (facultatif)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Pèse le plat une fois cuit (sans la casserole). La cuisson change le poids : l'eau s'évapore, les pâtes en absorbent." +
                if (state.rawWeightG > 0) " Ingrédients crus : ${formatGrams(state.rawWeightG)}." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Total : ${formatKcal(state.totalKcal)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        state.errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose, enabled = !state.saving, modifier = Modifier.weight(1f)) { Text("Annuler") }
            Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.weight(1f)) { Text("Enregistrer") }
        }
        if (!state.isNew) {
            TextButton(onClick = { confirmDelete = true }, enabled = !state.saving) {
                Text("Supprimer ce plat", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showSearch) {
        FoodSearchDialog(
            onFoodChosen = { food ->
                viewModel.addFoundFood(food)
                showSearch = false
            },
            onDismiss = { showSearch = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce plat ?") },
            text = { Text("Les repas où tu l'as déjà ajouté ne changent pas.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

/** Choisir un de mes plats pour le repas, puis la quantité : 1 part (si le plat a des parts) ou des grammes. */
@Composable
internal fun RecipePickerDialog(
    recipes: List<Recipe>,
    onPicked: (Food) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf<Recipe?>(null) }
    var grams by remember { mutableStateOf("") }
    val recipe = chosen
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe?.name ?: "Mes plats") },
        text = {
            if (recipe == null) {
                if (recipes.isEmpty()) {
                    Text("Pas encore de plat. Crée-en un dans « Mes plats ».")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(recipes, key = { it.id }) { r ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { chosen = r; grams = "" }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall)
                                Text(recipeSummary(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recipe.servingWeightG?.let { w ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(1.0, 0.5, 2.0).forEach { parts ->
                                AssistChip(
                                    onClick = { onPicked(recipe.portion(w * parts)) },
                                    label = { Text(if (parts == 0.5) "½ part" else if (parts == 1.0) "1 part" else "2 parts") },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = grams,
                        onValueChange = { grams = it },
                        label = { Text("Ou en grammes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    parseNumber(grams)?.takeIf { it > 0 }?.let {
                        Text("≈ ${formatKcal(recipe.portion(it).kcal)}", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            val g = parseNumber(grams)?.takeIf { it > 0 && it <= MAX_FOOD_VALUE }
            if (recipe != null) {
                TextButton(onClick = { g?.let { onPicked(recipe.portion(it)) } }, enabled = g != null) { Text("Ajouter") }
            } else {
                TextButton(onClick = onManage) { Text("Gérer mes plats") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (recipe != null) chosen = null else onDismiss() }) {
                Text(if (recipe != null) "Retour" else "Annuler")
            }
        },
    )
}
