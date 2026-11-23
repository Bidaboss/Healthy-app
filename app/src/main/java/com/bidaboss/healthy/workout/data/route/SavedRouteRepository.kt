package com.bidaboss.healthy.workout.data.route

import com.bidaboss.healthy.BuildConfig
import com.bidaboss.healthy.core.route.GeoPoint
import com.bidaboss.healthy.core.route.RouteAddresses
import com.bidaboss.healthy.core.route.RoutePlanning
import com.bidaboss.healthy.core.route.UnreadableResponse
import com.bidaboss.healthy.data.TransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Un trajet enregistré, tel que l'interface l'affiche. */
data class SavedRoute(
    val id: Long,
    val name: String,
    val addresses: RouteAddresses,
    /** Distance d'un sens, ou null tant qu'elle n'est ni calculée ni saisie. */
    val distanceKm: Double?,
    val distanceCalculated: Boolean,
)

/** Pourquoi le calcul de distance a échoué. Jamais d'adresse ni de coordonnée ici : seulement une position. */
sealed interface RouteFailure {
    /** L'adresse numéro [index] (0 = départ, dernier = arrivée) est introuvable. */
    data class AddressNotFound(val index: Int, val count: Int) : RouteFailure

    /** Les points n'ont pas de chemin à vélo entre eux. */
    data object NoRoute : RouteFailure

    data object NoConnection : RouteFailure

    data object Timeout : RouteFailure

    data class Http(val code: Int) : RouteFailure

    data object Unreadable : RouteFailure

    data class Other(val kind: String) : RouteFailure
}

sealed interface DistanceResult {
    data class Success(val km: Double) : DistanceResult

    data class Failure(val reason: RouteFailure) : DistanceResult
}

/** Réponse HTTP brute : code et contenu (vide si le code n'est pas 200). */
data class RouteHttpResponse(val code: Int, val body: String)

/**
 * Trajets enregistrés et calcul de leur distance (exception réseau, docs/architecture.md « Trajets : calcul de distance »).
 * Le réseau n'est utilisé que par [calculateDistance], appelé quand l'utilisateur appuie sur « Calculer la distance » :
 * jamais au changement d'adresse ni en tâche de fond. [fetch] et [pause] sont remplaçables dans les tests.
 */
class SavedRouteRepository(
    private val dao: SavedRouteDao,
    private val transactions: TransactionRunner,
    private val fetch: (url: String) -> RouteHttpResponse = ::httpGet,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) },
) {
    fun observeAll(): Flow<List<SavedRoute>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: Long): SavedRoute? = dao.get(id)?.toModel()

    /**
     * Enregistre un trajet (nouveau si [id] est null). La distance suit les adresses : si elles changent, elle est
     * effacée et doit être recalculée ; sinon on garde celle déjà connue. [manualKm] impose une distance saisie.
     * Rend l'identifiant, ou null si le nom, le départ ou l'arrivée manque, ou s'il y a trop d'étapes.
     */
    suspend fun save(
        id: Long?,
        name: String,
        start: String,
        stops: List<String>,
        end: String,
        calculatedKm: Double?,
        manualKm: Double?,
        nowEpochMs: Long,
    ): Long? {
        val cleanName = name.replace(Regex("\\s+"), " ").trim().take(MAX_NAME_LENGTH)
        val addresses = RoutePlanning.clean(start, stops, end)
        if (cleanName.isEmpty() || addresses == null) return null
        val (km, source) = when {
            manualKm != null -> manualKm to SOURCE_MANUAL
            calculatedKm != null -> calculatedKm to SOURCE_CALCULATED
            else -> null to null
        }
        return transactions.inTransaction {
            val entity = SavedRouteEntity(
                id = id ?: 0,
                name = cleanName,
                start = addresses.start,
                stops = addresses.stops.joinToString("\n"),
                end = addresses.end,
                distanceKm = km,
                distanceSource = source,
                createdAtEpochMs = id?.let { dao.get(it)?.createdAtEpochMs } ?: nowEpochMs,
            )
            if (id == null) dao.insert(entity) else {
                dao.update(entity)
                id
            }
        }
    }

    suspend fun delete(id: Long) = transactions.inTransaction {
        dao.unlinkActivities(id)
        dao.delete(id)
    }

    /**
     * Calcule la distance d'un sens à vélo : une recherche par adresse (une par seconde au plus), puis un seul calcul.
     * Rien n'est enregistré ici : l'appelant garde le résultat avec le trajet.
     */
    suspend fun calculateDistance(addresses: RouteAddresses): DistanceResult = withContext(Dispatchers.IO) {
        try {
            val all = addresses.all
            val points = ArrayList<GeoPoint>(all.size)
            all.forEachIndexed { index, address ->
                if (index > 0) pause(GEOCODER_PAUSE_MS)
                val response = fetch(RoutePlanning.geocodeUrl(address))
                if (response.code != HttpURLConnection.HTTP_OK) return@withContext failure(RouteFailure.Http(response.code))
                val point = RoutePlanning.parseGeocode(response.body)
                    ?: return@withContext failure(RouteFailure.AddressNotFound(index, all.size))
                points += point
            }
            val response = fetch(RoutePlanning.routeUrl(points))
            when {
                response.code == HttpURLConnection.HTTP_BAD_REQUEST -> failure(RouteFailure.NoRoute)
                response.code != HttpURLConnection.HTTP_OK -> failure(RouteFailure.Http(response.code))
                else -> RoutePlanning.parseDistanceKm(response.body)
                    ?.let { DistanceResult.Success(it) } ?: failure(RouteFailure.NoRoute)
            }
        } catch (_: UnreadableResponse) {
            failure(RouteFailure.Unreadable)
        } catch (_: SocketTimeoutException) {
            failure(RouteFailure.Timeout)
        } catch (_: UnknownHostException) {
            failure(RouteFailure.NoConnection)
        } catch (_: ConnectException) {
            failure(RouteFailure.NoConnection)
        } catch (e: IOException) {
            failure(RouteFailure.Other(e.javaClass.simpleName))
        }
    }

    private fun failure(reason: RouteFailure) = DistanceResult.Failure(reason)

    private fun SavedRouteEntity.toModel() = SavedRoute(
        id = id,
        name = name,
        addresses = RouteAddresses(start, stops.split('\n').filter { it.isNotBlank() }, end),
        distanceKm = distanceKm,
        distanceCalculated = distanceSource == SOURCE_CALCULATED,
    )

    companion object {
        const val SOURCE_CALCULATED = "calcule"
        const val SOURCE_MANUAL = "saisi"
        const val MAX_NAME_LENGTH = 60

        /** Nominatim accepte au plus une requête par seconde. */
        const val GEOCODER_PAUSE_MS = 1_100L
    }
}

private fun httpGet(url: String): RouteHttpResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        // Nominatim exige un agent qui identifie l'app : nom et version, jamais de contact ni d'identifiant.
        connection.setRequestProperty("User-Agent", "Healthy-app/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept", "application/json")
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) return RouteHttpResponse(code, "")
        return RouteHttpResponse(code, connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    } finally {
        connection.disconnect()
    }
}
