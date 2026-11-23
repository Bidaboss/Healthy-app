package com.bidaboss.healthy.body.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Le profil : une seule ligne (id = 1). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    /** Code de Sex (« homme » ou « femme »). */
    val sex: String,
    @ColumnInfo(name = "first_name") val firstName: String?,
    /** Date de naissance, format yyyy-MM-dd. */
    @ColumnInfo(name = "birth_date") val birthDate: String,
    @ColumnInfo(name = "height_cm") val heightCm: Double,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    /** Code d'ActivityLevel. */
    val activity: String,
    /** Code de Goal (« maintien » pour l'instant). */
    val goal: String,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
    /** Écart adopté par rapport à la formule du maintien, en kcal par jour (0 = formule seule). */
    @ColumnInfo(name = "maintenance_adjustment_kcal", defaultValue = "0") val maintenanceAdjustmentKcal: Int = 0,
    /** Code d'ActivityLevel au moment où l'ajustement a été adopté. */
    @ColumnInfo(name = "adjustment_activity") val adjustmentActivity: String? = null,
    /** Dernière décision sur une proposition de maintien (jour, yyyy-MM-dd). */
    @ColumnInfo(name = "proposal_decided_on") val proposalDecidedOn: String? = null,
    /** « Pas maintenant » : la carte reste masquée jusqu'à ce jour (yyyy-MM-dd). */
    @ColumnInfo(name = "proposal_snoozed_until") val proposalSnoozedUntil: String? = null,
)

/** Une pesée par jour au plus : en saisir une deuxième le même jour la remplace. */
@Entity(tableName = "weigh_ins", indices = [Index(value = ["local_date"], unique = true)])
data class WeighInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "waist_cm") val waistCm: Double?,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

/** Un ajout d'eau (verre, bouteille ou quantité libre) ; on peut annuler le dernier. */
@Entity(tableName = "water_intakes", indices = [Index("local_date")])
data class WaterIntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_date") val localDate: String,
    val ml: Int,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

data class DailyWaterRow(val localDate: String, val ml: Int)

data class MealDrinkRow(val name: String, val ml: Int, val mealType: String, val eatenAtEpochMs: Long)

@Dao
interface BodyDao {
    @Insert
    suspend fun insertWater(intake: WaterIntakeEntity)

    @Query("DELETE FROM water_intakes WHERE id = (SELECT id FROM water_intakes WHERE local_date = :localDate ORDER BY id DESC LIMIT 1)")
    suspend fun deleteLastWater(localDate: String): Int

    @Query("SELECT * FROM water_intakes WHERE local_date = :localDate ORDER BY created_at_epoch_ms DESC, id DESC")
    fun observeWaterIntakes(localDate: String): Flow<List<WaterIntakeEntity>>

    @Query("DELETE FROM water_intakes WHERE id = :id")
    suspend fun deleteWater(id: Long): Int

    /**
     * Eau bue par jour : les ajouts de l'écran Eau + les aliments « boisson » avec une quantité des repas du jour
     * (1 g = 1 ml ; l'alcool et les aliments ordinaires ne comptent pas). Le seul endroit qui additionne les deux.
     */
    @Query(
        "SELECT localDate, SUM(ml) AS ml FROM (" +
            "SELECT local_date AS localDate, ml AS ml FROM water_intakes " +
            "WHERE local_date >= :fromDate AND local_date <= :toDate " +
            "UNION ALL " +
            "SELECT m.local_date AS localDate, CAST(ROUND(i.quantity_g) AS INTEGER) AS ml " +
            "FROM meal_items i JOIN meals m ON m.id = i.meal_id " +
            "WHERE i.drink = 'boisson' AND i.quantity_g IS NOT NULL AND m.local_date >= :fromDate AND m.local_date <= :toDate" +
            ") GROUP BY localDate",
    )
    fun observeDailyWater(fromDate: String, toDate: String): Flow<List<DailyWaterRow>>

    /** Les boissons des repas d'un jour (partie « Dans les repas » de l'écran Eau), les plus récentes d'abord. */
    @Query(
        "SELECT i.name AS name, CAST(ROUND(i.quantity_g) AS INTEGER) AS ml, m.meal_type AS mealType, " +
            "m.eaten_at_epoch_ms AS eatenAtEpochMs FROM meal_items i JOIN meals m ON m.id = i.meal_id " +
            "WHERE i.drink = 'boisson' AND i.quantity_g IS NOT NULL AND m.local_date = :localDate " +
            "ORDER BY m.eaten_at_epoch_ms DESC, i.position",
    )
    fun observeMealDrinks(localDate: String): Flow<List<MealDrinkRow>>

    @Query("SELECT * FROM profile WHERE id = 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfile(): ProfileEntity?

    /** Adopte (ou retire, avec 0 et null) l'ajustement du maintien et note la décision. */
    @Query(
        "UPDATE profile SET maintenance_adjustment_kcal = :kcal, adjustment_activity = :activity, " +
            "proposal_decided_on = :decidedOn, proposal_snoozed_until = NULL WHERE id = 1",
    )
    suspend fun setAdjustment(kcal: Int, activity: String?, decidedOn: String)

    /** Refus (« Garder la formule ») : note la décision sans toucher à l'ajustement. */
    @Query("UPDATE profile SET proposal_decided_on = :decidedOn, proposal_snoozed_until = NULL WHERE id = 1")
    suspend fun setDecision(decidedOn: String)

    @Query("UPDATE profile SET proposal_snoozed_until = :until WHERE id = 1")
    suspend fun setSnooze(until: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: ProfileEntity)

    @Query("SELECT * FROM weigh_ins ORDER BY local_date")
    fun observeWeighIns(): Flow<List<WeighInEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWeighIn(weighIn: WeighInEntity)

    @Query("DELETE FROM weigh_ins WHERE local_date = :localDate")
    suspend fun deleteWeighIn(localDate: String)
}
