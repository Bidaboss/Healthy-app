package com.bidaboss.healthy.core.prompts

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PromptsTest {
    private val docs = File(System.getProperty("healthy.examplesDir") ?: error("healthy.examplesDir non défini")).parentFile
    private val markdown = File(docs, "format-import.md").readText()
    private fun example(name: String) = File(docs, "examples/$name").readText()

    @Test
    fun `les sections du format existent toujours`() {
        // Si un titre de docs/format-import.md change, les prompts perdraient le format : ce test casse.
        listOf("Règles générales", "Enveloppe", FormatDoc.MEAL_HEADING, FormatDoc.PLAN_HEADING).forEach {
            assertNotNull(FormatDoc.section(markdown, it), it)
        }
    }

    @Test
    fun `le prompt repas contient le format repas et rien du plan`() {
        val prompt = Prompts.meal(FormatDoc.forType(markdown, FormatDoc.MEAL_HEADING, example("repas-dejeuner.json")))
        assertTrue(prompt.contains("### Aliment (`items[]`)"))
        assertTrue(prompt.contains("\"meal_type\": \"dejeuner\""))
        assertTrue(prompt.contains("estimation_ia"))
        assertTrue(prompt.contains("repas terminé"))
        assertFalse(prompt.contains("### Exercice (`exercises[]`)"))
    }

    @Test
    fun `le prompt sport reprend le profil et le format du plan`() {
        val answers = SportPromptAnswers(
            "Alex", "homme", 38, 176, 75, "actif", "Débutant", "Forme générale", 3, "lundi, mercredi, vendredi", 30,
            "Aine (pubalgie)", "oui", "pas de fentes profondes", "Tapis", "aucun",
        )
        val prompt = Prompts.sportPlan(answers, FormatDoc.forType(markdown, FormatDoc.PLAN_HEADING, example("plan-sport-maison.json")))
        assertTrue(prompt.contains("Zones sensibles ou douleurs : Aine (pubalgie)"))
        assertTrue(prompt.contains("`video_url`"))
        assertTrue(prompt.contains("\"type\": \"workout_plan\""))
        assertFalse(prompt.contains("{"+"prenom}"))
        // Sans maintien adopte, le prompt n'en parle pas.
        assertFalse(prompt.contains("Maintien reel"))
        val adjusted = Prompts.sportPlan(
            answers.copy(maintenance = "Maintien reel estime : environ 2850 kcal par jour"),
            FormatDoc.forType(markdown, FormatDoc.PLAN_HEADING, example("plan-sport-maison.json")),
        )
        assertTrue(adjusted.contains("- Maintien reel estime : environ 2850 kcal par jour"))
    }
}

class PlanWriterTest {
    @Test
    fun `le plan ecrit se relit tel quel dans une enveloppe d'import`() {
        val docs = File(System.getProperty("healthy.examplesDir") ?: error("healthy.examplesDir non défini"))
        val parser = com.bidaboss.healthy.core.importformat.ImportParser()
        val original = (parser.parse(File(docs, "plan-sport-maison.json").readText())
            as com.bidaboss.healthy.core.importformat.ImportResult.Success).document
            as com.bidaboss.healthy.core.importformat.ImportDocument.WorkoutPlan
        val planJson = com.bidaboss.healthy.core.importformat.PlanWriter.write(original.plan)
        val again = """{"format":"healthy-import","schema_version":1,"type":"workout_plan",
            "id":"11111111-1111-4111-8111-111111111111","created_at":"2026-11-11T10:00:00","plan":$planJson}"""
        val reread = (parser.parse(again) as com.bidaboss.healthy.core.importformat.ImportResult.Success).document
            as com.bidaboss.healthy.core.importformat.ImportDocument.WorkoutPlan
        kotlin.test.assertEquals(original.plan, reread.plan)
    }
}
