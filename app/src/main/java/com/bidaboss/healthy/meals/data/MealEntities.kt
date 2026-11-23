package com.bidaboss.healthy.meals.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "meals",
    indices = [Index("local_date"), Index(value = ["import_id"], unique = true)],
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identifiant du fichier d'import, null pour une saisie manuelle. */
    @ColumnInfo(name = "import_id") val importId: String?,
    @ColumnInfo(name = "eaten_at_epoch_ms") val eatenAtEpochMs: Long,
    /** Jour du journal, format yyyy-MM-dd. */
    @ColumnInfo(name = "local_date") val localDate: String,
    /** Code de MealType (petit-dejeuner, dejeuner, diner, collation). */
    @ColumnInfo(name = "meal_type") val mealType: String,
    val title: String?,
    val notes: String?,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    /** Repas mis en favori, proposé dans « Mes favoris » à l'ajout d'un repas (v8). */
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
)

@Entity(
    tableName = "meal_items",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["meal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("meal_id")],
)
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meal_id") val mealId: Long,
    val position: Int,
    val name: String,
    @ColumnInfo(name = "quantity_g") val quantityG: Double?,
    val portion: String?,
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double?,
    @ColumnInfo(name = "carbs_g") val carbsG: Double?,
    @ColumnInfo(name = "fat_g") val fatG: Double?,
    @ColumnInfo(name = "fiber_g") val fiberG: Double?,
    /** Code de FoodSource. */
    val source: String,
    /** Code de Confidence, ou null. */
    val confidence: String?,
    val assumptions: String?,
    @ColumnInfo(name = "off_code") val offCode: String?,
    /** Code de DrinkKind (boisson, alcool), ou null pour un aliment ordinaire (v12). */
    val drink: String? = null,
)

data class MealWithItems(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "meal_id") val items: List<MealItemEntity>,
)
