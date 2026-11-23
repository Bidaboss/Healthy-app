package com.bidaboss.healthy.settings.data

import android.content.Context
import com.bidaboss.healthy.core.progress.Growth

/**
 * Dernière étape de Pousse que la personne a vue, pour dire « Pousse grandit ! » une seule fois quand
 * l'étape monte. Gardée dans les préférences du téléphone (aucune table, pas dans la sauvegarde).
 */
class PousseStageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pousse", Context.MODE_PRIVATE)

    /** Null au premier lancement, ou si la valeur est inconnue. */
    fun lastSeen(): Growth? = Growth.entries.firstOrNull { it.name == prefs.getString(KEY_STAGE, null) }

    fun setLastSeen(growth: Growth) {
        prefs.edit().putString(KEY_STAGE, growth.name).apply()
    }

    private companion object {
        const val KEY_STAGE = "last_stage"
    }
}
