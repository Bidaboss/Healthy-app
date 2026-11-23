package com.bidaboss.healthy.meals.ui

import com.bidaboss.healthy.ui.parseNumber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.importformat.FoodSource
import com.bidaboss.healthy.core.openfoodfacts.OffProduct
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.FoodHistory
import com.bidaboss.healthy.meals.data.HistoryFood
import com.bidaboss.healthy.meals.data.FoodSearchFailure
import com.bidaboss.healthy.meals.data.FoodSearchRepository
import com.bidaboss.healthy.meals.data.FoodSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodSearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    /** null tant qu'aucune recherche n'a été faite. */
    val results: List<OffProduct>? = null,
    val message: String? = null,
    /** Précision technique sous le message d'erreur (ex. « réponse 503 »), pour comprendre une panne. */
    val detail: String? = null,
    /** Produit choisi, en attente de la quantité. */
    val selected: OffProduct? = null,
    val quantityG: String = "",
)

/** « Déjà mangés » : les aliments déjà notés qui correspondent à ce qui est tapé. */
data class HistoryUiState(
    /** Faux sur un téléphone sans historique : la recherche garde alors son aspect habituel. */
    val hasHistory: Boolean = false,
    val items: List<HistoryFood> = emptyList(),
)

class FoodSearchViewModel(
    private val repository: FoodSearchRepository,
    /** Aliments déjà mangés (fournis par AppContainer, déduits des repas : aucun réseau). */
    history: Flow<List<HistoryFood>> = kotlinx.coroutines.flow.flowOf(emptyList()),
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodSearchUiState())
    val uiState: StateFlow<FoodSearchUiState> = _uiState.asStateFlow()

    private val allHistory = history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** La liste se filtre à chaque lettre tapée, sans attendre ni réseau. */
    val historyState: StateFlow<HistoryUiState> = combine(allHistory, _uiState.map { it.query }) { all, query ->
        HistoryUiState(hasHistory = all.isNotEmpty(), items = FoodHistory.filter(all, query).take(MAX_HISTORY_SHOWN))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private var searchJob: Job? = null

    /** Repart d'une recherche vide à chaque ouverture. */
    fun reset() {
        searchJob?.cancel()
        _uiState.value = FoodSearchUiState()
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.length < 2) {
            _uiState.update { it.copy(message = "Écris au moins 2 lettres.", detail = null) }
            return
        }
        searchJob?.cancel()
        _uiState.update { it.copy(searching = true, message = null, detail = null, selected = null) }
        searchJob = viewModelScope.launch {
            val result = repository.search(query)
            _uiState.update {
                when (result) {
                    is FoodSearchResult.Success -> it.copy(
                        searching = false,
                        results = result.products,
                        message = if (result.products.isEmpty()) "Aucun aliment trouvé. Essaie un autre mot." else null,
                    )
                    is FoodSearchResult.Failure -> it.copy(
                        searching = false,
                        message = result.reason.userMessage(),
                        detail = result.reason.technicalDetail(),
                    )
                }
            }
        }
    }

    fun select(product: OffProduct) = _uiState.update {
        it.copy(selected = product, quantityG = formatQuantity(product.servingG ?: 100.0), message = null, detail = null)
    }

    fun clearSelection() = _uiState.update { it.copy(selected = null) }

    fun setQuantity(quantityG: String) = _uiState.update { it.copy(quantityG = quantityG) }

    /** L'aliment choisi avec ses valeurs calculées pour la quantité saisie, ou null si elle est invalide. */
    fun buildFood(): Food? {
        val state = _uiState.value
        val product = state.selected ?: return null
        val quantity = parseNumber(state.quantityG)?.takeIf { it > 0 && it <= MAX_QUANTITY_G }
        if (quantity == null) {
            _uiState.update { it.copy(message = "Indique une quantité en grammes, entre 1 et $MAX_QUANTITY_G.") }
            return null
        }
        val nutrients = product.forQuantity(quantity)
        return Food(
            name = listOfNotNull(product.name, product.brand).joinToString(" - "),
            quantityG = quantity,
            kcal = nutrients.kcal,
            proteinG = nutrients.proteinG,
            carbsG = nutrients.carbsG,
            fatG = nutrients.fatG,
            fiberG = nutrients.fiberG,
            source = FoodSource.OPEN_FOOD_FACTS,
            offCode = product.code,
        )
    }

    private fun formatQuantity(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString().replace('.', ',')

    private companion object {
        const val MAX_QUANTITY_G = 5000
        const val MAX_HISTORY_SHOWN = 30
    }
}

/** Message simple : on ne parle de la connexion du téléphone que quand c'est vraiment elle qui manque. */
internal fun FoodSearchFailure.userMessage(): String = when (this) {
    FoodSearchFailure.NoConnection -> "Recherche impossible. Vérifie ta connexion internet, puis réessaie."
    FoodSearchFailure.Timeout ->
        "Open Food Facts met trop de temps à répondre. Réessaie dans un instant, ou ajoute l'aliment à la main."
    is FoodSearchFailure.Http, FoodSearchFailure.Unreadable, is FoodSearchFailure.Other ->
        "Open Food Facts ne répond pas correctement pour le moment. Réessaie dans un instant, " +
            "ou ajoute l'aliment à la main."
}

internal fun FoodSearchFailure.technicalDetail(): String = when (this) {
    FoodSearchFailure.NoConnection -> "Détail : serveur injoignable"
    FoodSearchFailure.Timeout -> "Détail : délai dépassé"
    is FoodSearchFailure.Http -> "Détail : réponse $code"
    FoodSearchFailure.Unreadable -> "Détail : réponse illisible"
    is FoodSearchFailure.Other -> "Détail : $kind"
}
