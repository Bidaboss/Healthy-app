package com.bidaboss.healthy.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.HealthyApplication
import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.BodyProfile
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.core.body.WeighIn
import com.bidaboss.healthy.core.importformat.ImportResult
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.importexport.ui.ImportViewModel
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.theme.HealthyTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Aperçus d'écrans pour la relecture du design : les 5 onglets dans les 3 thèmes, avec un profil fictif.
 * Ne vérifie que « rien ne plante » ; les images ne sont écrites que si on donne un dossier
 * (variable SCREENSHOTS_OUT ou propriété screenshots.out), voir docs/architecture.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HealthyApplication::class, qualifiers = "w390dp-h1500dp-xhdpi")
open class ScreenshotsTest {
    /** Ajouté au nom des images : vide en clair, « -sombre » pour [ScreenshotsDarkTest]. */
    protected open val suffix = ""

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun classique() = shoot(AppTheme.CLASSIC)
    @Test fun pirate() = shoot(AppTheme.PIRATE)
    @Test fun voyage() = shoot(AppTheme.TRAVEL)

    private fun shoot(theme: AppTheme) {
        val app = ApplicationProvider.getApplicationContext<HealthyApplication>()
        val container = app.container
        seed(container)
        val importViewModel = ImportViewModel(container.importParser, container.importRepository)
        compose.setContent {
            HealthyTheme(theme) { HealthyApp(importViewModel, importRequests = 0, onPickFile = {}) }
        }
        val out = (System.getenv("SCREENSHOTS_OUT") ?: System.getProperty("screenshots.out"))?.let { File(it).apply { mkdirs() } }
        listOf("Accueil" to "aujourdhui", "Repas" to "repas", "Sport" to "sport", "Santé" to "sante", "Moi" to "moi").forEach { (label, file) ->
            val tab = hasText(label) and hasClickAction()
            // Le conteneur lit d'abord la base : on attend que la barre d'onglets apparaisse.
            compose.waitUntil(30_000) { compose.onAllNodes(tab).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(tab).onFirst().performClick()
            settle()
            if (out != null) {
                // captureToImage attend un redessin que Robolectric ne donne pas : on dessine la fenêtre à la main.
                val view = compose.activity.window.decorView
                val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bmp))
                File(out, "${theme.name.lowercase()}-$file$suffix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun settle() {
        repeat(6) {
            Thread.sleep(250)
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
        }
    }

    private fun seed(c: com.bidaboss.healthy.AppContainer) = runBlocking {
        val today = LocalDate.now()
        // Le lundi, le bilan de la semaine s'ouvre tout seul : on le marque comme déjà vu.
        com.bidaboss.healthy.core.progress.WeeklyRecaps.recapWeek(java.time.LocalDateTime.now())?.let { c.weeklyRecapStore.lastShownWeek = it }
        // Profil fictif : jamais de donnée réelle dans le dépôt.
        c.bodyRepository.saveProfileWithFirstWeighIn(
            BodyProfile(Sex.MALE, LocalDate.of(1988, 7, 15), 176.0, 75.0, ActivityLevel.LIGHT, firstName = "Alex"),
            today.minusDays(40),
        )
        listOf(34 to 75.0, 27 to 74.6, 20 to 74.5, 13 to 74.1, 6 to 73.9, 0 to 73.6).forEach { (ago, kg) ->
            c.bodyRepository.saveWeighIn(WeighIn(today.minusDays(ago.toLong()), kg))
        }
        for (ago in 6 downTo 1) {
            c.mealRepository.saveMeal(MealDraft(null, MealType.BREAKFAST, today.minusDays(ago.toLong()).atTime(8, 0), null, null,
                listOf(Food("Porridge d'avoine", kcal = 380.0, proteinG = 14.0, carbsG = 58.0, fatG = 9.0, fiberG = 8.0))))
            c.mealRepository.saveMeal(MealDraft(null, MealType.LUNCH, today.minusDays(ago.toLong()).atTime(12, 30), null, null,
                listOf(Food("Poulet et riz", kcal = 650.0 + ago * 40, proteinG = 45.0, carbsG = 70.0, fatG = 14.0, fiberG = 5.0))))
            c.bodyRepository.addWater(today.minusDays(ago.toLong()), Hydration.GLASS_ML * (6 + ago % 3))
        }
        c.mealRepository.saveMeal(MealDraft(null, MealType.BREAKFAST, today.atTime(8, 0), "Petit-déjeuner", null,
            listOf(Food("Café au lait", quantityG = 250.0, kcal = 90.0, proteinG = 6.0, carbsG = 9.0, fatG = 3.0),
                Food("Porridge d'avoine", quantityG = 200.0, kcal = 380.0, proteinG = 14.0, carbsG = 58.0, fatG = 9.0, fiberG = 8.0))))
        c.mealRepository.saveMeal(MealDraft(null, MealType.LUNCH, today.atTime(12, 30), null, null,
            listOf(Food("Salade, poulet et quinoa", kcal = 620.0, proteinG = 42.0, carbsG = 55.0, fatG = 22.0, fiberG = 9.0))))
        repeat(4) { c.bodyRepository.addWater(today, Hydration.GLASS_ML) }
        val plan = File("../docs/examples/plan-sport-maison.json").readText()
        (c.importParser.parse(plan) as? ImportResult.Success)?.let { c.importRepository.save(it.document) }
        c.workoutLogRepository.addActivity("Marche", 30, today.minusDays(1), nowEpochMs = 1)
    }
}
