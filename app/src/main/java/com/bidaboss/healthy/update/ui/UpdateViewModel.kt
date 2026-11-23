package com.bidaboss.healthy.update.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.update.RemoteApk
import com.bidaboss.healthy.update.data.UpdateCheck
import com.bidaboss.healthy.update.data.UpdateException
import com.bidaboss.healthy.update.data.UpdateRepository
import com.bidaboss.healthy.update.data.UpdateStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Ce que la fenêtre de mise à jour affiche. Hidden : rien à montrer. */
sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data object NoFolder : UpdateUiState
    data class Available(val apk: RemoteApk) : UpdateUiState
    data class Downloading(val apk: RemoteApk, val progress: Float?) : UpdateUiState
    data class ReadyToInstall(val apk: RemoteApk, val file: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/**
 * Vérifie le dossier Drive chaque fois que l'app revient au premier plan (au plus toutes les 30 min,
 * en silence si rien de nouveau ou si le réseau manque), ou à la demande depuis Réglages (avec un
 * message dans tous les cas). « Plus tard » met la version de côté pendant 24 h.
 */
class UpdateViewModel(
    private val repository: UpdateRepository,
    private val store: UpdateStore,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun checkAutomatically() {
        // Rien pendant qu'une fenêtre est ouverte ou qu'une vérification tourne déjà.
        if (_state.value != UpdateUiState.Hidden || job?.isActive == true) return
        if (now() - store.lastAutoCheckEpochMs < AUTO_CHECK_INTERVAL_MS) return
        job = viewModelScope.launch {
            val result = runCatching { repository.check() }.getOrNull() ?: return@launch
            // Noté seulement après une vérification réussie : sans réseau, on réessaie au prochain retour.
            store.lastAutoCheckEpochMs = now()
            if (result is UpdateCheck.Available && !isSnoozed(result.apk.versionCode) && _state.value == UpdateUiState.Hidden) {
                _state.value = UpdateUiState.Available(result.apk)
            }
        }
    }

    private fun isSnoozed(versionCode: Int): Boolean =
        store.snoozedVersionCode == versionCode && now() - store.snoozedAtEpochMs < SNOOZE_MS

    /** « Plus tard » sur une nouvelle version : on ne la repropose pas d'elle-même avant 24 h. */
    fun later() {
        when (val current = _state.value) {
            is UpdateUiState.Available -> snooze(current.apk.versionCode)
            is UpdateUiState.ReadyToInstall -> snooze(current.apk.versionCode)
            else -> Unit
        }
        dismiss()
    }

    private fun snooze(versionCode: Int) {
        store.snoozedVersionCode = versionCode
        store.snoozedAtEpochMs = now()
    }

    fun checkNow() {
        job?.cancel()
        _state.value = UpdateUiState.Checking
        job = viewModelScope.launch {
            _state.value = runCatching { repository.check() }.fold(
                onSuccess = {
                    when (it) {
                        UpdateCheck.NoFolder -> UpdateUiState.NoFolder
                        UpdateCheck.UpToDate -> UpdateUiState.UpToDate
                        is UpdateCheck.Available -> UpdateUiState.Available(it.apk)
                    }
                },
                onFailure = { UpdateUiState.Failed(shownMessage(it, "Vérification impossible. Vérifie ta connexion.")) },
            )
        }
    }

    fun download(apk: RemoteApk) {
        job?.cancel()
        _state.value = UpdateUiState.Downloading(apk, null)
        job = viewModelScope.launch {
            _state.value = runCatching {
                repository.download(apk) { _state.value = UpdateUiState.Downloading(apk, it) }
            }.fold(
                onSuccess = { UpdateUiState.ReadyToInstall(apk, it) },
                onFailure = { UpdateUiState.Failed(shownMessage(it, "Téléchargement impossible. Vérifie ta connexion.")) },
            )
        }
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateUiState.Hidden
    }

    companion object {
        const val AUTO_CHECK_INTERVAL_MS = 30 * 60 * 1000L
        const val SNOOZE_MS = 24 * 60 * 60 * 1000L
    }
}

/** Seuls nos messages sont affichés : ceux du système peuvent contenir le lien du dossier. */
private fun shownMessage(error: Throwable, fallback: String): String =
    (error as? UpdateException)?.message ?: fallback
