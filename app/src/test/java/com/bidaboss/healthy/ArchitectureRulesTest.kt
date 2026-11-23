package com.bidaboss.healthy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Vérifie automatiquement deux règles de docs/architecture.md. */
class ArchitectureRulesTest {

    private val sources = File("src/main/java").also {
        check(it.isDirectory) { "Dossier des sources introuvable : ${it.absolutePath}" }
    }

    private fun kotlinFiles() = sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val declaration = Regex("""^(?:(?:abstract|data|sealed|fun|internal|open|enum|annotation)\s+)*(?:class|interface|object)\s+(\w+)""")

    /** Noms complets des classes déclarées dans un fichier qui importe Room (entités, DAO, relations, base). */
    private fun roomClasses(): Set<String> = kotlinFiles()
        .filter { file -> file.readLines().any { it.startsWith("import androidx.room") } }
        .flatMap { file ->
            val lines = file.readLines()
            val pkg = lines.first { it.startsWith("package ") }.removePrefix("package ").trim()
            lines.mapNotNull { declaration.find(it)?.groupValues?.get(1) }.map { "$pkg.$it" }
        }
        .toSet()

    @Test
    fun `les ecrans n'utilisent jamais Room directement`() {
        val forbidden = roomClasses()
        assertTrue("Aucune classe Room détectée : le test ne vérifie plus rien", forbidden.size >= 10)

        val offenders = kotlinFiles()
            .filter { it.invariantSeparatorsPath.contains("/ui/") }
            .flatMap { file ->
                file.readLines()
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").trim() }
                    .filter { it.startsWith("androidx.room") || it in forbidden }
                    .map { "${file.path} importe $it" }
            }
        assertTrue("Les paquets ui ne doivent importer aucune classe Room : $offenders", offenders.isEmpty())
    }

    @Test
    fun `aucune migration destructive`() {
        val offenders = kotlinFiles()
            .filter { file -> file.readLines().any { it.contains("fallbackToDestructiveMigration") && !it.trim().startsWith("*") } }
            .map { it.path }
        assertTrue("fallbackToDestructiveMigration est interdit : $offenders", offenders.isEmpty())
    }
}
