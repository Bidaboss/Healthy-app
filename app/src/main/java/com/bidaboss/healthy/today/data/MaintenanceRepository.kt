package com.bidaboss.healthy.today.data

import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.body.data.MaintenanceInfo
import com.bidaboss.healthy.core.body.IntakeDay
import com.bidaboss.healthy.core.body.MaintenanceEstimator
import com.bidaboss.healthy.core.body.MaintenanceProposal
import com.bidaboss.healthy.meals.data.MealRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate

/**
 * Maintien réel estimé à partir des repas et des pesées (docs/decisions.md, « Maintien réel »).
 * Ici et non dans `body` ou `meals` : il réunit les deux, qui ne dépendent pas l'un de l'autre.
 */
class MaintenanceRepository(private val meals: MealRepository, private val body: BodyRepository) {

    /** null sans profil. Le calcul se fait en tâche de fond et se met à jour avec les repas et les pesées. */
    fun observe(today: LocalDate): Flow<MaintenanceInfo?> {
        val from = today.minusDays(MaintenanceEstimator.WINDOW_DAYS.toLong())
        return combine(
            body.observeProfile(),
            body.observeWeighIns(),
            meals.observeMealsBetween(from, today.minusDays(1)),
            body.observeProposalState(),
        ) { profile, weighIns, windowMeals, state ->
            profile ?: return@combine null
            val intake = windowMeals.groupBy { it.localDate }
                .mapValues { (_, day) -> IntakeDay(day.sumOf { it.totalKcal }, day.size) }
            val estimate = MaintenanceEstimator.estimate(profile, weighIns, intake, today)
            val current = estimate.formulaKcal + profile.maintenanceAdjustmentKcal
            MaintenanceInfo(
                proposal = MaintenanceProposal.of(estimate, current, state, today),
                adjustmentKcal = profile.maintenanceAdjustmentKcal,
                activityChanged = profile.maintenanceAdjustmentKcal != 0 &&
                    profile.adjustmentActivity != null && profile.adjustmentActivity != profile.activity,
                snoozed = state.snoozedUntil?.let { today.isBefore(it) } ?: false,
            )
        }.flowOn(Dispatchers.Default)
    }
}
