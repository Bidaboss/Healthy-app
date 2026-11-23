package com.bidaboss.healthy.workout.data

import com.bidaboss.healthy.core.importformat.ImportedPlan
import com.bidaboss.healthy.core.workout.PlannedExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Le plan tel que l'interface l'affiche (aucune classe Room ne sort d'ici). */
data class WorkoutPlan(
    val id: Long,
    val name: String,
    val description: String?,
    val weeks: Int?,
    val sessions: List<WorkoutSession>,
)

data class WorkoutSession(
    val id: Long,
    val title: String,
    val dayOfWeek: Int?,
    /** Versions par durée, triées de la plus courte à la plus longue. */
    val variants: List<WorkoutVariant>,
)

data class WorkoutVariant(
    val durationMin: Int,
    val warmUp: String?,
    val coolDown: String?,
    val exercises: List<PlannedExercise>,
)

class WorkoutRepository(private val workoutDao: WorkoutDao) {

    fun observeActivePlan(): Flow<WorkoutPlan?> =
        workoutDao.observeActivePlan().map { it?.toPlan() }

    suspend fun getActivePlan(): WorkoutPlan? = observeActivePlan().first()

    /** À appeler dans une transaction (voir ImportRepository). L'ancien plan est archivé. */
    suspend fun insertImportedPlan(importId: String, plan: ImportedPlan, nowEpochMs: Long) {
        workoutDao.archiveActivePlans()
        val planId = workoutDao.insertPlan(
            WorkoutPlanEntity(
                importId = importId,
                name = plan.name,
                description = plan.description,
                weeks = plan.weeks,
                isActive = true,
                importedAtEpochMs = nowEpochMs,
            ),
        )
        plan.sessions.forEachIndexed { sessionIndex, session ->
            val sessionId = workoutDao.insertSession(
                WorkoutSessionEntity(
                    planId = planId,
                    position = sessionIndex,
                    title = session.title,
                    dayOfWeek = session.dayOfWeek,
                    notes = session.notes,
                ),
            )
            session.variants.forEach { variant ->
                val variantId = workoutDao.insertVariant(
                    WorkoutSessionVariantEntity(
                        sessionId = sessionId,
                        durationMin = variant.durationMin,
                        warmUp = variant.warmUp,
                        coolDown = variant.coolDown,
                    ),
                )
                workoutDao.insertExercises(
                    variant.exercises.mapIndexed { index, exercise ->
                        WorkoutExerciseEntity(
                            variantId = variantId,
                            position = index,
                            name = exercise.name,
                            sets = exercise.sets,
                            reps = exercise.reps,
                            durationS = exercise.durationS,
                            restS = exercise.restS,
                            instructions = exercise.instructions,
                            easierVariant = exercise.easierVariant,
                            harderVariant = exercise.harderVariant,
                            notes = exercise.notes,
                            videoUrl = exercise.videoUrl,
                            easierVideoUrl = exercise.easierVideoUrl,
                            harderVideoUrl = exercise.harderVideoUrl,
                        )
                    },
                )
            }
        }
    }

    private fun PlanWithSessions.toPlan() = WorkoutPlan(
        id = plan.id,
        name = plan.name,
        description = plan.description,
        weeks = plan.weeks,
        sessions = sessions.sortedBy { it.session.position }.map { s ->
            WorkoutSession(
                id = s.session.id,
                title = s.session.title,
                dayOfWeek = s.session.dayOfWeek,
                variants = s.variants.sortedBy { it.variant.durationMin }.map { v ->
                    WorkoutVariant(
                        durationMin = v.variant.durationMin,
                        warmUp = v.variant.warmUp,
                        coolDown = v.variant.coolDown,
                        exercises = v.exercises.sortedBy { it.position }.map {
                            PlannedExercise(
                                name = it.name,
                                sets = it.sets,
                                reps = it.reps,
                                durationS = it.durationS,
                                restS = it.restS,
                                instructions = it.instructions,
                                easierVariant = it.easierVariant,
                                harderVariant = it.harderVariant,
                                notes = it.notes,
                                videoUrl = it.videoUrl,
                                easierVideoUrl = it.easierVideoUrl,
                                harderVideoUrl = it.harderVideoUrl,
                            )
                        },
                    )
                },
            )
        },
    )
}
