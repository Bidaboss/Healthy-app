package com.bidaboss.healthy.meals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.meals.data.HabitDismissStore
import com.bidaboss.healthy.meals.data.HabitualMeal
import com.bidaboss.healthy.meals.data.HabitualMeals
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Carte « Ton petit-déjeuner habituel ? » des Repas : null s'il n'y a rien de fiable à proposer. */
data class HabitUiState(val suggestion: HabitualMeal? = null)

/**
 * Suggestion de repas habituel (PRD saisie rapide, 22/11/2026) : jamais d'ajout automatique ni de notification,
 * seulement une carte. « Pas aujourd'hui » la masque jusqu'au lendemain.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HabitViewModel(
    private val meals: MealRepository,
    private val store: HabitDismissStore,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    private val clock = MutableStateFlow(now())
    private val dismissedDay = MutableStateFlow(store.dismissedDay())

    val uiState: StateFlow<HabitUiState> = clock
        .flatMapLatest { at ->
            val day = at.toLocalDate()
            combine(meals.observeMealsBetween(day.minusDays(HabitualMeals.RECENT_DAYS), day), dismissedDay) { all, dismissed ->
                if (dismissed == day) {
                    HabitUiState()
                } else {
                    val suggestion = HabitualMeals.suggest(
                        past = all,
                        today = all.filter { it.localDate == day },
                        now = at,
                        skipTypesNotedToday = true,
                        limit = 1,
                    ).firstOrNull()
                    HabitUiState(suggestion)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitUiState())

    /** À chaque retour sur Aujourd'hui : l'heure a avancé, la fenêtre de 1 h 30 aussi. */
    fun onResume() {
        clock.value = now()
        dismissedDay.value = store.dismissedDay()
    }

    /** « Ajouter » : le repas est enregistré pour maintenant (nouveau repas, sans import_id). */
    fun add(suggestion: HabitualMeal) {
        viewModelScope.launch {
            meals.saveMeal(MealDraft(null, suggestion.type, now(), suggestion.title, null, suggestion.foods))
        }
    }

    fun dismiss() {
        val day = now().toLocalDate()
        store.dismiss(day)
        dismissedDay.value = day
    }
}
