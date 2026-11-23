package com.bidaboss.healthy.settings.data

import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.motivation.DaySnapshot
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Rassemble la journée (repas, eau, séance) pour choisir la notification de motivation. */
class MotivationRepository(
    private val mealRepository: MealRepository,
    private val bodyRepository: BodyRepository,
    private val workoutRepository: WorkoutRepository,
    private val workoutLogRepository: WorkoutLogRepository,
) {
    suspend fun snapshot(day: LocalDate): DaySnapshot {
        val sessionsDone = workoutLogRepository.observeSessionCount(day, day).first()
        val profile = bodyRepository.observeProfile().first()
        val target = profile?.let {
            val weighIns = bodyRepository.observeWeighIns().first()
            Hydration.targetMl(it.sex, Maintenance.referenceWeightKg(it.weightKg, weighIns, day), sessionsDone)
        } ?: Hydration.MIN_ML
        return DaySnapshot(
            mealCount = mealRepository.observeMeals(day).first().size,
            waterMl = bodyRepository.observeDailyWater(day, day).first()[day] ?: 0,
            waterTargetMl = target,
            plannedSession = workoutRepository.getActivePlan()?.sessions
                ?.firstOrNull { it.dayOfWeek == day.dayOfWeek.value }?.title,
            // Une activité ajoutée à la main (kiné…) compte comme séance faite : pas de relance ce jour-là.
            sessionsDone = sessionsDone + workoutLogRepository.observeActivityCount(day, day).first(),
        )
    }
}
