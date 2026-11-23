package com.bidaboss.healthy.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.workout.data.ReminderSettings
import com.bidaboss.healthy.workout.data.ReminderSettingsStore
import com.bidaboss.healthy.workout.data.WorkoutRepository
import com.bidaboss.healthy.workout.data.trainingDays
import com.bidaboss.healthy.workout.reminder.SessionReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime

data class ReminderUiState(
    val settings: ReminderSettings = ReminderSettings(),
    /** Jours d'entraînement du plan actif (1 = lundi … 7 = dimanche). Vide : pas de plan ou pas de jours. */
    val trainingDays: List<Int> = emptyList(),
    val hasPlan: Boolean = false,
)

class ReminderViewModel(
    workoutRepository: WorkoutRepository,
    private val store: ReminderSettingsStore,
    private val scheduler: SessionReminderScheduler,
) : ViewModel() {

    val uiState: StateFlow<ReminderUiState> =
        combine(store.settings, workoutRepository.observeActivePlan()) { settings, plan ->
            ReminderUiState(settings = settings, trainingDays = trainingDays(plan), hasPlan = plan != null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderUiState())

    fun setEnabled(enabled: Boolean) = update(store.settings.value.copy(enabled = enabled))

    fun setTime(time: LocalTime) = update(store.settings.value.copy(time = time))

    private fun update(settings: ReminderSettings) {
        store.save(settings)
        scheduler.apply(settings)
    }
}
