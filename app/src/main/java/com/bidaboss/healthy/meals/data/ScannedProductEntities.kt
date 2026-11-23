package com.bidaboss.healthy.meals.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Un produit scanné retenu sur le téléphone (v11), avec ses valeurs pour 100 g. Les aliments déjà ajoutés
 * à un repas gardent leurs propres valeurs : corriger un produit ne change jamais les repas passés.
 */
@Entity(tableName = "scanned_products")
data class ScannedProductEntity(
    /** Code-barres normalisé (voir `Barcodes`). */
    @PrimaryKey val code: String,
    val name: String,
    val brand: String?,
    @ColumnInfo(name = "kcal_per_100g") val kcalPer100g: Double,
    @ColumnInfo(name = "protein_per_100g") val proteinPer100g: Double?,
    @ColumnInfo(name = "carbs_per_100g") val carbsPer100g: Double?,
    @ColumnInfo(name = "fat_per_100g") val fatPer100g: Double?,
    @ColumnInfo(name = "fiber_per_100g") val fiberPer100g: Double?,
    @ColumnInfo(name = "serving_g") val servingG: Double?,
    /** Vrai si les valeurs viennent d'Open Food Facts, faux si elles ont été saisies depuis l'étiquette. */
    @ColumnInfo(name = "from_open_food_facts") val fromOpenFoodFacts: Boolean,
    @ColumnInfo(name = "last_used_epoch_ms") val lastUsedEpochMs: Long,
)

@Dao
interface ScannedProductDao {
    @Query("SELECT * FROM scanned_products WHERE code = :code")
    suspend fun get(code: String): ScannedProductEntity?

    @Upsert
    suspend fun upsert(product: ScannedProductEntity)

    @Query("SELECT * FROM scanned_products ORDER BY last_used_epoch_ms DESC")
    fun observeAll(): Flow<List<ScannedProductEntity>>

    @Query("DELETE FROM scanned_products WHERE code = :code")
    suspend fun delete(code: String)
}
