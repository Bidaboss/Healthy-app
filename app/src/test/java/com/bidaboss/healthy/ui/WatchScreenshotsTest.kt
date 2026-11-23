package com.bidaboss.healthy.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.HealthyApplication
import com.bidaboss.healthy.core.watch.SleepSegment
import com.bidaboss.healthy.core.watch.SleepStage
import com.bidaboss.healthy.health.data.SleepDetail
import com.bidaboss.healthy.health.data.WatchDay
import com.bidaboss.healthy.health.data.WatchDayRecord
import com.bidaboss.healthy.health.data.WatchStatus
import com.bidaboss.healthy.health.ui.HealthUiState
import com.bidaboss.healthy.health.ui.SleepSection
import com.bidaboss.healthy.health.ui.WatchTab
import com.bidaboss.healthy.health.ui.WatchTrendsViewModel
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.today.ui.WatchCard
import com.bidaboss.healthy.today.ui.WatchUiState
import com.bidaboss.healthy.ui.theme.HealthyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Aperçus avec une montre fictive (jamais de vraie donnée) : carte « Ma montre » d'Aujourd'hui, puis les parties
 * Sommeil et Montre de Santé. Comme [ScreenshotsTest], n'écrit les PNG que si SCREENSHOTS_OUT est donné.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HealthyApplication::class, qualifiers = "w390dp-h2800dp-xhdpi")
class WatchScreenshotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()

    private fun record(ago: Int) = WatchDayRecord(
        date = today.minusDays(ago.toLong()),
        steps = 7000 + ago * 450,
        activeKcal = 420 + ago * 25,
        sleepMin = 400 + ago * 6,
        avgBpm = 62 + ago % 3,
        minBpm = 48,
        distanceM = 5200 + ago * 300,
        sleep = SleepDetail(
            start = today.minusDays(ago.toLong() + 1).atTime(23, 10),
            end = today.minusDays(ago.toLong()).atTime(6, 55),
            segments = listOf(
                SleepSegment(SleepStage.LIGHT, 150), SleepSegment(SleepStage.DEEP, 80), SleepSegment(SleepStage.REM, 95),
                SleepSegment(SleepStage.LIGHT, 100), SleepSegment(SleepStage.AWAKE, 12), SleepSegment(SleepStage.REM, 25),
            ),
            nightAvgBpm = 54,
            nightMinBpm = 47,
        ),
    )

    private val health = HealthUiState(
        status = WatchStatus.READY,
        watchWeek = (6 downTo 0).map { record(it) },
        hasHistory = true,
    )

    @Test fun classique() = shoot(AppTheme.CLASSIC)

    private fun shoot(theme: AppTheme) {
        val app = ApplicationProvider.getApplicationContext<HealthyApplication>()
        val trends = WatchTrendsViewModel(app.container.watchRepository, app.container.sessionSummary)
        compose.setContent {
            HealthyTheme(theme) {
                PhotoBackground(PhotoTab.TODAY, Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        WatchCard(
                            WatchUiState(true, today, WatchDay(steps = 8420, activeKcal = 2310, avgBpm = 64, minBpm = 48, distanceM = 6400), emptyList()),
                            onAdd = {}, onIgnore = {}, onOpen = {},
                        )
                        SleepSection(health, onConnect = {})
                        WatchTab(health, trends, onConnect = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(1500)
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        (System.getenv("SCREENSHOTS_OUT") ?: System.getProperty("screenshots.out"))?.let { dir ->
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File(dir).apply { mkdirs() }.let { File(it, "montre-${theme.name.lowercase()}.png").outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 100, o) } }
        }
    }
}
