package com.bidaboss.healthy.today.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.progress.Motivation
import com.bidaboss.healthy.core.progress.WeeklyRecap
import com.bidaboss.healthy.core.progress.WeeklyRecaps
import com.bidaboss.healthy.today.data.WeeklyRecapRepository
import com.bidaboss.healthy.today.data.WeeklyRecapStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Carte et écran du bilan de la semaine (fil Design B4). */
class WeeklyRecapViewModel(
    private val repository: WeeklyRecapRepository,
    private val store: WeeklyRecapStore,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    private val _availableWeek = MutableStateFlow<LocalDate?>(null)

    /** Lundi de la semaine dont le bilan peut s'ouvrir maintenant (dimanche 18 h → mardi), ou null. */
    val availableWeek: StateFlow<LocalDate?> = _availableWeek.asStateFlow()

    private val _autoOpen = MutableStateFlow(false)

    /** Vrai le lundi à la première ouverture : l'écran ouvre le bilan puis appelle [onAutoOpened]. */
    val autoOpen: StateFlow<Boolean> = _autoOpen.asStateFlow()

    private val _recap = MutableStateFlow<WeeklyRecap?>(null)
    val recap: StateFlow<WeeklyRecap?> = _recap.asStateFlow()

    init {
        refresh()
    }

    /** À chaque retour sur Aujourd'hui : la fenêtre suit l'horloge. */
    fun refresh() {
        val at = now()
        _availableWeek.value = WeeklyRecaps.recapWeek(at)
        _autoOpen.value = WeeklyRecaps.shouldAutoOpen(at, store.lastShownWeek)
    }

    fun onAutoOpened() {
        _availableWeek.value?.let { store.lastShownWeek = it }
        _autoOpen.value = false
    }

    /** Charge le bilan de la semaine disponible (ou de la semaine en cours hors fenêtre). */
    fun load() {
        val available = WeeklyRecaps.recapWeek(now())
        // Ouvert à la main dans la fenêtre : il ne se rouvrira pas tout seul lundi.
        available?.let { store.lastShownWeek = it }
        val week = available ?: Motivation.weekStart(now().toLocalDate())
        viewModelScope.launch { _recap.value = repository.recap(week) }
    }
}
