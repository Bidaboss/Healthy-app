package com.bidaboss.healthy.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.abs
import kotlin.math.cos
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.theme.HealthyTheme
import kotlin.random.Random

private val ConfettiColors = listOf(Color(0xFFFF8A65), Color(0xFFFFC857), Color(0xFF3BB273), Color(0xFF4C8DF6), Color(0xFF7BD99A))
private val Gold = Color(0xFFE0A526)
private val GoldDark = Color(0xFF9A6A0B)
private val TravelColors = listOf(Color(0xFFF28C38), Color(0xFF2BB3A3), Color(0xFF1C6E8C), Color(0xFFE5464F))

private class Piece(
    val x: Float, val vx: Float, val vy: Float, val spin: Float,
    val color: Color, val w: Float, val h: Float, val delay: Float,
)

/**
 * Pluie de récompense dessinée en Compose (aucune bibliothèque) : part du haut de l'écran une seule fois.
 * Confettis en Classique, pièces d'or en Pirate, cartes postales et autocollants en Voyage.
 * Si les animations sont coupées dans les réglages du téléphone, elle se termine aussitôt.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier, count: Int = 90, durationMs: Int = 2800) {
    val theme = HealthyTheme.ambiance.theme
    val palette = when (theme) {
        AppTheme.TRAVEL -> TravelColors
        else -> ConfettiColors
    }
    val pieces = remember(theme) {
        val random = Random(42)
        List(count) {
            Piece(
                x = random.nextFloat(),
                vx = (random.nextFloat() - 0.5f) * 0.5f,
                vy = 0.35f + random.nextFloat() * 0.5f,
                spin = (random.nextFloat() - 0.5f) * 1080f,
                color = palette[random.nextInt(palette.size)],
                w = 8f + random.nextFloat() * 8f,
                h = 5f + random.nextFloat() * 5f,
                delay = random.nextFloat() * 0.25f,
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMs, easing = LinearEasing)) }

    Canvas(modifier) {
        val t0 = progress.value
        if (t0 >= 1f) return@Canvas
        pieces.forEach { p ->
            val t = ((t0 - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            // Chute avec un peu de gravité et de dérive sur le côté.
            val x = (p.x + p.vx * t) * size.width
            val y = (-0.05f + p.vy * t + 0.6f * t * t) * size.height
            val alpha = if (t > 0.8f) (1f - t) / 0.2f else 1f
            val scale = density
            val center = Offset(x, y)
            when (theme) {
                AppTheme.CLASSIC -> rotate(p.spin * t, pivot = center) {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - p.w * scale / 2, y - p.h * scale / 2),
                        size = Size(p.w * scale, p.h * scale),
                    )
                }
                // La pièce tourne sur elle-même : on l'aplatit selon l'angle.
                AppTheme.PIRATE -> scale(abs(cos(Math.toRadians((p.spin * t).toDouble()))).toFloat().coerceAtLeast(0.15f), 1f, center) {
                    coin(center, p.w * scale * 0.7f, alpha)
                }
                AppTheme.TRAVEL -> rotate(p.spin * t * 0.25f, pivot = center) {
                    if (p.delay * 100 % 2 < 1) postcard(center, p.w * scale * 1.6f, p.color, alpha) else sticker(center, p.w * scale * 0.8f, p.color, alpha)
                }
            }
        }
    }
}

private fun DrawScope.coin(center: Offset, r: Float, alpha: Float) {
    drawCircle(GoldDark.copy(alpha = alpha), radius = r, center = center)
    drawCircle(Gold.copy(alpha = alpha), radius = r * 0.82f, center = center)
    drawCircle(Color.White.copy(alpha = 0.5f * alpha), radius = r * 0.25f, center = center - Offset(r * 0.3f, r * 0.3f))
}

/** Carte postale : rectangle blanc, timbre de couleur en haut à droite, deux lignes d'adresse. */
private fun DrawScope.postcard(center: Offset, w: Float, color: Color, alpha: Float) {
    val h = w * 0.66f
    val topLeft = center - Offset(w / 2, h / 2)
    drawRoundRect(Color.White.copy(alpha = alpha), topLeft, Size(w, h), CornerRadius(w * 0.06f))
    drawRoundRect(color.copy(alpha = 0.5f * alpha), topLeft, Size(w, h), CornerRadius(w * 0.06f), style = Stroke(w * 0.03f))
    drawRect(color.copy(alpha = alpha), topLeft + Offset(w * 0.68f, h * 0.12f), Size(w * 0.22f, h * 0.3f))
    listOf(0.6f, 0.78f).forEach { f ->
        drawLine(color.copy(alpha = 0.6f * alpha), topLeft + Offset(w * 0.5f, h * f), topLeft + Offset(w * 0.88f, h * f), w * 0.03f)
    }
}

/** Autocollant rond avec un liseré blanc. */
private fun DrawScope.sticker(center: Offset, r: Float, color: Color, alpha: Float) {
    drawCircle(Color.White.copy(alpha = alpha), radius = r, center = center)
    drawCircle(color.copy(alpha = alpha), radius = r * 0.8f, center = center)
}
