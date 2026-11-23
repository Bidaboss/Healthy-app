package com.bidaboss.healthy.core.importformat

import kotlinx.serialization.json.Json
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class MealWriterTest {

    private val example = File(
        System.getProperty("healthy.examplesDir") ?: error("healthy.examplesDir non défini"),
        "repas-dejeuner.json",
    ).readText()

    private val parser = ImportParser(ZoneId.of("Europe/Paris"))

    @Test
    fun `un repas reecrit redonne exactement l'exemple de la documentation`() {
        val document = assertIs<ImportResult.Success>(parser.parse(example)).document
        val meal = assertIs<ImportDocument.Meal>(document)
        val written = MealWriter.write(meal.meal, meal.id, meal.createdAt)
        assertEquals(Json.parseToJsonElement(example), Json.parseToJsonElement(written))
    }

    @Test
    fun `un repas partage se reimporte avec les memes aliments et sans replaces`() {
        val meal = assertIs<ImportDocument.Meal>(assertIs<ImportResult.Success>(parser.parse(example)).document).meal
        val written = MealWriter.write(meal, "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", OffsetDateTime.parse("2026-11-13T22:10:00+01:00"))
        val again = assertIs<ImportDocument.Meal>(assertIs<ImportResult.Success>(parser.parse(written)).document)
        assertEquals(meal, again.meal)
        assertEquals(null, again.replaces)
        assertFalse("replaces" in written)
    }
}
