package com.bidaboss.healthy.meals.data

import com.bidaboss.healthy.core.importformat.Confidence
import com.bidaboss.healthy.core.importformat.DrinkKind
import com.bidaboss.healthy.core.importformat.FoodSource
import com.bidaboss.healthy.core.importformat.ImportedFood
import com.bidaboss.healthy.core.importformat.ImportedMeal
import com.bidaboss.healthy.core.importformat.MealWriter
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.data.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Un repas tel que l'interface l'affiche (aucune classe Room ne sort d'ici). */
data class Meal(
    val id: Long,
    val type: MealType,
    val title: String?,
    val notes: String?,
    val eatenAt: LocalDateTime,
    val localDate: LocalDate,
    val foods: List<Food>,
    val isFavorite: Boolean = false,
) {
    val totalKcal: Double get() = foods.sumOf { it.kcal }

    /** Repas noté en calories seules : aucun aliment n'a de protéines, glucides, lipides ni fibres. */
    val isCaloriesOnly: Boolean
        get() = foods.isNotEmpty() && foods.all { it.proteinG == null && it.carbsG == null && it.fatG == null && it.fiberG == null }
    val totalProteinG: Double get() = foods.sumOf { it.proteinG ?: 0.0 }
    val totalCarbsG: Double get() = foods.sumOf { it.carbsG ?: 0.0 }
    val totalFatG: Double get() = foods.sumOf { it.fatG ?: 0.0 }
    val totalFiberG: Double get() = foods.sumOf { it.fiberG ?: 0.0 }
}

data class Food(
    val name: String,
    val quantityG: Double? = null,
    val portion: String? = null,
    val kcal: Double,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val source: FoodSource = FoodSource.MANUAL,
    val confidence: Confidence? = null,
    val assumptions: String? = null,
    val offCode: String? = null,
    /** null = aliment ordinaire ; une boisson avec quantité compte dans l'eau bue, l'alcool jamais. */
    val drink: DrinkKind? = null,
)

/** Où un aliment ajouté en un geste est allé : [type] du repas, [createdMeal] s'il a fallu le créer. */
data class AddedFood(val mealId: Long, val type: MealType, val itemId: Long?, val createdMeal: Boolean)

/** Type de repas proposé selon l'heure. */
fun defaultTypeFor(time: LocalTime): MealType = when (time.hour) {
    in 5..10 -> MealType.BREAKFAST
    in 11..14 -> MealType.LUNCH
    in 18..22 -> MealType.DINNER
    else -> MealType.SNACK
}

/**
 * Boissons caloriques en un geste (fil Nutrition, 13/11/2026). Valeurs moyennes, source manuelle.
 * Le soda et le jus sont des boissons (elles comptent dans l'eau bue), la bière et le vin de l'alcool (jamais).
 */
enum class Drink(
    val label: String,
    val volumeMl: Int,
    val kcal: Double,
    val carbsG: Double,
    val kind: DrinkKind,
    val proteinG: Double = 0.0,
) {
    BEER("Bière", 250, 105.0, 9.0, DrinkKind.ALCOHOL),
    WINE("Vin", 120, 85.0, 3.0, DrinkKind.ALCOHOL),
    SODA("Soda", 330, 140.0, 35.0, DrinkKind.DRINK),
    JUICE("Jus de fruits", 200, 90.0, 20.0, DrinkKind.DRINK, proteinG = 1.0),
    ;

    /** « 25 cL » */
    val volumeLabel: String get() = "${volumeMl / 10} cL"

    fun toFood() = Food(
        name = label,
        quantityG = volumeMl.toDouble(),
        portion = volumeLabel,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = 0.0,
        fiberG = 0.0,
        source = FoodSource.MANUAL,
        drink = kind,
    )
}

/** Repas à créer (id nul) ou à modifier, saisi dans l'app. */
data class MealDraft(
    val id: Long?,
    val type: MealType,
    val eatenAt: LocalDateTime,
    val title: String?,
    val notes: String?,
    val foods: List<Food>,
)

class MealRepository(
    private val mealDao: MealDao,
    private val transactions: TransactionRunner,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    /** « Déjà mangés » : les aliments déjà notés, regroupés, mis à jour à chaque repas enregistré. */
    fun observeFoodHistory(now: () -> Long = System::currentTimeMillis): Flow<List<HistoryFood>> =
        mealDao.observeFoodHistory(HISTORY_ROWS).map { FoodHistory.build(it, now()) }

    /** Les repas de deux dates incluses, du plus ancien au plus récent. */
    fun observeMealsBetween(from: LocalDate, to: LocalDate): Flow<List<Meal>> =
        mealDao.observeMealsBetween(from.toString(), to.toString()).map { rows -> rows.map { it.toMeal() } }

    fun observeMeals(date: LocalDate): Flow<List<Meal>> =
        mealDao.observeMealsForDate(date.toString()).map { rows -> rows.map { it.toMeal() } }

    /** Jours où au moins un repas est noté (pour les badges). */
    fun observeMealDates(): Flow<Set<LocalDate>> =
        mealDao.observeMealDates().map { dates -> dates.mapTo(HashSet()) { LocalDate.parse(it) } }

    /** Calories mangées par jour entre deux dates incluses ; un jour sans repas est absent. */
    fun observeDailyKcal(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Double>> =
        mealDao.observeDailyKcal(from.toString(), to.toString()).map { rows ->
            rows.associate { LocalDate.parse(it.localDate) to it.kcal }
        }

    /** Met un repas en favori, ou l'enlève des favoris. */
    suspend fun setFavorite(mealId: Long, favorite: Boolean) = mealDao.setFavorite(mealId, favorite)

    /** Repas en favori, du plus récent au plus ancien. */
    fun observeFavorites(): Flow<List<Meal>> =
        mealDao.observeFavorites().map { rows -> rows.map { it.toMeal() }.distinctBy { it.sameAsKey() } }

    /**
     * Derniers repas différents (même nom et mêmes aliments = un seul), hors favoris déjà proposés.
     * Sert à « Récents » dans l'ajout d'un repas.
     */
    fun observeRecentMeals(limit: Int = RECENT_LIMIT): Flow<List<Meal>> =
        mealDao.observeLatestMeals(RECENT_SCAN).map { rows ->
            rows.map { it.toMeal() }
                .filterNot { it.isFavorite }
                .distinctBy { it.sameAsKey() }
                .take(limit)
        }

    private fun Meal.sameAsKey() = (title?.trim()?.lowercase() to foods.map { it.name.trim().lowercase() })

    /** Repas entre deux jours inclus, du plus ancien au plus récent. */
    suspend fun getMealsBetween(from: LocalDate, to: LocalDate): List<Meal> =
        mealDao.getMealsBetween(from.toString(), to.toString()).map { it.toMeal() }

    /**
     * Ajoute une boisson au repas en cours : le dernier repas d'aujourd'hui du type de l'heure actuelle,
     * commencé il y a moins de [CURRENT_MEAL_HOURS] heures.
     * Sans repas en cours, crée une collation à l'heure actuelle. Rend l'id du repas complété.
     */
    suspend fun addDrink(drink: Drink, now: LocalDateTime): Long =
        addToCurrentMeal(drink.toFood(), now, newMealType = MealType.SNACK).mealId

    /**
     * Même règle que les boissons pour un aliment ajouté en un geste (scan) : au repas en cours, sinon un nouveau
     * repas du type de l'heure. Rend de quoi annuler ([undoAdd]).
     */
    suspend fun addFoodToCurrentMeal(food: Food, now: LocalDateTime): AddedFood =
        addToCurrentMeal(food, now, newMealType = defaultTypeFor(now.toLocalTime()))

    private suspend fun addToCurrentMeal(food: Food, now: LocalDateTime, newMealType: MealType): AddedFood =
        transactions.inTransaction {
            val type = defaultTypeFor(now.toLocalTime())
            val current = mealDao.getMealsBetween(now.toLocalDate().toString(), now.toLocalDate().toString())
                .lastOrNull { row ->
                    val eatenAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(row.meal.eatenAtEpochMs), zone())
                    row.meal.mealType == type.code && !eatenAt.isAfter(now) && eatenAt.isAfter(now.minusHours(CURRENT_MEAL_HOURS))
                }
            if (current != null) {
                val position = (current.items.maxOfOrNull { it.position } ?: -1) + 1
                val itemId = mealDao.insertItem(food.toEntity(current.meal.id, position))
                AddedFood(current.meal.id, type, itemId, createdMeal = false)
            } else {
                val mealId = saveMeal(MealDraft(null, newMealType, now, null, null, listOf(food)))
                AddedFood(mealId, newMealType, itemId = null, createdMeal = true)
            }
        }

    /** Annule un ajout en un geste : retire l'aliment, ou le repas qu'il a créé. */
    suspend fun undoAdd(added: AddedFood) = transactions.inTransaction {
        if (added.createdMeal) {
            mealDao.deleteItems(added.mealId)
            mealDao.deleteMeal(added.mealId)
        } else {
            added.itemId?.let { mealDao.deleteItem(it) }
        }
    }

    /**
     * « Partager ce repas » : le repas enregistré en fichier d'import `meal` (nouvel [id], sans `replaces`),
     * qu'un proche importe tel quel. null si le repas n'existe plus.
     */
    suspend fun shareMeal(mealId: Long, id: String, createdAt: OffsetDateTime): String? {
        val meal = getMeal(mealId) ?: return null
        val imported = ImportedMeal(
            eatenAt = meal.eatenAt.atZone(zone()).toOffsetDateTime(),
            type = meal.type,
            title = meal.title,
            notes = meal.notes,
            items = meal.foods.map {
                ImportedFood(
                    it.name, it.quantityG, it.portion, it.kcal, it.proteinG, it.carbsG, it.fatG, it.fiberG,
                    it.source, it.confidence, it.assumptions, it.offCode, it.drink,
                )
            },
        )
        return MealWriter.write(imported, id, createdAt)
    }

    suspend fun getMeal(mealId: Long): Meal? = mealDao.getMeal(mealId)?.toMeal()

    suspend fun findImportedMeal(importId: String): Meal? = mealDao.getMealByImportId(importId)?.toMeal()

    /**
     * Supprime un repas à la demande de l'utilisateur. Si le repas venait d'un import, on oublie aussi
     * cet import : le même fichier peut alors être réimporté.
     */
    suspend fun deleteMeal(mealId: Long) = transactions.inTransaction {
        val importId = mealDao.getMeal(mealId)?.meal?.importId
        mealDao.deleteItems(mealId)
        mealDao.deleteMeal(mealId)
        if (importId != null) mealDao.deleteImportRecord(importId)
    }

    /** Crée ou met à jour un repas saisi à la main. Renvoie son identifiant. */
    suspend fun saveMeal(draft: MealDraft, nowEpochMs: Long = System.currentTimeMillis()): Long = transactions.inTransaction {
        val eatenAtEpochMs = draft.eatenAt.atZone(zone()).toInstant().toEpochMilli()
        val existing = draft.id?.let { mealDao.getMeal(it) }?.meal
        val mealId = if (existing == null) {
            mealDao.insertMeal(
                MealEntity(
                    importId = null,
                    eatenAtEpochMs = eatenAtEpochMs,
                    localDate = draft.eatenAt.toLocalDate().toString(),
                    mealType = draft.type.code,
                    title = draft.title,
                    notes = draft.notes,
                    createdAtEpochMs = nowEpochMs,
                ),
            )
        } else {
            mealDao.updateMeal(
                existing.copy(
                    eatenAtEpochMs = eatenAtEpochMs,
                    localDate = draft.eatenAt.toLocalDate().toString(),
                    mealType = draft.type.code,
                    title = draft.title,
                    notes = draft.notes,
                ),
            )
            mealDao.deleteItems(existing.id)
            existing.id
        }
        mealDao.insertItems(draft.foods.mapIndexed { index, food -> food.toEntity(mealId, index) })
        mealId
    }

    /** À appeler dans une transaction (voir ImportRepository). */
    suspend fun insertImportedMeal(importId: String, meal: ImportedMeal, nowEpochMs: Long, favorite: Boolean = false) {
        val mealId = mealDao.insertMeal(
            MealEntity(
                importId = importId,
                eatenAtEpochMs = meal.eatenAt.toInstant().toEpochMilli(),
                // Règle unique : le jour du journal est toujours la date locale du téléphone.
                localDate = meal.eatenAt.atZoneSameInstant(zone()).toLocalDate().toString(),
                mealType = meal.type.code,
                title = meal.title,
                notes = meal.notes,
                createdAtEpochMs = nowEpochMs,
                isFavorite = favorite,
            ),
        )
        mealDao.insertItems(
            meal.items.mapIndexed { index, food ->
                Food(
                    name = food.name,
                    quantityG = food.quantityG,
                    portion = food.portion,
                    kcal = food.kcal,
                    proteinG = food.proteinG,
                    carbsG = food.carbsG,
                    fatG = food.fatG,
                    fiberG = food.fiberG,
                    source = food.source,
                    confidence = food.confidence,
                    assumptions = food.assumptions,
                    offCode = food.offCode,
                    drink = food.drink,
                ).toEntity(mealId, index)
            },
        )
    }

    /** À appeler dans une transaction. Renvoie true si un repas a été supprimé. */
    suspend fun deleteImportedMeal(importId: String): Boolean {
        val meal = mealDao.getMealByImportId(importId)?.meal ?: return false
        mealDao.deleteItems(meal.id)
        mealDao.deleteMeal(meal.id)
        return true
    }

    companion object {
        const val RECENT_LIMIT = 6

        /** Un repas commencé il y a moins de 3 h est encore « en cours » pour les boissons. */
        const val CURRENT_MEAL_HOURS = 3L

        /** Nombre de lignes d'aliments lues pour « Déjà mangés » (plus d'un an d'historique courant). */
        const val HISTORY_ROWS = 3000

        /** Nombre de repas lus pour trouver les récents différents. */
        private const val RECENT_SCAN = 40
    }

    private fun Food.toEntity(mealId: Long, position: Int) = MealItemEntity(
        mealId = mealId,
        position = position,
        name = name,
        quantityG = quantityG,
        portion = portion,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        fiberG = fiberG,
        source = source.code,
        confidence = confidence?.code,
        assumptions = assumptions,
        offCode = offCode,
        drink = drink?.code,
    )

    private fun MealWithItems.toMeal() = Meal(
        id = meal.id,
        type = MealType.fromCode(meal.mealType) ?: MealType.SNACK,
        title = meal.title,
        notes = meal.notes,
        eatenAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(meal.eatenAtEpochMs), zone()),
        localDate = LocalDate.parse(meal.localDate),
        isFavorite = meal.isFavorite,
        foods = items.sortedBy { it.position }.map {
            Food(
                name = it.name,
                quantityG = it.quantityG,
                portion = it.portion,
                kcal = it.kcal,
                proteinG = it.proteinG,
                carbsG = it.carbsG,
                fatG = it.fatG,
                fiberG = it.fiberG,
                source = FoodSource.fromCode(it.source) ?: FoodSource.MANUAL,
                confidence = it.confidence?.let(Confidence::fromCode),
                assumptions = it.assumptions,
                offCode = it.offCode,
                drink = it.drink?.let(DrinkKind::fromCode),
            )
        },
    )
}
