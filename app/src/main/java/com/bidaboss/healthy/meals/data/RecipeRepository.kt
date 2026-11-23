package com.bidaboss.healthy.meals.data

import com.bidaboss.healthy.core.importformat.FoodSource
import com.bidaboss.healthy.data.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/** Un plat de « Mes plats », tel que l'interface l'affiche. */
data class Recipe(
    val id: Long,
    val name: String,
    val cookedWeightG: Double,
    val servings: Int?,
    val ingredients: List<Food>,
) {
    val totalKcal: Double get() = ingredients.sumOf { it.kcal }

    /** Poids d'une part, ou null sans nombre de parts. */
    val servingWeightG: Double? get() = servings?.takeIf { it > 0 }?.let { cookedWeightG / it }

    /**
     * Le plat en aliment de repas, au prorata de [grams] sur le poids cuit. Source manuelle : ce sont les
     * valeurs de la recette, pas une estimation.
     */
    fun portion(grams: Double): Food {
        val factor = grams / cookedWeightG
        fun sum(value: (Food) -> Double?): Double? =
            ingredients.mapNotNull(value).takeIf { it.isNotEmpty() }?.sum()?.let { round1(it * factor) }
        return Food(
            name = name,
            quantityG = round1(grams),
            portion = servingWeightG?.let { w -> partsLabel(grams / w) },
            kcal = round1(totalKcal * factor),
            proteinG = sum { it.proteinG },
            carbsG = sum { it.carbsG },
            fatG = sum { it.fatG },
            fiberG = sum { it.fiberG },
            source = FoodSource.MANUAL,
        )
    }

    private fun round1(value: Double) = Math.round(value * 10) / 10.0

    /** « 1 part », « 1,5 part », « 2 parts » (au quart près). */
    private fun partsLabel(parts: Double): String {
        val quarter = (parts * 4).roundToInt() / 4.0
        val text = if (quarter == Math.floor(quarter)) quarter.toLong().toString() else quarter.toString().replace('.', ',')
        return "$text ${if (quarter >= 2) "parts" else "part"}"
    }
}

/** Plat à créer (id nul) ou à modifier. */
data class RecipeDraft(
    val id: Long?,
    val name: String,
    val cookedWeightG: Double,
    val servings: Int?,
    val ingredients: List<Food>,
)

class RecipeRepository(private val dao: RecipeDao, private val transactions: TransactionRunner) {

    fun observeRecipes(): Flow<List<Recipe>> = dao.observeRecipes().map { rows -> rows.map { it.toRecipe() } }

    suspend fun getRecipe(id: Long): Recipe? = dao.getRecipe(id)?.toRecipe()

    /** Crée ou met à jour un plat. Renvoie son identifiant. */
    suspend fun save(draft: RecipeDraft, nowEpochMs: Long = System.currentTimeMillis()): Long = transactions.inTransaction {
        val existing = draft.id?.let { dao.getRecipe(it) }?.recipe
        val id = if (existing == null) {
            dao.insertRecipe(RecipeEntity(name = draft.name, cookedWeightG = draft.cookedWeightG, servings = draft.servings, createdAtEpochMs = nowEpochMs))
        } else {
            dao.updateRecipe(existing.copy(name = draft.name, cookedWeightG = draft.cookedWeightG, servings = draft.servings))
            dao.deleteItems(existing.id)
            existing.id
        }
        dao.insertItems(
            draft.ingredients.mapIndexed { i, f ->
                RecipeItemEntity(
                    recipeId = id, position = i, name = f.name, quantityG = f.quantityG, kcal = f.kcal,
                    proteinG = f.proteinG, carbsG = f.carbsG, fatG = f.fatG, fiberG = f.fiberG,
                )
            },
        )
        id
    }

    suspend fun delete(id: Long) = transactions.inTransaction {
        dao.deleteItems(id)
        dao.deleteRecipe(id)
    }

    private fun RecipeWithItems.toRecipe() = Recipe(
        id = recipe.id,
        name = recipe.name,
        cookedWeightG = recipe.cookedWeightG,
        servings = recipe.servings,
        ingredients = items.sortedBy { it.position }.map {
            Food(it.name, it.quantityG, null, it.kcal, it.proteinG, it.carbsG, it.fatG, it.fiberG)
        },
    )
}
