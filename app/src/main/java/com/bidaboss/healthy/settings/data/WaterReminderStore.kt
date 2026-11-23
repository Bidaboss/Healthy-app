package com.bidaboss.healthy.settings.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rappels pour boire activés ou non (désactivés par défaut). Gardé dans les préférences du
 * téléphone, comme les autres notifications : pas dans la sauvegarde.
 */
class WaterReminderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("water_reminder", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _enabled.value = enabled
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
