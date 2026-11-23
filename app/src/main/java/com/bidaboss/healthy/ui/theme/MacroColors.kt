package com.bidaboss.healthy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bidaboss.healthy.settings.data.AppTheme

/**
 * Une couleur par macro (fil Design, 12/11/2026) : protéines bleu, glucides orange, lipides corail,
 * fibres brun (13/11/2026).
 * Aucune ne juge la journée ; seule la couleur des calories le fait. Le trait atteint 3:1 sur les
 * cartes de chaque thème, en clair comme en sombre (vérifié par ThemesTest).
 */
data class MacroColors(val protein: Color, val carbs: Color, val fat: Color, val fiber: Color)

private val LightMacros = MacroColors(protein = Color(0xFF1E6FD9), carbs = Color(0xFFD35400), fat = Color(0xFFD81B60), fiber = Color(0xFF6D4C41))
private val DarkMacros = MacroColors(protein = Color(0xFF90CAF9), carbs = Color(0xFFFFB74D), fat = Color(0xFFF48FB1), fiber = Color(0xFFBCAAA4))

/** Pirate : bleu plus marine pour rester dans la palette parchemin. */
private val PirateLightMacros = LightMacros.copy(protein = Color(0xFF1F4E8C))

internal fun macroColorsOf(theme: AppTheme, dark: Boolean): MacroColors = when {
    dark -> DarkMacros
    theme == AppTheme.PIRATE -> PirateLightMacros
    else -> LightMacros
}

/** Couleurs des macros pour le thème et le mode (clair ou sombre) en cours. */
@Composable
fun macroColors(): MacroColors = macroColorsOf(HealthyTheme.ambiance.theme, isSystemInDarkTheme())
