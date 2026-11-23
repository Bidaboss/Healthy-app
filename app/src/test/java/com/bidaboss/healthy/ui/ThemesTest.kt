package com.bidaboss.healthy.ui

import android.app.Application
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.settings.data.AppearanceStore
import com.bidaboss.healthy.ui.theme.ambianceOf
import com.bidaboss.healthy.ui.theme.colorSchemeOf
import com.bidaboss.healthy.ui.theme.macroColorsOf
import com.bidaboss.healthy.ui.theme.tonalColorsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Thèmes Classique, Pirate et Voyage : contrastes lisibles, réglage gardé, textes propres à chaque thème. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ThemesTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
        return light / dark
    }

    /** Texte posé sur fond : chaque paire doit atteindre 4,5:1 (WCAG AA, texte normal). */
    private fun ColorScheme.textPairs() = mapOf(
        "texte sur fond" to (onBackground to background),
        "texte sur carte" to (onSurface to surfaceContainerHighest),
        "texte secondaire sur carte" to (onSurfaceVariant to surfaceContainerHighest),
        "texte secondaire sur fond" to (onSurfaceVariant to background),
        "titre coloré sur fond" to (primary to background),
        "titre coloré sur carte" to (primary to surfaceContainerHighest),
        "bouton principal" to (onPrimary to primary),
        "bouton secondaire" to (onSecondary to secondary),
        "erreur sur carte" to (error to surfaceContainerHighest),
        "barre d'onglets" to (onSurface to surfaceContainer),
        "onglet actif" to (onSecondaryContainer to secondaryContainer),
    )

    @Test
    fun `tous les textes des trois themes sont lisibles en clair et en sombre`() {
        val failures = AppTheme.entries.flatMap { theme ->
            listOf(false, true).flatMap { dark ->
                colorSchemeOf(theme, dark).textPairs().mapNotNull { (name, pair) ->
                    val ratio = contrast(pair.first, pair.second)
                    if (ratio < 4.5) "$theme ${if (dark) "sombre" else "clair"} : $name = %.2f".format(ratio) else null
                }
            }
        }
        assertTrue("Contrastes sous 4,5:1 : $failures", failures.isEmpty())
    }

    @Test
    fun `en-tete et bouton d'accent lisibles dans chaque theme`() {
        AppTheme.entries.forEach { theme ->
            val a = ambianceOf(theme)
            assertTrue("$theme : texte de l'en-tête", contrast(a.onHeader, a.header) >= 4.5)
            assertTrue("$theme : bouton d'accent", contrast(a.onAccent, a.accent) >= 4.5)
        }
    }

    @Test
    fun `les barres de macros se voient sur les cartes de chaque theme`() {
        val failures = AppTheme.entries.flatMap { theme ->
            listOf(false, true).flatMap { dark ->
                val card = colorSchemeOf(theme, dark).surfaceContainerHighest
                val macros = macroColorsOf(theme, dark)
                mapOf("protéines" to macros.protein, "glucides" to macros.carbs, "lipides" to macros.fat, "fibres" to macros.fiber)
                    .mapNotNull { (name, color) ->
                        val ratio = contrast(color, card)
                        if (ratio < 3.0) "$theme ${if (dark) "sombre" else "clair"} : $name = %.2f".format(ratio) else null
                    }
            }
        }
        assertTrue("Barres sous 3:1 : $failures", failures.isEmpty())
    }

    @Test
    fun `le theme choisi est garde apres redemarrage, Classique par defaut`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(AppTheme.CLASSIC, AppearanceStore(context).theme.value)

        AppearanceStore(context).setTheme(AppTheme.TRAVEL)

        assertEquals(AppTheme.TRAVEL, AppearanceStore(context).theme.value)
    }

    @Test
    fun `seuls les titres et messages changent avec le theme`() {
        val classic = ThemeTexts.of(AppTheme.CLASSIC)
        assertEquals("Salut Alex !", classic.greeting("Alex"))
        assertEquals("Bravo !", classic.celebrationTitle)
        assertEquals(null, classic.onTarget)

        val pirate = ThemeTexts.of(AppTheme.PIRATE)
        assertEquals("Journal de bord du jour", pirate.dayTitle)
        assertEquals("Cap tenu !", pirate.onTarget)
        assertEquals("Séance terminée, moussaillon !", pirate.celebrationTitle)

        val travel = ThemeTexts.of(AppTheme.TRAVEL)
        assertEquals("Carnet de voyage du jour", travel.dayTitle)
        assertEquals("3 étapes", "3 ${travel.streakUnit(3)}")
        assertTrue(travel.headerMessage(Mood.ENCOURAGING, 0).startsWith("Belle escale, on repart ?"))
    }

    @Test
    fun `un ancien theme retire revient a Classique`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        listOf("SPACE", "ARCADE", "MOUNTAIN", "n'importe quoi").forEach { old ->
            context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE).edit().putString("theme", old).commit()
            assertEquals(old, AppTheme.CLASSIC, AppearanceStore(context).theme.value)
        }
        // Les trois thèmes gardés sont relus tels quels.
        AppTheme.entries.forEach { kept ->
            context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE).edit().putString("theme", kept.name).commit()
            assertEquals(kept, AppearanceStore(context).theme.value)
        }
    }

    @Test
    fun `les trois themes ont un texte pour chaque ecran`() {
        AppTheme.entries.forEach { theme ->
            val texts = ThemeTexts.of(theme)
            listOf(texts.restDay, texts.noMeal, texts.noPlan, texts.badgesTitle, texts.celebrationText)
                .forEach { assertTrue("$theme", it.isNotBlank()) }
        }
        assertEquals(3, AppTheme.entries.size)
    }

    @Test
    fun `l'onglet actif se voit sur la barre dans chaque theme`() {
        AppTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val scheme = colorSchemeOf(theme, dark)
                val ratio = contrast(scheme.secondaryContainer, scheme.surfaceContainer)
                assertTrue("$theme ${if (dark) "sombre" else "clair"} : %.2f".format(ratio), ratio >= 3.0)
            }
        }
    }

    @Test
    fun `les boutons tonals sont lisibles dans chaque theme`() {
        val failures = AppTheme.entries.flatMap { theme ->
            listOf(false, true).mapNotNull { dark ->
                val c = tonalColorsOf(theme, dark)
                val ratio = contrast(c.content, c.container)
                if (ratio < 4.5) "$theme ${if (dark) "sombre" else "clair"} : %.2f".format(ratio) else null
            }
        }
        assertTrue("Boutons tonals sous 4,5:1 : $failures", failures.isEmpty())
    }
}
