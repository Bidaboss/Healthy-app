package com.bidaboss.healthy.importexport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.importformat.ImportDocument
import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.core.importformat.ImportResult
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.importexport.data.ImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val replacedMealFormat = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", Locale.FRENCH)

sealed interface ImportUiState {
    /** Rien en cours : on attend un texte ou un fichier. */
    data object Idle : ImportUiState

    data class Invalid(val errors: List<String>) : ImportUiState

    /** Fichier valide, en attente de confirmation. */
    data class Preview(
        val document: ImportDocument,
        val alreadyImported: Boolean,
        val replacement: Replacement = Replacement.None,
        /** Enregistrement en cours : le bouton est désactivé pour éviter un double appui. */
        val saving: Boolean = false,
        /** Ce que l'app a ignoré sans bloquer l'import (ex. un lien vidéo abîmé). */
        val warnings: List<String> = emptyList(),
    ) : ImportUiState

    data class Saved(val message: String) : ImportUiState
}

/** Ce que devient le repas visé par le champ « replaces » du fichier. */
sealed interface Replacement {
    data object None : Replacement

    /** Le repas à remplacer existe : [description] le décrit (titre et date). */
    data class Found(val description: String) : Replacement

    data object NotFound : Replacement
}

class ImportViewModel(
    private val parser: ImportParser,
    private val repository: ImportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** Analyse un texte collé, partagé ou lu dans un fichier, et prépare l'aperçu. */
    fun submitText(text: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { parser.parse(text) }
            _uiState.value = when (result) {
                is ImportResult.Failure -> ImportUiState.Invalid(result.errors)
                is ImportResult.Success -> if (repository.isBackupTooRecent(result.document)) {
                    ImportUiState.Invalid(
                        listOf("Cette sauvegarde vient d'une version plus récente de Healthy. Mets d'abord l'app à jour sur ce téléphone."),
                    )
                } else {
                    val document = result.document.inPhoneZone()
                    ImportUiState.Preview(
                        document = document,
                        alreadyImported = repository.isAlreadyImported(document),
                        replacement = replacementFor(document),
                        warnings = result.warnings,
                    )
                }
            }
        }
    }

    /** L'heure du repas est toujours montrée et enregistrée dans le fuseau du téléphone. */
    private fun ImportDocument.inPhoneZone(): ImportDocument = when (this) {
        is ImportDocument.Meal -> copy(
            meal = meal.copy(eatenAt = meal.eatenAt.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime()),
        )
        is ImportDocument.WorkoutPlan, is ImportDocument.Backup -> this
    }

    private suspend fun replacementFor(document: ImportDocument): Replacement {
        if (document !is ImportDocument.Meal || document.replaces == null) return Replacement.None
        val meal = repository.findReplacedMeal(document) ?: return Replacement.NotFound
        return Replacement.Found("${meal.title ?: meal.type.label} du ${meal.eatenAt.format(replacedMealFormat)}")
    }

    /** Correction du type de repas ou de la date avant d'enregistrer (aperçu d'un repas). */
    fun editMeal(type: MealType, eatenAt: LocalDateTime) {
        val preview = _uiState.value as? ImportUiState.Preview ?: return
        val document = preview.document as? ImportDocument.Meal ?: return
        if (preview.saving) return
        val meal = document.meal.copy(
            type = type,
            eatenAt = eatenAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
        )
        _uiState.value = preview.copy(document = document.copy(meal = meal))
    }

    fun reportError(message: String) {
        _uiState.value = ImportUiState.Invalid(listOf(message))
    }

    fun confirm() {
        val preview = _uiState.value as? ImportUiState.Preview ?: return
        if (preview.saving || preview.alreadyImported) return
        _uiState.value = preview.copy(saving = true)
        viewModelScope.launch {
            _uiState.value = try {
                val saved = repository.save(preview.document)
                ImportUiState.Saved(
                    when {
                        !saved -> "Ce fichier avait déjà été importé. Rien n'a été ajouté."
                        preview.replacement is Replacement.Found -> "Repas corrigé dans ton journal."
                        preview.document is ImportDocument.Meal -> "Repas ajouté à ton journal."
                        preview.document is ImportDocument.Backup -> "Sauvegarde restaurée : tes données sont revenues."
                        else -> "Nouveau plan sportif activé."
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportUiState.Invalid(listOf("L'enregistrement a échoué. Réessaie."))
            }
        }
    }

    fun reset() {
        _uiState.value = ImportUiState.Idle
    }
}
