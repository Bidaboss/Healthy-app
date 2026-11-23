package com.bidaboss.healthy.core.mealreport

import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.core.importformat.ImportResult
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MealReportWriterTest {

    private val example = File(
        System.getProperty("healthy.examplesDir") ?: error("healthy.examplesDir non défini"),
        "bilan-repas.json",
    ).readText()

    private val start = LocalDate.of(2026, 11, 7)

    // Le même bilan que docs/examples/bilan-repas.json, repas donnés dans le désordre.
    private val report = MealReport(
        periodStart = start,
        periodEnd = start.plusDays(6),
        maintenanceKcal = 2450,
        maintenanceAdjustmentKcal = 140,
        proteinTargetG = 128,
        fiberTargetG = 30,
        days = (0..4).map { ReportDay(start.plusDays(it.toLong()), 0, emptyList()) } + listOf(
            ReportDay(
                LocalDate.of(2026, 11, 12),
                1200,
                listOf(
                    ReportMeal(
                        "dejeuner",
                        LocalDateTime.of(2026, 11, 12, 12, 30),
                        "Poulet riz",
                        listOf(
                            ReportFood("Poulet rôti", 120.0, null, 230.0, proteinG = 33.0, fatG = 10.0),
                            ReportFood("Riz basmati cuit", 200.5, null, 260.0, proteinG = 5.5, carbsG = 56.0, fatG = 1.0, fiberG = 1.0),
                            ReportFood("Haricots verts", 150.0, null, 90.0, proteinG = 3.0, carbsG = 10.0, fatG = 5.0, fiberG = 5.0),
                        ),
                    ),
                    ReportMeal(
                        "petit-dejeuner",
                        LocalDateTime.of(2026, 11, 12, 7, 45),
                        null,
                        listOf(
                            ReportFood("Pain complet", 80.0, null, 200.0, proteinG = 3.0, carbsG = 30.0, fatG = 5.0, fiberG = 3.0),
                            ReportFood("Café au lait", null, "1 bol", 100.0, proteinG = 3.0, carbsG = 5.0, fatG = 5.0),
                        ),
                    ),
                ),
            ),
            ReportDay(
                LocalDate.of(2026, 11, 13),
                500,
                listOf(
                    ReportMeal(
                        "collation",
                        LocalDateTime.of(2026, 11, 13, 16, 0),
                        null,
                        listOf(ReportFood("Pomme et amandes", null, null, 150.0, proteinG = 4.0, carbsG = 20.0, fatG = 6.0, fiberG = 3.0)),
                    ),
                    // Repas noté en calories seules : aucune valeur de macros.
                    ReportMeal(
                        "diner",
                        LocalDateTime.of(2026, 11, 13, 20, 0),
                        "Repas au restaurant",
                        listOf(ReportFood("Repas au restaurant", null, null, 800.0)),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `le bilan ecrit est exactement l'exemple de la documentation`() {
        val written = MealReportWriter.write(report, "3f6a2b81-9c4d-4e7f-8a1b-2d5c6e7f8091", LocalDateTime.of(2026, 11, 13, 21, 30))
        assertEquals(Json.parseToJsonElement(example), Json.parseToJsonElement(written))
    }

    @Test
    fun `un repas en calories seules est marque, les autres non`() {
        val written = MealReportWriter.write(report, "3f6a2b81-9c4d-4e7f-8a1b-2d5c6e7f8091", LocalDateTime.of(2026, 11, 13, 21, 30))
        assertEquals(1, Regex("\"macros_missing\": true").findAll(written).count(), written)
        assertEquals(1, Regex("\"meals_without_macros\": 1").findAll(written).count(), written)
        assertTrue(report.days.last().meals.last().macrosMissing)
        assertFalse(report.days.last().meals.first().macrosMissing)
    }

    @Test
    fun `sans profil le bilan garde seulement la cible de fibres`() {
        val written = MealReportWriter.write(
            report.copy(maintenanceKcal = null, maintenanceAdjustmentKcal = null, proteinTargetG = null),
            "3f6a2b81-9c4d-4e7f-8a1b-2d5c6e7f8091",
            LocalDateTime.of(2026, 11, 13, 21, 30),
        )
        assertFalse("maintenance_kcal" in written)
        assertFalse("maintenance_adjustment_kcal" in written)
        assertTrue("\"fiber_target_g\": 30" in written, written)
    }

    @Test
    fun `un bilan repas colle dans l'import est refuse avec une explication`() {
        val result = ImportParser().parse(example)
        assertIs<ImportResult.Failure>(result)
        assertTrue(result.errors.single().contains("bilan repas"), result.errors.toString())
    }
}
