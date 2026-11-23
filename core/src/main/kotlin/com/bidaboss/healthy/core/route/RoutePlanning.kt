package com.bidaboss.healthy.core.route

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/** Un point GPS. */
data class GeoPoint(val lat: Double, val lon: Double)

/** Sens d'un trajet enregistré, au moment de l'ajouter au journal. */
enum class RouteDirection(val code: String, val label: String) {
    OUTBOUND("aller", "Aller"),
    RETURN("retour", "Retour"),
    ROUND_TRIP("aller_retour", "Aller-retour"),
    ;

    /** Distance parcourue pour une distance d'un sens, arrondie à 0,1 km. */
    fun distanceKm(oneWayKm: Double): Double = RoutePlanning.round1(if (this == ROUND_TRIP) oneWayKm * 2 else oneWayKm)

    companion object {
        fun fromCode(code: String?): RouteDirection? = entries.firstOrNull { it.code == code }
    }
}

/** Adresses d'un trajet : départ, étapes (0 à [RoutePlanning.MAX_STOPS]) et arrivée. */
data class RouteAddresses(val start: String, val stops: List<String>, val end: String) {
    /** Les points dans l'ordre du parcours : départ, étapes, arrivée. */
    val all: List<String> get() = listOf(start) + stops + end
}

/**
 * Calcul de la distance d'un trajet par adresses (docs/architecture.md, « Trajets : calcul de distance »).
 * Ici, seulement le texte : adresses nettoyées, adresses web à appeler, lecture des réponses. Le réseau est dans `:app`.
 * Jamais d'adresse ni de coordonnée dans les messages d'erreur : ce qui sort d'ici ne contient que des positions.
 */
object RoutePlanning {
    /** Service qui transforme une adresse en point GPS (OpenStreetMap, gratuit, sans clé). */
    const val GEOCODER_HOST = "nominatim.openstreetmap.org"

    /** Service qui calcule la distance à vélo entre des points (OSRM, hébergé par OpenStreetMap, sans clé). */
    const val ROUTING_HOST = "routing.openstreetmap.de"

    /** Pas plus d'étapes : on ménage un service gratuit (8 étapes = 10 adresses = 10 requêtes d'une seconde). */
    const val MAX_STOPS = 8

    const val MAX_ADDRESS_LENGTH = 200

    /** Vitesse moyenne proposée à vélo pour estimer la durée d'un trajet. */
    const val BIKE_SPEED_KMH = 15.0

    private val json = Json { ignoreUnknownKeys = true }

    /** Adresse sans espaces en trop ni retour à la ligne, coupée à [MAX_ADDRESS_LENGTH] ; vide si elle ne contient rien. */
    fun cleanAddress(raw: String): String =
        raw.replace(Regex("\\s+"), " ").trim().take(MAX_ADDRESS_LENGTH).trim()

    /** Adresses nettoyées, étapes vides retirées. Null si le départ ou l'arrivée manque. */
    fun clean(start: String, stops: List<String>, end: String): RouteAddresses? {
        val s = cleanAddress(start)
        val e = cleanAddress(end)
        if (s.isEmpty() || e.isEmpty()) return null
        val steps = stops.map(::cleanAddress).filter { it.isNotEmpty() }
        if (steps.size > MAX_STOPS) return null
        return RouteAddresses(s, steps, e)
    }

    /** Adresse de recherche d'un point : une seule réponse demandée, rien d'autre que le texte de l'adresse. */
    fun geocodeUrl(address: String): String {
        val q = URLEncoder.encode(cleanAddress(address), "UTF-8")
        return "https://$GEOCODER_HOST/search?q=$q&format=jsonv2&limit=1&addressdetails=0"
    }

    /** Lit la réponse de recherche : le premier point, null si l'adresse est introuvable. Lève [UnreadableResponse] si illisible. */
    fun parseGeocode(body: String): GeoPoint? {
        val array = try {
            json.parseToJsonElement(body) as? JsonArray
        } catch (_: SerializationException) {
            null
        } ?: throw UnreadableResponse()
        val first = array.firstOrNull() as? JsonObject ?: return null
        val lat = (first["lat"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
        val lon = (first["lon"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) throw UnreadableResponse()
        return GeoPoint(lat, lon)
    }

    /** Adresse de calcul à vélo entre des points (longitude puis latitude, comme l'attend le service). */
    fun routeUrl(points: List<GeoPoint>): String {
        require(points.size >= 2) { "Il faut au moins deux points" }
        val coordinates = points.joinToString(";") { "${coordinate(it.lon)},${coordinate(it.lat)}" }
        return "https://$ROUTING_HOST/routed-bike/route/v1/driving/$coordinates?overview=false&alternatives=false&steps=false"
    }

    /** Lit la distance en km (arrondie à 0,1) de la réponse du calcul. Null si aucun chemin n'existe ; lève [UnreadableResponse] si illisible. */
    fun parseDistanceKm(body: String): Double? {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: throw UnreadableResponse()
        val code = (root["code"] as? JsonPrimitive)?.contentOrNull
        if (code == "NoRoute") return null
        if (code != "Ok") throw UnreadableResponse()
        val meters = ((root["routes"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("distance")?.let { it as? JsonPrimitive }?.doubleOrNull
        if (meters == null || meters < 0) throw UnreadableResponse()
        return round1(meters / 1000.0)
    }

    /** Durée estimée en minutes (au moins 1) à [speedKmh]. */
    fun estimateMinutes(distanceKm: Double, speedKmh: Double = BIKE_SPEED_KMH): Int =
        (distanceKm / speedKmh * 60).roundToInt().coerceAtLeast(1)

    /** « 12,4 km » : virgule française, une décimale seulement si besoin. */
    fun formatKm(km: Double): String {
        val rounded = round1(km)
        return if (rounded == rounded.toInt().toDouble()) "${rounded.toInt()} km" else String.format(Locale.FRANCE, "%.1f km", rounded)
    }

    /** Lit une distance saisie à la main (« 12,5 », « 12.5 »). Null si ce n'est pas un nombre entre 0,1 et 500 km. */
    fun parseManualKm(text: String): Double? {
        val km = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return round1(km).takeIf { it in 0.1..MAX_KM }
    }

    const val MAX_KM = 500.0

    private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

    internal fun round1(value: Double): Double = Math.round(value * 10) / 10.0
}

/** Réponse d'un service impossible à lire. Sans contenu : on n'y met jamais d'adresse. */
class UnreadableResponse : RuntimeException()
