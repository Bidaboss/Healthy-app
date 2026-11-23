package com.bidaboss.healthy.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.bidaboss.healthy.core.progress.Growth
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.settings.data.AppTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Dessine Pousse (3 thèmes × 4 étapes, 3 humeurs) ; échoue si un dessin plante. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class PousseRenderTest {
    @Test
    fun `dessine toutes les etapes sans planter`() {
        val cell = 240
        val themes = listOf(AppTheme.CLASSIC, AppTheme.PIRATE, AppTheme.TRAVEL)
        val moods = listOf(Mood.HAPPY, Mood.ENCOURAGING, Mood.PROUD)
        val rows = themes.size + moods.size - 1
        val bmp = Bitmap.createBitmap(cell * 4, cell * rows, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp.asImageBitmap())
        val scope = CanvasDrawScope()
        val bg = listOf(Color(0xFFF3FAF5), Color(0xFF1B2A22))
        scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(bmp.width.toFloat(), bmp.height.toFloat())) {
            drawRect(bg[0])
            var row = 0
            themes.forEach { theme ->
                Growth.entries.forEachIndexed { col, g ->
                    translateAt(col * cell.toFloat(), row * cell.toFloat(), cell) { drawPousse(Mood.HAPPY, theme, Color(0xFFFFC857), growth = g) }
                }
                row++
            }
            // Humeurs en classique, arbre et jeune pousse.
            moods.drop(1).forEach { mood ->
                listOf(Growth.SPROUT, Growth.TREE, Growth.LEAF, Growth.SHRUB).forEachIndexed { col, g ->
                    translateAt(col * cell.toFloat(), row * cell.toFloat(), cell) { drawPousse(mood, AppTheme.CLASSIC, Color(0xFFFFC857), growth = g) }
                }
                row++
            }
        }
        // Aperçu pour la relecture visuelle : écrit seulement si on le demande (variable POUSSE_OUT=chemin.png).
        (System.getenv("POUSSE_OUT") ?: System.getProperty("pousse.out"))?.let { path ->
            File(path).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.translateAt(
        x: Float, y: Float, cell: Int, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    ) {
        withTransform({ translate(x, y); scale(cell / 120f, cell / 120f, pivot = Offset.Zero) }) { block() }
    }
}
