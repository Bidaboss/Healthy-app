package com.bidaboss.healthy.core.trainingreport

import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.core.importformat.ImportResult
import com.bidaboss.healthy.core.workout.DoneValues
import kotlinx.serialization.json.Json
import java.io.File
import com.bidaboss.healthy.core.workout.BodyPart
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrainingReportWriterTest {

    private val example = File(
        System.getProperty("healthy.examplesDir") ?: error("healthy.examplesDir non défini"),
        "bilan-entrainement.json",
    ).readText()

    // Le même bilan que docs/examples/bilan-entrainement.json, donné dans le désordre.
    private val report = TrainingReport(
        periodStart = LocalDate.of(2026, 10, 15),
        periodEnd = LocalDate.of(2026, 11, 11),
        sessions = listOf(
            ReportSession(
                startedAt = LocalDateTime.of(2026, 11, 11, 18, 0),
                planName = "Remise en forme maison",
                sessionTitle = "Bas du corps",
                plannedDurationMin = 15,
                actualDurationMin = 9,
                feeling = "trop_dur",
                painZone = "adducteur droit",
                exercises = listOf(
                    ReportExercise("Squats", 2, 12, null, listOf(12), skipped = true),
                    ReportExercise("Pont fessier", 2, 15, null, emptyList(), skipped = false),
                ),
            ),
            ReportSession(
                startedAt = LocalDateTime.of(2026, 11, 9, 18, 30, 0, 999),
                planName = "Remise en forme maison",
                sessionTitle = "Haut du corps",
                plannedDurationMin = 30,
                actualDurationMin = 27,
                feeling = "ok",
                painZone = null,
                exercises = listOf(
                    ReportExercise("Pompes sur les genoux", 3, 10, null, listOf(10, 10, 8), skipped = false),
                    ReportExercise("Planche", 2, null, 30, listOf(30, 22), skipped = false),
                ),
            ),
        ),
        activities = listOf(ReportActivity(LocalDate.of(2026, 11, 8), "Marche", 45, listOf(BodyPart.CARDIO, BodyPart.LEGS))),
    )

    @Test
    fun `le bilan ecrit est exactement l'exemple de la documentation`() {
        val written = TrainingReportWriter.write(
            report,
            id = "7c1d9e42-5b3a-4f8e-a6d2-0e9f8b7c6a51",
            createdAt = LocalDateTime.of(2026, 11, 11, 19, 5),
        )
        assertEquals(Json.parseToJsonElement(example), Json.parseToJsonElement(written))
    }

    @Test
    fun `un bilan vide reste un fichier valide`() {
        val written = TrainingReportWriter.write(
            TrainingReport(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 11), emptyList(), emptyList()),
            id = "7c1d9e42-5b3a-4f8e-a6d2-0e9f8b7c6a51",
            createdAt = LocalDateTime.of(2026, 11, 11, 19, 5),
        )
        assertTrue("\"sessions\": []" in written, written)
        assertTrue("\"activities\": []" in written, written)
    }

    @Test
    fun `un bilan colle dans l'import est refuse avec une explication`() {
        val result = ImportParser().parse(example)
        assertIs<ImportResult.Failure>(result)
        assertTrue(result.errors.single().contains("bilan"), result.errors.toString())
    }

    @Test
    fun `les series faites se relisent comme elles ont ete ecrites`() {
        assertEquals("10,8,0", DoneValues.format(listOf(10, 8, 0)))
        assertEquals(listOf(10, 8, 0), DoneValues.parse("10,8,0"))
        assertEquals(emptyList(), DoneValues.parse(DoneValues.format(emptyList())))
        // Texte abîmé : on garde ce qui est lisible.
        assertEquals(listOf(10, 8), DoneValues.parse("10, x,-3,8,"))
    }
}
