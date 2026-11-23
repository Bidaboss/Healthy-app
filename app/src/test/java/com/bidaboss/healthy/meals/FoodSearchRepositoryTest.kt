package com.bidaboss.healthy.meals

import com.bidaboss.healthy.meals.data.FoodSearchFailure
import com.bidaboss.healthy.meals.data.FoodSearchRepository
import com.bidaboss.healthy.meals.data.FoodSearchResult
import com.bidaboss.healthy.core.openfoodfacts.OpenFoodFacts
import com.bidaboss.healthy.meals.data.HttpResponse
import com.bidaboss.healthy.meals.data.timeoutsFor
import com.bidaboss.healthy.meals.ui.technicalDetail
import com.bidaboss.healthy.meals.ui.userMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Chaque panne de la recherche doit être reconnue, pour afficher le bon message (bug du 11/11/2026). */
class FoodSearchRepositoryTest {

    private val body = """
        {"products":[{"code":"123","product_name_fr":"Yaourt nature","brands":"Marque",
        "nutriments":{"energy-kcal_100g":60,"proteins_100g":4}}]}
    """.trimIndent()

    private suspend fun searchWith(fetch: (String) -> HttpResponse) = FoodSearchRepository(fetch).search("yaourt")

    private suspend fun failureWith(fetch: (String) -> HttpResponse): FoodSearchFailure =
        (searchWith(fetch) as FoodSearchResult.Failure).reason

    @Test
    fun `une reponse correcte donne les produits`() = runTest {
        val asked = mutableListOf<String>()
        val result = searchWith { url -> asked += url; HttpResponse(200, body) }
        assertEquals("Yaourt nature", (result as FoodSearchResult.Success).products.single().name)
        assertEquals(1, asked.size)
        assertTrue(asked.single().startsWith("https://search.openfoodfacts.org/"))
    }

    @Test
    fun `si le premier serveur est sature le second est essaye`() = runTest {
        // Cas réel du 11/11/2026 : un des deux serveurs répond « 503 indisponible ».
        val asked = mutableListOf<String>()
        val result = searchWith { url ->
            asked += url
            if (asked.size == 1) HttpResponse(503, "") else HttpResponse(200, body)
        }
        assertEquals("Yaourt nature", (result as FoodSearchResult.Success).products.single().name)
        assertEquals(2, asked.size)
        assertTrue(asked[1].startsWith("https://world.openfoodfacts.org/"))
    }

    @Test
    fun `le premier serveur a un delai court, le serveur de secours un delai long`() {
        val (fast, fallback) = OpenFoodFacts.searchUrls("yaourt")
        assertEquals(8_000, timeoutsFor(fast).readMs)
        assertEquals(8_000, timeoutsFor(fast).connectMs)
        assertEquals(30_000, timeoutsFor(fallback).readMs)
        assertEquals(15_000, timeoutsFor(fallback).connectMs)
    }

    @Test
    fun `sans internet on ne tente pas le second serveur`() = runTest {
        var calls = 0
        val failure = failureWith { calls++; throw UnknownHostException("search.openfoodfacts.org") }
        assertEquals(FoodSearchFailure.NoConnection, failure)
        assertEquals(1, calls)
    }

    @Test
    fun `un delai depasse est distingue de la connexion absente de connexion`() = runTest {
        val failure = failureWith { throw SocketTimeoutException("timeout") }
        assertEquals(FoodSearchFailure.Timeout, failure)
        assertEquals("Détail : délai dépassé", failure.technicalDetail())
        assertFalse(failure.userMessage().contains("connexion"))
    }

    @Test
    fun `un serveur injoignable demande de verifier la connexion`() = runTest {
        assertEquals(FoodSearchFailure.NoConnection, failureWith { throw UnknownHostException("world.openfoodfacts.org") })
        val failure = failureWith { throw ConnectException("refused") }
        assertEquals(FoodSearchFailure.NoConnection, failure)
        assertTrue(failure.userMessage().contains("connexion"))
    }

    @Test
    fun `un code http en erreur est rapporte`() = runTest {
        val failure = failureWith { HttpResponse(503, "") }
        assertEquals(FoodSearchFailure.Http(503), failure)
        assertEquals("Détail : réponse 503", failure.technicalDetail())
        assertFalse(failure.userMessage().contains("connexion"))
    }

    @Test
    fun `une reponse illisible est rapportee`() = runTest {
        assertEquals(FoodSearchFailure.Unreadable, failureWith { HttpResponse(200, "<html>Maintenance</html>") })
        assertEquals(FoodSearchFailure.Unreadable, failureWith { HttpResponse(200, """{"count":0}""") })
    }

    @Test
    fun `une autre erreur reseau garde son nom technique`() = runTest {
        val failure = failureWith { throw IOException("boom") }
        assertEquals(FoodSearchFailure.Other("IOException"), failure)
        assertEquals("Détail : IOException", failure.technicalDetail())
    }
}
