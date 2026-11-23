package com.bidaboss.healthy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pictogramme au trait dessiné dans une grille de 24 x 24, comme ceux du prototype (chemin SVG, traits arrondis).
 * Sert aux pastilles de « Ma montre » et aux boutons de boisson d'Aujourd'hui.
 */
@Composable
fun SvgIcon(pathData: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, strokeWidth: Float = 2.2f) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier.size(size)) {
        val scale = this.size.width / 24f
        withTransform({ scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
            drawPath(
                path,
                tint,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Les pictogrammes du prototype (grille 24 x 24). */
object Glyphs {
    const val FLAME = "M12 2 C13 7 18 9 18 15 A6 6 0 0 1 6 15 C6 11 9 10 9 6 C11 8 11 9 12 10 Z"
    const val SHOE = "M4 16 C4 12 7 9 10 9 L10 4 H14 V9 C17 9 20 12 20 16 V19 H4 Z"
    const val ROUTE = "M8 18 H14 A3.5 3.5 0 0 0 14 11 H10 A3.5 3.5 0 0 1 10 4 H15.8"
    const val HEART = "M12 20 C5 15 3 11 3 8.5 A4.5 4.5 0 0 1 12 7 A4.5 4.5 0 0 1 21 8.5 C21 11 19 15 12 20 Z"
    const val GLASS = "M7 3 H17 L16 21 H8 Z M7.6 9 H16.4"
    const val COFFEE = "M4 9 H16 V15 A4 4 0 0 1 12 19 H8 A4 4 0 0 1 4 15 Z M16 10 H18 A2.5 2.5 0 0 1 18 15 H16 M8 3 V6 M12 3 V6"
    const val TEA = "M4 8 H17 V14 A5 5 0 0 1 12 19 H9 A5 5 0 0 1 4 14 Z M17 9 H19 A2.5 2.5 0 0 1 19 14 H17 M9 3 C9 5 11 5 11 7"
    const val BOTTLE = "M10 2 H14 V5 C16 6 17 8 17 10 V21 H7 V10 C7 8 8 6 10 5 Z M7 13 H17"
}
