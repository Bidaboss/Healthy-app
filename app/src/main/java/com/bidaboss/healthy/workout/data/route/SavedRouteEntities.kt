package com.bidaboss.healthy.workout.data.route

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Trajet enregistré (v15). Les adresses restent sur le téléphone ; la distance d'un sens est calculée une seule
 * fois (ou saisie à la main) puis gardée : le trajet se réutilise ensuite hors ligne.
 */
@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val start: String,
    /** Étapes, une par ligne (une adresse ne contient jamais de retour à la ligne) ; vide = aucune. */
    val stops: String,
    @ColumnInfo(name = "destination") val end: String,
    /** Distance d'un sens en km ; null tant qu'elle n'est ni calculée ni saisie. */
    @ColumnInfo(name = "distance_km") val distanceKm: Double?,
    /** `calcule` ou `saisi`, null sans distance. */
    @ColumnInfo(name = "distance_source") val distanceSource: String?,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

@Dao
interface SavedRouteDao {
    @Query("SELECT * FROM saved_routes ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<SavedRouteEntity>>

    @Query("SELECT * FROM saved_routes WHERE id = :id")
    suspend fun get(id: Long): SavedRouteEntity?

    @Insert
    suspend fun insert(route: SavedRouteEntity): Long

    @Update
    suspend fun update(route: SavedRouteEntity)

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun delete(id: Long)

    /** Les activités déjà ajoutées gardent leur distance et le nom du trajet ; seul le lien disparaît. */
    @Query("UPDATE activities SET route_id = NULL WHERE route_id = :id")
    suspend fun unlinkActivities(id: Long)
}
