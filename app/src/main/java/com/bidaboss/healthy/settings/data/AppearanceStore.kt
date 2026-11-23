package com.bidaboss.healthy.settings.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Les 3 thèmes de l'app (refonte du 23/11/2026) : Classique par défaut, Pirate, Voyage. Espace, Arcade rétro et
 * Montagne ont été retirés ; le nom est gardé tel quel dans les préférences.
 */
enum class AppTheme { CLASSIC, PIRATE, TRAVEL }

/**
 * Thème de l'app, Classique par défaut. Gardé dans les préférences du téléphone (pas dans Room :
 * ce n'est pas une donnée du journal), lu au démarrage.
 */
class AppearanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(read())
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _theme.value = theme
    }

    // Un réglage inconnu (Espace, Arcade rétro et Montagne ont été retirés) revient au thème Classique.
    private fun read(): AppTheme =
        AppTheme.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: AppTheme.CLASSIC

    private companion object {
        const val KEY_THEME = "theme"
    }
}
