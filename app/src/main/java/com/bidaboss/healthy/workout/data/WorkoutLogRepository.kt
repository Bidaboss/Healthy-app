package com.bidaboss.healthy.workout.data

import com.bidaboss.healthy.core.trainingreport.ReportActivity
import com.bidaboss.healthy.core.trainingreport.ReportExercise
import com.bidaboss.healthy.core.trainingreport.ReportSession
import com.bidaboss.healthy.core.trainingreport.TrainingReport
import com.bidaboss.healthy.core.trainingreport.TrainingReportWriter
import com.bidaboss.healthy.core.watch.SessionSummary
import com.bidaboss.healthy.core.workout.BodyPart
import com.bidaboss.healthy.core.workout.DoneValues
import com.bidaboss.healthy.core.workout.ExerciseResult
import com.bidaboss.healthy.core.workout.PlannedExercise
import com.bidaboss.healthy.data.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class Feeling(val code: String, val label: String) {
    TOO_EASY("trop_facile", "Trop facile"),
    OK("ok", "Bien"),
    TOO_HARD("trop_dur", "Trop dur");

    companion object {
        fun fromCode(code: String?): Feeling? = entries.firstOrNull { it.code == code }
    }
}

/** Une séance terminée, à enregistrer. */
data class FinishedSession(
    val planName: String,
    val sessionTitle: String,
    val durationMin: Int,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val feeling: Feeling?,
    val painZone: String?,
    val exercises: List<Pair<PlannedExercise, ExerciseResult>>,
)

/** Une séance de l'historique, telle que l'interface l'affiche. */
data class SessionLog(
    val id: Long,
    val startedAt: LocalDateTime,
    val planName: String,
    val sessionTitle: String,
    val durationMin: Int,
    val feeling: Feeling?,
    val painZone: String?,
    val exercises: List<ExerciseLog>,
)

data class ExerciseLog(
    val name: String,
    val plannedSets: Int,
    val plannedReps: Int?,
    val plannedDurationS: Int?,
    val doneValues: List<Int>,
    val skipped: Boolean,
)

/** Activité libre hors plan (marche, vélo…), telle que l'interface l'affiche. */
data class FreeActivity(
    val id: Long,
    val date: LocalDate,
    val type: String,
    val durationMin: Int,
    val bodyParts: List<BodyPart> = emptyList(),
)

class WorkoutLogRepository(
    private val logDao: WorkoutLogDao,
    private val transactions: TransactionRunner,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun observeRecentSessions(limit: Int = 30): Flow<List<SessionLog>> =
        logDao.observeRecentLogs(limit).map { rows -> rows.map { it.toSessionLog() } }

    /** Nombre de séances faites entre deux jours inclus. */
    fun observeSessionCount(from: LocalDate, to: LocalDate): Flow<Int> =
        logDao.observeLogCount(from.toString(), to.toString())

    /** Jours où au moins une séance a été faite (pour la flamme des semaines actives). */
    fun observeSessionDates(): Flow<Set<LocalDate>> =
        logDao.observeLogDates().map { dates -> dates.mapTo(HashSet()) { LocalDate.parse(it) } }

    /**
     * Jours actifs : une séance du plan ou une activité ajoutée à la main (kiné, marche…).
     * Sert à la flamme, aux badges de semaines et à l'humeur de Pousse (choix du propriétaire, 12/11/2026).
     */
    fun observeActiveDates(): Flow<Set<LocalDate>> =
        logDao.observeActiveDates().map { dates -> dates.mapTo(HashSet()) { LocalDate.parse(it) } }

    fun observeRecentActivities(limit: Int = 30): Flow<List<FreeActivity>> =
        logDao.observeRecentActivities(limit).map { rows ->
            rows.map { FreeActivity(it.id, LocalDate.parse(it.localDate), it.type, it.durationMin, BodyPart.parse(it.bodyParts)) }
        }

    /** Nombre d'activités libres entre deux jours inclus. */
    fun observeActivityCount(from: LocalDate, to: LocalDate): Flow<Int> =
        logDao.observeActivityCount(from.toString(), to.toString())

    /**
     * Séances de la période (plan + activités, dont celles de la montre ajoutées au journal) : nombre et
     * durée totale, pour le bloc « Séances » de l'écran « Ma montre ».
     */
    fun observeSessionSummary(from: LocalDate, to: LocalDate): Flow<SessionSummary> = combine(
        logDao.observeLogCount(from.toString(), to.toString()),
        logDao.observeActivityCount(from.toString(), to.toString()),
        logDao.observeLogMinutes(from.toString(), to.toString()),
        logDao.observeActivityMinutes(from.toString(), to.toString()),
    ) { logs, activities, logMinutes, activityMinutes -> SessionSummary(logs + activities, logMinutes + activityMinutes) }

    /** Enregistre une activité libre. Rend false si le type est vide ou la durée hors limites. */
    suspend fun addActivity(
        type: String,
        durationMin: Int,
        date: LocalDate,
        nowEpochMs: Long,
        bodyParts: Collection<BodyPart> = emptyList(),
        healthId: String? = null,
    ): Boolean {
        val name = type.trim().take(MAX_ACTIVITY_TYPE_LENGTH)
        if (name.isEmpty() || durationMin !in 1..MAX_ACTIVITY_MINUTES) return false
        val inserted = logDao.insertActivity(
            ActivityEntity(
                localDate = date.toString(),
                type = name,
                durationMin = durationMin,
                notes = null,
                createdAtEpochMs = nowEpochMs,
                bodyParts = BodyPart.format(bodyParts),
                healthId = healthId,
            ),
        )
        // -1 : cette séance de la montre est déjà dans le journal.
        return inserted != -1L
    }

    /** Identifiants des séances de la montre déjà ajoutées au journal depuis [from]. */
    fun observeHealthIds(from: LocalDate): Flow<Set<String>> =
        logDao.observeHealthIds(from.toString()).map { it.toSet() }

    suspend fun deleteActivity(id: Long) = logDao.deleteActivity(id)

    /** Modifie une activité libre (demande du propriétaire, 13/11/2026). Mêmes limites que l'ajout. */
    suspend fun updateActivity(
        id: Long,
        type: String,
        durationMin: Int,
        date: LocalDate,
        bodyParts: Collection<BodyPart> = emptyList(),
    ): Boolean {
        val name = type.trim().take(MAX_ACTIVITY_TYPE_LENGTH)
        if (name.isEmpty() || durationMin !in 1..MAX_ACTIVITY_MINUTES) return false
        return logDao.updateActivity(id, date.toString(), name, durationMin, BodyPart.format(bodyParts)) > 0
    }

    suspend fun saveSession(session: FinishedSession): Long = transactions.inTransaction {
        val logId = logDao.insertLog(
            WorkoutLogEntity(
                startedAtEpochMs = session.startedAtEpochMs,
                finishedAtEpochMs = session.finishedAtEpochMs,
                localDate = Instant.ofEpochMilli(session.startedAtEpochMs).atZone(zone()).toLocalDate().toString(),
                planName = session.planName,
                sessionTitle = session.sessionTitle,
                durationMin = session.durationMin,
                feeling = session.feeling?.code,
                painZone = session.painZone?.trim()?.takeIf { it.isNotEmpty() },
                notes = null,
            ),
        )
        logDao.insertExercises(
            session.exercises.mapIndexed { index, (planned, result) ->
                WorkoutLogExerciseEntity(
                    logId = logId,
                    position = index,
                    name = planned.name,
                    plannedSets = planned.sets,
                    plannedReps = planned.reps,
                    plannedDurationS = planned.durationS,
                    doneValues = DoneValues.format(result.doneValues),
                    skipped = result.skipped,
                )
            },
        )
        logId
    }

    /**
     * Bilan d'entraînement à envoyer à Claude : les [REPORT_DAYS] derniers jours, [today] compris.
     * Rend le texte JSON du type `training_report` (docs/format-import.md).
     */
    suspend fun exportTrainingReport(today: LocalDate, id: String, nowEpochMs: Long): String {
        val from = today.minusDays(REPORT_DAYS - 1L)
        val report = TrainingReport(
            periodStart = from,
            periodEnd = today,
            sessions = logDao.getLogsBetween(from.toString(), today.toString()).map { row ->
                ReportSession(
                    startedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(row.log.startedAtEpochMs), zone()),
                    planName = row.log.planName,
                    sessionTitle = row.log.sessionTitle,
                    plannedDurationMin = row.log.durationMin,
                    // Arrondi à la minute la plus proche, jamais négatif (changement d'heure du téléphone).
                    actualDurationMin = ((row.log.finishedAtEpochMs - row.log.startedAtEpochMs + 30_000) / 60_000)
                        .coerceAtLeast(0).toInt(),
                    feeling = Feeling.fromCode(row.log.feeling)?.code,
                    painZone = row.log.painZone,
                    exercises = row.exercises.sortedBy { it.position }.map {
                        ReportExercise(
                            name = it.name,
                            plannedSets = it.plannedSets,
                            plannedReps = it.plannedReps,
                            plannedDurationS = it.plannedDurationS,
                            doneValues = DoneValues.parse(it.doneValues),
                            skipped = it.skipped,
                        )
                    },
                )
            },
            activities = logDao.getActivitiesBetween(from.toString(), today.toString()).map {
                ReportActivity(LocalDate.parse(it.localDate), it.type, it.durationMin, BodyPart.parse(it.bodyParts))
            },
        )
        return TrainingReportWriter.write(
            report,
            id = id,
            createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMs), zone()),
        )
    }

    companion object {
        /** Période couverte par le bilan : 4 semaines. */
        const val REPORT_DAYS = 28

        const val MAX_ACTIVITY_TYPE_LENGTH = 40

        /** 24 heures : au-delà, c'est une faute de frappe. */
        const val MAX_ACTIVITY_MINUTES = 1_440
    }

    private fun LogWithExercises.toSessionLog() = SessionLog(
        id = log.id,
        startedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(log.startedAtEpochMs), zone()),
        planName = log.planName,
        sessionTitle = log.sessionTitle,
        durationMin = log.durationMin,
        feeling = Feeling.fromCode(log.feeling),
        painZone = log.painZone,
        exercises = exercises.sortedBy { it.position }.map {
            ExerciseLog(
                name = it.name,
                plannedSets = it.plannedSets,
                plannedReps = it.plannedReps,
                plannedDurationS = it.plannedDurationS,
                doneValues = DoneValues.parse(it.doneValues),
                skipped = it.skipped,
            )
        },
    )
}
