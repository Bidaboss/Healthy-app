package com.bidaboss.healthy.meals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.importformat.DrinkKind
import com.bidaboss.healthy.ui.parseNumber
import com.bidaboss.healthy.meals.data.Meal
import com.bidaboss.healthy.ui.DateTimeButtons
import com.bidaboss.healthy.ui.MealTypeChips
import com.bidaboss.healthy.ui.formatKcal
import com.bidaboss.healthy.ui.healthyViewModelFactory
import java.time.LocalDate

/** Saisie d'un nouveau repas ([mealId] nul) ou modification d'un repas existant. */
@Composable
fun MealEditorScreen(
    mealId: Long?,
    initialDate: LocalDate,
    onClose: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenSavedFoods: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealEditorViewModel = viewModel(
        factory = healthyViewModelFactory { MealEditorViewModel(it.mealRepository, mealId, initialDate, savedFoods = it.savedFoodRepository) },
    ),
    recipesViewModel: RecipesViewModel = viewModel(factory = healthyViewModelFactory { RecipesViewModel(it.recipeRepository) }),
    savedFoodsViewModel: SavedFoodsViewModel = viewModel(factory = healthyViewModelFactory { SavedFoodsViewModel(it.savedFoodRepository) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recipes by recipesViewModel.recipes.collectAsStateWithLifecycle()
    var pickingRecipe by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var caloriesOnly by remember { mutableStateOf(false) }
    var addingFood by remember { mutableStateOf(false) }
    var pickingSaved by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val savedFoods by savedFoodsViewModel.foods.collectAsStateWithLifecycle()
    val savedKeys by viewModel.savedKeys.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onClose()
    }

    // Partage Android du fichier d'import : un proche l'ouvre dans son app Healthy.
    val mealToShare by viewModel.mealToShare.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(mealToShare) {
        val json = mealToShare ?: return@LaunchedEffect
        shareMealFile(context, json)
        viewModel.onMealShared()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = when {
                state.isCopy -> "Refaire le repas"
                state.isNew -> "Nouveau repas"
                else -> "Modifier le repas"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.loading) {
            Text("Chargement…")
            return@Column
        }

        if (state.isNew && !state.isCopy) {
            QuickMeals("Ton repas habituel ?", state.suggestions, viewModel::fillFrom)
            QuickMeals("Mes favoris", state.favorites, viewModel::fillFrom)
            QuickMeals("Récents", state.recents, viewModel::fillFrom)
        }
        MealTypeChips(selected = state.type, onSelect = viewModel::setType)
        DateTimeButtons(dateTime = state.eatenAt, onChange = viewModel::setEatenAt)
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            label = { Text("Nom du repas (facultatif)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.foods.forEachIndexed { index, food ->
            FoodCard(
                position = index + 1,
                food = food,
                onChange = viewModel::updateFood,
                onRemove = { viewModel.removeFood(food.key) },
                saved = savedKeyOf(food.name) in savedKeys,
                onToggleSaved = { viewModel.toggleSaved(food) },
            )
        }
        Button(onClick = { addingFood = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Ajouter un aliment")
        }

        Text(
            text = "Total : ${formatKcal(state.totalKcal)}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        state.errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose, enabled = !state.saving, modifier = Modifier.weight(1f)) {
                Text("Annuler")
            }
            Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.weight(1f)) {
                Text("Enregistrer")
            }
        }
        if (!state.isNew) {
            OutlinedButton(onClick = viewModel::redo, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text("Refaire ce repas aujourd'hui")
            }
            OutlinedButton(onClick = { sharing = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text("Partager ce repas")
            }
            TextButton(onClick = { confirmDelete = true }, enabled = !state.saving) {
                Text("Supprimer ce repas", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (sharing) {
        ShareMealDialog(
            onShare = { portion ->
                sharing = false
                viewModel.share(portion)
            },
            onDismiss = { sharing = false },
        )
    }

    if (addingFood) {
        AlertDialog(
            onDismissRequest = { addingFood = false },
            title = { Text("Ajouter un aliment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf<Pair<String, () -> Unit>>(
                        "Mes aliments" to { pickingSaved = true },
                        "Scanner un code-barres" to { showScan = true },
                        "Chercher un aliment" to { showSearch = true },
                        "Saisie rapide (juste les calories)" to { caloriesOnly = true },
                        "Mes plats" to { pickingRecipe = true },
                        "Un aliment à la main" to { viewModel.addFood() },
                    ).forEach { (label, action) ->
                        TextButton(
                            onClick = {
                                addingFood = false
                                action()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addingFood = false }) { Text("Fermer") } },
        )
    }

    if (pickingSaved) {
        SavedFoodPickerDialog(
            foods = savedFoods.orEmpty(),
            onPicked = { food ->
                viewModel.addFoundFood(food)
                pickingSaved = false
            },
            onManage = {
                pickingSaved = false
                onOpenSavedFoods()
            },
            onDismiss = { pickingSaved = false },
        )
    }

    if (pickingRecipe) {
        RecipePickerDialog(
            recipes = recipes.orEmpty(),
            onPicked = { food ->
                viewModel.addFoundFood(food)
                pickingRecipe = false
            },
            onManage = {
                pickingRecipe = false
                onOpenRecipes()
            },
            onDismiss = { pickingRecipe = false },
        )
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

    if (caloriesOnly) {
        CaloriesOnlyDialog(
            onConfirm = { name, kcal ->
                val error = viewModel.addCaloriesOnly(name, kcal)
                if (error == null) caloriesOnly = false
                error
            },
            onDismiss = { caloriesOnly = false },
        )
    }

    if (showScan) {
        ScanDialog(
            onFoodChosen = { food ->
                viewModel.addFoundFood(food)
                showScan = false
            },
            onDismiss = { showScan = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce repas ?") },
            text = { Text("Il sera retiré de ton journal. Cette action est définitive.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
internal fun FoodCard(
    position: Int,
    food: FoodInput,
    onChange: (FoodInput) -> Unit,
    onRemove: () -> Unit,
    label: String = "Aliment",
    showPortions: Boolean = true,
    showKind: Boolean = true,
    /** Marque-page « Mes aliments » : null = pas de marque-page (ingrédient d'un plat). */
    saved: Boolean? = null,
    onToggleSaved: () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = food.name,
                    onValueChange = { onChange(food.copy(name = it)) },
                    label = { Text("$label n°$position") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (saved != null) SavedFoodBookmark(saved = saved, onClick = onToggleSaved)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Retirer cet aliment")
                }
            }
            // Changer la quantité recalcule les valeurs ; corriger une valeur à la main fixe la nouvelle base.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { onChange(food.stepped(-1)) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Moins ${food.name.ifBlank { "cet aliment" }}")
                }
                NumberField("Quantité (g)", food.quantityG, { onChange(food.withQuantity(it)) }, Modifier.weight(1f))
                FilledTonalIconButton(onClick = { onChange(food.stepped(1)) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Plus ${food.name.ifBlank { "cet aliment" }}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Calories (kcal)", food.kcal, { onChange(food.copy(kcal = it).rebased()) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Protéines", food.proteinG, { onChange(food.copy(proteinG = it).rebased()) }, Modifier.weight(1f))
                NumberField("Glucides", food.carbsG, { onChange(food.copy(carbsG = it).rebased()) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Lipides", food.fatG, { onChange(food.copy(fatG = it).rebased()) }, Modifier.weight(1f))
                NumberField("Fibres", food.fiberG, { onChange(food.copy(fiberG = it).rebased()) }, Modifier.weight(1f))
            }
            // Portions en un geste : moitié, trois quarts, une fois et demie.
            if (showPortions) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Portion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Portion.entries.forEach { portion ->
                    AssistChip(
                        onClick = { onChange(food.scaledBy(portion.factor)) },
                        label = { Text(portion.label) },
                        modifier = Modifier.semantics { contentDescription = portionDescription(portion) },
                    )
                }
            }
            if (showKind) DrinkKindChoice(food, onChange)
            val original = food.original
            if (original?.needsCheck() == true || original?.assumptions != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (original.needsCheck()) ToCheckBadge()
                    original.assumptions?.let { Column(modifier = Modifier.weight(1f)) { AssumptionText(it, maxLines = 4) } }
                }
                if (original.needsCheck()) {
                    Text(
                        "Estimation peu sûre : corrige la quantité ou les valeurs si besoin, la pastille partira à l'enregistrement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (food.reference != null) {
                Text(
                    "Si tu changes la quantité, les calories et le reste sont recalculés.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Aliment / Boisson / Alcool : une boisson avec une quantité compte dans l'eau bue, l'alcool jamais. */
@Composable
private fun DrinkKindChoice(food: FoodInput, onChange: (FoodInput) -> Unit) {
    val choices = listOf<Pair<DrinkKind?, String>>(null to "Aliment", DrinkKind.DRINK to "Boisson", DrinkKind.ALCOHOL to "Alcool")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, (kind, label) ->
                SegmentedButton(
                    selected = food.drink == kind,
                    onClick = { onChange(food.copy(drink = kind)) },
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                    label = { Text(label) },
                )
            }
        }
        val hint = when {
            food.drink == DrinkKind.DRINK && parseNumber(food.quantityG).let { it == null || it <= 0 } ->
                "Ajoute une quantité pour la compter dans l'eau."
            food.drink == DrinkKind.DRINK -> "Compte dans l'eau bue (1 g = 1 mL)."
            food.drink == DrinkKind.ALCOHOL -> "L'alcool ne compte pas dans l'eau."
            else -> null
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Favoris ou récents : un geste remplit le repas avec le même nom et les mêmes aliments. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickMeals(title: String, meals: List<Meal>, onPick: (Meal) -> Unit) {
    if (meals.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            meals.forEach { meal ->
                SuggestionChip(
                    onClick = { onPick(meal) },
                    label = { Text("${quickMealName(meal)} · ${formatKcal(meal.totalKcal)}", maxLines = 1) },
                )
            }
        }
    }
}

/** Nom court d'un repas proposé : son nom, sinon ses deux premiers aliments. */
internal fun quickMealName(meal: Meal): String =
    meal.title?.takeIf { it.isNotBlank() }
        ?: meal.foods.take(2).joinToString(", ") { it.name }.let { if (meal.foods.size > 2) "$it…" else it }
            .ifEmpty { meal.type.label }

private fun portionDescription(portion: Portion) = when (portion) {
    Portion.HALF -> "Diviser la portion par deux"
    Portion.THREE_QUARTERS -> "Garder les trois quarts de la portion"
    Portion.ONE_AND_HALF -> "Multiplier la portion par une fois et demie"
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** « Juste les calories » : un nom (facultatif) et un nombre de calories, sans inventer d'aliments. */
@Composable
internal fun CaloriesOnlyDialog(onConfirm: (name: String, kcal: String) -> String?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Juste les calories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Pour un repas dont tu ne connais pas le détail. Les protéines et les fibres ne seront pas comptées.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom (facultatif)") },
                    placeholder = { Text(CALORIES_ONLY_NAME) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it; error = null },
                    label = { Text("Calories (kcal)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { error = onConfirm(name, kcal) }) { Text("Ajouter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
