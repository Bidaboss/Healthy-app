package com.bidaboss.healthy.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.HealthyApplication
import com.bidaboss.healthy.core.reference.RefDay
import com.bidaboss.healthy.core.reference.ReferenceProfile
import com.bidaboss.healthy.core.reference.ReferenceReports
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.today.ui.ReferenceRow
import com.bidaboss.healthy.ui.theme.HealthyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Aperçu de l'écran « Mes repères » avec des valeurs fictives. N'écrit le PNG que si SCREENSHOTS_OUT est donné. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HealthyApplication::class, qualifiers = "w390dp-h1900dp-xhdpi")
class ReferencesScreenshotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val report = ReferenceReports.build(
        List(28) { i ->
            RefDay(
                kcal = 2680, proteinG = 97.5, fiberG = 21.0, carbsG = 330.0, fatG = 85.0, waterMl = 2100,
                sportMinutes = if (i % 4 == 0) 80 else 0, sleepMin = 410, steps = 7800,
                waterGoalMl = 2200, maintenanceKcal = 2710,
            )
        },
        weightKg = 75.0, profile = ReferenceProfile(male = true, ageYears = 38),
    )

    @Test fun classique() = shoot(AppTheme.CLASSIC)
    @Test fun pirate() = shoot(AppTheme.PIRATE)

    private fun shoot(theme: AppTheme) {
        compose.setContent {
            HealthyTheme(theme) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            report.lines.forEach { com.bidaboss.healthy.today.ui.ReferenceRow(it, detailed = true) }
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            report.lines.take(3).forEach { ReferenceRow(it, detailed = false) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        (System.getenv("SCREENSHOTS_OUT") ?: System.getProperty("screenshots.out"))?.let { dir ->
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File(dir).apply { mkdirs() }.let { File(it, "reperes-${theme.name.lowercase()}.png").outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 100, o) } }
        }
    }
}
