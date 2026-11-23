package com.bidaboss.healthy.core.importformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Écrit l'objet `plan` d'un plan sportif (mêmes champs que l'import), pour le prompt d'ajustement :
 * l'IA voit ainsi le programme actuel dans le format qu'elle doit renvoyer.
 */
object PlanWriter {
    private val json = Json { prettyPrint = true }

    fun write(plan: ImportedPlan): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("name", plan.name)
            plan.description?.let { put("description", it) }
            plan.weeks?.let { put("weeks", it) }
            put("sessions", JsonArray(plan.sessions.map(::session)))
        },
    )

    private fun session(s: ImportedSession) = buildJsonObject {
        put("title", s.title)
        s.dayOfWeek?.let { put("day_of_week", it) }
        s.notes?.let { put("notes", it) }
        put("variants", JsonArray(s.variants.map(::variant)))
    }

    private fun variant(v: ImportedSessionVariant) = buildJsonObject {
        put("duration_min", v.durationMin)
        v.warmUp?.let { put("warm_up", it) }
        v.coolDown?.let { put("cool_down", it) }
        put("exercises", JsonArray(v.exercises.map(::exercise)))
    }

    private fun exercise(e: ImportedExercise) = buildJsonObject {
        put("name", e.name)
        put("sets", e.sets)
        e.reps?.let { put("reps", it) }
        e.durationS?.let { put("duration_s", it) }
        e.restS?.let { put("rest_s", it) }
        e.instructions?.let { put("instructions", it) }
        e.easierVariant?.let { put("easier_variant", it) }
        e.harderVariant?.let { put("harder_variant", it) }
        e.notes?.let { put("notes", it) }
        e.videoUrl?.let { put("video_url", it) }
        e.easierVideoUrl?.let { put("easier_video_url", it) }
        e.harderVideoUrl?.let { put("harder_video_url", it) }
    }
}
