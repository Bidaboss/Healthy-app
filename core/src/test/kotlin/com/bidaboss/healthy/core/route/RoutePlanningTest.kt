package com.bidaboss.healthy.core.route

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Adresses et coordonnées fictives : jamais de vrai lieu dans les tests. */
class RoutePlanningTest {

    @Test
    fun `une adresse est nettoyee et coupee`() {
        assertEquals("1 rue Exemple 00000 Ville", RoutePlanning.cleanAddress("  1   rue Exemple\n00000  Ville "))
        assertEquals(RoutePlanning.MAX_ADDRESS_LENGTH, RoutePlanning.cleanAddress("a".repeat(500)).length)
    }

    @Test
    fun `depart et arrivee sont obligatoires, les etapes vides sont retirees`() {
        val ok = RoutePlanning.clean("Rue A", listOf("", " Rue B ", "  "), "Rue C")
        assertEquals(RouteAddresses("Rue A", listOf("Rue B"), "Rue C"), ok)
        assertEquals(listOf("Rue A", "Rue B", "Rue C"), ok?.all)
        assertNull(RoutePlanning.clean("", emptyList(), "Rue C"))
        assertNull(RoutePlanning.clean("Rue A", emptyList(), "  "))
    }

    @Test
    fun `trop d etapes sont refusees`() {
        val stops = List(RoutePlanning.MAX_STOPS + 1) { "Rue $it" }
        assertNull(RoutePlanning.clean("Rue A", stops, "Rue C"))
        assertEquals(RoutePlanning.MAX_STOPS, RoutePlanning.clean("Rue A", stops.take(RoutePlanning.MAX_STOPS), "Rue C")?.stops?.size)
    }

    @Test
    fun `l adresse de recherche n envoie que le texte, en https`() {
        val url = RoutePlanning.geocodeUrl("1 rue Exemple, 00000 Ville")
        assertEquals(
            "https://nominatim.openstreetmap.org/search?q=1+rue+Exemple%2C+00000+Ville&format=jsonv2&limit=1&addressdetails=0",
            url,
        )
    }

    @Test
    fun `la reponse de recherche donne le premier point`() {
        val body = """[{"place_id":1,"lat":"10.123456","lon":"-20.654321","display_name":"x"}]"""
        assertEquals(GeoPoint(10.123456, -20.654321), RoutePlanning.parseGeocode(body))
    }

    @Test
    fun `une adresse introuvable donne null, une reponse illisible une erreur`() {
        assertNull(RoutePlanning.parseGeocode("[]"))
        assertFailsWith<UnreadableResponse> { RoutePlanning.parseGeocode("pas du json") }
        assertFailsWith<UnreadableResponse> { RoutePlanning.parseGeocode("""{"error":"x"}""") }
        assertFailsWith<UnreadableResponse> { RoutePlanning.parseGeocode("""[{"lat":"abc","lon":"1"}]""") }
        assertFailsWith<UnreadableResponse> { RoutePlanning.parseGeocode("""[{"lat":"95","lon":"1"}]""") }
    }

    @Test
    fun `l adresse de calcul met la longitude avant la latitude`() {
        val url = RoutePlanning.routeUrl(listOf(GeoPoint(10.5, 20.25), GeoPoint(11.0, 21.0)))
        assertEquals(
            "https://routing.openstreetmap.de/routed-bike/route/v1/driving/20.250000,10.500000;21.000000,11.000000" +
                "?overview=false&alternatives=false&steps=false",
            url,
        )
    }

    @Test
    fun `la distance est lue en km arrondie a 0,1`() {
        assertEquals(12.4, RoutePlanning.parseDistanceKm("""{"code":"Ok","routes":[{"distance":12383.7,"duration":2000}],"waypoints":[]}"""))
        assertNull(RoutePlanning.parseDistanceKm("""{"code":"NoRoute","message":"x"}"""))
        assertFailsWith<UnreadableResponse> { RoutePlanning.parseDistanceKm("""{"code":"InvalidQuery"}""") }
        assertFailsWith<UnreadableResponse> { RoutePlanning.parseDistanceKm("""{"code":"Ok","routes":[]}""") }
        assertFailsWith<UnreadableResponse> { RoutePlanning.parseDistanceKm("<html>") }
    }

    @Test
    fun `aller, retour et aller-retour`() {
        assertEquals(12.4, RouteDirection.OUTBOUND.distanceKm(12.4))
        assertEquals(12.4, RouteDirection.RETURN.distanceKm(12.4))
        assertEquals(24.8, RouteDirection.ROUND_TRIP.distanceKm(12.4))
        assertEquals(RouteDirection.ROUND_TRIP, RouteDirection.fromCode("aller_retour"))
        assertNull(RouteDirection.fromCode("autre"))
    }

    @Test
    fun `distance saisie a la main`() {
        assertEquals(12.5, RoutePlanning.parseManualKm("12,5"))
        assertEquals(12.5, RoutePlanning.parseManualKm(" 12.5 "))
        assertNull(RoutePlanning.parseManualKm("0"))
        assertNull(RoutePlanning.parseManualKm("abc"))
        assertNull(RoutePlanning.parseManualKm("900"))
    }

    @Test
    fun `duree estimee et affichage`() {
        assertEquals(60, RoutePlanning.estimateMinutes(15.0))
        assertEquals(1, RoutePlanning.estimateMinutes(0.1))
        assertEquals("12,4 km", RoutePlanning.formatKm(12.4))
        assertEquals("12 km", RoutePlanning.formatKm(12.0))
        assertTrue(RoutePlanning.formatKm(0.5).startsWith("0,5"))
    }
}
