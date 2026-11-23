package com.bidaboss.healthy.core.importformat

import com.bidaboss.healthy.core.backup.BackupReader
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import java.net.URI
import java.net.URISyntaxException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Lien Markdown « [texte](adresse) » : on garde l'adresse. */
private val MARKDOWN_LINK = Regex("""^\[[^\]]*]\((.+)\)$""")

/** Adresse sans « https:// » devant, comme « www.youtube.com/… ». */
private val BARE_HOST = Regex("""^(www\.|m\.)?[a-z0-9-]+(\.[a-z0-9-]+)+(/|$)""", RegexOption.IGNORE_CASE)

sealed interface ImportResult {
    /** [warnings] : ce que l'app a corrigé ou ignoré sans bloquer l'import (montré dans l'aperçu). */
    data class Success(val document: ImportDocument, val warnings: List<String> = emptyList()) : ImportResult
    data class Failure(val errors: List<String>) : ImportResult
}

/**
 * Lit un texte (JSON seul, ou message de Claude contenant le JSON) et le valide
 * selon docs/format-import.md. Ne lève jamais d'exception : renvoie une liste
 * d'erreurs en français lisibles par l'utilisateur.
 */
class ImportParser(
    /** Fuseau utilisé quand une date n'a pas de décalage horaire. */
    private val defaultZone: ZoneId = ZoneId.systemDefault(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = true
    }

    fun parse(text: String): ImportResult {
        val raw = extractJsonObject(text)
            ?: return fail("Aucun objet JSON trouvé. Colle le bloc de code envoyé par Claude, de { à }.")

        val root: JsonObject = try {
            json.parseToJsonElement(raw) as? JsonObject
                ?: return fail("Le contenu n'est pas un objet JSON.")
        } catch (e: SerializationException) {
            return fail("JSON illisible : vérifie que le texte a été copié en entier.")
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val format = root.string("format")
        if (format != FORMAT) {
            return fail("Ce n'est pas un fichier Healthy (champ « format » attendu : \"$FORMAT\").")
        }

        val version = (root["schema_version"] as? JsonPrimitive)?.intOrNull
            ?: return fail("Champ « schema_version » manquant ou invalide.")
        if (version > SCHEMA_VERSION) {
            return fail("Ce fichier utilise la version $version du format, l'app ne connaît que la version $SCHEMA_VERSION. Mets l'app à jour.")
        }
        if (version < 1) return fail("« schema_version » doit valoir au moins 1.")

        val id = root.string("id")?.trim()
        when {
            id.isNullOrEmpty() -> errors += "Champ « id » manquant."
            !id.isUuid() -> errors += "Champ « id » invalide : un UUID est attendu (ex. 3f2b8c1e-6a4d-4c8e-9b7a-1d2e3f4a5b6c)."
        }

        val replaces = root.string("replaces").cleaned()
        when {
            replaces == null -> Unit
            !replaces.isUuid() -> errors += "Champ « replaces » invalide : un UUID est attendu."
            replaces.equals(id, ignoreCase = true) -> errors += "« replaces » ne peut pas être égal à « id »."
        }

        val createdAt = root.string("created_at")?.let { parseDateTime(it) }
        if (createdAt == null) errors += "Champ « created_at » manquant ou date invalide (format attendu : 2026-11-11T12:30:00+01:00)."

        val document: ImportDocument? = when (val type = root.string("type")) {
            TYPE_MEAL -> decode<MealDto>(root["meal"], "meal", errors)
                ?.let { validateMeal(it, errors) }
                ?.let { meal -> if (id != null && createdAt != null) ImportDocument.Meal(id.lowercase(), createdAt, replaces?.lowercase(), meal) else null }

            TYPE_WORKOUT_PLAN -> decode<PlanDto>(root["plan"], "plan", errors)
                ?.let { validatePlan(it, errors, warnings) }
                ?.let { plan -> if (id != null && createdAt != null) ImportDocument.WorkoutPlan(id.lowercase(), createdAt, replaces?.lowercase(), plan) else null }

            TYPE_TRAINING_REPORT -> {
                errors += "Ce fichier est un bilan d'entraînement exporté par l'app : il est à envoyer à Claude, pas à importer."
                null
            }

            TYPE_MEAL_REPORT -> {
                errors += "Ce fichier est un bilan repas exporté par l'app : il est à envoyer à Claude, pas à importer."
                null
            }

            TYPE_BACKUP -> BackupReader.read(root["backup"], errors)
                ?.let { content -> if (id != null && createdAt != null) ImportDocument.Backup(id.lowercase(), createdAt, content) else null }

            null -> {
                errors += "Champ « type » manquant (\"$TYPE_MEAL\" ou \"$TYPE_WORKOUT_PLAN\")."
                null
            }

            else -> {
                errors += "Type « $type » inconnu (attendu : \"$TYPE_MEAL\" ou \"$TYPE_WORKOUT_PLAN\")."
                null
            }
        }

        return if (errors.isEmpty() && document != null) ImportResult.Success(document, warnings)
        else ImportResult.Failure(errors.ifEmpty { listOf("Fichier invalide.") })
    }

    private inline fun <reified T> decode(element: JsonElement?, field: String, errors: MutableList<String>): T? {
        if (element == null || element !is JsonObject) {
            errors += "Objet « $field » manquant."
            return null
        }
        return try {
            json.decodeFromJsonElement<T>(element)
        } catch (e: SerializationException) {
            errors += "Objet « $field » mal formé : un champ n'a pas le bon type (texte, nombre ou liste)."
            null
        } catch (e: IllegalArgumentException) {
            errors += "Objet « $field » mal formé."
            null
        }
    }

    private fun validateMeal(dto: MealDto, errors: MutableList<String>): ImportedMeal? {
        val start = errors.size
        val eatenAt = dto.eatenAt?.let { parseDateTime(it) }
        if (eatenAt == null) errors += "Repas : « eaten_at » manquant ou date invalide."

        val type = dto.mealType?.let { MealType.fromCode(it) }
        if (type == null) errors += "Repas : « meal_type » doit être petit-dejeuner, dejeuner, diner ou collation."

        val items = dto.items.orEmpty()
        if (items.isEmpty()) errors += "Repas : la liste « items » doit contenir au moins un aliment."
        if (items.size > MAX_ITEMS) errors += "Repas : pas plus de $MAX_ITEMS aliments."
        checkLength("Repas", "title", dto.title, MAX_NAME_LENGTH, errors)
        checkLength("Repas", "notes", dto.notes, MAX_TEXT_LENGTH, errors)

        val foods = items.mapIndexedNotNull { index, item -> validateFood(item, index + 1, errors) }

        if (errors.size > start || eatenAt == null || type == null) return null
        return ImportedMeal(
            eatenAt = eatenAt,
            type = type,
            title = dto.title.cleaned(),
            notes = dto.notes.cleaned(),
            items = foods,
        )
    }

    private fun validateFood(dto: FoodDto, position: Int, errors: MutableList<String>): ImportedFood? {
        val start = errors.size
        val label = "Aliment n°$position" + (dto.name.cleaned()?.let { " ($it)" } ?: "")
        val name = dto.name.cleaned()
        if (name == null) errors += "$label : « name » manquant."
        if (dto.kcal == null) errors += "$label : « kcal » manquant."

        listOf(
            "quantity_g" to dto.quantityG, "kcal" to dto.kcal, "protein_g" to dto.proteinG,
            "carbs_g" to dto.carbsG, "fat_g" to dto.fatG, "fiber_g" to dto.fiberG,
        ).forEach { (field, value) ->
            if (value != null && (value < 0 || value.isNaN() || value.isInfinite())) {
                errors += "$label : « $field » doit être un nombre positif."
            } else if (value != null && value > MAX_ITEM_VALUE) {
                errors += "$label : « $field » dépasse $MAX_ITEM_VALUE, la valeur semble fausse."
            }
        }
        checkLength(label, "name", dto.name, MAX_NAME_LENGTH, errors)
        checkLength(label, "portion", dto.portion, MAX_NAME_LENGTH, errors)
        checkLength(label, "assumptions", dto.assumptions, MAX_TEXT_LENGTH, errors)

        val source = when (val s = dto.source) {
            null -> FoodSource.CLAUDE_ESTIMATE
            else -> FoodSource.fromCode(s) ?: run {
                errors += "$label : « source » doit être estimation_claude, estimation_ia, openfoodfacts ou manuel."
                null
            }
        }
        val confidence = dto.confidence?.let { c ->
            Confidence.fromCode(c) ?: run {
                errors += "$label : « confidence » doit être haute, moyenne ou faible."
                null
            }
        }

        val drink = dto.drink?.let { d ->
            DrinkKind.fromCode(d) ?: run {
                errors += "$label : « drink » doit être boisson ou alcool (ou absent pour un aliment)."
                null
            }
        }

        if (errors.size > start || name == null || dto.kcal == null || source == null) return null
        return ImportedFood(
            name = name,
            quantityG = dto.quantityG,
            portion = dto.portion.cleaned(),
            kcal = dto.kcal,
            proteinG = dto.proteinG,
            carbsG = dto.carbsG,
            fatG = dto.fatG,
            fiberG = dto.fiberG,
            source = source,
            confidence = confidence,
            assumptions = dto.assumptions.cleaned(),
            offCode = dto.offCode.cleaned(),
            drink = drink,
        )
    }

    private fun validatePlan(dto: PlanDto, errors: MutableList<String>, warnings: MutableList<String>): ImportedPlan? {
        val start = errors.size
        val name = dto.name.cleaned()
        if (name == null) errors += "Plan : « name » manquant."
        if (dto.weeks != null && dto.weeks < 1) errors += "Plan : « weeks » doit être au moins 1."

        val sessions = dto.sessions.orEmpty()
        if (sessions.isEmpty()) errors += "Plan : la liste « sessions » doit contenir au moins une séance."
        if (sessions.size > MAX_ITEMS) errors += "Plan : pas plus de $MAX_ITEMS séances."
        checkLength("Plan", "name", dto.name, MAX_NAME_LENGTH, errors)
        checkLength("Plan", "description", dto.description, MAX_TEXT_LENGTH, errors)

        val validSessions = sessions.mapIndexedNotNull { i, s -> validateSession(s, i + 1, errors, warnings) }
        if (errors.size > start || name == null) return null
        return ImportedPlan(name, dto.description.cleaned(), dto.weeks, validSessions)
    }

    private fun validateSession(dto: SessionDto, position: Int, errors: MutableList<String>, warnings: MutableList<String>): ImportedSession? {
        val start = errors.size
        val label = "Séance n°$position" + (dto.title.cleaned()?.let { " ($it)" } ?: "")
        val title = dto.title.cleaned()
        if (title == null) errors += "$label : « title » manquant."
        if (dto.dayOfWeek != null && dto.dayOfWeek !in 1..7) errors += "$label : « day_of_week » doit aller de 1 (lundi) à 7 (dimanche)."
        val variants = dto.variants.orEmpty()
        if (variants.isEmpty()) errors += "$label : la liste « variants » doit contenir au moins une version de la séance."
        val valid = variants.mapIndexedNotNull { i, v -> validateVariant(v, "$label, version n°${i + 1}", errors, warnings) }
        val durations = variants.mapNotNull { it.durationMin }
        if (durations.size != durations.toSet().size) errors += "$label : deux versions ont la même « duration_min »."

        if (errors.size > start || title == null) return null
        return ImportedSession(
            title = title,
            dayOfWeek = dto.dayOfWeek,
            notes = dto.notes.cleaned(),
            variants = valid.sortedBy { it.durationMin },
        )
    }

    private fun validateVariant(dto: VariantDto, label: String, errors: MutableList<String>, warnings: MutableList<String>): ImportedSessionVariant? {
        val start = errors.size
        val fullLabel = label + (dto.durationMin?.let { " ($it min)" } ?: "")
        if (dto.durationMin == null || dto.durationMin < 1) errors += "$fullLabel : « duration_min » manquant ou invalide."

        val exercises = dto.exercises.orEmpty()
        if (exercises.isEmpty()) errors += "$fullLabel : la liste « exercises » doit contenir au moins un exercice."
        val valid = exercises.mapIndexedNotNull { i, e -> validateExercise(e, "$fullLabel, exercice n°${i + 1}", errors, warnings) }

        if (errors.size > start || dto.durationMin == null) return null
        return ImportedSessionVariant(
            durationMin = dto.durationMin,
            warmUp = dto.warmUp.cleaned(),
            coolDown = dto.coolDown.cleaned(),
            exercises = valid,
        )
    }

    private fun validateExercise(dto: ExerciseDto, label: String, errors: MutableList<String>, warnings: MutableList<String>): ImportedExercise? {
        val start = errors.size
        val name = dto.name.cleaned()
        val fullLabel = label + (name?.let { " ($it)" } ?: "")
        if (name == null) errors += "$fullLabel : « name » manquant."
        if (dto.sets == null || dto.sets < 1) errors += "$fullLabel : « sets » doit être au moins 1."
        when {
            dto.reps == null && dto.durationS == null -> errors += "$fullLabel : indiquer « reps » ou « duration_s »."
            dto.reps != null && dto.durationS != null -> errors += "$fullLabel : indiquer « reps » ou « duration_s », pas les deux."
        }
        if (dto.reps != null && dto.reps < 1) errors += "$fullLabel : « reps » doit être au moins 1."
        if (dto.durationS != null && dto.durationS < 1) errors += "$fullLabel : « duration_s » doit être au moins 1."
        if (dto.restS != null && dto.restS < 0) errors += "$fullLabel : « rest_s » ne peut pas être négatif."
        // Lien vidéo facultatif : un lien abîmé ne bloque jamais tout le plan (souvent écrit par une autre IA).
        val videoUrl = videoUrlOrNull(fullLabel, "video_url", dto.videoUrl, warnings)
        val easierVideoUrl = videoUrlOrNull(fullLabel, "easier_video_url", dto.easierVideoUrl, warnings)
        val harderVideoUrl = videoUrlOrNull(fullLabel, "harder_video_url", dto.harderVideoUrl, warnings)

        if (errors.size > start || name == null || dto.sets == null) return null
        return ImportedExercise(
            name = name,
            sets = dto.sets,
            reps = dto.reps,
            durationS = dto.durationS,
            restS = dto.restS,
            instructions = dto.instructions.cleaned(),
            easierVariant = dto.easierVariant.cleaned(),
            harderVariant = dto.harderVariant.cleaned(),
            notes = dto.notes.cleaned(),
            videoUrl = videoUrl,
            easierVideoUrl = easierVideoUrl,
            harderVideoUrl = harderVideoUrl,
        )
    }

    /**
     * Un lien vidéo est ouvert par le téléphone : on ne garde qu'une adresse web https complète.
     * Les petites fautes courantes sont réparées (espaces, http://, « www. » sans https://, lien Markdown) ;
     * sinon le lien est ignoré avec un avertissement, sans refuser l'import.
     */
    private fun videoUrlOrNull(label: String, field: String, value: String?, warnings: MutableList<String>): String? {
        val raw = value.cleaned() ?: return null
        val repaired = repairUrl(raw)
        if (isWebLink(repaired)) return repaired
        warnings += "$label : lien « $field » ignoré (il doit commencer par https://)."
        return null
    }

    private fun repairUrl(raw: String): String {
        var url = MARKDOWN_LINK.matchEntire(raw)?.groupValues?.get(1) ?: raw
        url = url.trim().trim('<', '>', '"', '\'').replace(Regex("\\s+"), "+")
        if (url.startsWith("http://", ignoreCase = true)) url = "https://" + url.substring("http://".length)
        if (!url.contains("://") && BARE_HOST.containsMatchIn(url)) url = "https://$url"
        return url
    }

    private fun isWebLink(url: String): Boolean {
        if (url.length > MAX_URL_LENGTH || url.any { it.isWhitespace() }) return false
        return try {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        } catch (_: URISyntaxException) {
            false
        }
    }

    private fun parseDateTime(value: String): OffsetDateTime? {
        val v = value.trim()
        return try {
            OffsetDateTime.parse(v)
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(v).atZone(defaultZone).toOffsetDateTime()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private fun checkLength(label: String, field: String, value: String?, max: Int, errors: MutableList<String>) {
        if (value != null && value.length > max) errors += "$label : « $field » est trop long ($max caractères au maximum)."
    }

    private fun String.isUuid(): Boolean = UUID_REGEX.matches(this)

    private fun fail(message: String) = ImportResult.Failure(listOf(message))

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val FORMAT = "healthy-import"
        const val SCHEMA_VERSION = 1
        const val TYPE_MEAL = "meal"
        const val TYPE_WORKOUT_PLAN = "workout_plan"
        const val TYPE_TRAINING_REPORT = "training_report"
        const val TYPE_MEAL_REPORT = "meal_report"
        const val TYPE_BACKUP = "backup"

        // Garde-fous : les totaux viennent des aliments du fichier, une valeur absurde fausserait le journal.
        const val MAX_ITEMS = 100
        const val MAX_ITEM_VALUE = 5000
        const val MAX_NAME_LENGTH = 200
        const val MAX_TEXT_LENGTH = 2000
        const val MAX_URL_LENGTH = 500

        private val UUID_REGEX =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** Extrait l'objet JSON d'un message (gère les blocs ```json et le texte autour). */
        fun extractJsonObject(text: String): String? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return text.substring(start, end + 1)
        }
    }
}
