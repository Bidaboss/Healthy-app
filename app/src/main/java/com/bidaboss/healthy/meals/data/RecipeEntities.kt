package com.bidaboss.healthy.meals.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** « Mes plats » : une recette enregistrée, ajoutée ensuite à un repas en parts ou en grammes (v9). */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Poids total du plat cuit, en grammes. */
    @ColumnInfo(name = "cooked_weight_g") val cookedWeightG: Double,
    /** Nombre de parts, ou null si non renseigné. */
    val servings: Int?,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

@Entity(
    tableName = "recipe_items",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipe_id")],
)
data class RecipeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recipe_id") val recipeId: Long,
    val position: Int,
    val name: String,
    @ColumnInfo(name = "quantity_g") val quantityG: Double?,
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double?,
    @ColumnInfo(name = "carbs_g") val carbsG: Double?,
    @ColumnInfo(name = "fat_g") val fatG: Double?,
    @ColumnInfo(name = "fiber_g") val fiberG: Double?,
)

data class RecipeWithItems(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipe_id") val items: List<RecipeItemEntity>,
)

@Dao
interface RecipeDao {
    @Insert
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Insert
    suspend fun insertItems(items: List<RecipeItemEntity>)

    @Query("DELETE FROM recipe_items WHERE recipe_id = :recipeId")
    suspend fun deleteItems(recipeId: Long)

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteRecipe(recipeId: Long)

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    suspend fun getRecipe(recipeId: Long): RecipeWithItems?

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    fun observeRecipes(): Flow<List<RecipeWithItems>>
}
