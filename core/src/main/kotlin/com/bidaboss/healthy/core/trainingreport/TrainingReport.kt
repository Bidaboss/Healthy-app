package com.bidaboss.healthy.core.trainingreport

import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.core.workout.BodyPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Bilan d'entraînement exporté pour Claude (voir docs/format-import.md, type `training_report`). */
data class TrainingReport(
    /** Premier et dernier jour couverts, inclus, en date locale du téléphone. */
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val sessions: List<ReportSession>,
    val activities: List<ReportActivity>,
)

data class ReportSession(
    val startedAt: LocalDateTime,
    val planName: String,
    val sessionTitle: String,
    /** Version choisie : 15, 30 ou 45 min. */
    val plannedDurationMin: Int,
    /** Temps réellement passé, du début à l'enregistrement. */
    val actualDurationMin: Int,
    /** `trop_facile`, `ok`, `trop_dur`, ou null si non renseigné. */
    val feeling: String?,
    val painZone: String?,
    val exercises: List<ReportExercise>,
)

data class ReportExercise(
    val name: String,
    val plannedSets: Int,
    val plannedReps: Int?,
    val plannedDurationS: Int?,
    /** Une valeur par série faite : répétitions, ou secondes pour un exercice chronométré. */
    val doneValues: List<Int>,
    val skipped: Boolean,
)

data class ReportActivity(
    val date: LocalDate,
    val type: String,
    val durationMin: Int,
    val bodyParts: List<BodyPart> = emptyList(),
)

/** Écrit un [TrainingReport] dans le format documenté. Fonction pure : ni horloge ni hasard ici. */
object TrainingReportWriter {
    private val json = Json { prettyPrint = true }

    fun write(report: TrainingReport, id: String, createdAt: LocalDateTime): String {
        val root = buildJsonObject {
            put("format", ImportParser.FORMAT)
            put("schema_version", ImportParser.SCHEMA_VERSION)
            put("type", ImportParser.TYPE_TRAINING_REPORT)
            put("id", id)
            put("created_at", createdAt.truncatedTo(ChronoUnit.SECONDS).toIsoString())
            put(
                "report",
                buildJsonObject {
                    put("period_start", report.periodStart.toString())
                    put("period_end", report.periodEnd.toString())
                    put("sessions", JsonArray(report.sessions.sortedBy { it.startedAt }.map(::session)))
                    put("activities", JsonArray(report.activities.sortedBy { it.date }.map(::activity)))
                },
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun session(session: ReportSession) = buildJsonObject {
        put("started_at", session.startedAt.truncatedTo(ChronoUnit.SECONDS).toIsoString())
        put("plan_name", session.planName)
        put("session_title", session.sessionTitle)
        put("planned_duration_min", session.plannedDurationMin)
        put("actual_duration_min", session.actualDurationMin)
        session.feeling?.let { put("feeling", it) }
        session.painZone?.let { put("pain_zone", it) }
        put("exercises", JsonArray(session.exercises.map(::exercise)))
    }

    private fun exercise(exercise: ReportExercise) = buildJsonObject {
        put("name", exercise.name)
        put("planned_sets", exercise.plannedSets)
        val done = JsonArray(exercise.doneValues.map { JsonPrimitive(it) })
        // Comme dans un plan : soit des répétitions, soit une durée.
        if (exercise.plannedReps == null && exercise.plannedDurationS != null) {
            put("planned_duration_s", exercise.plannedDurationS)
            put("done_duration_s", done)
        } else {
            exercise.plannedReps?.let { put("planned_reps", it) }
            put("done_reps", done)
        }
        put("skipped", exercise.skipped)
    }

    private fun activity(activity: ReportActivity) = buildJsonObject {
        put("date", activity.date.toString())
        put("type", activity.type)
        put("duration_min", activity.durationMin)
        put("body_parts", JsonArray(BodyPart.entries.filter { it in activity.bodyParts }.map { JsonPrimitive(it.code) }))
    }

    /** Toujours avec les secondes (`2026-11-11T18:00:00`), ce que `LocalDateTime.toString()` ne garantit pas. */
    private fun LocalDateTime.toIsoString(): String = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(this)
}
