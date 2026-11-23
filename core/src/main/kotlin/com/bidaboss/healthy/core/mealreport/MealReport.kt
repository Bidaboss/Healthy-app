package com.bidaboss.healthy.core.mealreport

import com.bidaboss.healthy.core.importformat.ImportParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** Bilan repas exporté pour Claude (voir docs/format-import.md, type `meal_report`). */
data class MealReport(
    /** Premier et dernier jour couverts, inclus, en date locale du téléphone. */
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    /** Repères du profil, null sans profil. */
    val maintenanceKcal: Int?,
    /** Part du maintien venant de l'ajustement adopté (0 = formule seule), null sans profil. */
    val maintenanceAdjustmentKcal: Int? = null,
    val proteinTargetG: Int?,
    val fiberTargetG: Int,
    /** Un jour par date de la période, même sans repas. */
    val days: List<ReportDay>,
)

data class ReportDay(
    val date: LocalDate,
    val waterMl: Int,
    val meals: List<ReportMeal>,
) {
    val kcal: Double get() = meals.sumOf { m -> m.items.sumOf { it.kcal } }
    val proteinG: Double get() = meals.sumOf { m -> m.items.sumOf { it.proteinG ?: 0.0 } }
    val carbsG: Double get() = meals.sumOf { m -> m.items.sumOf { it.carbsG ?: 0.0 } }
    val fatG: Double get() = meals.sumOf { m -> m.items.sumOf { it.fatG ?: 0.0 } }
    val fiberG: Double get() = meals.sumOf { m -> m.items.sumOf { it.fiberG ?: 0.0 } }
}

data class ReportMeal(
    /** Code du type de repas (`petit-dejeuner`, `dejeuner`, `diner`, `collation`). */
    val mealType: String,
    val eatenAt: LocalDateTime,
    val title: String?,
    val items: List<ReportFood>,
) {
    /** Repas noté en calories seules : aucun aliment n'a de protéines, glucides, lipides ni fibres. */
    val macrosMissing: Boolean
        get() = items.isNotEmpty() && items.all { it.proteinG == null && it.carbsG == null && it.fatG == null && it.fiberG == null }
}

data class ReportFood(
    val name: String,
    val quantityG: Double?,
    val portion: String?,
    val kcal: Double,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
)

/** Écrit un [MealReport] dans le format documenté. Fonction pure : ni horloge ni hasard ici. */
object MealReportWriter {
    private val json = Json { prettyPrint = true }

    fun write(report: MealReport, id: String, createdAt: LocalDateTime): String {
        val root = buildJsonObject {
            put("format", ImportParser.FORMAT)
            put("schema_version", ImportParser.SCHEMA_VERSION)
            put("type", ImportParser.TYPE_MEAL_REPORT)
            put("id", id)
            put("created_at", createdAt.truncatedTo(ChronoUnit.SECONDS).toIsoString())
            put(
                "report",
                buildJsonObject {
                    put("period_start", report.periodStart.toString())
                    put("period_end", report.periodEnd.toString())
                    put(
                        "profile",
                        buildJsonObject {
                            report.maintenanceKcal?.let { put("maintenance_kcal", it) }
                            report.maintenanceAdjustmentKcal?.let { put("maintenance_adjustment_kcal", it) }
                            report.proteinTargetG?.let { put("protein_target_g", it) }
                            put("fiber_target_g", report.fiberTargetG)
                        },
                    )
                    put("days", JsonArray(report.days.sortedBy { it.date }.map(::day)))
                },
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun day(day: ReportDay) = buildJsonObject {
        put("date", day.date.toString())
        put("kcal", day.kcal.roundToLong())
        putGrams("protein_g", day.proteinG)
        putGrams("carbs_g", day.carbsG)
        putGrams("fat_g", day.fatG)
        putGrams("fiber_g", day.fiberG)
        put("water_ml", day.waterMl)
        // Les repas en calories seules comptent dans `kcal` mais pas dans les protéines ni les fibres.
        day.meals.count { it.macrosMissing }.takeIf { it > 0 }?.let { put("meals_without_macros", it) }
        put("meals", JsonArray(day.meals.sortedBy { it.eatenAt }.map(::meal)))
    }

    private fun meal(meal: ReportMeal) = buildJsonObject {
        put("meal_type", meal.mealType)
        put("eaten_at", meal.eatenAt.truncatedTo(ChronoUnit.SECONDS).toIsoString())
        meal.title?.let { put("title", it) }
        put("kcal", meal.items.sumOf { it.kcal }.roundToLong())
        if (meal.macrosMissing) put("macros_missing", true)
        put(
            "items",
            JsonArray(
                meal.items.map { food ->
                    buildJsonObject {
                        put("name", food.name)
                        food.quantityG?.let { putGrams("quantity_g", it) }
                        food.portion?.let { put("portion", it) }
                        put("kcal", food.kcal.roundToLong())
                    }
                },
            ),
        )
    }

    /** Grammes à 0,1 près ; un nombre rond s'écrit sans « .0 ». */
    private fun JsonObjectBuilder.putGrams(key: String, value: Double) {
        val rounded = (value * 10).roundToLong()
        if (rounded % 10 == 0L) put(key, rounded / 10) else put(key, rounded / 10.0)
    }

    private fun LocalDateTime.toIsoString(): String = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(this)
}
