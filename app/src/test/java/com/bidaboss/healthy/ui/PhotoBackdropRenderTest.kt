package com.bidaboss.healthy.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.settings.data.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Dessine le fond photo (le vrai code de dessin) derrière des cartes factices : 3 thèmes × 4 onglets, en haut
 * de page puis défilé de 400 dp. Une planche PNG n'est écrite que si on la demande (variable BACKDROP_OUT).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class PhotoBackdropRenderTest {
    @Test
    fun `dessine les 12 decors en haut et defiles`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val density = 2.6f
        val w = (411 * density).toInt()
        val h = (860 * density).toInt()
        val cols = PhotoTab.entries.size * 2
        val gap = 20
        val themes = AppTheme.entries
        val sheet = Bitmap.createBitmap(cols * (w / 3 + gap), themes.size * (h / 3 + gap), Bitmap.Config.ARGB_8888)
        sheet.eraseColor(android.graphics.Color.WHITE)
        themes.forEachIndexed { row, theme ->
            PhotoTab.entries.forEachIndexed { tabIndex, tab ->
                val photo = BitmapFactory.decodeResource(context.resources, PhotoBackgrounds.drawableOf(tab, theme)).asImageBitmap()
                assertEquals(960, photo.width)
                listOf(0, 400).forEachIndexed { k, scrollDp ->
                    val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val bg = Color(0xFFF6FBF4)
                    CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(frame.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
                        drawRect(bg)
                        drawPhotoBackdrop(photo, PhotoBackgrounds.scrimOf(theme), bg, 1040 * density, (scrollDp * density).toInt())
                        // Cartes factices : la première commence vers 170 dp, comme dans le prototype.
                        translate(top = -scrollDp * density) {
                            for (i in 0 until 6) {
                                drawRoundRect(
                                    Color.White,
                                    topLeft = Offset(16 * density, (170 + i * 190) * density),
                                    size = Size(w - 32 * density, 170 * density),
                                    cornerRadius = CornerRadius(28 * density),
                                )
                            }
                        }
                    }
                    val small = Bitmap.createScaledBitmap(frame, w / 3, h / 3, true)
                    val x = (tabIndex * 2 + k) * (w / 3 + gap)
                    val y = row * (h / 3 + gap)
                    android.graphics.Canvas(sheet).drawBitmap(small, x.toFloat(), y.toFloat(), null)
                }
            }
        }
        System.getenv("BACKDROP_OUT")?.let { path -> File(path).outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}
