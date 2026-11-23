package com.bidaboss.healthy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bidaboss.healthy.settings.data.AppTheme

/**
 * Boutons secondaires « tonals » (eau, +/- des répétitions, « + » de Sport) : teinte claire et texte
 * foncé en clair, pour ne pas peser autant que le bouton principal (fil Design, 12/11/2026). La couleur
 * pleine de `secondaryContainer` reste réservée à l'onglet actif et aux chips sélectionnées.
 * En sombre, ils gardent `secondaryContainer`. Contraste du texte vérifié par ThemesTest.
 */
data class TonalColors(val container: Color, val content: Color)

internal fun tonalColorsOf(theme: AppTheme, dark: Boolean): TonalColors {
    if (dark) {
        val scheme = colorSchemeOf(theme, true)
        return TonalColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
    return when (theme) {
        AppTheme.CLASSIC -> TonalColors(Color(0xFFD3EEDC), Color(0xFF14532F))
        AppTheme.PIRATE -> TonalColors(Color(0xFFE6DCC6), Color(0xFF1E2A3A))
        AppTheme.TRAVEL -> TonalColors(Color(0xFFD2E8F0), Color(0xFF0F4459))
        AppTheme.SPACE -> TonalColors(Color(0xFFE4DDF5), Color(0xFF3A2580))
        AppTheme.ARCADE -> TonalColors(Color(0xFFFBD3E7), Color(0xFF7A0E4A))
        AppTheme.MOUNTAIN -> TonalColors(Color(0xFFD7E6E0), Color(0xFF1F4036))
    }
}

@Composable
private fun tonalColors(): TonalColors = tonalColorsOf(HealthyTheme.ambiance.theme, isSystemInDarkTheme())

/** Couleurs d'un FilledTonalButton de l'app. */
@Composable
fun healthyTonalButtonColors(): ButtonColors {
    val c = tonalColors()
    return ButtonDefaults.filledTonalButtonColors(containerColor = c.container, contentColor = c.content)
}

/** Couleurs d'un FilledTonalIconButton de l'app. */
@Composable
fun healthyTonalIconButtonColors(): IconButtonColors {
    val c = tonalColors()
    return IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.container, contentColor = c.content)
}
