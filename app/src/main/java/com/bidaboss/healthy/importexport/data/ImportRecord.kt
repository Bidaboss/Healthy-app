package com.bidaboss.healthy.importexport.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** Trace de chaque fichier importé : réimporter le même id ne crée pas de doublon. */
@Entity(tableName = "imports")
data class ImportRecordEntity(
    @PrimaryKey @ColumnInfo(name = "import_id") val importId: String,
    val type: String,
    @ColumnInfo(name = "imported_at_epoch_ms") val importedAtEpochMs: Long,
)

@Dao
interface ImportDao {
    @Query("SELECT EXISTS(SELECT 1 FROM imports WHERE import_id = :importId)")
    suspend fun exists(importId: String): Boolean

    @Insert
    suspend fun insert(record: ImportRecordEntity)
}
