package com.bidaboss.healthy.settings.data

import android.content.Context
import com.bidaboss.healthy.core.motivation.MotivationTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ton des notifications de motivation, ou null quand elles sont désactivées (par défaut).
 * Gardé dans les préférences du téléphone, comme le rappel de séance : pas dans la sauvegarde.
 */
class MotivationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("motivation", Context.MODE_PRIVATE)
    private val _tone = MutableStateFlow(read())
    val tone: StateFlow<MotivationTone?> = _tone.asStateFlow()

    fun setTone(tone: MotivationTone?) {
        prefs.edit().putString(KEY_TONE, tone?.name).apply()
        _tone.value = tone
    }

    // Une valeur inconnue (ton retiré dans une version future) désactive les notifications.
    private fun read(): MotivationTone? =
        MotivationTone.entries.firstOrNull { it.name == prefs.getString(KEY_TONE, null) }

    private companion object {
        const val KEY_TONE = "tone"
    }
}
