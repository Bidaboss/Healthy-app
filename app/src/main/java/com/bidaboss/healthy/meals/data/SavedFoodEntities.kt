package com.bidaboss.healthy.meals.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * « Mes aliments » (v15) : un aliment mis de côté pour le réutiliser, avec ses valeurs pour sa portion par
 * défaut. Distinct de « Mes plats » (une recette). Les repas déjà notés gardent leurs propres valeurs :
 * corriger ou supprimer un aliment ne change jamais un repas passé.
 */
@Entity(
    tableName = "saved_foods",
    indices = [Index(value = ["name_key"], unique = true)],
)
data class SavedFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Nom réduit (minuscules, sans accents) : un même aliment n'est enregistré qu'une fois. */
    @ColumnInfo(name = "name_key") val nameKey: String,
    /** Portion par défaut en grammes (ou millilitres pour une boisson), ou null si inconnue. */
    @ColumnInfo(name = "default_portion_g") val defaultPortionG: Double?,
    /** Valeurs pour la portion par défaut. */
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double?,
    @ColumnInfo(name = "carbs_g") val carbsG: Double?,
    @ColumnInfo(name = "fat_g") val fatG: Double?,
    @ColumnInfo(name = "fiber_g") val fiberG: Double?,
    /** Code de FoodSource. */
    val source: String,
    /** Code de DrinkKind (boisson, alcool), ou null pour un aliment ordinaire. */
    val drink: String?,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

@Dao
interface SavedFoodDao {
    @Query("SELECT * FROM saved_foods ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SavedFoodEntity>>

    @Query("SELECT * FROM saved_foods WHERE id = :id")
    suspend fun get(id: Long): SavedFoodEntity?

    @Query("SELECT * FROM saved_foods WHERE name_key = :key")
    suspend fun getByKey(key: String): SavedFoodEntity?

    /** Rend -1 si un aliment du même nom existe déjà. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(food: SavedFoodEntity): Long

    @Update
    suspend fun update(food: SavedFoodEntity)

    @Query("DELETE FROM saved_foods WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM saved_foods WHERE name_key = :key")
    suspend fun deleteByKey(key: String)
}
