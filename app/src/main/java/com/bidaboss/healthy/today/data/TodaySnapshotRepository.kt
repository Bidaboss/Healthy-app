package com.bidaboss.healthy.today.data

import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.Band
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.progress.Growth
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.core.progress.Motivation
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** Où en est la séance du jour, pour le widget. */
sealed interface SessionStatus {
    /** Séance prévue pas encore faite. */
    data class Planned(val title: String) : SessionStatus

    /** Séance ou activité enregistrée aujourd'hui. */
    data object Done : SessionStatus

    /** Rien de prévu aujourd'hui. */
    data object Rest : SessionStatus
}

/** Dernier repas noté du jour : « Déjeuner · 620 kcal · 12 h 30 ». */
data class LastMeal(val label: String, val kcal: Int, val at: LocalTime)

/** L'état de la journée en un coup d'œil (widget d'écran d'accueil). */
data class TodaySnapshot(
    val kcal: Int,
    val maintenanceKcal: Int?,
    val proteinG: Int,
    val proteinTargetG: Int?,
    val waterMl: Int,
    val waterTargetMl: Int,
    val session: SessionStatus,
    val lastMeal: LastMeal?,
    /** Couleur de l'anneau, même règle qu'Aujourd'hui : neutre (null) sauf dépassement net. */
    val band: Band? = null,
    val mood: Mood = Mood.HAPPY,
    val growth: Growth = Growth.LEAF,
)

/** Réunit repas, eau, profil et sport du jour (le tableau de bord voit tous les paquets). */
class TodaySnapshotRepository(
    private val meals: MealRepository,
    private val body: BodyRepository,
    private val plans: WorkoutRepository,
    private val logs: WorkoutLogRepository,
) {
    suspend fun snapshot(day: LocalDate): TodaySnapshot {
        val dayMeals = meals.observeMeals(day).first()
        val profile = body.observeProfile().first()
        val weighIns = body.observeWeighIns().first()
        val sessionsDone = logs.observeSessionCount(day, day).first()
        val activities = logs.observeActivityCount(day, day).first()
        val referenceWeight = profile?.let { Maintenance.referenceWeightKg(it.weightKg, weighIns, day) }
        val planned = plans.observeActivePlan().first()?.sessions?.firstOrNull { it.dayOfWeek == day.dayOfWeek.value }
        val last = dayMeals.maxByOrNull { it.eatenAt }
        val kcal = dayMeals.sumOf { it.totalKcal }
        val maintenance = profile?.let { Maintenance.maintenanceKcal(it, weighIns, day) }
        val activeDates = logs.observeActiveDates().first()
        return TodaySnapshot(
            kcal = kcal.roundToInt(),
            maintenanceKcal = maintenance,
            proteinG = dayMeals.sumOf { it.totalProteinG }.toInt(),
            proteinTargetG = referenceWeight?.let { Maintenance.proteinTargetG(it) },
            waterMl = body.observeDailyWater(day, day).first()[day] ?: 0,
            // Sans profil, on vise le minimum conseillé.
            waterTargetMl = if (profile != null && referenceWeight != null) {
                Hydration.targetMl(profile.sex, referenceWeight, sessionsDone)
            } else {
                Hydration.MIN_ML
            },
            session = when {
                sessionsDone > 0 || activities > 0 -> SessionStatus.Done
                planned != null -> SessionStatus.Planned(planned.title)
                else -> SessionStatus.Rest
            },
            lastMeal = last?.let { LastMeal(it.type.label, it.totalKcal.roundToInt(), it.eatenAt.toLocalTime()) },
            band = maintenance?.takeIf { kcal > 0 }?.let { Maintenance.bandSoFar(kcal, it) },
            // Même humeur et même taille que Pousse sur Aujourd'hui.
            mood = Motivation.mood(sessionDoneToday = day in activeDates, sessionPlannedToday = planned != null),
            growth = Motivation.growth(activeDates, day),
        )
    }
}
