package com.bidaboss.healthy.meals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.openfoodfacts.OffProduct
import com.bidaboss.healthy.meals.data.RefreshResult
import com.bidaboss.healthy.meals.data.ScannedProduct
import com.bidaboss.healthy.meals.data.ScannedProductRepository
import com.bidaboss.healthy.ui.parseNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Produit en cours de correction : le code reste le même, les champs sont du texte tapé. */
data class ProductEdit(val code: String, val fromOpenFoodFacts: Boolean, val form: LabelForm, val message: String? = null)

data class ScannedProductsUiState(
    /** null tant que la liste se charge. */
    val products: List<ScannedProduct>? = null,
    val editing: ProductEdit? = null,
    /** Produit dont on demande la suppression. */
    val deleting: ScannedProduct? = null,
    val refreshingCode: String? = null,
    val message: String? = null,
)

/** « Mes produits scannés » : voir, corriger, supprimer, mettre à jour depuis Open Food Facts. */
class ScannedProductsViewModel(private val repository: ScannedProductRepository) : ViewModel() {

    private val local = MutableStateFlow(ScannedProductsUiState())

    private val products = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<ScannedProductsUiState> = kotlinx.coroutines.flow.combine(local, products) { state, list ->
        state.copy(products = list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScannedProductsUiState())

    fun edit(scanned: ScannedProduct) {
        val p = scanned.product
        fun text(value: Double?) = value?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString().replace('.', ',') }.orEmpty()
        local.update {
            it.copy(
                editing = ProductEdit(
                    p.code,
                    scanned.fromOpenFoodFacts,
                    LabelForm(
                        name = p.name, brand = p.brand.orEmpty(), kcal = text(p.kcalPer100g), protein = text(p.proteinPer100g),
                        carbs = text(p.carbsPer100g), fat = text(p.fatPer100g), fiber = text(p.fiberPer100g), serving = text(p.servingG),
                    ),
                ),
                message = null,
            )
        }
    }

    fun setForm(form: LabelForm) = local.update { s -> s.copy(editing = s.editing?.copy(form = form, message = null)) }

    fun cancelEdit() = local.update { it.copy(editing = null) }

    /** Valide et enregistre la correction. Les repas déjà notés gardent leurs valeurs. */
    fun saveEdit() {
        val edit = local.value.editing ?: return
        val form = edit.form
        val name = form.name.trim()
        val kcal = parseNumber(form.kcal)?.takeIf { it in 0.0..MAX_KCAL_PER_100G }
        fun optional(text: String): Double? = parseNumber(text)?.takeIf { it in 0.0..MAX_GRAMS_PER_100G }
        val message = when {
            name.isEmpty() -> "Indique le nom du produit."
            kcal == null -> "Indique les calories pour 100 g (un nombre)."
            listOf(form.protein, form.carbs, form.fat, form.fiber).any { it.isNotBlank() && optional(it) == null } ->
                "Les protéines, glucides, lipides et fibres sont des nombres entre 0 et 100."
            form.serving.isNotBlank() && parseNumber(form.serving)?.takeIf { it > 0 && it <= MAX_SERVING_G } == null ->
                "La portion est un nombre de grammes."
            else -> null
        }
        if (message != null || kcal == null) {
            local.update { s -> s.copy(editing = s.editing?.copy(message = message)) }
            return
        }
        val product = OffProduct(
            code = edit.code,
            name = name,
            brand = form.brand.trim().ifEmpty { null },
            kcalPer100g = kcal,
            proteinPer100g = optional(form.protein),
            carbsPer100g = optional(form.carbs),
            fatPer100g = optional(form.fat),
            fiberPer100g = optional(form.fiber),
            servingG = parseNumber(form.serving)?.takeIf { it > 0 },
        )
        viewModelScope.launch {
            repository.update(product)
            local.update { it.copy(editing = null, message = "Produit corrigé. Les repas déjà notés ne changent pas.") }
        }
    }

    fun askDelete(product: ScannedProduct) = local.update { it.copy(deleting = product) }

    fun cancelDelete() = local.update { it.copy(deleting = null) }

    fun confirmDelete() {
        val product = local.value.deleting ?: return
        local.update { it.copy(deleting = null) }
        viewModelScope.launch {
            repository.delete(product.product.code)
            local.update { it.copy(message = "Produit supprimé. Les repas déjà notés ne changent pas.") }
        }
    }

    /** « Mettre à jour depuis Open Food Facts » : une requête, seulement quand on appuie. */
    fun refresh(product: ScannedProduct) {
        local.update { it.copy(refreshingCode = product.product.code, message = null) }
        viewModelScope.launch {
            val message = when (val result = repository.refresh(product.product.code)) {
                RefreshResult.Updated -> "Valeurs mises à jour depuis Open Food Facts."
                RefreshResult.Unchanged -> "Open Food Facts n'a pas de valeurs plus complètes : le produit reste tel quel."
                is RefreshResult.Failed -> result.reason.scanMessage()
            }
            local.update { it.copy(refreshingCode = null, message = message) }
        }
    }

    fun clearMessage() = local.update { it.copy(message = null) }

    private companion object {
        const val MAX_KCAL_PER_100G = 1000.0
        const val MAX_GRAMS_PER_100G = 100.0
        const val MAX_SERVING_G = 5000
    }
}
