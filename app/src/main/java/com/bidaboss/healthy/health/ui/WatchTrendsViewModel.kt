package com.bidaboss.healthy.health.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.watch.SessionSummary
import com.bidaboss.healthy.health.data.WatchDayRecord
import com.bidaboss.healthy.health.data.WatchRepository
import com.bidaboss.healthy.health.data.WatchStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Période affichée : 7 jours (par défaut) ou 30 jours. */
enum class TrendRange(val days: Int, val label: String) {
    WEEK(7, "7 jours"),
    MONTH(30, "30 jours"),
}

data class WatchTrendsUiState(
    /** null tant que la connexion à la montre n'a pas été lue. */
    val status: WatchStatus? = null,
    /** Sommeil et cœur autorisés ? Faux : ces deux blocs proposent de les ajouter. */
    val extrasGranted: Boolean = true,
    val range: TrendRange = TrendRange.WEEK,
    val today: LocalDate = LocalDate.now(),
    /** Les jours de la période, du plus ancien à aujourd'hui ; null = trou (aucun relevé ce jour-là). */
    val days: List<WatchDayRecord?> = emptyList(),
    /** Les jours de la période précédente, de même durée. */
    val previousDays: List<WatchDayRecord?> = emptyList(),
    val sessions: SessionSummary = SessionSummary(0, 0),
    /** Des relevés sont gardés sur le téléphone (même si la montre n'est plus connectée). */
    val hasHistory: Boolean = false,
) {
    /** L'autorisation a été retirée : l'historique reste visible mais plus rien de nouveau n'arrive. */
    val readingCut: Boolean get() = status != null && status != WatchStatus.READY && hasHistory

    /** Rien à afficher : pas connectée et aucun relevé gardé. */
    val notConnected: Boolean get() = status != null && status != WatchStatus.READY && !hasHistory
}

/**
 * Écran « Ma montre » (PRD du 23/11/2026) : tendances sur 7 ou 30 jours à partir des relevés gardés sur le
 * téléphone. La relecture de Health Connect se fait en tâche de fond : l'écran s'affiche tout de suite avec ce
 * qui est déjà gardé, puis se met à jour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchTrendsViewModel(
    private val watch: WatchRepository,
    /** Séances d'une période (plan et activités), fournies par AppContainer : Ma montre ne dépend pas du paquet `workout`. */
    private val sessionSummary: (LocalDate, LocalDate) -> Flow<SessionSummary>,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val range = MutableStateFlow(TrendRange.WEEK)
    private val today = MutableStateFlow(todayProvider())
    private val connection = MutableStateFlow<Pair<WatchStatus, Boolean>?>(null)

    private val keptCount = MutableStateFlow(0)

    private class Period(val days: List<WatchDayRecord?>, val previous: List<WatchDayRecord?>, val anyRecent: Boolean)

    private val period = combine(range, today) { r, t -> r to t }.flatMapLatest { (r, t) ->
        val n = r.days
        val from = t.minusDays(2L * n - 1)
        watch.observeHistory(from, t).map { recent ->
            val byDate = recent.associateBy { it.date }
            val all = (0 until 2 * n).map { byDate[from.plusDays(it.toLong())] }
            Period(all.drop(n), all.take(n), recent.isNotEmpty())
        }
    }

    private val sessions = combine(range, today) { r, t -> r to t }.flatMapLatest { (r, t) ->
        sessionSummary(t.minusDays(r.days - 1L), t)
    }

    private val base = combine(range, today, connection, period, sessions) { r, t, conn, p, s ->
        WatchTrendsUiState(
            status = conn?.first,
            extrasGranted = conn?.second ?: true,
            range = r,
            today = t,
            days = p.days,
            previousDays = p.previous,
            sessions = s,
            hasHistory = p.anyRecent,
        )
    }

    val uiState: StateFlow<WatchTrendsUiState> = combine(base, keptCount) { state, kept ->
        state.copy(hasHistory = state.hasHistory || kept > 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchTrendsUiState())

    fun selectRange(value: TrendRange) = range.update { value }

    /** À l'ouverture et au retour sur l'écran (par exemple après l'autorisation) : état de la montre, puis relevés. */
    fun onResume() {
        today.value = todayProvider()
        viewModelScope.launch {
            keptCount.value = watch.historyCount()
            val status = watch.status()
            connection.value = status to (status == WatchStatus.READY && watch.extrasGranted())
            if (status == WatchStatus.READY) {
                watch.syncHistory(today.value)
                keptCount.value = watch.historyCount()
            }
        }
    }
}
