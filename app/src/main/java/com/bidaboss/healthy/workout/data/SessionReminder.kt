package com.bidaboss.healthy.workout.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Réglage du rappel de séance : désactivé par défaut, à 18 h 00 si on l'active. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val time: LocalTime = LocalTime.of(18, 0),
)

/**
 * Le réglage du rappel, gardé dans les préférences du téléphone (pas dans Room : ce n'est pas une
 * donnée du journal). Les préférences sont exclues de la sauvegarde cloud comme le reste.
 */
class ReminderSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    fun save(settings: ReminderSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_MINUTE_OF_DAY, settings.time.hour * 60 + settings.time.minute)
            .apply()
        _settings.value = settings
    }

    private fun read(): ReminderSettings {
        val default = ReminderSettings()
        val minute = prefs.getInt(KEY_MINUTE_OF_DAY, default.time.hour * 60 + default.time.minute)
        return ReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, default.enabled),
            time = LocalTime.of(minute / 60, minute % 60),
        )
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_MINUTE_OF_DAY = "minute_of_day"
    }
}

/** Prochain rappel : aujourd'hui à [time] s'il n'est pas encore passé, sinon demain. */
fun nextReminderAt(now: LocalDateTime, time: LocalTime): LocalDateTime {
    val today = now.toLocalDate().atTime(time)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/**
 * La séance à rappeler aujourd'hui, ou null : pas de plan, pas de séance prévue ce jour-là
 * (`day_of_week` du plan), ou une séance déjà faite aujourd'hui.
 */
fun sessionToRemind(plan: WorkoutPlan?, today: LocalDate, sessionsDoneToday: Int): WorkoutSession? {
    if (plan == null || sessionsDoneToday > 0) return null
    return plan.sessions.firstOrNull { it.dayOfWeek == today.dayOfWeek.value }
}

/** Jours d'entraînement du plan (1 = lundi … 7 = dimanche), triés, sans doublon. */
fun trainingDays(plan: WorkoutPlan?): List<Int> =
    plan?.sessions?.mapNotNull { it.dayOfWeek }?.distinct()?.sorted().orEmpty()
