package com.bidaboss.healthy.workout.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Insert
    suspend fun insertVariant(variant: WorkoutSessionVariantEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<WorkoutExerciseEntity>)

    @Query("UPDATE workout_plans SET is_active = 0 WHERE is_active = 1")
    suspend fun archiveActivePlans()

    @Transaction
    @Query("SELECT * FROM workout_plans WHERE is_active = 1 ORDER BY imported_at_epoch_ms DESC LIMIT 1")
    fun observeActivePlan(): Flow<PlanWithSessions?>
}
