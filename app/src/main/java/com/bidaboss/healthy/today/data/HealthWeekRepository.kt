package com.bidaboss.healthy.today.data

import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.health.data.DayTotals
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Les 7 derniers jours pour l'onglet Santé : repas, eau et sport (le tableau de bord voit tous les paquets ;
 * `health` ne dépend pas des autres, AppContainer lui passe cette fonction).
 */
class HealthWeekRepository(
    private val meals: MealRepository,
    private val body: BodyRepository,
    private val logs: WorkoutLogRepository,
) {
    /** Les [DAYS] jours finissant à [today], du plus ancien à aujourd'hui. */
    fun observe(today: LocalDate): Flow<List<DayTotals>> {
        val first = today.minusDays(DAYS - 1L)
        val sport = (0 until DAYS).map { logs.observeSessionSummary(first.plusDays(it.toLong()), first.plusDays(it.toLong())) }
        return combine(
            meals.observeMealsBetween(first, today),
            body.observeDailyWater(first, today),
            combine(sport) { it.toList() },
            combine(body.observeProfile(), body.observeWeighIns()) { p, w -> p to w },
        ) { allMeals, water, perDay, (profile, weighIns) ->
            val byDay = allMeals.groupBy { it.localDate }
            (0 until DAYS).map { i ->
                val day = first.plusDays(i.toLong())
                DayTotals(
                    date = day,
                    kcal = byDay[day]?.takeIf { it.isNotEmpty() }?.sumOf { it.totalKcal }?.roundToInt(),
                    waterMl = water[day]?.takeIf { it > 0 },
                    sessions = perDay[i].count,
                    sportMinutes = perDay[i].minutes,
                    kcalGoal = profile?.let { Maintenance.maintenanceKcal(it, weighIns, day) },
                    waterGoalMl = profile?.let {
                        Hydration.targetMl(it.sex, Maintenance.referenceWeightKg(it.weightKg, weighIns, day), perDay[i].count)
                    },
                )
            }
        }
    }

    companion object {
        const val DAYS = 7
    }
}
