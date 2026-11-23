package com.bidaboss.healthy.core.backup

import com.bidaboss.healthy.core.importformat.ImportDocument
import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.core.importformat.ImportResult
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupTest {

    private val parser = ImportParser(ZoneId.of("Europe/Paris"))
    private val id = "3f2b8c1e-6a4d-4c8e-9b7a-1d2e3f4a5b6c"

    @Test
    fun `une sauvegarde ecrite se relit a l'identique`() {
        val content = BackupContent(
            databaseVersion = 4,
            tables = mapOf(
                "meals" to listOf(mapOf("id" to 1L, "title" to "Déjeuner « maison »", "import_id" to null)),
                "meal_items" to listOf(mapOf("id" to 7L, "meal_id" to 1L, "kcal" to 195.5)),
                "weigh_ins" to emptyList(),
            ),
        )
        val text = BackupWriter.write(content, id, LocalDateTime.of(2026, 11, 11, 18, 0))
        val doc = assertIs<ImportDocument.Backup>(assertIs<ImportResult.Success>(parser.parse(text)).document)
        assertEquals(content, doc.content)
        assertEquals(id, doc.id)
    }

    @Test
    fun `l'exemple de la documentation est valide`() {
        val text = File(System.getProperty("healthy.examplesDir") ?: error("healthy.examplesDir non défini"), "sauvegarde.json").readText()
        val doc = assertIs<ImportDocument.Backup>(assertIs<ImportResult.Success>(parser.parse(text)).document)
        assertEquals(1, doc.content.rowCount("meals"))
    }

    @Test
    fun `noms dangereux et valeurs imbriquees refuses`() {
        fun errors(backup: String): List<String> {
            val text = """{"format":"healthy-import","schema_version":1,"type":"backup","id":"$id",
                "created_at":"2026-11-11T18:00:00","backup":$backup}"""
            return assertIs<ImportResult.Failure>(parser.parse(text)).errors
        }
        assertTrue(errors("""{"database_version":4,"tables":{"meals; DROP TABLE x":[]}}""").any { it.contains("nom de table") })
        assertTrue(errors("""{"database_version":4,"tables":{"meals":[{"id`":1}]}}""").any { it.contains("nom de colonne") })
        assertTrue(errors("""{"database_version":4,"tables":{"meals":[{"id":[1]}]}}""").any { it.contains("valeur invalide") })
        assertTrue(errors("""{"tables":{}}""").any { it.contains("database_version") })
        assertTrue(errors("""{"database_version":4}""").any { it.contains("tables") })
    }
}
