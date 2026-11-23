package com.bidaboss.healthy.importexport.data

import com.bidaboss.healthy.core.importformat.ImportDocument
import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.data.TransactionRunner
import com.bidaboss.healthy.meals.data.Meal
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository

class ImportRepository(
    private val transactions: TransactionRunner,
    private val importDao: ImportDao,
    private val mealRepository: MealRepository,
    private val workoutRepository: WorkoutRepository,
    private val backupRepository: BackupRepository? = null,
) {
    fun isBackupTooRecent(document: ImportDocument): Boolean =
        document is ImportDocument.Backup && backupRepository?.isTooRecent(document.content) == true

    suspend fun isAlreadyImported(importId: String): Boolean = importDao.exists(importId)

    /** Une sauvegarde n'est jamais « déjà importée » : on peut restaurer la même plusieurs fois. */
    suspend fun isAlreadyImported(document: ImportDocument): Boolean =
        document !is ImportDocument.Backup && importDao.exists(document.id)

    /** Le repas que ce document corrige (champ « replaces »), s'il est encore dans le journal. */
    suspend fun findReplacedMeal(document: ImportDocument): Meal? =
        if (document is ImportDocument.Meal) document.replaces?.let { mealRepository.findImportedMeal(it) } else null

    /**
     * Enregistre l'import en une seule transaction. Renvoie false s'il était déjà importé.
     * Un repas avec « replaces » supprime d'abord le repas qu'il corrige.
     */
    suspend fun save(document: ImportDocument): Boolean = transactions.inTransaction {
        if (document is ImportDocument.Backup) {
            checkNotNull(backupRepository).restore(document.content)
            return@inTransaction true
        }
        if (importDao.exists(document.id)) return@inTransaction false
        val now = System.currentTimeMillis()
        when (document) {
            is ImportDocument.Meal -> {
                // Le repas corrigé garde son étoile « Favori ».
                val wasFavorite = document.replaces?.let { mealRepository.findImportedMeal(it)?.isFavorite } ?: false
                document.replaces?.let { mealRepository.deleteImportedMeal(it) }
                mealRepository.insertImportedMeal(document.id, document.meal, now, favorite = wasFavorite)
                importDao.insert(ImportRecordEntity(document.id, ImportParser.TYPE_MEAL, now))
            }
            is ImportDocument.WorkoutPlan -> {
                workoutRepository.insertImportedPlan(document.id, document.plan, now)
                importDao.insert(ImportRecordEntity(document.id, ImportParser.TYPE_WORKOUT_PLAN, now))
            }
            is ImportDocument.Backup -> Unit
        }
        true
    }
}
