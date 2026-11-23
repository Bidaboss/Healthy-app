package com.bidaboss.healthy.core.importformat

import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportParserTest {

    private val parser = ImportParser(defaultZone = ZoneId.of("Europe/Paris"))

    private val examplesDir = File(
        System.getProperty("healthy.examplesDir") ?: error("healthy.examplesDir non défini"),
    )

    private fun example(name: String) = File(examplesDir, name).readText()

    private fun success(text: String): ImportDocument {
        val result = parser.parse(text)
        if (result is ImportResult.Failure) error("Import refusé : ${result.errors}")
        return (result as ImportResult.Success).document
    }

    private fun errors(text: String): List<String> {
        val result = parser.parse(text)
        assertIs<ImportResult.Failure>(result, "L'import aurait dû être refusé")
        return result.errors
    }

    @Test
    fun `tous les exemples de la documentation sont valides`() {
        // Les fichiers « bilan-… » sont des exports de l'app (voir TrainingReportWriterTest), pas des imports.
        val files = examplesDir.listFiles { f -> f.extension == "json" && !f.name.startsWith("bilan-") }.orEmpty()
        assertTrue(files.size >= 3, "exemples introuvables dans $examplesDir")
        files.forEach { f ->
            val result = parser.parse(f.readText())
            assertIs<ImportResult.Success>(result, "${f.name} : $result")
        }
    }

    @Test
    fun `exemple de dejeuner lu correctement`() {
        val doc = assertIs<ImportDocument.Meal>(success(example("repas-dejeuner.json")))
        assertEquals("3f2b8c1e-6a4d-4c8e-9b7a-1d2e3f4a5b6c", doc.id)
        assertEquals(MealType.LUNCH, doc.meal.type)
        assertEquals("2026-11-11", doc.meal.localDate.toString())
        assertEquals(6, doc.meal.items.size)
        assertEquals(743.0, doc.meal.totalKcal, 0.001)
        val riz = doc.meal.items[1]
        assertEquals("Riz blanc cuit", riz.name)
        assertEquals(Confidence.LOW, riz.confidence)
        assertEquals(FoodSource.CLAUDE_ESTIMATE, riz.source)
        assertEquals("Riz blanc nature, sans beurre.", riz.assumptions)
    }

    @Test
    fun `boisson et alcool lus, aliment ordinaire sans drink`() {
        val items = assertIs<ImportDocument.Meal>(success(example("repas-dejeuner.json"))).meal.items
        assertNull(items[0].drink)
        assertEquals(DrinkKind.DRINK, items.single { it.name == "Jus d'orange" }.drink)
        assertEquals(DrinkKind.ALCOHOL, items.single { it.name == "Vin rouge" }.drink)
    }

    @Test
    fun `un drink inconnu est refuse avec une explication`() {
        val json = example("repas-dejeuner.json").replace("\"drink\": \"boisson\"", "\"drink\": \"liquide\"")
        val result = ImportParser(ZoneId.of("Europe/Paris")).parse(json)
        val failure = assertIs<ImportResult.Failure>(result)
        assertTrue(failure.errors.any { "drink" in it && "boisson ou alcool" in it }, failure.errors.toString())
    }

    @Test
    fun `collation minimale avec valeurs par defaut`() {
        val doc = assertIs<ImportDocument.Meal>(success(example("repas-collation-minimal.json")))
        val pomme = doc.meal.items.single()
        assertEquals(FoodSource.CLAUDE_ESTIMATE, pomme.source)
        assertNull(pomme.proteinG)
        assertNull(doc.meal.title)
    }

    @Test
    fun `exemple de plan sportif lu correctement`() {
        val doc = assertIs<ImportDocument.WorkoutPlan>(success(example("plan-sport-maison.json")))
        assertEquals(3, doc.plan.sessions.size)
        val seanceA = doc.plan.sessions[0]
        assertEquals(listOf(15, 30, 45), seanceA.variants.map { it.durationMin })
        val trenteMinutes = seanceA.variants[1]
        assertEquals("5 minutes : rotations des bras, jumping jacks.", trenteMinutes.warmUp)
        assertEquals("Pompes genoux au sol", trenteMinutes.exercises[0].easierVariant)
        assertEquals("https://www.youtube.com/results?search_query=pompes+exercice", trenteMinutes.exercises[0].videoUrl)
        assertEquals(
            "https://www.youtube.com/results?search_query=pompes+genoux+au+sol",
            trenteMinutes.exercises[0].easierVideoUrl,
        )
        // Le lien vidéo est optionnel.
        assertNull(seanceA.variants[0].exercises[0].videoUrl)
        val planche = trenteMinutes.exercises[2]
        assertEquals("Planche", planche.name)
        assertEquals(40, planche.durationS)
        assertNull(planche.reps)
    }

    @Test
    fun `seance sans version ou avec durees en double refusee`() {
        val sansVersion = """{"format":"healthy-import","schema_version":1,"type":"workout_plan","id":"11111111-1111-4111-8111-111111111111","created_at":"2026-11-11T10:00:00Z",
            "plan":{"name":"P","sessions":[{"title":"A","variants":[]}]}}"""
        assertTrue(errors(sansVersion).any { it.contains("au moins une version") })
        val exo = """{"name":"Squats","sets":1,"reps":5}"""
        val doublon = """{"format":"healthy-import","schema_version":1,"type":"workout_plan","id":"11111111-1111-4111-8111-111111111111","created_at":"2026-11-11T10:00:00Z",
            "plan":{"name":"P","sessions":[{"title":"A","variants":[
              {"duration_min":15,"exercises":[$exo]},{"duration_min":15,"exercises":[$exo]}]}]}}"""
        assertTrue(errors(doublon).any { it.contains("même « duration_min »") })
    }

    @Test
    fun `json entoure de texte et de balises markdown`() {
        val message = "Voici ton import :\n```json\n" + example("repas-collation-minimal.json") + "\n```\nBon appétit !"
        assertIs<ImportDocument.Meal>(success(message))
    }

    @Test
    fun `alias anglais acceptes et champs inconnus ignores`() {
        val text = example("repas-collation-minimal.json")
            .replace("\"collation\"", "\"snack\"")
            .replace("\"kcal\": 78", "\"kcal\": 78, \"confidence\": \"high\", \"champ_futur\": [1, 2]")
        val doc = assertIs<ImportDocument.Meal>(success(text))
        assertEquals(MealType.SNACK, doc.meal.type)
        assertEquals(Confidence.HIGH, doc.meal.items.single().confidence)
    }

    @Test
    fun `date sans decalage interpretee en heure locale`() {
        val text = example("repas-collation-minimal.json").replace("2026-11-11T16:00:00+01:00", "2026-07-01T16:00:00")
        val doc = assertIs<ImportDocument.Meal>(success(text))
        assertEquals("+02:00", doc.meal.eatenAt.offset.id)
    }

    @Test
    fun `champ replaces lu quand il est present`() {
        val text = example("repas-collation-minimal.json").replace("\"type\": \"meal\",", "\"type\": \"meal\", \"replaces\": \"22222222-2222-4222-8222-22222222222A\",")
        assertEquals("22222222-2222-4222-8222-22222222222a", success(text).replaces)
        assertNull(success(example("repas-collation-minimal.json")).replaces)
    }

    @Test
    fun `id qui n'est pas un UUID refuse`() {
        val text = example("repas-collation-minimal.json").replace("a1b2c3d4-0000-4000-8000-000000000001", "repas-1")
        assertTrue(errors(text).any { it.contains("UUID") })
    }

    @Test
    fun `valeur absurde et texte trop long refuses`() {
        val text = example("repas-collation-minimal.json")
            .replace("\"kcal\": 78", "\"kcal\": 78000")
            .replace("\"Pomme\"", "\"" + "a".repeat(201) + "\"")
        val e = errors(text)
        assertTrue(e.any { it.contains("« kcal » dépasse") }, e.toString())
        assertTrue(e.any { it.contains("trop long") }, e.toString())
    }

    @Test
    fun `version future refusee avec message clair`() {
        val e = errors(example("repas-collation-minimal.json").replace("\"schema_version\": 1", "\"schema_version\": 2"))
        assertTrue(e.single().contains("Mets l'app à jour"), e.toString())
    }

    @Test
    fun `texte sans json refuse`() {
        assertTrue(errors("bonjour").single().startsWith("Aucun objet JSON"))
    }

    @Test
    fun `json tronque refuse`() {
        val text = example("repas-dejeuner.json")
        assertTrue(errors(text.substring(0, text.length / 2) + "}").single().startsWith("JSON illisible"))
    }

    @Test
    fun `mauvais format refuse`() {
        val e = errors("""{"format":"autre","schema_version":1}""")
        assertTrue(e.single().contains("pas un fichier Healthy"))
    }

    @Test
    fun `aliment sans kcal et quantite negative refuses`() {
        val text = example("repas-collation-minimal.json")
            .replace("\"quantity_g\": 150, \"kcal\": 78", "\"quantity_g\": -5")
        val e = errors(text)
        assertTrue(e.any { it.contains("« kcal » manquant") }, e.toString())
        assertTrue(e.any { it.contains("« quantity_g » doit être un nombre positif") }, e.toString())
    }

    @Test
    fun `repas sans aliment refuse`() {
        val text = example("repas-collation-minimal.json")
            .replace(Regex("\"items\": \\[[^]]*]"), "\"items\": []")
        assertTrue(errors(text).any { it.contains("au moins un aliment") })
    }

    @Test
    fun `exercice avec reps et duree refuse`() {
        val text = example("plan-sport-maison.json")
            .replace("\"duration_s\": 40", "\"reps\": 5, \"duration_s\": 40")
        assertTrue(errors(text).any { it.contains("pas les deux") })
    }

    @Test
    fun `source estimation_ia acceptee`() {
        val text = example("repas-dejeuner.json").replaceFirst("\"estimation_claude\"", "\"estimation_ia\"")
        val doc = assertIs<ImportDocument.Meal>(success(text))
        assertEquals(FoodSource.AI_ESTIMATE, doc.meal.items[0].source)
    }

    /** Tous les liens vidéo principaux du plan importé, et les avertissements. */
    private fun videos(text: String): Pair<List<String>, List<String>> {
        val result = assertIs<ImportResult.Success>(parser.parse(text), "L'import aurait dû passer")
        val plan = assertIs<ImportDocument.WorkoutPlan>(result.document).plan
        return plan.sessions.flatMap { it.variants }.flatMap { it.exercises }.mapNotNull { it.videoUrl } to result.warnings
    }

    @Test
    fun `lien video dangereux ou casse ignore avec un avertissement, sans bloquer le plan`() {
        val base = example("plan-sport-maison.json")
        listOf("javascript:alert(1)", "intent://scan", "pompes", "https://", "ftp://x").forEach { bad ->
            val (urls, warnings) = videos(base.replace("https://www.youtube.com/results?search_query=pompes+exercice", bad))
            assertTrue(urls.none { it.contains("pompes+exercice") || it == bad }, "$bad : $urls")
            assertTrue(warnings.any { it.contains("lien « video_url » ignoré") }, "$bad : $warnings")
        }
        val badHarder = base.replace("https://www.youtube.com/results?search_query=pompes+pieds", "ftp://x")
        assertTrue(assertIs<ImportResult.Success>(parser.parse(badHarder)).warnings.any { it.contains("« harder_video_url »") })
    }

    @Test
    fun `petites fautes de lien video reparees`() {
        val base = example("plan-sport-maison.json")
        val expected = "https://www.youtube.com/results?search_query=pompes+exercice"
        listOf(
            // Espaces laissés par une autre IA (cas réel : programme fait avec Gemini, 11/11/2026).
            "https://www.youtube.com/results?search_query=pompes exercice",
            "http://www.youtube.com/results?search_query=pompes+exercice",
            "www.youtube.com/results?search_query=pompes+exercice",
            "[Vidéo](https://www.youtube.com/results?search_query=pompes+exercice)",
        ).forEach { variant ->
            val (urls, warnings) = videos(base.replace(expected, variant))
            assertTrue(expected in urls, "$variant : $urls")
            assertTrue(warnings.isEmpty(), "$variant : $warnings")
        }
    }

    @Test
    fun `type inconnu et id manquant signales ensemble`() {
        val e = errors("""{"format":"healthy-import","schema_version":1,"type":"recette","created_at":"2026-11-11T10:00:00Z"}""")
        assertTrue(e.any { it.contains("« id » manquant") }, e.toString())
        assertTrue(e.any { it.contains("Type « recette » inconnu") }, e.toString())
    }

    @Test
    fun `mauvais type de champ refuse sans planter`() {
        val text = example("repas-collation-minimal.json").replace("\"kcal\": 78", "\"kcal\": \"beaucoup\"")
        assertTrue(errors(text).any { it.contains("mal formé") })
    }
}
