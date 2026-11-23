package com.bidaboss.healthy.workout.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "workout_plans",
    indices = [Index(value = ["import_id"], unique = true)],
)
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "import_id") val importId: String?,
    val name: String,
    val description: String?,
    val weeks: Int?,
    /** Un seul plan actif à la fois ; les anciens restent archivés. */
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "imported_at_epoch_ms") val importedAtEpochMs: Long,
)

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("plan_id")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plan_id") val planId: Long,
    val position: Int,
    val title: String,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int?,
    val notes: String?,
)

/** Une version de la séance pour une durée donnée (15, 30 ou 45 min). */
@Entity(
    tableName = "workout_session_variants",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id")],
)
data class WorkoutSessionVariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "duration_min") val durationMin: Int,
    @ColumnInfo(name = "warm_up") val warmUp: String?,
    @ColumnInfo(name = "cool_down") val coolDown: String?,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("variant_id")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "variant_id") val variantId: Long,
    val position: Int,
    val name: String,
    val sets: Int,
    val reps: Int?,
    @ColumnInfo(name = "duration_s") val durationS: Int?,
    @ColumnInfo(name = "rest_s") val restS: Int?,
    val instructions: String?,
    @ColumnInfo(name = "easier_variant") val easierVariant: String?,
    @ColumnInfo(name = "harder_variant") val harderVariant: String?,
    val notes: String?,
    @ColumnInfo(name = "video_url") val videoUrl: String? = null,
    @ColumnInfo(name = "easier_video_url") val easierVideoUrl: String? = null,
    @ColumnInfo(name = "harder_video_url") val harderVideoUrl: String? = null,
)

data class VariantWithExercises(
    @Embedded val variant: WorkoutSessionVariantEntity,
    @Relation(parentColumn = "id", entityColumn = "variant_id") val exercises: List<WorkoutExerciseEntity>,
)

data class SessionWithVariants(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = WorkoutSessionVariantEntity::class, parentColumn = "id", entityColumn = "session_id")
    val variants: List<VariantWithExercises>,
)

data class PlanWithSessions(
    @Embedded val plan: WorkoutPlanEntity,
    @Relation(entity = WorkoutSessionEntity::class, parentColumn = "id", entityColumn = "plan_id")
    val sessions: List<SessionWithVariants>,
)
