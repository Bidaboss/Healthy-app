package com.bidaboss.healthy.core.importformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * Écrit un repas en fichier d'import complet (type `meal`), pour « Partager ce repas » : un proche
 * l'importe tel quel sur son téléphone. Nouvel `id` à chaque partage, jamais de `replaces`.
 * Fonction pure : ni horloge ni hasard ici.
 */
object MealWriter {
    private val json = Json { prettyPrint = true }

    fun write(meal: ImportedMeal, id: String, createdAt: OffsetDateTime): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", ImportParser.FORMAT)
            put("schema_version", ImportParser.SCHEMA_VERSION)
            put("type", ImportParser.TYPE_MEAL)
            put("id", id)
            put("created_at", createdAt.iso())
            put(
                "meal",
                buildJsonObject {
                    put("eaten_at", meal.eatenAt.iso())
                    put("meal_type", meal.type.code)
                    meal.title?.let { put("title", it) }
                    meal.notes?.let { put("notes", it) }
                    put("items", JsonArray(meal.items.map(::item)))
                },
            )
        },
    )

    private fun item(food: ImportedFood) = buildJsonObject {
        put("name", food.name)
        food.quantityG?.let { putNumber("quantity_g", it) }
        food.portion?.let { put("portion", it) }
        putNumber("kcal", food.kcal)
        food.proteinG?.let { putNumber("protein_g", it) }
        food.carbsG?.let { putNumber("carbs_g", it) }
        food.fatG?.let { putNumber("fat_g", it) }
        food.fiberG?.let { putNumber("fiber_g", it) }
        put("source", food.source.code)
        food.confidence?.let { put("confidence", it.code) }
        food.assumptions?.let { put("assumptions", it) }
        food.offCode?.let { put("off_code", it) }
        food.drink?.let { put("drink", it.code) }
    }

    /** À 0,1 près ; un nombre rond s'écrit sans « .0 ». */
    private fun JsonObjectBuilder.putNumber(key: String, value: Double) {
        val rounded = (value * 10).roundToLong()
        if (rounded % 10 == 0L) put(key, rounded / 10) else put(key, rounded / 10.0)
    }

    private fun OffsetDateTime.iso(): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(truncatedTo(ChronoUnit.SECONDS))
}
