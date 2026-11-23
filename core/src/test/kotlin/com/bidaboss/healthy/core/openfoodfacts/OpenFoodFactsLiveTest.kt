package com.bidaboss.healthy.core.openfoodfacts

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Vraie recherche sur Open Food Facts. Ne tourne que si HEALTHY_OFF_LIVE=true
 * (workflow manuel « Open Food Facts en direct »), pour que la construction normale
 * ne dépende pas du réseau.
 */
class OpenFoodFactsLiveTest {

    @Test
    fun `une vraie recherche renvoie des produits lisibles`() {
        assumeTrue(System.getProperty("healthy.offLive") == "true", "Test réseau désactivé")

        // Comme l'app : on essaie les adresses dans l'ordre et on garde la première qui répond bien.
        val attempts = mutableListOf<String>()
        val products = OpenFoodFacts.searchUrls("yaourt nature").firstNotNullOfOrNull { url ->
            val host = URL(url).host
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "HealthyApp-Android/test")
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                if (code != 200) {
                    attempts += "$host : réponse $code"
                    null
                } else {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val parsed = OpenFoodFacts.parseSearchResponse(body)
                    attempts += "$host : ${parsed?.size ?: "illisible"} produits, début ${body.take(200)}"
                    parsed?.takeIf { it.isNotEmpty() }
                }
            } catch (e: java.io.IOException) {
                attempts += "$host : ${e::class.simpleName}"
                null
            }
        }
        println("OFF-DIAG essais : $attempts")
        assertNotNull(products, "Aucune adresse n'a donné de produits : $attempts")
        println("Open Food Facts : ${products.size} produits lus, ex. ${products.take(3)}")
    }

    /**
     * Diagnostic : temps de réponse et code HTTP de plusieurs recherches, sur l'adresse utilisée par
     * l'app et sur le nouveau moteur de recherche d'Open Food Facts. N'échoue jamais : il renseigne.
     */
    @Test
    fun `mesure des temps de reponse`() {
        assumeTrue(System.getProperty("healthy.offLive") == "true", "Test réseau désactivé")

        fun measure(label: String, url: String) {
            val start = System.nanoTime()
            val outcome = try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 60_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("User-Agent", "HealthyApp-Android/test")
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val parsed = OpenFoodFacts.parseSearchResponse(body)
                "HTTP $code, ${body.length} caractères, ${parsed ?: "illisible"} produits lus, début : " +
                    body.take(300).replace('\n', ' ')
            } catch (e: Exception) {
                "ERREUR ${e::class.simpleName} : ${e.message}"
            }
            println("OFF-DIAG $label : ${(System.nanoTime() - start) / 1_000_000} ms, $outcome")
        }

        listOf("riz", "poulet", "yaourt nature", "pain complet", "banane").forEach { query ->
            measure("search.pl « $query »", OpenFoodFacts.searchUrl(query))
        }
        val fields = "code,product_name,product_name_fr,brands,nutriments,serving_quantity"
        listOf("riz", "yaourt nature").forEach { query ->
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            measure(
                "search-a-licious « $query »",
                OpenFoodFacts.fastSearchUrl(query),
            )
            measure(
                "api v2 « $query »",
                "https://world.openfoodfacts.org/api/v2/search?search_terms=$q&page_size=20&lc=fr&cc=fr&fields=$fields",
            )
        }
    }
}
