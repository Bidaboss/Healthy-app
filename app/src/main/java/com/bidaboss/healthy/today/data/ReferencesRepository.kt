package com.bidaboss.healthy.today.data

import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.core.reference.RefDay
import com.bidaboss.healthy.core.reference.ReferenceProfile
import com.bidaboss.healthy.core.reference.ReferenceReport
import com.bidaboss.healthy.core.reference.ReferenceReports
import com.bidaboss.healthy.health.data.WatchRepository
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * Les repères (PRD « repères nationaux ») : ce que l'app a noté sur 7 ou 28 jours, mis face à la table de `:core`.
 * Repas, eau, séances, profil et relevés de la montre ; rien ne sort du téléphone, aucune donnée n'est écrite.
 */
class ReferencesRepository(
    private val meals: MealRepository,
    private val body: BodyRepository,
    private val logs: WorkoutLogRepository,
    private val watch: WatchRepository,
) {
    /** Les [days] jours finissant à [today]. */
    fun observe(today: LocalDate, days: Int): Flow<ReferenceReport> {
        val first = today.minusDays(days - 1L)
        val sport = (0 until days).map { logs.observeSessionSummary(first.plusDays(it.toLong()), first.plusDays(it.toLong())) }
        return combine(
            meals.observeMealsBetween(first, today),
            body.observeDailyWater(first, today),
            combine(sport) { it.toList() },
            combine(body.observeProfile(), body.observeWeighIns()) { p, w -> p to w },
            watch.observeHistory(first, today),
        ) { allMeals, water, perDay, (profile, weighIns), watched ->
            val mealsByDay = allMeals.groupBy { it.localDate }
            val watchedByDay = watched.associateBy { it.date }
            val refDays = (0 until days).map { i ->
                val day = first.plusDays(i.toLong())
                val dayMeals = mealsByDay[day].orEmpty()
                // Les macros d'un jour ne comptent que si chacun de ses repas en porte : un repas en calories seules fausserait la moyenne.
                val hasMacros = dayMeals.isNotEmpty() && dayMeals.none { it.isCaloriesOnly }
                RefDay(
                    kcal = dayMeals.takeIf { it.isNotEmpty() }?.sumOf { it.totalKcal }?.toInt(),
                    proteinG = dayMeals.takeIf { hasMacros }?.sumOf { it.totalProteinG },
                    fiberG = dayMeals.takeIf { hasMacros }?.sumOf { it.totalFiberG },
                    carbsG = dayMeals.takeIf { hasMacros }?.sumOf { it.totalCarbsG },
                    fatG = dayMeals.takeIf { hasMacros }?.sumOf { it.totalFatG },
                    waterMl = water[day]?.takeIf { it > 0 },
                    sportMinutes = perDay[i].minutes,
                    sleepMin = watchedByDay[day]?.sleepMin,
                    steps = watchedByDay[day]?.steps,
                    waterGoalMl = profile?.let {
                        Hydration.targetMl(it.sex, Maintenance.referenceWeightKg(it.weightKg, weighIns, day), perDay[i].count)
                    },
                    maintenanceKcal = profile?.let { Maintenance.maintenanceKcal(it, weighIns, day) },
                )
            }
            val weight = profile?.let { Maintenance.referenceWeightKg(it.weightKg, weighIns, today) }
            ReferenceReports.build(
                refDays,
                weight,
                profile?.let { ReferenceProfile(it.sex == Sex.MALE, Maintenance.ageYears(it.birthDate, today)) },
            )
        }
    }
}
