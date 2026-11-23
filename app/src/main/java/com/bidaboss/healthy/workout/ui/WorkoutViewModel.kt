package com.bidaboss.healthy.workout.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.progress.Motivation
import com.bidaboss.healthy.core.workout.ActivityCalories
import com.bidaboss.healthy.core.workout.BodyPart
import com.bidaboss.healthy.workout.data.FreeActivity
import com.bidaboss.healthy.workout.data.SessionLog
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutPlan
import com.bidaboss.healthy.workout.data.WorkoutRepository
import com.bidaboss.healthy.workout.data.RouteUse
import com.bidaboss.healthy.workout.data.route.SavedRoute
import com.bidaboss.healthy.workout.data.route.SavedRouteRepository
import com.bidaboss.healthy.core.route.RouteDirection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class WorkoutUiState(
    val plan: WorkoutPlan? = null,
    /** Séances faites depuis lundi. */
    val sessionsThisWeek: Int = 0,
    val history: List<SessionLog> = emptyList(),
    /** Activités libres faites depuis lundi. */
    val activitiesThisWeek: Int = 0,
    val activities: List<FreeActivity> = emptyList(),
    /** Lundi de la semaine affichée, et jours actifs de cette semaine (séance ou activité). */
    val monday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val activeDaysThisWeek: Set<LocalDate> = emptySet(),
    /** Semaines actives d'affilée (même règle que la flamme d'Aujourd'hui). */
    val streakWeeks: Int = 0,
    /** Calories approximatives par activité (id → kcal) ; vide sans profil (pas de poids). */
    val activityKcal: Map<Long, Int> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
    workoutRepository: WorkoutRepository,
    private val logRepository: WorkoutLogRepository,
    /** Poids de référence du jour, fourni par AppContainer (Sport ne dépend pas du paquet `body`). */
    referenceWeightKg: (LocalDate) -> Flow<Double?> = { flowOf(null) },
    /** La montre est-elle connectée ? Fourni par AppContainer (Sport ne dépend pas du paquet `health`). */
    private val watchConnected: suspend () -> Boolean = { false },
    private val routeRepository: SavedRouteRepository? = null,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val date = MutableStateFlow(today())

    private val _watchConnected = MutableStateFlow(false)

    /** Vrai si la montre est connectée : le bouton « Mes stats montre » apparaît. */
    val hasWatch: StateFlow<Boolean> = _watchConnected.asStateFlow()

    private val _reportToShare = MutableStateFlow<String?>(null)

    /** Bilan prêt à être partagé (texte JSON), ou null. L'écran le remet à null une fois le partage ouvert. */
    val reportToShare: StateFlow<String?> = _reportToShare.asStateFlow()

    val uiState: StateFlow<WorkoutUiState> = date
        .flatMapLatest { day ->
            val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            combine(
                workoutRepository.observeActivePlan(),
                logRepository.observeSessionCount(monday, monday.plusDays(6)),
                logRepository.observeRecentSessions(),
                logRepository.observeActivityCount(monday, monday.plusDays(6)),
                logRepository.observeRecentActivities(),
            ) { plan, count, history, activityCount, activities ->
                WorkoutUiState(plan, count, history, activityCount, activities, monday = monday)
            }.combine(logRepository.observeActiveDates()) { state, activeDates ->
                state.copy(
                    activeDaysThisWeek = activeDates.filterTo(HashSet()) { !it.isBefore(monday) && !it.isAfter(monday.plusDays(6)) },
                    streakWeeks = Motivation.activeWeekStreak(activeDates, day),
                )
            }.combine(referenceWeightKg(day)) { state, weight ->
                state.copy(
                    activityKcal = state.activities.mapNotNull { a ->
                        ActivityCalories.estimateKcal(a.type, a.durationMin, weight)?.let { a.id to it }
                    }.toMap(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    /** À chaque retour sur l'écran : la semaine en cours suit le calendrier, et la connexion de la montre a pu changer. */
    fun onResume() {
        date.value = today()
        viewModelScope.launch { _watchConnected.value = runCatching { watchConnected() }.getOrDefault(false) }
    }

    /** Une activité du jour choisi (jamais dans le futur : un jour à venir devient aujourd'hui). */
    fun addActivity(type: String, durationMin: Int, date: LocalDate, bodyParts: Set<BodyPart>) {
        val day = minOf(date, today())
        viewModelScope.launch { logRepository.addActivity(type, durationMin, day, now(), bodyParts) }
    }

    /** Trajets enregistrés, pour la section « Mes trajets » de l'ajout d'activité. */
    val routes: StateFlow<List<SavedRoute>> = (routeRepository?.observeAll() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** « Ajouter ce trajet » : une activité de la distance du trajet (sens compris), durée estimée à vélo ou choisie. */
    fun addRouteActivity(type: String, route: SavedRoute, direction: RouteDirection, durationMin: Int, date: LocalDate) {
        val oneWay = route.distanceKm ?: return
        val day = minOf(date, today())
        val use = RouteUse(route.id, route.name, direction, direction.distanceKm(oneWay))
        viewModelScope.launch { logRepository.addActivity(type, durationMin, day, now(), route = use) }
    }

    /** Prépare le bilan des 4 dernières semaines pour l'envoyer à Claude. */
    fun exportReport() {
        viewModelScope.launch {
            _reportToShare.value = logRepository.exportTrainingReport(today(), UUID.randomUUID().toString(), now())
        }
    }

    fun onReportShared() {
        _reportToShare.value = null
    }

    fun updateActivity(id: Long, type: String, durationMin: Int, date: LocalDate, bodyParts: Set<BodyPart>) {
        val day = minOf(date, today())
        viewModelScope.launch { logRepository.updateActivity(id, type, durationMin, day, bodyParts) }
    }

    fun deleteActivity(id: Long) {
        viewModelScope.launch { logRepository.deleteActivity(id) }
    }
}
