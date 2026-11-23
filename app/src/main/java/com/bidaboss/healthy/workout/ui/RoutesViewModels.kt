package com.bidaboss.healthy.workout.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.route.RoutePlanning
import com.bidaboss.healthy.workout.data.route.DistanceResult
import com.bidaboss.healthy.workout.data.route.RouteFailure
import com.bidaboss.healthy.workout.data.route.SavedRoute
import com.bidaboss.healthy.workout.data.route.SavedRouteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Écran « Mes trajets » : la liste et la suppression. */
class RoutesViewModel(private val repository: SavedRouteRepository) : ViewModel() {
    val routes: StateFlow<List<SavedRoute>?> = MutableStateFlow<List<SavedRoute>?>(null).also { state ->
        viewModelScope.launch { repository.observeAll().collect { state.value = it } }
    }.asStateFlow()

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}

data class RouteEditorState(
    val loading: Boolean = true,
    val name: String = "",
    val start: String = "",
    val stops: List<String> = emptyList(),
    val end: String = "",
    /** Distance d'un sens, calculée ou saisie ; null tant qu'il n'y en a pas. */
    val distanceKm: Double? = null,
    val distanceCalculated: Boolean = false,
    /** Texte de la distance saisie à la main. */
    val manualText: String = "",
    val calculating: Boolean = false,
    val failure: RouteFailure? = null,
    val saved: Boolean = false,
) {
    val canCalculate: Boolean get() = RoutePlanning.clean(start, stops, end) != null && !calculating
    val canSave: Boolean get() = name.isNotBlank() && RoutePlanning.clean(start, stops, end) != null && !calculating
}

/**
 * Création et modification d'un trajet. Le calcul de distance ne part que sur l'appui du bouton :
 * changer une adresse efface la distance calculée mais n'envoie jamais rien.
 */
class RouteEditorViewModel(
    private val repository: SavedRouteRepository,
    private val routeId: Long?,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _state = MutableStateFlow(RouteEditorState(loading = routeId != null))
    val state: StateFlow<RouteEditorState> = _state.asStateFlow()
    private var calculation: Job? = null

    init {
        if (routeId != null) {
            viewModelScope.launch {
                val route = repository.get(routeId)
                _state.value = if (route == null) RouteEditorState(loading = false) else RouteEditorState(
                    loading = false,
                    name = route.name,
                    start = route.addresses.start,
                    stops = route.addresses.stops,
                    end = route.addresses.end,
                    distanceKm = route.distanceKm,
                    distanceCalculated = route.distanceCalculated,
                    manualText = if (route.distanceCalculated) "" else route.distanceKm?.let { plain(it) }.orEmpty(),
                )
            }
        }
    }

    fun onName(text: String) = _state.update { it.copy(name = text.take(SavedRouteRepository.MAX_NAME_LENGTH)) }

    fun onStart(text: String) = changeAddresses { it.copy(start = text.take(RoutePlanning.MAX_ADDRESS_LENGTH)) }

    fun onEnd(text: String) = changeAddresses { it.copy(end = text.take(RoutePlanning.MAX_ADDRESS_LENGTH)) }

    fun onStop(index: Int, text: String) = changeAddresses { s ->
        s.copy(stops = s.stops.mapIndexed { i, old -> if (i == index) text.take(RoutePlanning.MAX_ADDRESS_LENGTH) else old })
    }

    fun addStop() = changeAddresses { s ->
        if (s.stops.size >= RoutePlanning.MAX_STOPS) s else s.copy(stops = s.stops + "")
    }

    fun removeStop(index: Int) = changeAddresses { s -> s.copy(stops = s.stops.filterIndexed { i, _ -> i != index }) }

    /** Une adresse a changé : la distance calculée ne vaut plus, la saisie à la main reste. */
    private fun changeAddresses(change: (RouteEditorState) -> RouteEditorState) = _state.update { old ->
        val next = change(old)
        if (next == old) old
        else if (old.distanceCalculated) next.copy(distanceKm = null, distanceCalculated = false, failure = null)
        else next.copy(failure = null)
    }

    fun onManualDistance(text: String) = _state.update { s ->
        val cleaned = text.filter { it.isDigit() || it == ',' || it == '.' }.take(6)
        val km = RoutePlanning.parseManualKm(cleaned)
        when {
            km != null -> s.copy(manualText = cleaned, distanceKm = km, distanceCalculated = false, failure = null)
            // Une saisie vide ou invalide efface une distance saisie, pas une distance calculée.
            s.distanceCalculated -> s.copy(manualText = cleaned)
            else -> s.copy(manualText = cleaned, distanceKm = null)
        }
    }

    /** Appui sur « Calculer la distance » : la seule fois où des adresses sortent du téléphone. */
    fun calculate() {
        val addresses = RoutePlanning.clean(_state.value.start, _state.value.stops, _state.value.end) ?: return
        if (_state.value.calculating) return
        _state.update { it.copy(calculating = true, failure = null) }
        calculation = viewModelScope.launch {
            when (val result = repository.calculateDistance(addresses)) {
                is DistanceResult.Success -> _state.update {
                    it.copy(calculating = false, distanceKm = result.km, distanceCalculated = true, manualText = "")
                }
                is DistanceResult.Failure -> _state.update { it.copy(calculating = false, failure = result.reason) }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val id = repository.save(
                routeId, s.name, s.start, s.stops, s.end,
                calculatedKm = s.distanceKm.takeIf { s.distanceCalculated },
                manualKm = s.distanceKm.takeIf { !s.distanceCalculated },
                nowEpochMs = now(),
            )
            if (id != null) _state.update { it.copy(saved = true) }
        }
    }

    private fun plain(km: Double) = km.toString().removeSuffix(".0")
}
