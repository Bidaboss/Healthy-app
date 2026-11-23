package com.bidaboss.healthy.health.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.progress.WeeklyRecap
import com.bidaboss.healthy.core.progress.WeeklyRecaps
import com.bidaboss.healthy.core.progress.Motivation
import com.bidaboss.healthy.health.data.DayTotals
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Les quatre parties de l'onglet Santé. Sommeil et Montre n'existent qu'avec une montre connectée. */
enum class HealthSection(val label: String, val needsWatch: Boolean) {
    WEEK("7 jours", false),
    SLEEP("Sommeil", true),
    WATCH("Montre", true),
    RECAP("Bilan", false),
}

data class HealthUiState(
    /** null tant que la connexion à la montre n'a pas été lue. */
    val status: WatchStatus? = null,
    /** Sommeil et cœur autorisés ? */
    val extrasGranted: Boolean = true,
    /** Distance autorisée ? */
    val distanceGranted: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    /** Les 7 derniers jours notés dans l'app, du plus ancien à aujourd'hui. */
    val week: List<DayTotals> = emptyList(),
    /** Les 7 derniers jours de la montre, du plus ancien à aujourd'hui ; null = aucun relevé ce jour-là. */
    val watchWeek: List<WatchDayRecord?> = emptyList(),
    val hasHistory: Boolean = false,
    /** Bilan de la semaine (la semaine à résumer, ou la semaine en cours). */
    val recap: WeeklyRecap? = null,
) {
    /** Montre connectée : lue maintenant, ou au moins un historique gardé (autorisation retirée depuis). */
    val watchConnected: Boolean get() = status == WatchStatus.READY || hasHistory

    /** Les parties visibles : sans montre, ni Sommeil ni Montre. Tant que la connexion n'est pas lue, on n'en promet pas. */
    val sections: List<HealthSection>
        get() = HealthSection.entries.filter { !it.needsWatch || watchConnected }
}

/**
 * Onglet Santé (refonte, lot 4) : repas, eau et sport des 7 derniers jours, relevés de la montre, bilan de la
 * semaine. La relecture de Health Connect se fait en tâche de fond ; l'écran montre tout de suite ce qui est gardé.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModel(
    private val watch: WatchRepository,
    private val week: (LocalDate) -> Flow<List<DayTotals>>,
    private val recapOf: suspend (LocalDate) -> WeeklyRecap,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    private val today = MutableStateFlow(todayProvider())
    private class Connection(val status: WatchStatus, val extras: Boolean, val distance: Boolean)
    private val connection = MutableStateFlow<Connection?>(null)
    private val keptCount = MutableStateFlow(0)
    private val recap = MutableStateFlow<WeeklyRecap?>(null)

    private val appWeek = today.flatMapLatest { week(it) }

    private class Watched(val days: List<WatchDayRecord?>, val any: Boolean)

    private val watched = today.flatMapLatest { t ->
        val from = t.minusDays(WEEK - 1L)
        watch.observeHistory(from, t).map { rows ->
            val byDate = rows.associateBy { it.date }
            Watched((0 until WEEK).map { byDate[from.plusDays(it.toLong())] }, rows.isNotEmpty())
        }
    }

    val uiState: StateFlow<HealthUiState> = combine(today, connection, appWeek, watched, combine(keptCount, recap) { k, r -> k to r }) { t, conn, w, wd, extra ->
        HealthUiState(
            status = conn?.status,
            extrasGranted = conn?.extras ?: true,
            distanceGranted = conn?.distance ?: true,
            today = t,
            week = w,
            watchWeek = wd.days,
            hasHistory = wd.any || extra.first > 0,
            recap = extra.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState())

    /** À l'ouverture et au retour sur l'écran (par exemple après l'autorisation) : état de la montre, puis relevés. */
    fun onResume() {
        today.value = todayProvider()
        viewModelScope.launch {
            keptCount.value = watch.historyCount()
            val status = watch.status()
            connection.value = Connection(
                status,
                extras = status == WatchStatus.READY && watch.extrasGranted(),
                distance = status == WatchStatus.READY && watch.distanceGranted(),
            )
            if (status == WatchStatus.READY) {
                watch.syncHistory(today.value)
                keptCount.value = watch.historyCount()
            }
        }
        viewModelScope.launch {
            val at = nowProvider()
            val week = WeeklyRecaps.recapWeek(at) ?: Motivation.weekStart(at.toLocalDate())
            recap.value = runCatching { recapOf(week) }.getOrNull()
        }
    }

    private companion object {
        const val WEEK = 7
    }
}
