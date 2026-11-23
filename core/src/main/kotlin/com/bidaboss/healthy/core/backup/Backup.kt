package com.bidaboss.healthy.core.backup

import com.bidaboss.healthy.core.importformat.ImportParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Contenu d'une sauvegarde : une copie des tables de la base, ligne par ligne (voir docs/format-import.md,
 * type `backup`). Une valeur est null, un Long, un Double ou un String, comme dans SQLite.
 */
data class BackupContent(
    /** Version du schéma de la base au moment de l'export (HealthyDatabase.VERSION). */
    val databaseVersion: Int,
    val tables: Map<String, List<Map<String, Any?>>>,
) {
    fun rowCount(table: String): Int = tables[table]?.size ?: 0
}

/** Écrit une sauvegarde dans le format documenté. Fonction pure. */
object BackupWriter {
    private val json = Json { prettyPrint = false }

    fun write(content: BackupContent, id: String, createdAt: LocalDateTime): String {
        val root = buildJsonObject {
            put("format", ImportParser.FORMAT)
            put("schema_version", ImportParser.SCHEMA_VERSION)
            put("type", ImportParser.TYPE_BACKUP)
            put("id", id)
            put("created_at", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(createdAt.truncatedTo(ChronoUnit.SECONDS)))
            put(
                "backup",
                buildJsonObject {
                    put("database_version", content.databaseVersion)
                    put(
                        "tables",
                        JsonObject(
                            content.tables.mapValues { (_, rows) ->
                                JsonArray(rows.map { row -> JsonObject(row.mapValues { (_, v) -> v.toJson() }) })
                            },
                        ),
                    )
                },
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun Any?.toJson(): JsonElement = when (this) {
        null -> JsonNull
        is Long -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        else -> error("Valeur de type ${this::class.simpleName} impossible dans une sauvegarde")
    }
}

/** Lecture de l'objet `backup` : appelé par ImportParser. */
internal object BackupReader {

    /** Rend le contenu, ou ajoute les erreurs à [errors]. */
    fun read(element: JsonElement?, errors: MutableList<String>): BackupContent? {
        val obj = element as? JsonObject
        if (obj == null) {
            errors += "Objet « backup » manquant."
            return null
        }
        val version = (obj["database_version"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.toInt()
        if (version == null || version < 1) errors += "Sauvegarde : « database_version » manquant ou invalide."
        val tablesObj = obj["tables"] as? JsonObject
        if (tablesObj == null) {
            errors += "Sauvegarde : objet « tables » manquant."
            return null
        }
        val tables = mutableMapOf<String, List<Map<String, Any?>>>()
        for ((name, rowsElement) in tablesObj) {
            if (!NAME.matches(name)) {
                errors += "Sauvegarde : nom de table invalide « $name »."
                continue
            }
            val rows = rowsElement as? JsonArray
            if (rows == null) {
                errors += "Sauvegarde : la table « $name » doit être une liste."
                continue
            }
            val parsed = rows.mapNotNull { row -> readRow(name, row, errors) }
            tables[name] = parsed
        }
        if (errors.isNotEmpty() || version == null) return null
        return BackupContent(version, tables)
    }

    private fun readRow(table: String, row: JsonElement, errors: MutableList<String>): Map<String, Any?>? {
        val obj = row as? JsonObject
        if (obj == null) {
            errors += "Sauvegarde : une ligne de « $table » n'est pas un objet."
            return null
        }
        val values = mutableMapOf<String, Any?>()
        for ((column, value) in obj) {
            if (!NAME.matches(column)) {
                errors += "Sauvegarde : nom de colonne invalide « $column » dans « $table »."
                return null
            }
            values[column] = when {
                value is JsonNull -> null
                value is JsonPrimitive && value.isString -> value.content
                value is JsonPrimitive && value.longOrNull != null -> value.longOrNull
                value is JsonPrimitive && value.doubleOrNull != null -> value.doubleOrNull
                else -> {
                    errors += "Sauvegarde : valeur invalide pour « $column » dans « $table »."
                    return null
                }
            }
        }
        return values
    }

    /** Noms de tables et de colonnes : lettres minuscules, chiffres et _ (ils finissent dans du SQL). */
    private val NAME = Regex("[a-z][a-z0-9_]{0,63}")
}
