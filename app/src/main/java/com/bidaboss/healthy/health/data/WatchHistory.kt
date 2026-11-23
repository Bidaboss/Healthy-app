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
import com.bidaboss.healthy.core.watch.SleepStages
import java.time.LocalDate
import java.time.LocalDateTime

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
    // v17 (refonte, lot 4) : distance, détail de la nuit. Tout est facultatif : une mesure absente est null.
    @ColumnInfo(name = "distance_m") val distanceM: Int? = null,
    @ColumnInfo(name = "sleep_start") val sleepStart: String? = null,
    @ColumnInfo(name = "sleep_end") val sleepEnd: String? = null,
    @ColumnInfo(name = "sleep_stages") val sleepStages: String? = null,
    @ColumnInfo(name = "night_avg_bpm") val nightAvgBpm: Int? = null,
    @ColumnInfo(name = "night_min_bpm") val nightMinBpm: Int? = null,
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
        val sleep = day.sleep
        // Le détail d'une nuit ne remplace l'ancien que s'il est là ; ses phases vides gardent les anciennes si les heures sont les mêmes.
        val keepOldNight = sleep == null
        dao.upsert(
            WatchDayEntity(
                localDate = date.toString(),
                steps = day.steps.takeIf { it > 0 }?.toInt() ?: old?.steps,
                activeKcal = day.activeKcal ?: old?.activeKcal,
                sleepMin = day.sleepMin ?: old?.sleepMin,
                avgBpm = (if (day.hasHeart) day.avgBpm else null) ?: old?.avgBpm,
                minBpm = (if (day.hasHeart) day.minBpm else null) ?: old?.minBpm,
                updatedAtEpochMs = nowEpochMs,
                distanceM = day.distanceM?.takeIf { it > 0 } ?: old?.distanceM,
                sleepStart = if (keepOldNight) old?.sleepStart else sleep?.start?.toString(),
                sleepEnd = if (keepOldNight) old?.sleepEnd else sleep?.end?.toString(),
                sleepStages = if (keepOldNight) old?.sleepStages else SleepStages.encode(sleep!!.segments).takeIf { it.isNotEmpty() },
                nightAvgBpm = if (keepOldNight) old?.nightAvgBpm else sleep!!.nightAvgBpm,
                nightMinBpm = if (keepOldNight) old?.nightMinBpm else sleep!!.nightMinBpm,
            ),
        )
    }

    private fun WatchDayEntity.toRecord(): WatchDayRecord {
        val start = sleepStart?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val end = sleepEnd?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val detail = if (start != null && end != null) {
            SleepDetail(start, end, SleepStages.decode(sleepStages), nightAvgBpm, nightMinBpm)
        } else {
            null
        }
        return WatchDayRecord(LocalDate.parse(localDate), steps, activeKcal, sleepMin, avgBpm, minBpm, distanceM, detail)
    }
}
