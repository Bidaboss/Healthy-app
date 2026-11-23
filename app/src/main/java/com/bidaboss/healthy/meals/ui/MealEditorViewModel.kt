package com.bidaboss.healthy.meals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.importformat.Confidence
import com.bidaboss.healthy.core.importformat.DrinkKind
import com.bidaboss.healthy.core.importformat.FoodSource
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.HabitualMeals
import com.bidaboss.healthy.meals.data.Meal
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.meals.data.SavedFoodRepository
import com.bidaboss.healthy.meals.data.SharePortion
import com.bidaboss.healthy.meals.data.FoodHistory
import com.bidaboss.healthy.meals.data.defaultTypeFor
import com.bidaboss.healthy.ui.parseNumber
import com.bidaboss.healthy.ui.toInputText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Une ligne d'aliment en cours de saisie : les nombres restent du texte jusqu'à l'enregistrement. */
data class FoodInput(
    val key: Long,
    val name: String = "",
    val quantityG: String = "",
    val kcal: String = "",
    val proteinG: String = "",
    val carbsG: String = "",
    val fatG: String = "",
    val fiberG: String = "",
    /** Réglage Aliment (null) / Boisson / Alcool : une boisson avec quantité compte dans l'eau bue. */
    val drink: DrinkKind? = null,
    /** Aliment d'origine (import ou Open Food Facts), pour conserver ses informations cachées. */
    val original: Food? = null,
    /** Valeurs connues pour une quantité donnée : sert à recalculer quand la quantité change. */
    val reference: FoodReference? = null,
)

/** Les valeurs d'un aliment pour [quantityG] grammes. Une valeur null = champ vide, jamais recalculé. */
data class FoodReference(
    val quantityG: Double,
    val kcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
)

/**
 * L'utilisateur change la quantité : calories, protéines, glucides, lipides et fibres suivent en
 * proportion. Le calcul part toujours de la référence, pas des valeurs affichées, pour ne pas
 * accumuler les arrondis. Sans référence, rien ne bouge. Pendant que la quantité est vide ou illisible,
 * on réaffiche les valeurs de la référence : sinon les valeurs calculées pour un chiffre tapé en passant
 * (« 1 » en effaçant « 150 ») resteraient à l'écran et seraient enregistrées.
 */
fun FoodInput.withQuantity(text: String): FoodInput {
    val quantity = parseNumber(text)
    val ref = reference ?: return copy(quantityG = text)
    val factor = if (quantity == null || quantity <= 0) 1.0 else quantity / ref.quantityG
    fun scaled(value: Double?, current: String): String =
        value?.let { (Math.round(it * factor * 10) / 10.0).toInputText() } ?: current
    return copy(
        quantityG = text,
        kcal = scaled(ref.kcal, kcal),
        proteinG = scaled(ref.proteinG, proteinG),
        carbsG = scaled(ref.carbsG, carbsG),
        fatG = scaled(ref.fatG, fatG),
        fiberG = scaled(ref.fiberG, fiberG),
    )
}

/**
 * Boutons − et + de la quantité : 10 g de plus ou de moins (5 g sous 20 g), jamais moins de 1 g. Les calories et le
 * reste suivent en proportion. Sans quantité lisible, rien ne bouge (on ne devine pas une quantité).
 */
fun FoodInput.stepped(direction: Int): FoodInput {
    val quantity = parseNumber(quantityG)?.takeIf { it > 0 } ?: return this
    val step = if (quantity < 20) 5.0 else 10.0
    val next = (quantity + direction * step).coerceAtLeast(1.0)
    val base = if (reference == null) rebased() else this
    return base.withQuantity(next.toInputText())
}

/** Portions proposées en un geste dans l'éditeur (fil Nutrition, 12/11/2026). */
enum class Portion(val label: String, val factor: Double) {
    HALF("½", 0.5),
    THREE_QUARTERS("¾", 0.75),
    ONE_AND_HALF("×1,5", 1.5),
}

/**
 * Multiplie la portion par [factor]. Avec une quantité : elle est multipliée (au gramme près dès 10 g)
 * et le recalcul proportionnel habituel suit. Sans quantité : les valeurs sont multipliées directement.
 */
fun FoodInput.scaledBy(factor: Double): FoodInput {
    val quantity = parseNumber(quantityG)?.takeIf { it > 0 }
    if (quantity != null) {
        val raw = quantity * factor
        val rounded = if (raw >= 10) Math.round(raw).toDouble() else Math.round(raw * 10) / 10.0
        val base = if (reference == null) rebased() else this
        return base.withQuantity(rounded.toInputText())
    }
    fun scaled(text: String): String =
        parseNumber(text)?.let { (Math.round(it * factor * 10) / 10.0).toInputText() } ?: text
    return copy(
        kcal = scaled(kcal),
        proteinG = scaled(proteinG),
        carbsG = scaled(carbsG),
        fatG = scaled(fatG),
        fiberG = scaled(fiberG),
    )
}

/**
 * À appeler après une correction à la main d'une valeur (calories, protéines…) : les valeurs
 * affichées deviennent la nouvelle référence pour la quantité affichée.
 */
fun FoodInput.rebased(): FoodInput {
    val quantity = parseNumber(quantityG)?.takeIf { it > 0 } ?: return copy(reference = null)
    return copy(
        reference = FoodReference(
            quantityG = quantity,
            kcal = parseNumber(kcal),
            proteinG = parseNumber(proteinG),
            carbsG = parseNumber(carbsG),
            fatG = parseNumber(fatG),
            fiberG = parseNumber(fiberG),
        ),
    )
}

data class MealEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val type: MealType = MealType.LUNCH,
    val eatenAt: LocalDateTime = LocalDateTime.now(),
    val title: String = "",
    val foods: List<FoodInput> = emptyList(),
    val errors: List<String> = emptyList(),
    val saving: Boolean = false,
    /** Vrai quand le repas est enregistré ou supprimé : l'écran se ferme. */
    val finished: Boolean = false,
    /** Proposés à l'ajout d'un repas : un geste remplit le repas avec les mêmes aliments. */
    val favorites: List<Meal> = emptyList(),
    val recents: List<Meal> = emptyList(),
    /** Repas habituels à cette heure-ci (jusqu'à 3), pour un nouveau repas. */
    val suggestions: List<Meal> = emptyList(),
    /** Vrai quand le repas affiché est une copie (« Refaire ce repas »), pas encore enregistrée. */
    val isCopy: Boolean = false,
) {
    val totalKcal: Double get() = foods.sumOf { parseNumber(it.kcal) ?: 0.0 }
}

class MealEditorViewModel(
    private val mealRepository: MealRepository,
    mealId: Long?,
    initialDate: LocalDate,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    /** « Mes aliments » (marque-page) ; null dans les tests qui n'en ont pas besoin. */
    private val savedFoods: SavedFoodRepository? = null,
) : ViewModel() {

    /** Noms réduits des aliments déjà dans « Mes aliments » : le marque-page est plein pour eux. */
    val savedKeys: StateFlow<Set<String>> = (savedFoods?.observeKeys() ?: flowOf(emptySet()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Marque-page d'une ligne : l'ajoute à « Mes aliments » avec ses valeurs actuelles, ou l'en retire. */
    fun toggleSaved(input: FoodInput) {
        val food = input.toFood(1, mutableListOf()) ?: return
        viewModelScope.launch { savedFoods?.toggle(food) }
    }

    /** Repas modifié ; null = nouvel enregistrement (nouveau repas ou copie). */
    private var mealId: Long? = mealId

    private var nextKey = 1L
    private var notes: String? = null

    private val _uiState = MutableStateFlow(MealEditorUiState())
    val uiState: StateFlow<MealEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val meal = mealId?.let { mealRepository.getMeal(it) }
            val loaded = if (meal == null) {
                val time = now().toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                MealEditorUiState(
                    loading = false,
                    isNew = true,
                    type = defaultTypeFor(time),
                    eatenAt = LocalDateTime.of(initialDate, time),
                    foods = listOf(FoodInput(key = nextKey++)),
                )
            } else {
                notes = meal.notes
                MealEditorUiState(
                    loading = false,
                    isNew = false,
                    type = meal.type,
                    eatenAt = meal.eatenAt,
                    title = meal.title.orEmpty(),
                    foods = meal.foods.map { it.toInput() },
                )
            }
            // Garde les favoris et récents s'ils sont arrivés avant le repas.
            _uiState.update { current -> loaded.copy(favorites = current.favorites, recents = current.recents, suggestions = current.suggestions) }
        }
        if (mealId == null) {
            viewModelScope.launch {
                val at = now()
                val day = at.toLocalDate()
                val all = mealRepository.observeMealsBetween(day.minusDays(HabitualMeals.RECENT_DAYS), day).first()
                val habitual = HabitualMeals.suggest(all, all.filter { it.localDate == day }, at, skipTypesNotedToday = false)
                _uiState.update { it.copy(suggestions = habitual.mapIndexed { i, h -> h.toMeal(i, at) }) }
            }
        }
        viewModelScope.launch {
            combine(mealRepository.observeFavorites(), mealRepository.observeRecentMeals()) { favorites, recents ->
                favorites to recents
            }.collect { (favorites, recents) -> _uiState.update { it.copy(favorites = favorites, recents = recents) } }
        }
    }

    /**
     * « Refaire ce repas » : le repas affiché devient un nouveau repas, aujourd'hui à l'heure actuelle,
     * type déduit de l'heure (modifiable). Rien n'est enregistré avant « Enregistrer ».
     */
    fun redo() {
        if (_uiState.value.loading || _uiState.value.saving) return
        mealId = null
        notes = null
        val at = now().truncatedTo(ChronoUnit.MINUTES)
        _uiState.update {
            it.copy(isNew = true, isCopy = true, type = defaultTypeFor(at.toLocalTime()), eatenAt = at, errors = emptyList())
        }
    }

    private val _mealToShare = MutableStateFlow<String?>(null)

    /** Repas prêt à partager (fichier d'import JSON), ou null. L'écran le remet à null une fois le partage ouvert. */
    val mealToShare: StateFlow<String?> = _mealToShare.asStateFlow()

    /** « Partager ce repas » : la version enregistrée, à la portion de l'autre personne, en fichier qu'un proche peut importer. */
    fun share(portion: SharePortion) {
        val id = mealId ?: return
        viewModelScope.launch {
            _mealToShare.value = mealRepository.shareMeal(
                id, UUID.randomUUID().toString(), now().atZone(ZoneId.systemDefault()).toOffsetDateTime(), portion,
            )
        }
    }

    fun onMealShared() {
        _mealToShare.value = null
    }

    /** Remplit le nouveau repas avec un favori ou un récent (nom et aliments ; jour, heure et type gardés). */
    fun fillFrom(meal: Meal) = _uiState.update { state ->
        state.copy(title = meal.title.orEmpty(), foods = meal.foods.map { it.toInput() }, errors = emptyList())
    }

    fun setType(type: MealType) = _uiState.update { it.copy(type = type) }

    fun setEatenAt(eatenAt: LocalDateTime) = _uiState.update { it.copy(eatenAt = eatenAt) }

    fun setTitle(title: String) = _uiState.update { it.copy(title = title) }

    fun addFood() = _uiState.update { it.copy(foods = it.foods + FoodInput(key = nextKey++)) }

    /** Ajoute un aliment trouvé dans Open Food Facts (remplace la première ligne si elle est vide). */
    fun addFoundFood(food: Food) = _uiState.update { state ->
        state.copy(foods = state.foods.filterNot { it.isBlank() } + food.toInput())
    }

    /**
     * Repas en calories seules (restaurant…) : un seul aliment, voir [caloriesOnlyFood]. Rend le message d'erreur,
     * ou null si c'est fait.
     */
    fun addCaloriesOnly(name: String, kcalText: String): String? {
        val (food, error) = caloriesOnlyFood(name, kcalText)
        if (food == null) return error
        addFoundFood(food)
        return null
    }

    fun removeFood(key: Long) = _uiState.update { state -> state.copy(foods = state.foods.filterNot { it.key == key }) }

    fun updateFood(food: FoodInput) = _uiState.update { state ->
        state.copy(foods = state.foods.map { if (it.key == food.key) food else it })
    }

    fun save() {
        val state = _uiState.value
        if (state.saving || state.loading) return
        val errors = mutableListOf<String>()
        val inputs = state.foods.filterNot { it.isBlank() }
        if (inputs.isEmpty()) errors += "Ajoute au moins un aliment."
        val foods = inputs.mapIndexedNotNull { index, input -> input.toFood(index + 1, errors) }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        _uiState.update { it.copy(saving = true, errors = emptyList()) }
        viewModelScope.launch {
            try {
                mealRepository.saveMeal(
                    MealDraft(
                        id = mealId,
                        type = state.type,
                        eatenAt = state.eatenAt,
                        title = state.title.trim().takeIf { it.isNotEmpty() },
                        notes = notes,
                        foods = foods,
                    ),
                )
                _uiState.update { it.copy(saving = false, finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, errors = listOf("L'enregistrement a échoué. Réessaie.")) }
            }
        }
    }

    fun delete() {
        val id = mealId ?: return
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                mealRepository.deleteMeal(id)
                _uiState.update { it.copy(saving = false, finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, errors = listOf("La suppression a échoué. Réessaie.")) }
            }
        }
    }

    private fun Food.toInput() = toInput(nextKey++)
}

/**
 * Aliment « juste les calories » : source manuelle, protéines, glucides, lipides et fibres **vides** (jamais estimés
 * ni mis à zéro), avec la pastille « à vérifier » (le moindre ajout de valeur à la main la retire). Rend l'aliment,
 * ou le message d'erreur.
 */
internal fun caloriesOnlyFood(name: String, kcalText: String): Pair<Food?, String?> {
    val kcal = parseNumber(kcalText)
    if (kcal == null || !kcal.isFinite() || kcal <= 0 || kcal > MAX_FOOD_VALUE) {
        return null to "Indique un nombre de calories entre 1 et $MAX_FOOD_VALUE."
    }
    val food = Food(
        name = name.trim().ifEmpty { CALORIES_ONLY_NAME },
        kcal = kcal,
        source = FoodSource.MANUAL,
        confidence = Confidence.LOW,
    )
    return food to null
}

/** Nom d'un aliment pour comparer avec « Mes aliments » (minuscules, sans accents). */
internal fun savedKeyOf(name: String): String = FoodHistory.normalize(name)

/** Nom proposé pour un repas noté en calories seules. */
internal const val CALORIES_ONLY_NAME = "Repas au restaurant"

/** Plus grande valeur acceptée pour un champ d'aliment. */
internal const val MAX_FOOD_VALUE = 5000

/** Un aliment enregistré, en ligne de saisie (gardé en `original` pour ses informations cachées). */
internal fun Food.toInput(key: Long) = FoodInput(
    key = key,
    name = name,
    quantityG = quantityG.toInputText(),
    kcal = kcal.toInputText(),
    proteinG = proteinG.toInputText(),
    carbsG = carbsG.toInputText(),
    fatG = fatG.toInputText(),
    fiberG = fiberG.toInputText(),
    drink = drink,
    original = this,
).rebased()

internal fun FoodInput.isBlank() =
    listOf(name, quantityG, kcal, proteinG, carbsG, fatG, fiberG).all { it.isBlank() }

/** Valide une ligne de saisie ; ajoute les erreurs à [errors] et rend null si elle est invalide. */
internal fun FoodInput.toFood(position: Int, errors: MutableList<String>): Food? {
    val start = errors.size
    val label = "Aliment n°$position"
    val cleanName = name.trim()
    if (cleanName.isEmpty()) errors += "$label : indique un nom."
    if (kcal.isBlank()) errors += "$label : indique les calories."

    fun number(text: String, field: String): Double? {
        if (text.isBlank()) return null
        val value = parseNumber(text)
        if (value == null || !value.isFinite() || value < 0 || value > MAX_FOOD_VALUE) {
            errors += "$label : « $field » doit être un nombre entre 0 et $MAX_FOOD_VALUE."
            return null
        }
        return value
    }

    val kcalValue = number(kcal, "calories")
    val quantity = number(quantityG, "quantité")
    val protein = number(proteinG, "protéines")
    val carbs = number(carbsG, "glucides")
    val fat = number(fatG, "lipides")
    val fiber = number(fiberG, "fibres")
    if (errors.size > start || kcalValue == null) return null

    val edited = (original ?: Food(name = cleanName, kcal = kcalValue)).copy(
        name = cleanName,
        quantityG = quantity,
        kcal = kcalValue,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat,
        fiberG = fiber,
        drink = drink,
    )
    // Un aliment corrigé à la main n'est plus une estimation : sa source devient « manuel ».
    // Changer le réglage Aliment / Boisson / Alcool n'est pas une correction des valeurs.
    return if (original != null && edited.copy(drink = original.drink) != original) {
        edited.copy(source = FoodSource.MANUAL, confidence = null)
    } else {
        edited
    }
}
