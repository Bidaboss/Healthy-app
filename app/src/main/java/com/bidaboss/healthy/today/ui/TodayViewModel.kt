package com.bidaboss.healthy.today.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.Band
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.workout.data.FreeActivity
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository
import com.bidaboss.healthy.workout.data.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Un jour des 7 derniers : calories notées (0 si rien) et couleur (null si rien de noté ou pas d'objectif). */
data class DaySummary(val date: LocalDate, val kcal: Double, val band: Band?)

data class TodayUiState(
    val totalKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val mealCount: Int = 0,
    /** Repas du jour notés en calories seules : leurs protéines et fibres ne sont pas comptées. */
    val caloriesOnlyMeals: Int = 0,
    val todaySession: WorkoutSession? = null,
    /** null tant que le profil n'est pas rempli. */
    val maintenanceKcal: Int? = null,
    val todayBand: Band? = null,
    val proteinTargetG: Int? = null,
    /** Repères de lipides et glucides du maintien (30 % / le reste), null sans profil. */
    val fatTargetG: Int? = null,
    val carbsTargetG: Int? = null,
    /** Moyenne des jours notés sur les 7 derniers jours, et sa couleur. */
    val averageKcal: Double? = null,
    val averageBand: Band? = null,
    val lastDays: List<DaySummary> = emptyList(),
    val latestWeightKg: Double? = null,
    val weighedToday: Boolean = false,
    /** Activités ajoutées à la main ce jour-là (kiné, marche…). */
    val todayActivities: List<FreeActivity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    mealRepository: MealRepository,
    workoutRepository: WorkoutRepository,
    bodyRepository: BodyRepository,
    workoutLogRepository: WorkoutLogRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val date = MutableStateFlow(today())

    val uiState: StateFlow<TodayUiState> = date
        .flatMapLatest { day ->
            val from = day.minusDays(Maintenance.AVERAGE_DAYS - 1L)
            combine(
                mealRepository.observeMeals(day),
                workoutRepository.observeActivePlan(),
                mealRepository.observeDailyKcal(from, day),
                bodyRepository.observeProfile(),
                bodyRepository.observeWeighIns(),
            ) { meals, plan, daily, profile, weighIns ->
                val totalKcal = meals.sumOf { it.totalKcal }
                val maintenance = profile?.let { Maintenance.maintenanceKcal(it, weighIns, day) }
                val referenceWeight = profile?.let { Maintenance.referenceWeightKg(it.weightKg, weighIns, day) }
                val average = Maintenance.averageKcal(daily, day)
                fun bandOf(kcal: Double?) = if (maintenance == null || kcal == null || kcal <= 0) null else Maintenance.band(kcal, maintenance)

                // Jour en cours : neutre tant qu'il n'est pas fini (voir Maintenance.bandSoFar).
                fun bandSoFar(kcal: Double) = if (maintenance == null || kcal <= 0) null else Maintenance.bandSoFar(kcal, maintenance)
                TodayUiState(
                    totalKcal = totalKcal,
                    proteinG = meals.sumOf { it.totalProteinG },
                    carbsG = meals.sumOf { it.totalCarbsG },
                    fatG = meals.sumOf { it.totalFatG },
                    fiberG = meals.sumOf { it.totalFiberG },
                    mealCount = meals.size,
                    caloriesOnlyMeals = meals.count { it.isCaloriesOnly },
                    todaySession = plan?.sessions?.firstOrNull { it.dayOfWeek == day.dayOfWeek.value },
                    maintenanceKcal = maintenance,
                    todayBand = bandSoFar(totalKcal),
                    proteinTargetG = referenceWeight?.let { Maintenance.proteinTargetG(it) },
                    fatTargetG = maintenance?.let { Maintenance.fatTargetG(it) },
                    carbsTargetG = maintenance?.let { m ->
                        referenceWeight?.let { Maintenance.carbsTargetG(m, Maintenance.proteinTargetG(it)) }
                    },
                    averageKcal = average,
                    averageBand = bandOf(average),
                    lastDays = (0 until Maintenance.AVERAGE_DAYS).map { i ->
                        val d = from.plusDays(i.toLong())
                        val kcal = daily[d] ?: 0.0
                        DaySummary(d, kcal, if (d == day) bandSoFar(kcal) else bandOf(kcal))
                    },
                    latestWeightKg = weighIns.maxByOrNull { it.date }?.weightKg,
                    weighedToday = weighIns.any { it.date == day },
                )
            }.combine(workoutLogRepository.observeRecentActivities()) { state, activities ->
                state.copy(todayActivities = activities.filter { it.date == day })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /** À chaque retour sur l'écran : si minuit est passé, on affiche le nouveau jour. */
    fun onResume() {
        date.value = today()
    }
}
