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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.bidaboss.healthy.core.importformat.DrinkKind
import com.bidaboss.healthy.core.importformat.FoodSource
import com.bidaboss.healthy.meals.data.FoodHistory
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.SaveFoodResult
import com.bidaboss.healthy.meals.data.SavedFood
import com.bidaboss.healthy.meals.data.SavedFoodDraft
import com.bidaboss.healthy.meals.data.SavedFoodRepository
import com.bidaboss.healthy.ui.EmptyState
import com.bidaboss.healthy.ui.formatGrams
import com.bidaboss.healthy.ui.formatKcal
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.parseNumber
import com.bidaboss.healthy.ui.toInputText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** « Mes aliments » : la liste des favoris (null tant qu'elle charge). */
class SavedFoodsViewModel(private val repository: SavedFoodRepository) : ViewModel() {
    val foods: StateFlow<List<SavedFood>?> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Une ligne de la liste : portion et calories (« 150 g · 210 kcal »). */
internal fun savedFoodSummary(food: SavedFood): String =
    listOfNotNull(food.portionG?.let { formatGrams(it) }, formatKcal(food.kcal)).joinToString(" · ")

/** Choisir un de mes aliments pour le repas : un appui ou « + » l'ajoute avec sa portion par défaut. */
@Composable
internal fun SavedFoodPickerDialog(
    foods: List<SavedFood>,
    onPicked: (Food) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(foods, query) {
        val words = FoodHistory.normalize(query).split(' ').filter { it.isNotEmpty() }
        foods.filter { food -> words.all { it in FoodHistory.normalize(food.name) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mes aliments") },
        text = {
            if (foods.isEmpty()) {
                Text("Pas encore d'aliment. Appuie sur le marque-page d'un aliment d'un repas, ou crée-en un.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Chercher dans mes aliments") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = { it.id }) { food ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onPicked(food.toFood()) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(food.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        savedFoodSummary(food),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onPicked(food.toFood()) }) {
                                    Icon(Icons.Filled.Add, contentDescription = "Ajouter ${food.name} au repas")
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onManage) { Text("Gérer mes aliments") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** Écran « Mes aliments » : la liste, un aliment à corriger ou à supprimer, un nouveau à créer. */
@Composable
fun SavedFoodsScreen(
    onOpenFood: (Long?) -> Unit,
    onOpenScannedProducts: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedFoodsViewModel = viewModel(factory = healthyViewModelFactory { SavedFoodsViewModel(it.savedFoodRepository) }),
) {
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Mes aliments", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Les aliments que tu manges souvent, prêts à ajouter à un repas. Le marque-page d'un aliment l'ajoute ici.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(onClick = { onOpenFood(null) }, modifier = Modifier.fillMaxWidth()) { Text("Créer un aliment") }
        }
        val list = foods
        if (list != null && list.isEmpty()) {
            item { EmptyState("Pas encore d'aliment enregistré.") }
        }
        items(list.orEmpty(), key = { it.id }) { food ->
            Card(onClick = { onOpenFood(food.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(food.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        savedFoodSummary(food),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = onOpenScannedProducts, modifier = Modifier.fillMaxWidth()) { Text("Mes produits scannés") }
        }
        item { TextButton(onClick = onClose) { Text("Retour") } }
    }
}

data class SavedFoodEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val portionG: String = "",
    val kcal: String = "",
    val proteinG: String = "",
    val carbsG: String = "",
    val fatG: String = "",
    val fiberG: String = "",
    val drink: DrinkKind? = null,
    val errors: List<String> = emptyList(),
    val saving: Boolean = false,
    val finished: Boolean = false,
)

/** « Créer un aliment » (id nul) ou corriger un aliment de « Mes aliments ». */
class SavedFoodEditorViewModel(
    private val repository: SavedFoodRepository,
    private val foodId: Long?,
) : ViewModel() {
    private val _state = MutableStateFlow(SavedFoodEditorState(loading = foodId != null))
    val state: StateFlow<SavedFoodEditorState> = _state.asStateFlow()

    /** Source gardée telle quelle à la correction (un aliment venu d'Open Food Facts le reste). */
    private var source: FoodSource = FoodSource.MANUAL

    init {
        if (foodId != null) {
            viewModelScope.launch {
                val food = repository.get(foodId)
                if (food == null) {
                    _state.update { it.copy(loading = false, finished = true) }
                } else {
                    source = food.source
                    _state.value = SavedFoodEditorState(
                        loading = false,
                        isNew = false,
                        name = food.name,
                        portionG = food.portionG.toInputText(),
                        kcal = food.kcal.toInputText(),
                        proteinG = food.proteinG.toInputText(),
                        carbsG = food.carbsG.toInputText(),
                        fatG = food.fatG.toInputText(),
                        fiberG = food.fiberG.toInputText(),
                        drink = food.drink,
                    )
                }
            }
        }
    }

    fun update(change: (SavedFoodEditorState) -> SavedFoodEditorState) = _state.update(change)

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        val errors = mutableListOf<String>()
        if (s.name.isBlank()) errors += "Indique un nom."
        fun number(text: String, field: String, required: Boolean = false): Double? {
            if (text.isBlank()) {
                if (required) errors += "Indique $field."
                return null
            }
            val value = parseNumber(text)
            if (value == null || !value.isFinite() || value < 0 || value > MAX_FOOD_VALUE) {
                errors += "« $field » doit être un nombre entre 0 et $MAX_FOOD_VALUE."
                return null
            }
            return value
        }
        val kcal = number(s.kcal, "les calories", required = true)
        val portion = number(s.portionG, "la portion")
        val protein = number(s.proteinG, "les protéines")
        val carbs = number(s.carbsG, "les glucides")
        val fat = number(s.fatG, "les lipides")
        val fiber = number(s.fiberG, "les fibres")
        if (errors.isNotEmpty() || kcal == null) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true, errors = emptyList()) }
        viewModelScope.launch {
            val result = repository.save(
                SavedFoodDraft(foodId, s.name, portion, kcal, protein, carbs, fat, fiber, source, s.drink),
            )
            _state.update {
                when (result) {
                    is SaveFoodResult.Saved -> it.copy(saving = false, finished = true)
                    SaveFoodResult.NameTaken -> it.copy(saving = false, errors = listOf("Un aliment porte déjà ce nom."))
                }
            }
        }
    }

    fun delete() {
        val id = foodId ?: return
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }
}

@Composable
fun SavedFoodEditorScreen(
    foodId: Long?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedFoodEditorViewModel = viewModel(
        factory = healthyViewModelFactory { SavedFoodEditorViewModel(it.savedFoodRepository, foodId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.finished) { if (state.finished) onClose() }

    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (state.isNew) "Créer un aliment" else "Modifier l'aliment", style = MaterialTheme.typography.headlineMedium)
        if (state.loading) {
            Text("Chargement…")
            return@Column
        }
        Text(
            "Les valeurs sont celles de la portion indiquée. Elles seront reprises telles quelles quand tu l'ajoutes à un repas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = { v -> viewModel.update { it.copy(name = v) } },
            label = { Text("Nom") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodNumberField("Portion (g)", state.portionG, { v -> viewModel.update { it.copy(portionG = v) } }, Modifier.weight(1f))
            FoodNumberField("Calories (kcal)", state.kcal, { v -> viewModel.update { it.copy(kcal = v) } }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodNumberField("Protéines", state.proteinG, { v -> viewModel.update { it.copy(proteinG = v) } }, Modifier.weight(1f))
            FoodNumberField("Glucides", state.carbsG, { v -> viewModel.update { it.copy(carbsG = v) } }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodNumberField("Lipides", state.fatG, { v -> viewModel.update { it.copy(fatG = v) } }, Modifier.weight(1f))
            FoodNumberField("Fibres", state.fiberG, { v -> viewModel.update { it.copy(fiberG = v) } }, Modifier.weight(1f))
        }
        val choices = listOf<Pair<DrinkKind?, String>>(null to "Aliment", DrinkKind.DRINK to "Boisson", DrinkKind.ALCOHOL to "Alcool")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, (kind, label) ->
                SegmentedButton(
                    selected = state.drink == kind,
                    onClick = { viewModel.update { it.copy(drink = kind) } },
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                    label = { Text(label) },
                )
            }
        }
        state.errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose, enabled = !state.saving, modifier = Modifier.weight(1f)) { Text("Annuler") }
            Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.weight(1f)) { Text("Enregistrer") }
        }
        if (!state.isNew) {
            TextButton(onClick = { confirmDelete = true }, enabled = !state.saving) {
                Text("Supprimer cet aliment", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cet aliment ?") },
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

@Composable
private fun FoodNumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
