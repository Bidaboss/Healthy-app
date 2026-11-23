package com.bidaboss.healthy.importexport.data

import android.content.ContentValues
import android.database.Cursor
import com.bidaboss.healthy.core.backup.BackupContent
import com.bidaboss.healthy.core.backup.BackupWriter
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.TransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

/** Ce que la restauration a remis en place (pour le message de fin). */
data class RestoreSummary(val meals: Int, val sessions: Int, val weighIns: Int)

/**
 * Sauvegarde et restauration de toute la base, table par table (format `backup`, docs/format-import.md).
 * La restauration remplace tout, dans une seule transaction : en cas d'erreur, rien ne change.
 */
class BackupRepository(private val database: HealthyDatabase, private val transactions: TransactionRunner) {

    suspend fun export(now: LocalDateTime = LocalDateTime.now()): String = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val tables = TABLES.associateWith { table ->
            db.query("SELECT * FROM `$table` ORDER BY rowid").use { it.readRows() }
        }
        BackupWriter.write(BackupContent(HealthyDatabase.VERSION, tables), UUID.randomUUID().toString(), now)
    }

    /** Vrai si la sauvegarde vient d'une version plus récente de l'app : on ne sait pas la relire. */
    fun isTooRecent(content: BackupContent): Boolean = content.databaseVersion > HealthyDatabase.VERSION

    /**
     * Remplace toutes les données par celles de la sauvegarde. Une sauvegarde d'une version plus ancienne
     * de la base est acceptée (les colonnes ajoutées depuis restent vides) ; une plus récente est refusée.
     */
    suspend fun restore(content: BackupContent): RestoreSummary {
        require(content.databaseVersion <= HealthyDatabase.VERSION) { "Sauvegarde d'une version plus récente de l'app" }
        transactions.inTransaction {
            val db = database.openHelper.writableDatabase
            TABLES.reversed().forEach { db.execSQL("DELETE FROM `$it`") }
            TABLES.forEach { table ->
                val columns = db.query("PRAGMA table_info(`$table`)").use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
                }
                content.tables[table].orEmpty().forEach { row ->
                    val values = ContentValues()
                    row.filterKeys { it in columns }.forEach { (column, value) ->
                        when (value) {
                            null -> values.putNull(column)
                            is Long -> values.put(column, value)
                            is Double -> values.put(column, value)
                            is String -> values.put(column, value)
                        }
                    }
                    db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
                }
            }
        }
        return RestoreSummary(
            meals = content.rowCount("meals"),
            sessions = content.rowCount("workout_logs"),
            weighIns = content.rowCount("weigh_ins"),
        )
    }

    private fun Cursor.readRows(): List<Map<String, Any?>> = buildList {
        while (moveToNext()) {
            add(
                (0 until columnCount).associate { i ->
                    getColumnName(i) to when (getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
                        else -> getString(i)
                    }
                },
            )
        }
    }

    companion object {
        /**
         * Tables sauvegardées, parents avant enfants (ordre d'insertion ; l'effacement se fait à l'envers).
         * Une nouvelle table doit être ajoutée ici (vérifié par BackupRepositoryTest).
         */
        val TABLES = listOf(
            "meals",
            "meal_items",
            "workout_plans",
            "workout_sessions",
            "workout_session_variants",
            "workout_exercises",
            "imports",
            "workout_logs",
            "workout_log_exercises",
            "activities",
            "profile",
            "weigh_ins",
            "water_intakes",
            "sport_profile",
            "recipes",
            "recipe_items",
            "scanned_products",
            "watch_days",
            "saved_foods",
            "saved_routes",
        )
    }
}
