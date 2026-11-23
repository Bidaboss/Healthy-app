package com.bidaboss.healthy.meals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.RecipeDraft
import com.bidaboss.healthy.meals.data.RecipeRepository
import com.bidaboss.healthy.ui.parseNumber
import com.bidaboss.healthy.ui.toInputText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val cookedWeightG: String = "",
    val servings: String = "",
    val ingredients: List<FoodInput> = emptyList(),
    val errors: List<String> = emptyList(),
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val totalKcal: Double get() = ingredients.sumOf { parseNumber(it.kcal) ?: 0.0 }

    /** Somme des quantités d'ingrédients : poids cru proposé si le poids cuit n'est pas pesé. */
    val rawWeightG: Double get() = ingredients.sumOf { parseNumber(it.quantityG) ?: 0.0 }
}

class RecipeEditorViewModel(
    private val recipes: RecipeRepository,
    private val recipeId: Long?,
) : ViewModel() {

    private var nextKey = 1L
    private val _uiState = MutableStateFlow(RecipeEditorUiState())
    val uiState: StateFlow<RecipeEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val recipe = recipeId?.let { recipes.getRecipe(it) }
            _uiState.value = if (recipe == null) {
                RecipeEditorUiState(loading = false, ingredients = listOf(FoodInput(key = nextKey++)))
            } else {
                RecipeEditorUiState(
                    loading = false,
                    isNew = false,
                    name = recipe.name,
                    cookedWeightG = recipe.cookedWeightG.toInputText(),
                    servings = recipe.servings?.toString().orEmpty(),
                    ingredients = recipe.ingredients.map { it.toInput(nextKey++) },
                )
            }
        }
    }

    fun setName(text: String) = _uiState.update { it.copy(name = text) }
    fun setCookedWeight(text: String) = _uiState.update { it.copy(cookedWeightG = text) }
    fun setServings(text: String) = _uiState.update { it.copy(servings = text) }
    fun addIngredient() = _uiState.update { it.copy(ingredients = it.ingredients + FoodInput(key = nextKey++)) }
    fun addFoundFood(food: Food) = _uiState.update { s -> s.copy(ingredients = s.ingredients.filterNot { it.isBlank() } + food.toInput(nextKey++)) }
    fun removeIngredient(key: Long) = _uiState.update { s -> s.copy(ingredients = s.ingredients.filterNot { it.key == key }) }
    fun updateIngredient(food: FoodInput) = _uiState.update { s -> s.copy(ingredients = s.ingredients.map { if (it.key == food.key) food else it }) }

    fun save() {
        val state = _uiState.value
        if (state.saving || state.loading) return
        val errors = mutableListOf<String>()
        val name = state.name.trim()
        if (name.isEmpty()) errors += "Donne un nom au plat."
        val weight = parseNumber(state.cookedWeightG)
        if (weight == null || weight <= 0 || weight > MAX_COOKED_WEIGHT_G) {
            errors += "Indique le poids total du plat cuit (en grammes)."
        }
        val servings = state.servings.trim().takeIf { it.isNotEmpty() }?.let { text ->
            text.toIntOrNull()?.takeIf { it in 1..MAX_SERVINGS } ?: run {
                errors += "Le nombre de parts doit être un entier entre 1 et $MAX_SERVINGS."
                null
            }
        }
        val inputs = state.ingredients.filterNot { it.isBlank() }
        if (inputs.isEmpty()) errors += "Ajoute au moins un ingrédient."
        val foods = inputs.mapIndexedNotNull { i, input -> input.toFood(i + 1, errors) }
        if (errors.isNotEmpty() || weight == null) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        _uiState.update { it.copy(saving = true, errors = emptyList()) }
        viewModelScope.launch {
            try {
                recipes.save(RecipeDraft(recipeId, name, weight, servings, foods))
                _uiState.update { it.copy(saving = false, finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, errors = listOf("L'enregistrement a échoué. Réessaie.")) }
            }
        }
    }

    fun delete() {
        val id = recipeId ?: return
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            recipes.delete(id)
            _uiState.update { it.copy(saving = false, finished = true) }
        }
    }

    private companion object {
        const val MAX_COOKED_WEIGHT_G = 20_000
        const val MAX_SERVINGS = 50
    }
}
