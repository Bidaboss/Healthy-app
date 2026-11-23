package com.bidaboss.healthy.meals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.meals.data.AddedFood
import com.bidaboss.healthy.meals.data.Drink
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.Meal
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.meals.data.SavedFoodRepository
import com.bidaboss.healthy.meals.data.SharePortion
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

data class MealsUiState(
    val date: LocalDate,
    val isToday: Boolean = true,
    val groups: List<MealGroup> = emptyList(),
    val totalKcal: Double = 0.0,
    /** Maintien du jour, null sans profil : sert à la petite barre du total en haut. */
    val maintenanceKcal: Int? = null,
    /** Repas du jour notés en calories seules : leurs protéines et fibres ne sont pas comptées. */
    val caloriesOnlyMeals: Int = 0,
)

data class MealGroup(val type: MealType, val meals: List<Meal>)

@OptIn(ExperimentalCoroutinesApi::class)
class MealsViewModel(
    private val mealRepository: MealRepository,
    /** Maintien d'un jour, null sans profil (fourni par AppContainer). */
    maintenanceKcal: (LocalDate) -> Flow<Int?>,
    /** Bilan repas (fourni par AppContainer : il lit aussi l'eau et le profil). */
    private val exportMealReport: suspend (today: LocalDate, id: String, now: LocalDateTime) -> String,
    /** « Mes aliments » (marque-page des aliments d'un repas) ; null dans les tests qui n'en ont pas besoin. */
    private val savedFoods: SavedFoodRepository? = null,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    /** Noms réduits des aliments déjà dans « Mes aliments » : le marque-page est plein pour eux. */
    val savedKeys: StateFlow<Set<String>> = (savedFoods?.observeKeys() ?: flowOf(emptySet()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleSaved(food: Food) {
        viewModelScope.launch { savedFoods?.toggle(food) }
    }

    private val _mealToShare = MutableStateFlow<String?>(null)

    /** Repas prêt à partager (fichier d'import JSON à la portion choisie), ou null. L'écran le remet à null une fois le partage ouvert. */
    val mealToShare: StateFlow<String?> = _mealToShare.asStateFlow()

    /** « Partager ce repas » : la portion de l'autre personne, en fichier qu'il importe dans son app. */
    fun shareMeal(meal: Meal, portion: SharePortion) {
        viewModelScope.launch {
            _mealToShare.value = mealRepository.shareMeal(
                meal.id, UUID.randomUUID().toString(), now().atZone(ZoneId.systemDefault()).toOffsetDateTime(), portion,
            )
        }
    }

    fun onMealShared() {
        _mealToShare.value = null
    }

    private val _reportToShare = MutableStateFlow<String?>(null)

    /** Bilan prêt à être partagé (texte JSON), ou null. L'écran le remet à null une fois le partage ouvert. */
    val reportToShare: StateFlow<String?> = _reportToShare.asStateFlow()

    /** Prépare le bilan repas des 7 derniers jours pour l'envoyer à Claude. */
    fun exportReport() {
        viewModelScope.launch { _reportToShare.value = exportMealReport(today(), UUID.randomUUID().toString(), now()) }
    }

    fun onReportShared() {
        _reportToShare.value = null
    }

    private val selectedDate = MutableStateFlow(today())

    /** Vrai tant que l'utilisateur regarde « aujourd'hui » : la date suit alors le calendrier. */
    private var followsToday = true

    val uiState: StateFlow<MealsUiState> = selectedDate
        .flatMapLatest { date ->
            combine(
                mealRepository.observeMeals(date),
                maintenanceKcal(date),
            ) { meals, maintenance ->
                MealsUiState(
                    date = date,
                    isToday = date == today(),
                    groups = MealType.entries.mapNotNull { type ->
                        meals.filter { it.type == type }.takeIf { it.isNotEmpty() }?.let { MealGroup(type, it) }
                    },
                    totalKcal = meals.sumOf { it.totalKcal },
                    maintenanceKcal = maintenance,
                    caloriesOnlyMeals = meals.count { it.isCaloriesOnly },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealsUiState(date = selectedDate.value))

    /** À chaque retour sur l'écran : si minuit est passé, on bascule sur le nouveau jour. */
    fun onResume() {
        if (followsToday) selectedDate.value = today()
    }

    fun previousDay() = select(selectedDate.value.minusDays(1))

    fun nextDay() = select(selectedDate.value.plusDays(1))

    fun goToToday() = select(today())

    /** Boisson en un geste : au repas en cours, sinon une collation maintenant. */
    fun addDrink(drink: Drink) {
        viewModelScope.launch { mealRepository.addDrink(drink, now()) }
    }

    private val _lastAdded = MutableStateFlow<AddedFood?>(null)

    /** Dernier aliment scanné ajouté en un geste : l'écran le confirme (« Ajouté au déjeuner ») avec « Annuler ». */
    val lastAdded: StateFlow<AddedFood?> = _lastAdded.asStateFlow()

    /** Aliment ajouté en un geste (scan, recherche, favori…) : au repas en cours, sinon un nouveau repas du type de l'heure. */
    fun addFood(food: Food) {
        viewModelScope.launch { _lastAdded.value = mealRepository.addFoodToCurrentMeal(food, now()) }
    }

    /** « Saisie rapide » : juste les calories. Rend le message d'erreur, ou null si c'est ajouté. */
    fun addCaloriesOnly(name: String, kcalText: String): String? {
        val (food, error) = caloriesOnlyFood(name, kcalText)
        if (food == null) return error
        addFood(food)
        return null
    }

    fun undoLastAdded() {
        val added = _lastAdded.value ?: return
        _lastAdded.value = null
        viewModelScope.launch { mealRepository.undoAdd(added) }
    }

    /** Le message a été montré : on n'y revient pas. */
    fun onAddedShown() {
        _lastAdded.value = null
    }

    fun toggleFavorite(meal: Meal) {
        viewModelScope.launch { mealRepository.setFavorite(meal.id, !meal.isFavorite) }
    }

    private fun select(date: LocalDate) {
        followsToday = date == today()
        selectedDate.value = date
    }
}
