package com.bidaboss.healthy.today.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.body.data.MealDrink
import com.bidaboss.healthy.body.data.WaterIntake
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.ui.parseNumber
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** Un jour de l'historique de l'eau. */
data class WaterDay(val date: LocalDate, val ml: Int)

data class WaterUiState(
    val todayMl: Int = 0,
    /** null tant que le profil n'est pas rempli. */
    val targetMl: Int? = null,
    val sessionsToday: Int = 0,
    /** Les 7 derniers jours, du plus ancien à aujourd'hui. */
    val lastDays: List<WaterDay> = emptyList(),
    /** Ajouts du jour, du plus récent au plus ancien. */
    val todayIntakes: List<WaterIntake> = emptyList(),
    /** Boissons des repas du jour, comptées dans l'eau (on les modifie depuis le repas). */
    val mealDrinks: List<MealDrink> = emptyList(),
    val freeAmount: String = "",
    val message: String? = null,
    /** Dernier ajout supprimé, pour proposer « Annuler » ; null une fois le message passé. */
    val lastDeleted: WaterIntake? = null,
)

/** Eau du jour : objectif, ajouts rapides, liste des ajouts du jour, historique de 7 jours. */
@OptIn(ExperimentalCoroutinesApi::class)
class WaterViewModel(
    private val bodyRepository: BodyRepository,
    workoutLogRepository: WorkoutLogRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val date = MutableStateFlow(today())
    private val form = MutableStateFlow(Pair("", null as String?))
    private val lastDeleted = MutableStateFlow<WaterIntake?>(null)

    val uiState: StateFlow<WaterUiState> = date.flatMapLatest { day ->
        val from = day.minusDays(Maintenance.AVERAGE_DAYS - 1L)
        val waterFlow = combine(
            bodyRepository.observeDailyWater(from, day),
            bodyRepository.observeWaterIntakes(day),
            bodyRepository.observeMealDrinks(day),
        ) { daily, intakes, drinks -> Triple(daily, intakes, drinks) }
        combine(
            waterFlow,
            bodyRepository.observeProfile(),
            bodyRepository.observeWeighIns(),
            workoutLogRepository.observeSessionCount(day, day),
            combine(form, lastDeleted) { f, deleted -> f to deleted },
        ) { (water, intakes, mealDrinks), profile, weighIns, sessions, (formState, deleted) ->
            val (free, message) = formState
            WaterUiState(
                todayMl = water[day] ?: 0,
                targetMl = profile?.let {
                    Hydration.targetMl(it.sex, Maintenance.referenceWeightKg(it.weightKg, weighIns, day), sessions)
                },
                sessionsToday = sessions,
                lastDays = (0 until Maintenance.AVERAGE_DAYS).map { i ->
                    val d = from.plusDays(i.toLong())
                    WaterDay(d, water[d] ?: 0)
                },
                todayIntakes = intakes,
                mealDrinks = mealDrinks,
                freeAmount = free,
                message = message,
                lastDeleted = deleted,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterUiState())

    fun onResume() {
        date.value = today()
    }

    fun add(ml: Int) {
        viewModelScope.launch { bodyRepository.addWater(today(), ml) }
    }

    fun undo() {
        viewModelScope.launch {
            if (!bodyRepository.undoLastWater(today())) form.update { it.copy(second = "Rien à annuler aujourd'hui.") }
        }
    }

    fun delete(intake: WaterIntake) {
        viewModelScope.launch {
            // Le jour affiché au moment de la suppression : un « Annuler » après minuit remet l'ajout dans le bon jour.
            deletedDay = date.value
            if (bodyRepository.deleteWater(intake.id)) lastDeleted.value = intake
        }
    }

    private var deletedDay: LocalDate = today()

    /** « Annuler » après une suppression : l'ajout revient avec son heure d'origine. */
    fun restoreDeleted() {
        val intake = lastDeleted.value ?: return
        lastDeleted.value = null
        viewModelScope.launch { bodyRepository.restoreWater(deletedDay, intake) }
    }

    /** Le message « Ajout supprimé » a été vu ou ignoré. */
    fun deletedMessageShown() {
        lastDeleted.value = null
    }

    fun setFreeAmount(text: String) = form.update { Pair(text, null) }

    /** Quantité libre, en centilitres (plus parlant qu'en millilitres pour un verre). */
    fun addFreeAmount() {
        val cl = parseNumber(form.value.first)
        val ml = cl?.let { (it * 10).roundToInt() }
        if (ml == null || ml !in 1..BodyRepository.MAX_WATER_ADD_ML) {
            form.update { it.copy(second = "Indique une quantité en centilitres, entre 1 et 300.") }
            return
        }
        add(ml)
        form.value = Pair("", null)
    }
}
