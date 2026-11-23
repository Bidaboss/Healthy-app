package com.bidaboss.healthy.workout.data

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
import kotlinx.coroutines.flow.Flow

/**
 * Séance réalisée. C'est une **copie** (nom du plan, titre, exercices) et non un lien vers le plan :
 * l'historique survit ainsi au remplacement ou à la suppression d'un plan.
 */
@Entity(tableName = "workout_logs", indices = [Index("local_date")])
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at_epoch_ms") val startedAtEpochMs: Long,
    @ColumnInfo(name = "finished_at_epoch_ms") val finishedAtEpochMs: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "plan_name") val planName: String,
    @ColumnInfo(name = "session_title") val sessionTitle: String,
    /** Version de durée choisie (15, 30 ou 45 min). */
    @ColumnInfo(name = "duration_min") val durationMin: Int,
    /** Code de Feeling, ou null. */
    val feeling: String?,
    /** Zone douloureuse signalée, ou null s'il n'y a pas eu de douleur. */
    @ColumnInfo(name = "pain_zone") val painZone: String?,
    val notes: String?,
)

@Entity(
    tableName = "workout_log_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["log_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("log_id")],
)
data class WorkoutLogExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "log_id") val logId: Long,
    val position: Int,
    val name: String,
    @ColumnInfo(name = "planned_sets") val plannedSets: Int,
    @ColumnInfo(name = "planned_reps") val plannedReps: Int?,
    @ColumnInfo(name = "planned_duration_s") val plannedDurationS: Int?,
    /** Une valeur par série faite, séparées par des virgules (répétitions ou secondes). */
    @ColumnInfo(name = "done_values") val doneValues: String,
    val skipped: Boolean,
)

/** Activité libre hors plan (marche, vélo…). */
@Entity(tableName = "activities", indices = [Index("local_date"), Index(value = ["health_id"], unique = true)])
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_date") val localDate: String,
    val type: String,
    @ColumnInfo(name = "duration_min") val durationMin: Int,
    val notes: String?,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    /** Zones travaillées : codes de `BodyPart` séparés par des virgules, ou null (v7). */
    @ColumnInfo(name = "body_parts") val bodyParts: String? = null,
    /** Identifiant de la séance de la montre (Health Connect) dont elle vient, pour ne l'ajouter qu'une fois (v10). */
    @ColumnInfo(name = "health_id") val healthId: String? = null,
)

data class LogWithExercises(
    @Embedded val log: WorkoutLogEntity,
    @Relation(parentColumn = "id", entityColumn = "log_id") val exercises: List<WorkoutLogExerciseEntity>,
)

@Dao
interface WorkoutLogDao {
    @Insert
    suspend fun insertLog(log: WorkoutLogEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<WorkoutLogExerciseEntity>)

    @Transaction
    @Query("SELECT * FROM workout_logs ORDER BY started_at_epoch_ms DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int): Flow<List<LogWithExercises>>

    @Transaction
    @Query(
        "SELECT * FROM workout_logs WHERE local_date >= :fromDate AND local_date <= :toDate " +
            "ORDER BY started_at_epoch_ms",
    )
    suspend fun getLogsBetween(fromDate: String, toDate: String): List<LogWithExercises>

    @Query("SELECT * FROM activities WHERE local_date >= :fromDate AND local_date <= :toDate ORDER BY local_date, id")
    suspend fun getActivitiesBetween(fromDate: String, toDate: String): List<ActivityEntity>

    @Query("SELECT COUNT(*) FROM workout_logs WHERE local_date >= :fromDate AND local_date <= :toDate")
    fun observeLogCount(fromDate: String, toDate: String): Flow<Int>

    @Query("SELECT DISTINCT local_date FROM workout_logs")
    fun observeLogDates(): Flow<List<String>>

    @Query("SELECT DISTINCT local_date FROM workout_logs UNION SELECT DISTINCT local_date FROM activities")
    fun observeActiveDates(): Flow<List<String>>

    /** Rend -1 si une activité de la montre avec le même identifiant existe déjà. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertActivity(activity: ActivityEntity): Long

    @Query("SELECT health_id FROM activities WHERE health_id IS NOT NULL AND local_date >= :fromDate")
    fun observeHealthIds(fromDate: String): Flow<List<String>>

    @Query("SELECT * FROM activities ORDER BY local_date DESC, id DESC LIMIT :limit")
    fun observeRecentActivities(limit: Int): Flow<List<ActivityEntity>>

    @Query("SELECT COUNT(*) FROM activities WHERE local_date >= :fromDate AND local_date <= :toDate")
    fun observeActivityCount(fromDate: String, toDate: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(duration_min), 0) FROM workout_logs WHERE local_date >= :fromDate AND local_date <= :toDate")
    fun observeLogMinutes(fromDate: String, toDate: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(duration_min), 0) FROM activities WHERE local_date >= :fromDate AND local_date <= :toDate")
    fun observeActivityMinutes(fromDate: String, toDate: String): Flow<Int>

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteActivity(id: Long)

    @Query(
        "UPDATE activities SET local_date = :localDate, type = :type, duration_min = :durationMin, " +
            "body_parts = :bodyParts WHERE id = :id",
    )
    suspend fun updateActivity(id: Long, localDate: String, type: String, durationMin: Int, bodyParts: String?): Int
}

/** Profil sportif : une seule ligne (id = 1). Listes enregistrées en codes séparés par des virgules. */
@Entity(tableName = "sport_profile")
data class SportProfileEntity(
    @PrimaryKey val id: Int = 1,
    val level: String,
    @ColumnInfo(name = "sessions_per_week") val sessionsPerWeek: Int,
    val days: String,
    @ColumnInfo(name = "duration_min") val durationMin: Int,
    val goal: String,
    @ColumnInfo(name = "pain_zones") val painZones: String,
    @ColumnInfo(name = "pain_other") val painOther: String,
    @ColumnInfo(name = "medical_follow_up") val medicalFollowUp: Boolean,
    @ColumnInfo(name = "medical_notes") val medicalNotes: String,
    val equipment: String,
    val avoid: String,
)

@Dao
interface SportProfileDao {
    @Query("SELECT * FROM sport_profile WHERE id = 1")
    fun observe(): Flow<SportProfileEntity?>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun save(profile: SportProfileEntity)
}
