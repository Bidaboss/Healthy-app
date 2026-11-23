package com.bidaboss.healthy.today.data

import android.content.Context
import androidx.core.content.edit
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.progress.RecapDay
import com.bidaboss.healthy.core.progress.WeeklyRecap
import com.bidaboss.healthy.core.progress.WeeklyRecaps
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Bilan de la semaine : réunit repas, eau, profil et sport (le tableau de bord voit tous les paquets). */
class WeeklyRecapRepository(
    private val meals: MealRepository,
    private val body: BodyRepository,
    private val logs: WorkoutLogRepository,
) {
    suspend fun recap(weekStart: LocalDate): WeeklyRecap {
        val weekEnd = weekStart.plusDays(6)
        val byDay = meals.getMealsBetween(weekStart, weekEnd).groupBy { it.localDate }
        val water = body.observeDailyWater(weekStart, weekEnd).first()
        val profile = body.observeProfile().first()
        val weighIns = body.observeWeighIns().first()
        val days = (0L..6L).map { i ->
            val day = weekStart.plusDays(i)
            val dayMeals = byDay[day].orEmpty()
            RecapDay(
                date = day,
                hasMeal = dayMeals.isNotEmpty(),
                kcal = dayMeals.sumOf { it.totalKcal },
                proteinG = dayMeals.sumOf { it.totalProteinG },
                fiberG = dayMeals.sumOf { it.totalFiberG },
                waterMl = water[day] ?: 0,
                waterTargetMl = profile?.let {
                    val sessions = logs.observeSessionCount(day, day).first()
                    Hydration.targetMl(it.sex, Maintenance.referenceWeightKg(it.weightKg, weighIns, day), sessions)
                },
            )
        }
        return WeeklyRecaps.build(
            weekStart = weekStart,
            days = days,
            maintenanceKcal = profile?.let { Maintenance.maintenanceKcal(it, weighIns, weekEnd) },
            proteinTargetG = profile?.let { Maintenance.proteinTargetG(Maintenance.referenceWeightKg(it.weightKg, weighIns, weekEnd)) },
            sessions = logs.observeSessionCount(weekStart, weekEnd).first(),
            activities = logs.observeActivityCount(weekStart, weekEnd).first(),
            activeDates = logs.observeActiveDates().first(),
            sessionCountBefore = logs.observeSessionCount(FIRST_DAY, weekStart.minusDays(1)).first(),
            sessionCountAtEnd = logs.observeSessionCount(FIRST_DAY, weekEnd).first(),
            mealDates = meals.observeMealDates().first(),
        )
    }

    private companion object {
        val FIRST_DAY: LocalDate = LocalDate.of(2000, 1, 1)
    }
}

/** Dernière semaine dont le bilan s'est ouvert tout seul (préférences du téléphone, pas dans la sauvegarde). */
class WeeklyRecapStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("weekly_recap", Context.MODE_PRIVATE)

    var lastShownWeek: LocalDate?
        get() = prefs.getString(KEY_LAST_SHOWN, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) = prefs.edit { putString(KEY_LAST_SHOWN, value?.toString()) }

    private companion object {
        const val KEY_LAST_SHOWN = "last_shown_week"
    }
}
