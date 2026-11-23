package com.bidaboss.healthy.meals.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    @Insert
    suspend fun insertItem(item: MealItemEntity): Long

    @Query("DELETE FROM meal_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Insert
    suspend fun insertItems(items: List<MealItemEntity>)

    @Update
    suspend fun updateMeal(meal: MealEntity)

    @Query("DELETE FROM meal_items WHERE meal_id = :mealId")
    suspend fun deleteItems(mealId: Long)

    @Query("DELETE FROM meals WHERE id = :mealId")
    suspend fun deleteMeal(mealId: Long)

    /** Oublie qu'un fichier a été importé, pour pouvoir le réimporter après suppression du repas. */
    @Query("DELETE FROM imports WHERE import_id = :importId")
    suspend fun deleteImportRecord(importId: String)

    @Transaction
    @Query("SELECT * FROM meals WHERE id = :mealId")
    suspend fun getMeal(mealId: Long): MealWithItems?

    @Transaction
    @Query("SELECT * FROM meals WHERE import_id = :importId")
    suspend fun getMealByImportId(importId: String): MealWithItems?

    @Query("UPDATE meals SET is_favorite = :favorite WHERE id = :mealId")
    suspend fun setFavorite(mealId: Long, favorite: Boolean)

    @Transaction
    @Query("SELECT * FROM meals WHERE is_favorite = 1 ORDER BY eaten_at_epoch_ms DESC")
    fun observeFavorites(): Flow<List<MealWithItems>>

    @Transaction
    @Query("SELECT * FROM meals ORDER BY eaten_at_epoch_ms DESC LIMIT :limit")
    fun observeLatestMeals(limit: Int): Flow<List<MealWithItems>>

    @Transaction
    @Query("SELECT * FROM meals WHERE local_date >= :fromDate AND local_date <= :toDate ORDER BY eaten_at_epoch_ms")
    suspend fun getMealsBetween(fromDate: String, toDate: String): List<MealWithItems>

    /** Les aliments des repas les plus récents, avec l'heure du repas : de quoi construire « Déjà mangés ». */
    @Query(
        "SELECT i.name, i.quantity_g, i.portion, i.kcal, i.protein_g, i.carbs_g, i.fat_g, i.fiber_g, i.source, i.off_code, i.drink, " +
            "m.eaten_at_epoch_ms FROM meal_items i JOIN meals m ON m.id = i.meal_id " +
            "ORDER BY m.eaten_at_epoch_ms DESC LIMIT :limit",
    )
    fun observeFoodHistory(limit: Int): Flow<List<FoodHistoryRow>>

    @Transaction
    @Query("SELECT * FROM meals WHERE local_date >= :fromDate AND local_date <= :toDate ORDER BY eaten_at_epoch_ms")
    fun observeMealsBetween(fromDate: String, toDate: String): Flow<List<MealWithItems>>

    @Query("SELECT DISTINCT local_date FROM meals")
    fun observeMealDates(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM meals WHERE local_date = :localDate ORDER BY eaten_at_epoch_ms")
    fun observeMealsForDate(localDate: String): Flow<List<MealWithItems>>

    /** Total des calories par jour entre deux dates incluses (seuls les jours avec un repas apparaissent). */
    @Query(
        "SELECT m.local_date AS localDate, SUM(i.kcal) AS kcal FROM meals m JOIN meal_items i ON i.meal_id = m.id " +
            "WHERE m.local_date >= :fromDate AND m.local_date <= :toDate GROUP BY m.local_date",
    )
    fun observeDailyKcal(fromDate: String, toDate: String): Flow<List<DailyKcalRow>>
}

data class DailyKcalRow(val localDate: String, val kcal: Double)

/** Une ligne d'aliment d'un repas passé, telle que la lit la requête de l'historique. */
data class FoodHistoryRow(
    val name: String,
    @ColumnInfo(name = "quantity_g") val quantityG: Double?,
    val portion: String?,
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double?,
    @ColumnInfo(name = "carbs_g") val carbsG: Double?,
    @ColumnInfo(name = "fat_g") val fatG: Double?,
    @ColumnInfo(name = "fiber_g") val fiberG: Double?,
    val source: String,
    @ColumnInfo(name = "off_code") val offCode: String?,
    val drink: String?,
    @ColumnInfo(name = "eaten_at_epoch_ms") val eatenAtEpochMs: Long,
)
