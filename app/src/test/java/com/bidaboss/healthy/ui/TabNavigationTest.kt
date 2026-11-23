package com.bidaboss.healthy.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La barre d'onglets doit toujours ramener sur l'onglet touché, d'où qu'on vienne. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TabNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    private fun start() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = Routes.TODAY) {
                for (route in listOf(Routes.TODAY, Routes.MEALS, Routes.WORKOUT, Routes.HEALTH, Routes.ME, Routes.WATCH, Routes.WATER)) {
                    composable(route) { Text(route) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tab(route: String) {
        compose.runOnUiThread { nav.navigateToTab(route) }
        compose.waitForIdle()
    }

    private fun open(route: String) {
        compose.runOnUiThread { nav.navigate(route) { launchSingleTop = true } }
        compose.waitForIdle()
    }

    private fun current() = nav.currentBackStackEntry?.destination?.route

    @Test
    fun `de Sante on revient a Accueil`() {
        start()
        tab(Routes.HEALTH)
        assertEquals(Routes.HEALTH, current())
        tab(Routes.TODAY)
        assertEquals(Routes.TODAY, current())
    }

    @Test
    fun `de Ma montre ouverte depuis Accueil on revient a Accueil`() {
        start()
        open(Routes.WATCH)
        assertEquals(Routes.WATCH, current())
        tab(Routes.TODAY)
        assertEquals(Routes.TODAY, current())
    }

    @Test
    fun `apres un tour des onglets on revient a Accueil depuis chacun`() {
        start()
        for (route in listOf(Routes.MEALS, Routes.WORKOUT, Routes.HEALTH, Routes.ME, Routes.HEALTH)) {
            tab(route)
            assertEquals(route, current())
            tab(Routes.TODAY)
            assertEquals(Routes.TODAY, current())
        }
        tab(Routes.HEALTH)
        tab(Routes.MEALS)
        tab(Routes.HEALTH)
        tab(Routes.TODAY)
        assertEquals(Routes.TODAY, current())
    }
}
