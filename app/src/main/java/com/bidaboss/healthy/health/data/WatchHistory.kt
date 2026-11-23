package com.bidaboss.healthy.health.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Le relevé d'un jour de la montre, gardé sur le téléphone. Une mesure absente est null, jamais zéro (v13). */
@Entity(tableName = "watch_days")
data class WatchDayEntity(
    @PrimaryKey @ColumnInfo(name = "local_date") val localDate: String,
    val steps: Int?,
    @ColumnInfo(name = "active_kcal") val activeKcal: Int?,
    @ColumnInfo(name = "sleep_min") val sleepMin: Int?,
    @ColumnInfo(name = "avg_bpm") val avgBpm: Int?,
    @ColumnInfo(name = "min_bpm") val minBpm: Int?,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

@Dao
interface WatchDayDao {
    @Query("SELECT * FROM watch_days WHERE local_date >= :fromDate AND local_date <= :toDate ORDER BY local_date")
    fun observeBetween(fromDate: String, toDate: String): Flow<List<WatchDayEntity>>

    @Query("SELECT * FROM watch_days WHERE local_date = :localDate")
    suspend fun get(localDate: String): WatchDayEntity?

    @Query("SELECT COUNT(*) FROM watch_days")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: WatchDayEntity)

    @Query("DELETE FROM watch_days")
    suspend fun deleteAll()
}

/**
 * Historique des relevés de la montre (PRD « Ma montre », choix du propriétaire du 23/11/2026) : un jour = une ligne, gardée
 * sans limite de durée, incluse dans la sauvegarde. Rien de Room ne sort d'ici.
 */
class WatchHistory(private val dao: WatchDayDao) {

    fun observe(from: LocalDate, to: LocalDate): Flow<List<WatchDayRecord>> =
        dao.observeBetween(from.toString(), to.toString()).map { rows -> rows.map { it.toRecord() } }

    suspend fun count(): Int = dao.count()

    suspend fun clear() = dao.deleteAll()

    /**
     * Enregistre ou met à jour le relevé de [date] avec ce que la montre vient de rendre. Une mesure absente
     * (autorisation retirée, montre pas synchronisée) ne remplace jamais une mesure déjà gardée. Un jour
     * entièrement vide n'écrit rien : pas de ligne de zéros.
     */
    suspend fun save(date: LocalDate, day: WatchDay, nowEpochMs: Long) {
        if (day.isEmpty) return
        val old = dao.get(date.toString())
        dao.upsert(
            WatchDayEntity(
                localDate = date.toString(),
                steps = day.steps.takeIf { it > 0 }?.toInt() ?: old?.steps,
                activeKcal = day.activeKcal ?: old?.activeKcal,
                sleepMin = day.sleepMin ?: old?.sleepMin,
                avgBpm = (if (day.hasHeart) day.avgBpm else null) ?: old?.avgBpm,
                minBpm = (if (day.hasHeart) day.minBpm else null) ?: old?.minBpm,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    private fun WatchDayEntity.toRecord() = WatchDayRecord(LocalDate.parse(localDate), steps, activeKcal, sleepMin, avgBpm, minBpm)
}
