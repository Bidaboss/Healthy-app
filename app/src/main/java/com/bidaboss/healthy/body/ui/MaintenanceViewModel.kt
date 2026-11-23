package com.bidaboss.healthy.body.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.body.data.MaintenanceInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** null tant que ça charge ou sans profil. */
data class MaintenanceUiState(val info: MaintenanceInfo? = null)

/** Maintien réel : la carte de Moi et la section « Mon maintien » du profil. */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModel(
    observe: (LocalDate) -> kotlinx.coroutines.flow.Flow<MaintenanceInfo?>,
    private val body: BodyRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val date = MutableStateFlow(today())

    val uiState: StateFlow<MaintenanceUiState> = date
        .flatMapLatest { day -> kotlinx.coroutines.flow.flow { observe(day).collect { emit(MaintenanceUiState(it)) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MaintenanceUiState())

    /** À chaque retour sur l'écran : si minuit est passé, on recalcule pour le nouveau jour. */
    fun onResume() {
        date.value = today()
    }

    /** « Adopter » : enregistre l'écart entre l'estimation et la formule. */
    fun adopt() {
        val proposal = uiState.value.info?.proposal ?: return
        viewModelScope.launch { body.adoptAdjustment(proposal.adjustmentKcal, today()) }
    }

    /** « Garder la formule » : ferme la proposition pour 21 jours. */
    fun keepFormula() {
        viewModelScope.launch { body.declineProposal(today()) }
    }

    /** « Revenir à la formule » : retire l'ajustement. */
    fun revertToFormula() {
        viewModelScope.launch { body.clearAdjustment(today()) }
    }

    /** « Pas maintenant » : masque la carte d'Aujourd'hui 14 jours. */
    fun later() {
        viewModelScope.launch { body.snoozeProposal(today()) }
    }
}
