package com.bidaboss.healthy.today.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.reference.ReferenceReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ReferencesUiState(
    val periodDays: Int = ReferencesViewModel.DEFAULT_DAYS,
    /** null tant que le calcul n'est pas arrivé. */
    val report: ReferenceReport? = null,
)

/** Écran Repères et carte d'Aujourd'hui : 28 jours par défaut, 7 jours au choix. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReferencesViewModel(
    private val observe: (LocalDate, Int) -> Flow<ReferenceReport>,
    private val today: () -> LocalDate = { LocalDate.now() },
    initialDays: Int = DEFAULT_DAYS,
) : ViewModel() {

    private val period = MutableStateFlow(initialDays)

    val uiState: StateFlow<ReferencesUiState> = period
        .flatMapLatest { days -> observe(today(), days).let { flow -> combine(flow, MutableStateFlow(days)) { r, d -> ReferencesUiState(d, r) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReferencesUiState(initialDays))

    fun setPeriod(days: Int) {
        if (days in PERIODS) period.value = days
    }

    companion object {
        const val DEFAULT_DAYS = 28
        val PERIODS = listOf(7, 28)
    }
}
