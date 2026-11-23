package com.bidaboss.healthy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bidaboss.healthy.core.body.Band

/** Couleurs des tranches autour du maintien, plus claires en thème sombre pour rester lisibles. */
@Composable
fun Band.color(): Color = colorFor(isSystemInDarkTheme())

/** Couleur de la bande en mode clair ou sombre, hors Compose (widget). */
fun Band.colorFor(dark: Boolean): Color {
    return when (this) {
        Band.GREEN -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        Band.ORANGE -> if (dark) Color(0xFFFFB74D) else Color(0xFFE65100)
        Band.RED -> if (dark) Color(0xFFEF9A9A) else Color(0xFFC62828)
    }
}

/**
 * Le même sens en mots : la couleur seule ne suffit pas (daltonisme, lecture d'écran). On dit
 * « tes besoins » plutôt que « maintien », et dans quel sens on s'en écarte (retour du propriétaire, 12/11/2026).
 */
fun bandLabel(band: Band, kcal: Double, maintenanceKcal: Int): String {
    val above = kcal > maintenanceKcal
    return when (band) {
        Band.GREEN -> "proche de tes besoins"
        Band.ORANGE -> if (above) "un peu plus que tes besoins" else "un peu moins que tes besoins"
        Band.RED -> if (above) "bien plus que tes besoins" else "bien moins que tes besoins"
    }
}
