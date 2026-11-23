package com.bidaboss.healthy.meals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.importformat.FoodSource
import com.bidaboss.healthy.core.openfoodfacts.OffProduct
import com.bidaboss.healthy.core.openfoodfacts.OffProductDraft
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.FoodSearchFailure
import com.bidaboss.healthy.meals.data.ScanResult
import com.bidaboss.healthy.meals.data.ScannedProduct
import com.bidaboss.healthy.meals.data.ScannedProductRepository
import com.bidaboss.healthy.ui.parseNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanStep {
    /** La caméra cherche un code. */
    SCANNING,

    /** Code lu, on cherche le produit. */
    LOADING,

    /** Produit trouvé : quantité et « Ajouter ». */
    PRODUCT,

    /** Open Food Facts ne connaît pas le produit (ou sans calories) : proposer de saisir l'étiquette. */
    UNKNOWN,

    /** Open Food Facts n'a pas pu répondre. */
    FAILED,

    /** Formulaire de l'étiquette. */
    LABEL,
}

/** Champs du formulaire « Saisir l'étiquette », en texte comme tapés. */
data class LabelForm(
    val name: String = "",
    val brand: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val serving: String = "",
)

data class ScanUiState(
    val step: ScanStep = ScanStep.SCANNING,
    val code: String? = null,
    val product: ScannedProduct? = null,
    /** Le produit était déjà sur le téléphone. */
    val remembered: Boolean = false,
    val quantityG: String = "",
    val failure: FoodSearchFailure? = null,
    val label: LabelForm = LabelForm(),
    val message: String? = null,
)

/** Scan d'un code-barres : lecture, produit (téléphone puis Open Food Facts), quantité, saisie de l'étiquette. */
class ScanViewModel(private val repository: ScannedProductRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Repart d'un scan vide à chaque ouverture. */
    fun reset() {
        _uiState.value = ScanUiState()
    }

    /** Appelé par la caméra à chaque lecture : un code n'est pris qu'une fois, tant qu'on n'est pas revenu à la visée. */
    fun onCodeRead(rawCode: String) {
        if (_uiState.value.step != ScanStep.SCANNING) return
        _uiState.update { it.copy(step = ScanStep.LOADING, message = null) }
        viewModelScope.launch {
            when (val result = repository.scan(rawCode)) {
                is ScanResult.Known -> _uiState.update {
                    it.copy(
                        step = ScanStep.PRODUCT,
                        code = result.product.product.code,
                        product = result.product,
                        remembered = result.remembered,
                        quantityG = formatQuantity(result.product.product.servingG ?: DEFAULT_QUANTITY_G),
                    )
                }
                is ScanResult.Unknown -> _uiState.update {
                    it.copy(step = ScanStep.UNKNOWN, code = result.code, label = result.draft.toForm(), failure = null)
                }
                is ScanResult.Failed -> _uiState.update {
                    it.copy(step = ScanStep.FAILED, code = result.code, failure = result.reason, label = LabelForm())
                }
                ScanResult.InvalidCode -> _uiState.update {
                    it.copy(step = ScanStep.SCANNING, message = "Ce code n'a pas pu être lu. Réessaie.")
                }
            }
        }
    }

    /** Retour à la visée (autre produit, ou réessayer). */
    fun scanAgain() = _uiState.update { ScanUiState() }

    /** « Réessayer » après une panne : on refait la recherche avec le même code. */
    fun retry() {
        val code = _uiState.value.code ?: return scanAgain()
        _uiState.update { it.copy(step = ScanStep.SCANNING) }
        onCodeRead(code)
    }

    fun openLabel() = _uiState.update { it.copy(step = ScanStep.LABEL, message = null) }

    fun setLabel(label: LabelForm) = _uiState.update { it.copy(label = label, message = null) }

    fun setQuantity(quantityG: String) = _uiState.update { it.copy(quantityG = quantityG, message = null) }

    /** Valide le formulaire, retient le produit sous son code et passe à la quantité. */
    fun saveLabel() {
        val code = _uiState.value.code ?: return
        val form = _uiState.value.label
        val name = form.name.trim()
        val kcal = parseNumber(form.kcal)?.takeIf { it in 0.0..MAX_KCAL_PER_100G }
        if (name.isEmpty()) return fail("Indique le nom du produit.")
        if (kcal == null) return fail("Indique les calories pour 100 g (un nombre).")
        fun optional(text: String): Double? = parseNumber(text)?.takeIf { it in 0.0..MAX_GRAMS_PER_100G }
        // Une case remplie mais illisible est une erreur, une case vide est simplement « pas indiquée ».
        listOf(form.protein, form.carbs, form.fat, form.fiber).forEach {
            if (it.isNotBlank() && optional(it) == null) return fail("Les protéines, glucides, lipides et fibres sont des nombres entre 0 et 100.")
        }
        val serving = parseNumber(form.serving)?.takeIf { it > 0 && it <= MAX_QUANTITY_G }
        if (form.serving.isNotBlank() && serving == null) return fail("La portion est un nombre de grammes.")
        val product = OffProduct(
            code = code,
            name = name,
            brand = form.brand.trim().ifEmpty { null },
            kcalPer100g = kcal,
            proteinPer100g = optional(form.protein),
            carbsPer100g = optional(form.carbs),
            fatPer100g = optional(form.fat),
            fiberPer100g = optional(form.fiber),
            servingG = serving,
        )
        viewModelScope.launch {
            val saved = repository.saveFromLabel(code, product)
            _uiState.update {
                it.copy(
                    step = ScanStep.PRODUCT,
                    product = saved,
                    remembered = false,
                    quantityG = formatQuantity(serving ?: DEFAULT_QUANTITY_G),
                    message = null,
                )
            }
        }
    }

    /** L'aliment à ajouter au repas, avec ses valeurs calculées pour la quantité saisie ; null si elle est invalide. */
    fun buildFood(): Food? {
        val state = _uiState.value
        val scanned = state.product ?: return null
        val quantity = parseNumber(state.quantityG)?.takeIf { it > 0 && it <= MAX_QUANTITY_G }
        if (quantity == null) {
            fail("Indique une quantité en grammes, entre 1 et $MAX_QUANTITY_G.")
            return null
        }
        val product = scanned.product
        val nutrients = product.forQuantity(quantity)
        return Food(
            name = listOfNotNull(product.name, product.brand).joinToString(" - "),
            quantityG = quantity,
            kcal = nutrients.kcal,
            proteinG = nutrients.proteinG,
            carbsG = nutrients.carbsG,
            fatG = nutrients.fatG,
            fiberG = nutrients.fiberG,
            source = if (scanned.fromOpenFoodFacts) FoodSource.OPEN_FOOD_FACTS else FoodSource.MANUAL,
            offCode = product.code.takeIf { scanned.fromOpenFoodFacts },
        )
    }

    private fun fail(message: String) = _uiState.update { it.copy(message = message) }

    private fun OffProductDraft?.toForm(): LabelForm {
        if (this == null) return LabelForm()
        fun text(value: Double?) = value?.let { formatQuantity(it) }.orEmpty()
        return LabelForm(
            name = name.orEmpty(),
            brand = brand.orEmpty(),
            kcal = text(kcalPer100g),
            protein = text(proteinPer100g),
            carbs = text(carbsPer100g),
            fat = text(fatPer100g),
            fiber = text(fiberPer100g),
            serving = text(servingG),
        )
    }

    private fun formatQuantity(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString().replace('.', ',')

    private companion object {
        const val DEFAULT_QUANTITY_G = 100.0
        const val MAX_QUANTITY_G = 5000
        const val MAX_KCAL_PER_100G = 1000.0
        const val MAX_GRAMS_PER_100G = 100.0
    }
}

/** Panne du scan : « Pas de connexion » est dit simplement, le reste reprend les messages de la recherche. */
internal fun FoodSearchFailure.scanMessage(): String = when (this) {
    FoodSearchFailure.NoConnection -> "Pas de connexion. Tu peux saisir l'étiquette ou réessayer plus tard."
    else -> userMessage()
}
