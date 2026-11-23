package com.bidaboss.healthy.today.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.health.data.WatchDay
import com.bidaboss.healthy.health.data.WatchRepository
import com.bidaboss.healthy.health.data.WatchSession
import com.bidaboss.healthy.health.data.WatchSnapshot
import com.bidaboss.healthy.health.data.WatchStatus
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Carte « Ma montre » d'Aujourd'hui. Elle n'apparaît que si la montre est connectée ([visible]). */
data class WatchUiState(
    val visible: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    /** null : la lecture a échoué. */
    val day: WatchDay? = null,
    /** Séances des derniers jours pas encore dans le journal, les plus récentes d'abord. */
    val proposals: List<WatchSession> = emptyList(),
)

/**
 * Le tableau de bord réunit la montre et le journal d'activités : une séance de la montre est ajoutée
 * au journal avec son identifiant, pour ne plus jamais être proposée (fil Garmin, 22/11/2026).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModel(
    private val watch: WatchRepository,
    private val logs: WorkoutLogRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private class Loaded(val status: WatchStatus, val snapshot: WatchSnapshot?)

    private val date = MutableStateFlow(today())
    private val loaded = MutableStateFlow<Loaded?>(null)

    val uiState: StateFlow<WatchUiState> = combine(
        loaded,
        date.flatMapLatest { logs.observeHealthIds(it.minusDays(WatchRepository.RECENT_DAYS - 1L)) },
        date,
    ) { loaded, added, day ->
        WatchUiState(
            visible = loaded?.status == WatchStatus.READY,
            date = day,
            day = loaded?.snapshot?.day,
            proposals = loaded?.snapshot?.sessions.orEmpty().filter { it.id !in added },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchUiState())

    /** À chaque retour sur Aujourd'hui : la montre se synchronise en arrière-plan, on relit. */
    fun onResume() {
        date.value = today()
        reload()
    }

    fun add(session: WatchSession) {
        viewModelScope.launch {
            logs.addActivity(session.type, session.durationMin, session.date, now(), healthId = session.id)
        }
    }

    fun ignore(session: WatchSession) {
        watch.ignore(session.id)
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val status = watch.status()
            loaded.value = Loaded(status, if (status == WatchStatus.READY) watch.snapshot(date.value) else null)
            // Le relevé du jour (et des derniers jours) est gardé pour l'écran « Ma montre » : en tâche de fond.
            if (status == WatchStatus.READY) watch.syncHistory(date.value)
        }
    }
}
