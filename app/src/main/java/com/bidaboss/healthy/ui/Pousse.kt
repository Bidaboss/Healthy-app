package com.bidaboss.healthy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.core.progress.Growth
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.theme.HealthyTheme

private val Body = Color(0xFF7BD99A)
private val LeafDark = Color(0xFF3BB273)
internal val PousseInk = Color(0xFF17301F)
private val Ink = PousseInk
private val Cheek = Color(0xFFFF8A65)
private val PirateRed = Color(0xFFC0392B)
private val Straw = Color(0xFFE8C27A)
private val StrawDark = Color(0xFFC99A4B)
private val Leather = Color(0xFF8B5A2B)
private val FlameCore = Color(0xFFFFC857)

/**
 * Pousse, la mascotte (maquette du fil Design), dessinée sur une grille de 120 × 120. Sa tenue suit
 * le thème : bandana et cache-œil en pirate, chapeau de paille et sac à dos en voyage.
 */
@Composable
fun Pousse(mood: Mood, modifier: Modifier = Modifier, size: Dp = 72.dp, growth: Growth = Growth.SPROUT) {
    val theme = HealthyTheme.ambiance.theme
    val sparkle = HealthyTheme.ambiance.sparkle
    val label = pousseLabel(theme, mood, growth)
    Canvas(modifier.size(size).semantics { contentDescription = label }) {
        scale(this.size.minDimension / 120f, pivot = Offset.Zero) { drawPousse(mood, theme, sparkle, growth = growth) }
    }
}

/** Ce que dit le lecteur d'écran de Pousse, selon le thème, l'humeur et sa taille. */
internal fun pousseLabel(theme: AppTheme, mood: Mood, growth: Growth): String {
    val who = when (theme) {
        AppTheme.CLASSIC -> "Pousse"
        AppTheme.PIRATE -> "Capitaine Pousse"
        AppTheme.TRAVEL -> "Pousse en voyage"
    }
    return when (mood) {
        Mood.HAPPY -> "$who, contente"
        Mood.ENCOURAGING -> "$who t'encourage"
        Mood.PROUD -> "$who, fière de toi"
    } + if (growth != Growth.SPROUT) ", ${growth.label}" else ""
}

/** Pousse sur la grille 120 × 120 ; [shadow] = false pour les illustrations d'exercices (ombre dessinée à part). */
internal fun DrawScope.drawPousse(
    mood: Mood,
    theme: AppTheme,
    sparkle: Color,
    shadow: Boolean = true,
    growth: Growth = Growth.SPROUT,
) {
    if (shadow) drawOval(Color.Black.copy(alpha = 0.15f), topLeft = Offset(32f, 107f), size = Size(56f, 10f))
    // En voyage, les feuilles sortent du chapeau : on les dessine après lui.
    val leavesOnTop = theme == AppTheme.TRAVEL
    if (!leavesOnTop) leaves(mood, growth)
    if (theme == AppTheme.TRAVEL) {
        // Sac à dos porté de trois quarts : il dépasse à droite du corps.
        drawRoundRect(Leather, topLeft = Offset(70f, 56f), size = Size(40f, 48f), cornerRadius = CornerRadius(12f))
        drawRoundRect(StrawDark, topLeft = Offset(98f, 70f), size = Size(12f, 18f), cornerRadius = CornerRadius(4f))
    }
    drawCircle(Color.White, radius = 38f, center = Offset(60f, 72f))
    drawCircle(Body, radius = 34f, center = Offset(60f, 74f))
    drawOval(Color.White.copy(alpha = 0.45f), topLeft = Offset(38f, 50f), size = Size(20f, 12f))
    drawCircle(Cheek.copy(alpha = 0.85f), radius = 5f, center = Offset(39f, 82f))
    drawCircle(Cheek.copy(alpha = 0.85f), radius = 5f, center = Offset(81f, 82f))

    val line = Stroke(width = 4f, cap = StrokeCap.Round)
    when (mood) {
        Mood.HAPPY -> {
            eye(47f)
            eye(73f)
            drawPath(Path().apply { moveTo(50f, 84f); quadraticTo(60f, 94f, 70f, 84f) }, Ink, style = line)
        }
        Mood.ENCOURAGING -> {
            eye(47f)
            drawLine(Ink, Offset(68f, 70f), Offset(78f, 70f), strokeWidth = 4f, cap = StrokeCap.Round)
            drawPath(Path().apply { moveTo(52f, 85f); quadraticTo(60f, 90f, 68f, 85f) }, Ink, style = line)
            drawLine(Cheek, Offset(96f, 60f), Offset(106f, 50f), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(Cheek, Offset(100f, 72f), Offset(112f, 68f), strokeWidth = 4f, cap = StrokeCap.Round)
        }
        Mood.PROUD -> {
            drawPath(Path().apply { moveTo(41f, 70f); quadraticTo(47f, 64f, 53f, 70f) }, Ink, style = line)
            drawPath(Path().apply { moveTo(67f, 70f); quadraticTo(73f, 64f, 79f, 70f) }, Ink, style = line)
            drawPath(Path().apply { moveTo(48f, 82f); quadraticTo(60f, 100f, 72f, 82f); close() }, Ink)
            listOf(Offset(14f, 30f) to Offset(18f, 40f), Offset(106f, 30f) to Offset(102f, 40f), Offset(20f, 14f) to Offset(26f, 22f))
                .forEach { (a, b) -> drawLine(sparkle, a, b, strokeWidth = 4f, cap = StrokeCap.Round) }
        }
    }
    when (theme) {
        AppTheme.CLASSIC -> Unit
        AppTheme.PIRATE -> pirateOutfit()
        AppTheme.TRAVEL -> {
            travelOutfit()
            leaves(mood, growth)
        }
    }
}

/** Feuilles : plus grandes quand elle est fière ; tige, feuilles en plus et fleur selon le palier. */
private fun DrawScope.leaves(mood: Mood, growth: Growth) {
    if (growth >= Growth.PLANT) {
        // Tige qui sort du haut de la tête, avec deux feuilles basses de chaque côté.
        val top = if (growth == Growth.SHRUB) 6f else 10f
        drawLine(LeafDark, Offset(60f, 36f), Offset(60f, top), strokeWidth = 4f, cap = StrokeCap.Round)
        drawPath(leaf(60f, 26f, 54f, 20f, 44f, 18f, 34f, 22f, 40f, 30f, 52f, 30f), Body)
        drawPath(leaf(60f, 22f, 66f, 16f, 76f, 14f, 86f, 18f, 80f, 26f, 68f, 26f), LeafDark)
        if (growth == Growth.SHRUB) {
            drawPath(leaf(60f, 14f, 54f, 8f, 46f, 6f, 38f, 10f, 42f, 16f, 52f, 16f), LeafDark)
            drawPath(leaf(60f, 10f, 66f, 4f, 74f, 2f, 82f, 6f, 78f, 12f, 68f, 12f), Body)
            // Petite fleur au sommet.
            listOf(Offset(56f, 6f), Offset(64f, 6f), Offset(60f, 2f), Offset(60f, 10f))
                .forEach { drawCircle(Cheek, radius = 3.2f, center = it) }
            drawCircle(FlameCore, radius = 2.6f, center = Offset(60f, 6f))
        } else {
            drawCircle(Cheek, radius = 3.5f, center = Offset(60f, top))
        }
    }
    if (growth >= Growth.LEAVES) {
        // Deux petites feuilles en plus, plus bas sur les côtés.
        drawPath(leaf(56f, 38f, 50f, 34f, 40f, 32f, 30f, 36f, 36f, 42f, 48f, 41f), Body)
        drawPath(leaf(64f, 38f, 70f, 34f, 80f, 32f, 90f, 36f, 84f, 42f, 72f, 41f), LeafDark)
    }
    if (mood == Mood.PROUD) {
        drawPath(leaf(60f, 34f, 56f, 16f, 44f, 6f, 28f, 4f, 30f, 24f, 42f, 33f), LeafDark)
        drawPath(leaf(60f, 34f, 64f, 14f, 78f, 4f, 96f, 4f, 92f, 26f, 78f, 34f), Body)
    } else {
        drawPath(leaf(60f, 34f, 58f, 22f, 50f, 14f, 38f, 12f, 40f, 26f, 48f, 33f), LeafDark)
        drawPath(leaf(60f, 34f, 62f, 20f, 72f, 12f, 86f, 12f, 84f, 28f, 74f, 34f), Body)
    }
}

/** Bandana rouge à pois sur le haut de la tête, nœud à gauche, cache-œil sur l'œil droit (comme l'icône). */
private fun DrawScope.pirateOutfit() {
    val head = Path().apply { addOval(Rect(Offset(60f, 74f), 34f)) }
    clipPath(head) {
        drawRect(PirateRed, topLeft = Offset(20f, 30f), size = Size(80f, 26f))
        listOf(Offset(44f, 47f), Offset(60f, 44f), Offset(76f, 47f), Offset(52f, 52f), Offset(68f, 52f))
            .forEach { drawCircle(Color.White, radius = 2.2f, center = it) }
    }
    drawPath(Path().apply { moveTo(28f, 54f); lineTo(14f, 48f); lineTo(16f, 62f); close() }, PirateRed)
    drawPath(Path().apply { moveTo(28f, 56f); lineTo(18f, 68f); lineTo(28f, 68f); close() }, PirateRed)
    drawLine(Ink, Offset(52f, 56f), Offset(93f, 80f), strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawCircle(Ink, radius = 8f, center = Offset(73f, 70f))
}

/** Chapeau de paille posé sur la tête et bretelle du sac à dos. */
private fun DrawScope.travelOutfit() {
    // Une seule bretelle, fine, du côté du sac.
    drawPath(Path().apply { moveTo(84f, 58f); quadraticTo(90f, 78f, 86f, 100f) }, Leather, style = Stroke(width = 3f, cap = StrokeCap.Round))
    drawOval(Straw, topLeft = Offset(20f, 40f), size = Size(80f, 14f))
    drawRoundRect(Straw, topLeft = Offset(40f, 26f), size = Size(40f, 22f), cornerRadius = CornerRadius(10f))
    drawRect(Color(0xFF1C6E8C), topLeft = Offset(40f, 38f), size = Size(40f, 6f))
    drawOval(StrawDark, topLeft = Offset(20f, 40f), size = Size(80f, 14f), style = Stroke(width = 2f))
}

private fun DrawScope.eye(x: Float) {
    drawCircle(Ink, radius = 5f, center = Offset(x, 70f))
    drawCircle(Color.White, radius = 1.6f, center = Offset(x + 2f, 68f))
}

/** Feuille : deux courbes de Bézier du point de départ vers la pointe et retour. */
private fun leaf(
    x0: Float, y0: Float,
    c1x: Float, c1y: Float, c2x: Float, c2y: Float, tipX: Float, tipY: Float,
    c3x: Float, c3y: Float, c4x: Float, c4y: Float,
) = Path().apply {
    moveTo(x0, y0)
    cubicTo(c1x, c1y, c2x, c2y, tipX, tipY)
    cubicTo(c3x, c3y, c4x, c4y, x0, y0)
    close()
}

/** Petite flamme de la série de semaines actives. */
@Composable
fun StreakFlame(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val flame = HealthyTheme.ambiance.flame
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) {
            drawPath(
                Path().apply {
                    moveTo(12f, 2f)
                    cubicTo(13f, 7f, 18f, 9f, 18f, 15f)
                    cubicTo(18f, 18.3f, 15.3f, 21f, 12f, 21f)
                    cubicTo(8.7f, 21f, 6f, 18.3f, 6f, 15f)
                    cubicTo(6f, 11f, 9f, 10f, 9f, 6f)
                    cubicTo(11f, 8f, 11f, 9f, 12f, 10f)
                    close()
                },
                flame,
            )
            drawPath(
                Path().apply {
                    moveTo(12f, 12f)
                    cubicTo(13f, 14f, 15f, 15f, 15f, 17f)
                    cubicTo(15f, 18.7f, 13.7f, 20f, 12f, 20f)
                    cubicTo(10.3f, 20f, 9f, 18.7f, 9f, 17f)
                    cubicTo(9f, 15f, 11f, 14f, 12f, 12f)
                    close()
                },
                FlameCore,
            )
        }
    }
}
