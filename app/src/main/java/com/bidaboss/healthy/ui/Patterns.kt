package com.bidaboss.healthy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.theme.HealthyTheme

/*
 * Un motif discret par espace (fil Design, 11/11/2026), dessiné en vectoriel d'après la maquette
 * (artboard « Textures », tuiles de 280 × 280). Jamais sous l'anneau des calories, les macros ou le
 * chrono : ces éléments sont sur des cartes unies.
 * Thème Pirate : carte au trésor partout. Thème Voyage : trajets d'avion en pointillés, avions, boussole.
 * Espace : étoiles, orbites en pointillés, petites planètes. Arcade rétro : grille de pixels et petits
 * cœurs. Montagne : lignes de crêtes et sapins en silhouette (fil Design, 12/11/2026).
 */

/** Motif de l'en-tête d'Aujourd'hui selon le thème. */
@Composable
fun Modifier.headerPattern(): Modifier = when (HealthyTheme.ambiance.theme) {
    AppTheme.CLASSIC, AppTheme.PIRATE -> treasureMapPattern()
    AppTheme.TRAVEL -> travelHeaderPattern()
    AppTheme.SPACE -> drawBehind { headerTiles(Color.White.copy(alpha = 0.16f)) { spaceTile(it) } }
    AppTheme.ARCADE -> drawBehind { headerTiles(Color.White.copy(alpha = 0.10f)) { arcadeTile(it) } }
    AppTheme.MOUNTAIN -> drawBehind { headerTiles(Color.White.copy(alpha = 0.14f)) { mountainTile(it) } }
}

/** Fond de l'écran Repas selon le thème. */
@Composable
fun Modifier.mealsBackground(): Modifier = when (HealthyTheme.ambiance.theme) {
    AppTheme.CLASSIC -> foodPattern()
    AppTheme.PIRATE -> mapTilePattern()
    AppTheme.TRAVEL -> routesTilePattern()
    AppTheme.SPACE -> themedTiles { spaceTile(it) }
    AppTheme.ARCADE -> themedTiles { arcadeTile(it) }
    AppTheme.MOUNTAIN -> themedTiles { mountainTile(it) }
}

/** Fond de l'écran Sport selon le thème. */
@Composable
fun Modifier.workoutBackground(): Modifier = when (HealthyTheme.ambiance.theme) {
    AppTheme.CLASSIC -> speedLinesPattern()
    AppTheme.PIRATE -> mapTilePattern()
    AppTheme.TRAVEL -> routesTilePattern()
    AppTheme.SPACE -> themedTiles { spaceTile(it) }
    AppTheme.ARCADE -> themedTiles { arcadeTile(it) }
    AppTheme.MOUNTAIN -> themedTiles { mountainTile(it) }
}

private const val TILE = 280f

/**
 * Aujourd'hui : courbes de niveau et pointillé blancs, petite croix corail, dans l'en-tête vert.
 * Dessiné à la taille de l'en-tête (pas de tuile) pour que la croix tombe sous la flamme, loin du texte.
 */
private fun Modifier.treasureMapPattern(): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val d = density
    val white = Color.White.copy(alpha = 0.12f)
    val line = Stroke(width = 1.5f * d)
    clipRect {
        listOf(0.22f, 0.48f, 0.74f).forEach { f ->
            val y = h * f
            drawPath(
                Path().apply {
                    moveTo(-10f, y)
                    cubicTo(w * 0.2f, y - 28f * d, w * 0.4f, y + 28f * d, w * 0.65f, y)
                    cubicTo(w * 0.85f, y - 22f * d, w * 0.95f, y - 10f * d, w + 10f, y - 12f * d)
                },
                white,
                style = line,
            )
        }
        val target = Offset(w * 0.84f, h * 0.9f)
        drawOval(white, topLeft = target - Offset(36f * d, 14f * d), size = Size(72f * d, 28f * d), style = line)
        drawOval(white, topLeft = target - Offset(20f * d, 8f * d), size = Size(40f * d, 16f * d), style = line)
        drawPath(
            Path().apply {
                moveTo(w * 0.3f, h + 4f * d)
                quadraticTo(w * 0.55f, h * 0.78f, target.x - 40f * d, target.y)
            },
            white,
            style = Stroke(width = line.width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * d, 5f * d))),
        )
        val cross = Color(0xFFE5464F).copy(alpha = 0.5f)
        val c = 4f * d
        drawLine(cross, target - Offset(c, c), target + Offset(c, c), strokeWidth = 2.5f * d, cap = StrokeCap.Round)
        drawLine(cross, target + Offset(c, -c), target + Offset(-c, c), strokeWidth = 2.5f * d, cap = StrokeCap.Round)
    }
}

/** Repas : fond clair semé de pommes, carottes et fourchettes au trait. */
@Composable
private fun Modifier.foodPattern(): Modifier {
    val dark = isSystemInDarkTheme()
    val background = if (dark) MaterialTheme.colorScheme.background else Color(0xFFF3FAF5)
    val ink = if (dark) Color(0xFF7BD99A).copy(alpha = 0.10f) else HealthyTheme.ambiance.patternInk.copy(alpha = 0.14f)
    return background(background).drawBehind {
        val s = density / 2.5f * 1.6f
        tiled(s) { foodTile(ink) }
    }
}

/** Sport : fond pêche avec diagonales corail, comme des lignes de vitesse. */
@Composable
private fun Modifier.speedLinesPattern(): Modifier {
    val dark = isSystemInDarkTheme()
    val background = if (dark) MaterialTheme.colorScheme.background else Color(0xFFFFF4EC)
    val ink = HealthyTheme.ambiance.flame.copy(alpha = if (dark) 0.10f else 0.12f)
    return background(background).drawBehind {
        tiled(density / 2.5f * 1.6f) {
            listOf(
                Offset(20f, 90f) to Offset(90f, 20f),
                Offset(60f, 130f) to Offset(170f, 20f),
                Offset(120f, 150f) to Offset(250f, 20f),
                Offset(20f, 270f) to Offset(150f, 140f),
                Offset(120f, 270f) to Offset(260f, 130f),
            ).forEach { (a, b) -> drawLine(ink, a, b, strokeWidth = 6f, cap = StrokeCap.Round) }
            listOf(Offset(230f, 230f), Offset(248f, 216f), Offset(212f, 244f))
                .forEach { drawCircle(ink, radius = 4f, center = it) }
        }
    }
}

/** Encre des motifs pirate et voyage : couleur du texte du fond, très légère. */
@Composable
private fun themedInk(): Color =
    MaterialTheme.colorScheme.onBackground.copy(alpha = if (isSystemInDarkTheme()) 0.08f else 0.10f)

/** Pirate : courbes de niveau, chemin en pointillés et petite croix, sur le parchemin. */
@Composable
private fun Modifier.mapTilePattern(): Modifier {
    val ink = themedInk()
    return background(MaterialTheme.colorScheme.background).drawBehind {
        tiled(density / 2.5f * 1.6f) { mapTile(ink) }
    }
}

/** Voyage : trajets d'avion en pointillés et petits avions, sur le sable. */
@Composable
private fun Modifier.routesTilePattern(): Modifier {
    val ink = themedInk()
    return background(MaterialTheme.colorScheme.background).drawBehind {
        tiled(density / 2.5f * 1.6f) { routesTile(ink) }
    }
}

/** Voyage : trajet en pointillés, petit avion et boussole blancs dans l'en-tête, loin du texte. */
private fun Modifier.travelHeaderPattern(): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val d = density
    val white = Color.White.copy(alpha = 0.16f)
    clipRect {
        drawPath(
            Path().apply {
                moveTo(-10f, h * 0.85f)
                cubicTo(w * 0.25f, h * 0.2f, w * 0.55f, h * 1.1f, w * 0.78f, h * 0.25f)
            },
            white,
            style = Stroke(width = 1.5f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * d, 5f * d))),
        )
        plane(Offset(w * 0.8f, h * 0.22f), d * 0.9f, white)
        // Boussole sous la flamme, comme la croix de la carte au trésor.
        val c = Offset(w * 0.86f, h * 0.88f)
        val r = 16f * d
        drawCircle(white, radius = r, center = c, style = Stroke(width = 1.5f * d))
        drawPath(Path().apply { moveTo(c.x, c.y - r * 0.8f); lineTo(c.x + r * 0.22f, c.y); lineTo(c.x, c.y + r * 0.8f); lineTo(c.x - r * 0.22f, c.y); close() }, white)
    }
}

/** Fond uni du thème avec une tuile dessinée à l'encre du texte, très légère. */
@Composable
private fun Modifier.themedTiles(tile: DrawScope.(Color) -> Unit): Modifier {
    val ink = themedInk()
    return background(MaterialTheme.colorScheme.background).drawBehind { tiled(density / 2.5f * 1.6f) { tile(ink) } }
}

/** En-tête d'Aujourd'hui : la même tuile, en blanc léger, un peu plus grande. */
private fun DrawScope.headerTiles(ink: Color, tile: DrawScope.(Color) -> Unit) = tiled(density / 2.5f * 2f) { tile(ink) }

/** Espace : étoiles, orbite en pointillés autour d'une petite planète, planète à anneau. */
private fun DrawScope.spaceTile(ink: Color) {
    listOf(Offset(24f, 30f) to 3f, Offset(120f, 18f) to 2f, Offset(250f, 40f) to 3f, Offset(40f, 150f) to 2f,
        Offset(260f, 150f) to 2f, Offset(150f, 250f) to 3f, Offset(20f, 260f) to 2f, Offset(200f, 110f) to 2f)
        .forEach { (c, r) -> drawCircle(ink, radius = r, center = c) }
    sparkleStar(Offset(90f, 90f), 9f, ink)
    sparkleStar(Offset(230f, 230f), 7f, ink)
    drawOval(
        ink,
        topLeft = Offset(120f, 140f),
        size = Size(120f, 56f),
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))),
    )
    drawCircle(ink, radius = 14f, center = Offset(180f, 168f))
    drawCircle(ink, radius = 10f, center = Offset(60f, 210f), style = Stroke(width = 2.5f))
    drawOval(ink, topLeft = Offset(42f, 205f), size = Size(36f, 10f), style = Stroke(width = 2f))
}

private fun DrawScope.sparkleStar(c: Offset, r: Float, ink: Color) {
    drawLine(ink, c - Offset(r, 0f), c + Offset(r, 0f), strokeWidth = 2.5f, cap = StrokeCap.Round)
    drawLine(ink, c - Offset(0f, r), c + Offset(0f, r), strokeWidth = 2.5f, cap = StrokeCap.Round)
}

/** Arcade rétro : grille de pixels et petits cœurs pixelisés. */
private fun DrawScope.arcadeTile(ink: Color) {
    var x = 0f
    while (x < TILE) {
        drawLine(ink.copy(alpha = ink.alpha * 0.5f), Offset(x, 0f), Offset(x, TILE), strokeWidth = 1f)
        drawLine(ink.copy(alpha = ink.alpha * 0.5f), Offset(0f, x), Offset(TILE, x), strokeWidth = 1f)
        x += 28f
    }
    pixelHeart(Offset(56f, 56f), ink)
    pixelHeart(Offset(196f, 168f), ink)
    pixelHeart(Offset(84f, 224f), ink)
}

private fun DrawScope.pixelHeart(at: Offset, ink: Color) {
    val rows = listOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...")
    val k = 4f
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, c -> if (c == 'X') drawRect(ink, at + Offset(x * k, y * k), Size(k, k)) }
    }
}

/** Montagne : deux lignes de crêtes et des sapins en silhouette. */
private fun DrawScope.mountainTile(ink: Color) {
    val ridge = Stroke(width = 2.5f, cap = StrokeCap.Round)
    drawPath(Path().apply { moveTo(0f, 110f); lineTo(50f, 60f); lineTo(80f, 85f); lineTo(140f, 30f); lineTo(200f, 95f); lineTo(240f, 70f); lineTo(280f, 110f) }, ink, style = ridge)
    drawPath(Path().apply { moveTo(0f, 230f); lineTo(70f, 170f); lineTo(110f, 205f); lineTo(170f, 150f); lineTo(280f, 230f) }, ink, style = ridge)
    listOf(Offset(40f, 150f) to 1f, Offset(64f, 156f) to 0.7f, Offset(220f, 260f) to 1f, Offset(244f, 266f) to 0.7f, Offset(200f, 160f) to 0.8f)
        .forEach { (at, k) -> fir(at, k, ink) }
}

private fun DrawScope.fir(at: Offset, k: Float, ink: Color) {
    drawPath(
        Path().apply {
            moveTo(at.x, at.y - 34f * k)
            lineTo(at.x + 10f * k, at.y - 18f * k); lineTo(at.x + 5f * k, at.y - 18f * k)
            lineTo(at.x + 14f * k, at.y - 4f * k); lineTo(at.x + 2f * k, at.y - 4f * k)
            lineTo(at.x + 2f * k, at.y); lineTo(at.x - 2f * k, at.y); lineTo(at.x - 2f * k, at.y - 4f * k)
            lineTo(at.x - 14f * k, at.y - 4f * k); lineTo(at.x - 5f * k, at.y - 18f * k)
            lineTo(at.x - 10f * k, at.y - 18f * k); close()
        },
        ink,
    )
}

/** Petit avion vu de dessus, nez vers la droite, sur une grille de 280. */
private fun DrawScope.plane(at: Offset, k: Float, color: Color) {
    translate(at.x, at.y) {
        scaleTile(k) {
            drawPath(
                Path().apply {
                    moveTo(18f, 0f); lineTo(-14f, -3f); lineTo(-18f, 0f); lineTo(-14f, 3f); close()
                    moveTo(4f, -2f); lineTo(-6f, -16f); lineTo(-10f, -16f); lineTo(-4f, -2f); close()
                    moveTo(4f, 2f); lineTo(-6f, 16f); lineTo(-10f, 16f); lineTo(-4f, 2f); close()
                    moveTo(-12f, -2f); lineTo(-18f, -8f); lineTo(-20f, -8f); lineTo(-17f, -1f); close()
                    moveTo(-12f, 2f); lineTo(-18f, 8f); lineTo(-20f, 8f); lineTo(-17f, 1f); close()
                },
                color,
            )
        }
    }
}

private inline fun DrawScope.scaleTile(sx: Float, sy: Float = sx, block: DrawScope.() -> Unit) {
    drawContext.transform.scale(sx, sy, Offset.Zero)
    block()
    drawContext.transform.scale(1f / sx, 1f / sy, Offset.Zero)
}

/** Répète une tuile de 280 × 280 (à l'échelle [s]) sur toute la surface. */
private inline fun DrawScope.tiled(s: Float, crossinline tile: DrawScope.() -> Unit) {
    val step = TILE * s
    clipRect {
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                translate(x, y) { scaleTile(s) { tile() } }
                x += step
            }
            y += step
        }
    }
}

/** Repas : pommes, carottes et fourchettes au trait. */
private fun DrawScope.foodTile(ink: Color) {
    val line = Stroke(width = 2.5f, cap = StrokeCap.Round)
    fun apple(x: Float, y: Float) {
        drawCircle(ink, radius = 10f, center = Offset(x, y), style = line)
        drawPath(Path().apply { moveTo(x, y - 10f); cubicTo(x, y - 16f, x + 4f, y - 18f, x + 8f, y - 18f) }, ink, style = line)
    }
    fun carrot(x: Float, y: Float) {
        drawLine(ink, Offset(x, y), Offset(x + 16f, y + 24f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawLine(ink, Offset(x + 8f, y), Offset(x + 10f, y - 4f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawLine(ink, Offset(x + 4f, y + 2f), Offset(x - 2f, y - 2f), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
    fun fork(x: Float, y: Float) {
        drawLine(ink, Offset(x, y), Offset(x, y + 30f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(x - 6f, y); lineTo(x - 6f, y + 10f)
                cubicTo(x - 6f, y + 18f, x + 6f, y + 18f, x + 6f, y + 10f); lineTo(x + 6f, y)
            },
            ink,
            style = line,
        )
    }
    apple(40f, 40f); carrot(120f, 30f); fork(210f, 26f)
    apple(80f, 120f); carrot(170f, 110f); fork(250f, 106f)
    fork(30f, 186f); apple(130f, 200f); carrot(210f, 190f)
}

/** Pirate : courbes de niveau, chemin en pointillés et petite croix. */
private fun DrawScope.mapTile(ink: Color) {
    val line = Stroke(width = 2.5f)
    listOf(60f, 150f, 240f).forEach { y ->
        drawPath(
            Path().apply { moveTo(0f, y); cubicTo(70f, y - 30f, 140f, y + 30f, 210f, y); cubicTo(240f, y - 10f, 260f, y - 6f, 280f, y) },
            ink,
            style = line,
        )
    }
    drawPath(
        Path().apply { moveTo(30f, 270f); quadraticTo(120f, 190f, 196f, 206f) },
        ink,
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))),
    )
    drawLine(ink, Offset(204f, 196f), Offset(220f, 212f), strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(ink, Offset(220f, 196f), Offset(204f, 212f), strokeWidth = 4f, cap = StrokeCap.Round)
}

/** Voyage : trajets d'avion en pointillés et petits avions. */
private fun DrawScope.routesTile(ink: Color) {
    val dashed = Stroke(width = 2.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)))
    drawPath(Path().apply { moveTo(10f, 120f); quadraticTo(90f, 20f, 190f, 60f) }, ink, style = dashed)
    drawPath(Path().apply { moveTo(80f, 270f); quadraticTo(150f, 170f, 260f, 200f) }, ink, style = dashed)
    plane(Offset(206f, 66f), 1f, ink)
    plane(Offset(40f, 200f), 0.8f, ink)
}

/**
 * Motif du thème pour le fond du widget, à l'encre [ink] (le widget le teinte ensuite) ; [s] règle la
 * taille d'une tuile. Les chiffres du widget sont posés sur des pavés unis, jamais sur ce motif.
 */
internal fun DrawScope.widgetPattern(theme: AppTheme, ink: Color, s: Float) = tiled(s) {
    when (theme) {
        AppTheme.CLASSIC -> foodTile(ink)
        AppTheme.PIRATE -> mapTile(ink)
        AppTheme.TRAVEL -> routesTile(ink)
        AppTheme.SPACE -> spaceTile(ink)
        AppTheme.ARCADE -> arcadeTile(ink)
        AppTheme.MOUNTAIN -> mountainTile(ink)
    }
}
