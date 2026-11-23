package com.bidaboss.healthy.meals.data

import com.bidaboss.healthy.BuildConfig
import com.bidaboss.healthy.core.openfoodfacts.OffProduct
import com.bidaboss.healthy.core.openfoodfacts.OpenFoodFacts
import com.bidaboss.healthy.core.openfoodfacts.ProductLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

sealed interface FoodSearchResult {
    data class Success(val products: List<OffProduct>) : FoodSearchResult

    data class Failure(val reason: FoodSearchFailure) : FoodSearchResult
}

/** Résultat de la lecture de la fiche d'un produit par son code-barres. */
sealed interface ProductFetch {
    data class Success(val lookup: ProductLookup) : ProductFetch

    data class Failure(val reason: FoodSearchFailure) : ProductFetch
}

/** Pourquoi une recherche a échoué : sert à afficher un message juste et un détail utile au diagnostic. */
sealed interface FoodSearchFailure {
    /** Le téléphone n'arrive pas à joindre le serveur (pas d'internet, DNS, connexion refusée). */
    data object NoConnection : FoodSearchFailure

    /** Le serveur n'a pas répondu dans le délai. */
    data object Timeout : FoodSearchFailure

    /** Le serveur a répondu autre chose que 200 (503 quand il est surchargé, 429 si trop de demandes…). */
    data class Http(val code: Int) : FoodSearchFailure

    /** Réponse 200 mais contenu illisible. */
    data object Unreadable : FoodSearchFailure

    /** Autre erreur réseau ; [kind] est le nom technique de l'erreur. */
    data class Other(val kind: String) : FoodSearchFailure
}

/** Réponse HTTP brute : code et contenu (vide si le code n'est pas 200). */
data class HttpResponse(val code: Int, val body: String)

/**
 * Recherche d'aliments dans Open Food Facts : le seul accès réseau de l'app.
 * Seul le texte recherché est envoyé, aucune donnée personnelle.
 * [fetch] est remplaçable dans les tests.
 */
class FoodSearchRepository(private val fetch: (url: String) -> HttpResponse = ::httpGet) {

    /**
     * Essaie les adresses d'Open Food Facts dans l'ordre et garde la première bonne réponse.
     * Si toutes échouent, rapporte la panne de la dernière. Un serveur injoignable arrête tout de suite :
     * sans internet, inutile d'attendre deux fois.
     */
    suspend fun search(query: String): FoodSearchResult = withContext(Dispatchers.IO) {
        var last: FoodSearchResult = FoodSearchResult.Failure(FoodSearchFailure.Unreadable)
        for (url in OpenFoodFacts.searchUrls(query)) {
            last = searchOne(url)
            if (last is FoodSearchResult.Success) break
            if (last == FoodSearchResult.Failure(FoodSearchFailure.NoConnection)) break
        }
        last
    }

    /**
     * La fiche d'un produit par son code-barres : une seule requête, où seul le code est envoyé.
     * Un code inconnu est une réponse (404 ou statut 0), pas une panne.
     */
    suspend fun lookupProduct(code: String): ProductFetch = withContext(Dispatchers.IO) {
        try {
            val response = fetch(OpenFoodFacts.productUrl(code))
            when {
                response.code == HttpURLConnection.HTTP_NOT_FOUND -> ProductFetch.Success(ProductLookup.NotFound)
                response.code != HttpURLConnection.HTTP_OK -> ProductFetch.Failure(FoodSearchFailure.Http(response.code))
                else -> OpenFoodFacts.parseProductResponse(response.body, code)?.let { ProductFetch.Success(it) }
                    ?: ProductFetch.Failure(FoodSearchFailure.Unreadable)
            }
        } catch (_: SocketTimeoutException) {
            ProductFetch.Failure(FoodSearchFailure.Timeout)
        } catch (_: UnknownHostException) {
            ProductFetch.Failure(FoodSearchFailure.NoConnection)
        } catch (_: ConnectException) {
            ProductFetch.Failure(FoodSearchFailure.NoConnection)
        } catch (e: IOException) {
            ProductFetch.Failure(FoodSearchFailure.Other(e.javaClass.simpleName))
        }
    }

    private fun searchOne(url: String): FoodSearchResult = try {
        val response = fetch(url)
        if (response.code != HttpURLConnection.HTTP_OK) {
            FoodSearchResult.Failure(FoodSearchFailure.Http(response.code))
        } else {
            OpenFoodFacts.parseSearchResponse(response.body)?.let { FoodSearchResult.Success(it) }
                ?: FoodSearchResult.Failure(FoodSearchFailure.Unreadable)
        }
    } catch (_: SocketTimeoutException) {
        FoodSearchResult.Failure(FoodSearchFailure.Timeout)
    } catch (_: UnknownHostException) {
        FoodSearchResult.Failure(FoodSearchFailure.NoConnection)
    } catch (_: ConnectException) {
        FoodSearchResult.Failure(FoodSearchFailure.NoConnection)
    } catch (e: IOException) {
        FoodSearchResult.Failure(FoodSearchFailure.Other(e.javaClass.simpleName))
    }
}

/** Délais d'une requête, en millisecondes. */
data class Timeouts(val connectMs: Int, val readMs: Int)

/**
 * Le nouveau moteur (search.openfoodfacts.org) répond en moins d'une seconde quand il va bien :
 * s'il traîne, mieux vaut passer vite à l'ancienne recherche, qui peut mettre plus de 10 secondes
 * à répondre et garde donc un délai long. Pire attente : 8 + 8 s puis 15 + 30 s, au lieu d'environ 90 s.
 */
fun timeoutsFor(url: String): Timeouts =
    if (url.startsWith("https://${OpenFoodFacts.SEARCH_HOST}/")) Timeouts(connectMs = 8_000, readMs = 8_000)
    else Timeouts(connectMs = 15_000, readMs = 30_000)

private fun httpGet(url: String): HttpResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        val timeouts = timeoutsFor(url)
        connection.connectTimeout = timeouts.connectMs
        connection.readTimeout = timeouts.readMs
        connection.setRequestProperty("User-Agent", "HealthyApp-Android/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept", "application/json")
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) return HttpResponse(code, "")
        return HttpResponse(code, connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    } finally {
        connection.disconnect()
    }
}
