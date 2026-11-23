package com.bidaboss.healthy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.core.progress.Growth
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.theme.HealthyTheme

internal val PousseInk = Color(0xFF17301F)
private val FlameCore = Color(0xFFFFC857)

/**
 * Pousse, la mascotte, en relief (refonte, lot 5) : dégradés pour le volume, reflet en haut à gauche,
 * ombre au sol. Dessinée sur une grille de 120 × 120, portée du composant `Pousse` du prototype.
 *
 * 4 étapes selon [growth] (feuille, jeune pousse, arbuste avec tronc, arbre en fleur). Sa tenue suit
 * le thème : bandana rouge et cache-œil en Pirate, chapeau de paille et sac à dos en Voyage, rien en
 * Classique. Appel simple pour les autres écrans : `Pousse(growth = ..., size = ...)`.
 */
@Composable
fun Pousse(
    mood: Mood = Mood.HAPPY,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    growth: Growth = Growth.LEAF,
    theme: AppTheme = HealthyTheme.ambiance.theme,
) {
    val sparkle = HealthyTheme.ambiance.sparkle
    val label = pousseLabel(theme, mood, growth)
    Canvas(modifier.size(size).semantics { contentDescription = label }) {
        scale(this.size.minDimension / 120f, pivot = Offset.Zero) { drawPousse(mood, theme, sparkle, growth = growth) }
    }
}

/** Ce que dit le lecteur d'écran de Pousse, selon le thème, l'humeur et l'étape. */
internal fun pousseLabel(theme: AppTheme, mood: Mood, growth: Growth): String {
    val who = when (theme) {
        AppTheme.PIRATE -> "Capitaine Pousse"
        AppTheme.TRAVEL -> "Pousse en voyage"
        else -> "Pousse"
    }
    val feeling = when (mood) {
        Mood.HAPPY -> "contente"
        Mood.ENCOURAGING -> "t'encourage"
        Mood.PROUD -> "fière de toi"
    }
    return "$who, $feeling, ${growth.label}"
}

// Les chemins sont lus du texte SVG du prototype, tels quels : un seul endroit à comparer.
private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

private val leafPath by lazy { svg("M0 0 C-13 -8 -15 -27 0 -44 C15 -27 13 -8 0 0 Z") }
private val leafBodyOutline by lazy { svg("M60 28 C92 48 98 82 60 110 C22 82 28 48 60 28 Z") }
private val leafBody by lazy { svg("M60 31 C89 49 94 81 60 107 C26 81 31 49 60 31 Z") }
private val trunk by lazy { svg("M44 106 C41 90 43 70 45 54 C52 50 68 50 75 54 C77 70 79 90 76 106 Z") }
private val trunkRoots by lazy { svg("M36 112 C42 108 46 106 48 100 L72 100 C74 106 78 108 84 112 Z") }
private val soil by lazy { svg("M26 112 Q60 100 94 112 Z") }
private val bandana by lazy { svg("M27 62 Q60 26 93 62 L93 52 Q60 14 27 52 Z") }
private val bandanaKnot by lazy { svg("M93 54 L106 44 L104 60 Z") }
private val strawCrown by lazy { svg("M38 52 Q60 22 82 52 Z") }

private fun c(hex: Long) = Color(hex or 0xFF000000)

/** Dégradé radial en pourcentages du cadre de [box] (comme `objectBoundingBox` en SVG). */
private fun radial(box: androidx.compose.ui.geometry.Rect, cx: Float, cy: Float, r: Float, vararg stops: Pair<Float, Color>) =
    Brush.radialGradient(
        colorStops = stops,
        center = Offset(box.left + box.width * cx, box.top + box.height * cy),
        radius = r * maxOf(box.width, box.height),
    )

private fun linear(
    box: androidx.compose.ui.geometry.Rect, x1: Float, y1: Float, x2: Float, y2: Float, vararg stops: Pair<Float, Color>,
) = Brush.linearGradient(
    colorStops = stops,
    start = Offset(box.left + box.width * x1, box.top + box.height * y1),
    end = Offset(box.left + box.width * x2, box.top + box.height * y2),
)

private class Paints {
    val leafBox = leafPath.getBounds()
    val leafA = linear(leafBox, 0f, 0f, 1f, 1f, 0f to c(0x8CE3A8), 1f to c(0x2A9158))
    val leafB = linear(leafBox, 1f, 0f, 0f, 1f, 0f to c(0xBDF2CA), 1f to c(0x3DAA6C))
    val body = leafBody.getBounds().let {
        radial(it, 0.34f, 0.28f, 0.85f, 0f to c(0xE4FFEA), 0.3f to c(0x9CE8B4), 0.75f to c(0x4FBF7C), 1f to c(0x2A8A55))
    }
    val trunkBrush = trunk.getBounds().let { linear(it, 0f, 0f, 1f, 0f, 0f to c(0xC79561), 0.35f to c(0xA9743F), 1f to c(0x6E4524)) }
    val rootsBrush = trunkRoots.getBounds().let { linear(it, 0f, 0f, 1f, 0f, 0f to c(0xC79561), 0.35f to c(0xA9743F), 1f to c(0x6E4524)) }
    val bandanaBrush = bandana.getBounds().let { linear(it, 0f, 0f, 0f, 1f, 0f to c(0xE5584A), 1f to c(0xA12D22)) }
    val knotBrush = bandanaKnot.getBounds().let { linear(it, 0f, 0f, 0f, 1f, 0f to c(0xE5584A), 1f to c(0xA12D22)) }
    val hatBrush = Brush.verticalGradient(0f to c(0xF8E2A6), 1f to c(0xD9AE55), startY = 14f, endY = 52f)
    val brimBrush = Brush.verticalGradient(0f to c(0xF8E2A6), 1f to c(0xD9AE55), startY = 45f, endY = 61f)
    val bagBrush = radial(androidx.compose.ui.geometry.Rect(80f, 68f, 98f, 100f), 0.35f, 0.3f, 0.8f, 0f to c(0xF2B35A), 1f to c(0xB8741F))
    val flower = radial(androidx.compose.ui.geometry.Rect(-13.2f, -15f, 13.2f, 14f), 0.4f, 0.35f, 0.7f, 0f to c(0xFFC1A8), 1f to c(0xF0603C))
    val fruit = radial(androidx.compose.ui.geometry.Rect(-4.4f, -4.4f, 4.4f, 4.4f), 0.35f, 0.3f, 0.75f, 0f to c(0xFF9A8A), 1f to c(0xC73A2E))
}

private val paints by lazy { Paints() }

/**
 * Pousse sur la grille 120 × 120 ; [shadow] = false pour les illustrations d'exercices (ombre dessinée à part),
 * qui demandent aussi [fullSize] (pas de réduction selon l'étape).
 */
internal fun DrawScope.drawPousse(
    mood: Mood,
    theme: AppTheme,
    sparkle: Color,
    shadow: Boolean = true,
    growth: Growth = Growth.LEAF,
    fullSize: Boolean = false,
) {
    val p = paints
    val hasTrunk = growth >= Growth.SHRUB
    // fullSize : les illustrations d'exercices (membres dessinés autour) veulent le corps à pleine taille.
    val s = if (fullSize) 1f else when (growth) {
        Growth.LEAF -> 0.62f
        Growth.SPROUT -> 0.7f
        Growth.SHRUB -> 0.88f
        Growth.TREE -> 0.8f
    }
    withTransform({ translate(60f - 60f * s, 112f - 112f * s); scale(s, s, pivot = Offset.Zero) }) {
        if (shadow) {
            // Ombre au sol, puis petit tas de terre et brins d'herbe.
            withTransform({ scale(1f, 7f / 34f, pivot = Offset(60f, 112f)) }) {
                drawCircle(
                    Brush.radialGradient(0f to Color.Black.copy(alpha = 0.42f), 1f to Color.Transparent, center = Offset(60f, 112f), radius = 34f),
                    radius = 34f,
                    center = Offset(60f, 112f),
                )
            }
            drawPath(soil, c(0x6B4A2B), alpha = 0.92f)
            drawPath(svg("M31 112 Q60 103 89 112"), c(0x8A6238), alpha = 0.7f, style = Stroke(width = 2f))
            drawPath(svg("M20 112 L22 104 L26 112 M96 112 L99 103 L102 112"), c(0x3FA66B), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
        }
        if (theme == AppTheme.TRAVEL) drawBackpack(p, hasTrunk)
        if (hasTrunk) drawTree(p, growth) else drawLeafBody(p, growth)
        withTransform({ if (hasTrunk) scale(0.78f, 0.78f, pivot = Offset(60f, 78f)) }) { drawFace(mood) }
        if (hasTrunk) {
            // Avec un tronc, la tenue se pose sur le haut du tronc, à l'échelle du tronc.
            when (theme) {
                AppTheme.PIRATE -> {
                    withTransform({ translate(0f, 4f); scale(0.6f, 0.6f, pivot = Offset(60f, 56f)) }) { drawBandana(p) }
                    drawEyePatch(Offset(70.2f, 73.3f), 5.5f, Offset(53f, 66.5f))
                }
                AppTheme.TRAVEL -> {
                    withTransform({ scale(0.62f, 0.62f, pivot = Offset(60f, 56f)) }) { drawStrawHat(p) }
                    drawBackpackStrap(-8f)
                }
                else -> Unit
            }
        } else {
            withTransform({ scale(0.8f, 1f, pivot = Offset(60f, 0f)) }) {
                when (theme) {
                    AppTheme.PIRATE -> {
                        drawBandana(p)
                        drawEyePatch(Offset(73f, 73f), 9f, Offset(34f, 63f))
                    }
                    AppTheme.TRAVEL -> drawStrawHat(p)
                    else -> Unit
                }
            }
            if (theme == AppTheme.TRAVEL) drawBackpackStrap(0f)
        }
    }
    if (mood == Mood.PROUD) {
        listOf(Offset(14f, 30f) to Offset(18f, 40f), Offset(106f, 30f) to Offset(102f, 40f), Offset(20f, 14f) to Offset(26f, 22f))
            .forEach { (a, b) -> drawLine(sparkle, a, b, strokeWidth = 4f, cap = StrokeCap.Round) }
    }
}

private fun DrawScope.leaf(p: Paints, x: Float, y: Float, angle: Float, size: Float, second: Boolean) {
    withTransform({ translate(x, y); rotate(angle, pivot = Offset.Zero); scale(size, size, pivot = Offset.Zero) }) {
        drawPath(leafPath, if (second) p.leafB else p.leafA)
    }
}

private fun DrawScope.flower(p: Paints, x: Float, y: Float, size: Float) {
    withTransform({ translate(x, y); scale(size, size, pivot = Offset.Zero) }) {
        listOf(0f to (0f to -8f), 72f to (8f to -3f), 144f to (5f to 7f), 216f to (-5f to 7f), 288f to (-8f to -3f)).forEach { (deg, at) ->
            rotate(deg, pivot = Offset(at.first, at.second)) {
                drawOval(p.flower, topLeft = Offset(at.first - 5.2f, at.second - 7f), size = Size(10.4f, 14f))
            }
        }
        drawCircle(c(0xFFD25C), radius = 4.6f)
        drawCircle(c(0xFFF3C4), radius = 1.6f, center = Offset(-1.4f, -1.4f))
    }
}

/** Étapes 1 et 2 : le corps est une feuille ; la jeune pousse a deux petites feuilles dans le dos. */
private fun DrawScope.drawLeafBody(p: Paints, growth: Growth) {
    if (growth == Growth.SPROUT) {
        leaf(p, 60f, 36f, -34f, 0.62f, false)
        leaf(p, 60f, 36f, 34f, 0.62f, true)
    }
    drawPath(leafBodyOutline, Color.White, alpha = 0.9f)
    drawPath(leafBody, p.body)
    val vein = c(0x1F6B42)
    drawPath(svg("M60 104 C60 84 60 60 60 38"), vein, alpha = 0.25f, style = Stroke(width = 2f, cap = StrokeCap.Round))
    drawPath(svg("M60 92 L46 82 M60 80 L74 70 M60 70 L48 62"), vein, alpha = 0.18f, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
    rotate(-30f, pivot = Offset(45f, 54f)) {
        drawOval(Color.White, topLeft = Offset(36f, 48.5f), size = Size(18f, 11f), alpha = 0.55f)
    }
    drawLine(c(0x2A8A55), Offset(60f, 110f), Offset(60f, 116f), strokeWidth = 3.2f, cap = StrokeCap.Round)
}

/** Étapes 3 et 4 : tronc et couronne de feuilles ; l'arbre en fleur a trois fleurs et deux fruits. */
private fun DrawScope.drawTree(p: Paints, growth: Growth) {
    if (growth == Growth.SHRUB) {
        leaf(p, 60f, 56f, -78f, 0.95f, false); leaf(p, 60f, 56f, 78f, 0.95f, true)
        leaf(p, 60f, 56f, -48f, 1.1f, true); leaf(p, 60f, 56f, 48f, 1.1f, false)
        leaf(p, 60f, 56f, -18f, 1.15f, false); leaf(p, 60f, 56f, 18f, 1.15f, true)
    } else {
        leaf(p, 60f, 56f, -96f, 0.95f, false); leaf(p, 60f, 56f, 96f, 0.95f, true)
        leaf(p, 60f, 56f, -70f, 1.15f, true); leaf(p, 60f, 56f, 70f, 1.15f, false)
        leaf(p, 60f, 56f, -44f, 1.3f, false); leaf(p, 60f, 56f, 44f, 1.3f, true)
        leaf(p, 60f, 56f, -18f, 1.4f, true); leaf(p, 60f, 56f, 18f, 1.4f, false)
        leaf(p, 60f, 56f, 0f, 1.45f, false)
        flower(p, 60f, 4f, 0.95f); flower(p, 27f, 24f, 0.7f); flower(p, 93f, 24f, 0.7f)
        listOf(Offset(36f, 44f), Offset(84f, 42f)).forEach { at ->
            withTransform({ translate(at.x, at.y) }) { drawCircle(p.fruit, radius = 4.4f, center = Offset.Zero) }
        }
    }
    drawPath(trunkRoots, p.rootsBrush)
    drawPath(trunk, p.trunkBrush)
    drawPath(trunk, Color.White, alpha = 0.5f, style = Stroke(width = 2f))
    drawPath(svg("M52 60 C51 72 52 84 51 98 M66 58 C68 70 67 84 69 98"), c(0x4B2C14), alpha = 0.25f, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
    drawOval(Color.White, topLeft = Offset(46.6f, 54f), size = Size(6.8f, 16f), alpha = 0.28f)
    drawOval(c(0x8B5A2E), topLeft = Offset(44.5f, 50.2f), size = Size(31f, 7.6f))
    drawOval(c(0xD2A06A), topLeft = Offset(48f, 50.9f), size = Size(24f, 5.2f), alpha = 0.7f)
}

private fun DrawScope.drawFace(mood: Mood) {
    val ink = PousseInk
    val line = Stroke(width = 4f, cap = StrokeCap.Round)
    fun eye(x: Float) {
        drawCircle(ink, radius = 5.4f, center = Offset(x, 72f))
        drawCircle(Color.White, radius = 1.8f, center = Offset(x + 2f, 70f))
    }
    val cheek = Brush.radialGradient(0f to c(0xFF8A65).copy(alpha = 0.85f), 1f to c(0xFF8A65).copy(alpha = 0f), center = Offset(39f, 84f), radius = 7f)
    drawCircle(cheek, radius = 7f, center = Offset(39f, 84f))
    withTransform({ translate(42f, 0f) }) { drawCircle(cheek, radius = 7f, center = Offset(39f, 84f)) }
    when (mood) {
        Mood.HAPPY -> {
            eye(47f); eye(73f)
            drawCircle(Color.White, radius = 1f, center = Offset(46f, 75f), alpha = 0.7f)
            drawPath(svg("M50 85 Q60 95 70 85"), ink, style = line)
        }
        Mood.ENCOURAGING -> {
            eye(47f)
            drawLine(ink, Offset(68f, 72f), Offset(78f, 72f), strokeWidth = 4f, cap = StrokeCap.Round)
            drawPath(svg("M52 86 Q60 91 68 86"), ink, style = line)
        }
        Mood.PROUD -> {
            drawPath(svg("M41 72 Q47 66 53 72"), ink, style = line)
            drawPath(svg("M67 72 Q73 66 79 72"), ink, style = line)
            drawPath(svg("M48 83 Q60 101 72 83 Z"), ink)
        }
    }
}

/** Bandana rouge à pois, nœud à droite. */
private fun DrawScope.drawBandana(p: Paints) {
    drawPath(bandana, p.bandanaBrush)
    drawPath(svg("M27 62 Q60 28 93 62"), c(0xFFB4A8), alpha = 0.5f, style = Stroke(width = 1.4f))
    drawPath(bandanaKnot, p.knotBrush)
    listOf(Offset(46f, 44f), Offset(60f, 38f), Offset(74f, 44f)).forEach { drawCircle(Color.White, radius = 2f, center = it, alpha = 0.9f) }
}

/** Cache-œil sur l'œil droit (comme l'icône) ; [strapFrom] = début de la lanière côté gauche. */
private fun DrawScope.drawEyePatch(center: Offset, radius: Float, strapFrom: Offset) {
    drawLine(PousseInk, strapFrom, center, strokeWidth = 2.6f)
    drawCircle(PousseInk, radius = radius, center = center)
    drawOval(Color.White, topLeft = Offset(center.x - radius * 0.7f, center.y - radius * 0.55f), size = Size(radius * 0.7f, radius * 0.45f), alpha = 0.3f)
}

/** Chapeau de paille à ruban bleu. */
private fun DrawScope.drawStrawHat(p: Paints) {
    drawPath(strawCrown, p.hatBrush)
    drawRoundRect(c(0x1C6E8C), topLeft = Offset(38f, 47f), size = Size(44f, 6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f))
    drawOval(p.brimBrush, topLeft = Offset(15f, 45f), size = Size(90f, 16f))
    drawPath(svg("M22 53 Q60 45 98 53"), c(0xB8903F), alpha = 0.7f, style = Stroke(width = 2f))
    rotate(-20f, pivot = Offset(44f, 40f)) {
        drawOval(Color.White, topLeft = Offset(36f, 37f), size = Size(16f, 6f), alpha = 0.35f)
    }
}

/** Sac à dos de voyage (orange kaki, pour le distinguer du tronc), derrière le corps à droite. */
private fun DrawScope.drawBackpack(p: Paints, tree: Boolean) {
    val x = if (tree) 70f else 80f
    withTransform({ translate(x - 80f, 0f) }) {
        drawRoundRect(p.bagBrush, topLeft = Offset(80f, 68f), size = Size(18f, 32f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
        // Rabat plus foncé et boucle.
        drawRoundRect(c(0x9A5B14), topLeft = Offset(80f, 68f), size = Size(18f, 13f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f))
        drawCircle(c(0xFFE7A8), radius = 1.8f, center = Offset(89f, 79f))
        drawPath(svg("M83 74 Q89 70 95 74"), c(0xFFE7A8), alpha = 0.8f, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }
}

/** Bretelle visible sur le corps, du haut du dos vers le sac. */
private fun DrawScope.drawBackpackStrap(dx: Float) {
    withTransform({ translate(dx, 0f) }) {
        drawPath(svg("M84 58 Q90 78 86 100"), c(0xB8741F), style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawPath(svg("M84 58 Q90 78 86 100"), c(0xF2B35A), alpha = 0.6f, style = Stroke(width = 1.4f, cap = StrokeCap.Round))
    }
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
