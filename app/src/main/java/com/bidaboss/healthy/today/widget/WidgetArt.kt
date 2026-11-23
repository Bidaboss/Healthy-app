package com.bidaboss.healthy.today.widget

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.bidaboss.healthy.core.progress.Growth
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.drawPousse
import com.bidaboss.healthy.ui.theme.ambianceOf
import com.bidaboss.healthy.ui.widgetPattern

/**
 * Dessins du widget en images : Glance ne sait pas dessiner d'anneau ni de forme libre. Les formes
 * sont blanches et le widget les teint avec les couleurs du thème (clair ou sombre suivent ainsi
 * le téléphone) ; seul Pousse garde ses couleurs.
 */
internal object WidgetArt {

    /**
     * [mask] = true : image d'un seul canal (1 octet par point) pour les formes teintées. Glance garde
     * une copie des images par taille de widget : il faut rester bien sous la limite d'Android.
     */
    private fun draw(widthPx: Int, heightPx: Int, mask: Boolean = true, block: DrawScope.() -> Unit): Bitmap {
        val config = if (mask) Bitmap.Config.ALPHA_8 else Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), config)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(android.graphics.Canvas(bitmap)),
            Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
            block,
        )
        return bitmap
    }

    /** Fond de l'anneau : cercle complet. */
    fun ringTrack(sizePx: Int, strokePx: Float): Bitmap = draw(sizePx, sizePx) {
        drawCircle(Color.White, radius = (size.minDimension - strokePx) / 2f, style = Stroke(width = strokePx))
    }

    /** Partie remplie de l'anneau, depuis midi dans le sens des aiguilles ; plein au-delà du maintien. */
    fun ringProgress(sizePx: Int, strokePx: Float, fraction: Float): Bitmap = draw(sizePx, sizePx) {
        val sweep = 360f * fraction.coerceIn(0f, 1f)
        if (sweep <= 0f) return@draw
        val inset = strokePx / 2f
        drawArc(
            Color.White,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokePx, size.height - strokePx),
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }

    /** Palier du verre : 0, 1, 2, 3 ou 4 quarts. */
    fun glassLevel(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 4f).toInt()

    private fun DrawScope.glassPath(): Path {
        val inset = size.width * 0.16f
        val stroke = size.width * 0.07f
        return Path().apply {
            moveTo(stroke, stroke)
            lineTo(size.width - stroke, stroke)
            lineTo(size.width - inset, size.height - stroke)
            lineTo(inset, size.height - stroke)
            close()
        }
    }

    fun glassOutline(widthPx: Int, heightPx: Int): Bitmap = draw(widthPx, heightPx) {
        drawPath(glassPath(), Color.White, style = Stroke(width = size.width * 0.08f))
    }

    fun glassWater(widthPx: Int, heightPx: Int, level: Int): Bitmap = draw(widthPx, heightPx) {
        if (level <= 0) return@draw
        clipPath(glassPath()) {
            val top = size.height * (1f - level / 4f)
            drawRect(Color.White, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
        }
    }

    fun pousse(theme: AppTheme, mood: Mood, growth: Growth, sizePx: Int): Bitmap = draw(sizePx, sizePx, mask = false) {
        scale(size.minDimension / 120f, pivot = Offset.Zero) {
            drawPousse(mood, theme, ambianceOf(theme).sparkle, growth = growth)
        }
    }

    /** Motif du thème, encre blanche très légère : il ne doit jamais gêner la lecture. */
    fun pattern(theme: AppTheme, widthPx: Int, heightPx: Int, density: Float): Bitmap = draw(widthPx, heightPx) {
        widgetPattern(theme, Color.White.copy(alpha = 0.09f), density / 2.5f * 1.4f)
    }
}
