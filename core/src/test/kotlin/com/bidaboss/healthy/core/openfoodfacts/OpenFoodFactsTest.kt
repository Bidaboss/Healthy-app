package com.bidaboss.healthy.core.openfoodfacts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenFoodFactsTest {

    private val response = """
        {"count":3,"page":1,"products":[
          {"code":"3017620422003","product_name":"Nutella","product_name_fr":"Pâte à tartiner","brands":"Ferrero, Nutella",
           "serving_quantity":"15",
           "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":"57.5","fat_100g":30.9}},
          {"code":"111","product_name":"Sans calories","nutriments":{"proteins_100g":1}},
          {"code":"222","product_name":"","nutriments":{"energy-kcal_100g":10}},
          {"code":"333","product_name":"Yaourt nature","nutriments":{"energy-kcal_100g":"61","fiber_100g":0},"champ_inconnu":true}
        ]}
    """.trimIndent()

    @Test
    fun `produits lus et produits inutilisables ignores`() {
        val products = assertNotNull(OpenFoodFacts.parseSearchResponse(response))
        assertEquals(listOf("Pâte à tartiner", "Yaourt nature"), products.map { it.name })
        val nutella = products[0]
        assertEquals("Ferrero", nutella.brand)
        assertEquals(15.0, nutella.servingG)
        assertEquals(57.5, nutella.carbsPer100g)
        assertNull(nutella.fiberPer100g)
        assertEquals(61.0, products[1].kcalPer100g)
        assertNull(products[1].brand)
    }

    @Test
    fun `valeurs calculees pour une quantite`() {
        val nutella = OpenFoodFacts.parseSearchResponse(response)!![0]
        val portion = nutella.forQuantity(15.0)
        assertEquals(80.9, portion.kcal)
        assertEquals(0.9, portion.proteinG)
        assertEquals(8.6, portion.carbsG)
        assertNull(portion.fiberG)
    }

    @Test
    fun `reponse illisible ou sans liste renvoie null`() {
        assertNull(OpenFoodFacts.parseSearchResponse("<html>erreur</html>"))
        assertNull(OpenFoodFacts.parseSearchResponse("""{"status":0}"""))
        assertTrue(OpenFoodFacts.parseSearchResponse("""{"products":[]}""")!!.isEmpty())
    }

    @Test
    fun `adresse de recherche encodee`() {
        val url = OpenFoodFacts.searchUrl(" pâte à tartiner ")
        assertTrue(url.startsWith("https://world.openfoodfacts.org/cgi/search.pl?search_terms=p%C3%A2te+%C3%A0+tartiner&"), url)
    }

    @Test
    fun `la reponse du nouveau moteur est lue aussi`() {
        // Début d'une vraie réponse de search.openfoodfacts.org (11/11/2026) : « hits », marques en liste.
        val body = """
            {"hits":[{"code":"3760103144373","brands":["Autour du riz"],
            "nutriments":{"carbohydrates_100g":77,"energy-kcal_100g":372,"fat_100g":3.4,"fiber_100g":2.7,"proteins_100g":7.1},
            "product_name":"Riz Thaï demi-complet"},
            {"code":"1","brands":[],"nutriments":{"energy-kcal_100g":53},"product_name":"Yaourt nature"}]}
        """.trimIndent()
        val products = assertNotNull(OpenFoodFacts.parseSearchResponse(body))
        assertEquals(2, products.size)
        assertEquals("Riz Thaï demi-complet", products[0].name)
        assertEquals("Autour du riz", products[0].brand)
        assertEquals(372.0, products[0].kcalPer100g)
        assertEquals(77.0, products[0].carbsPer100g)
        assertEquals(null, products[1].brand)
    }

    @Test
    fun `le nouveau moteur est essaye en premier`() {
        val urls = OpenFoodFacts.searchUrls("pâte à tartiner")
        assertEquals(2, urls.size)
        assertTrue(urls[0].startsWith("https://search.openfoodfacts.org/search?q=p%C3%A2te+%C3%A0+tartiner&langs=fr&"), urls[0])
        assertEquals(OpenFoodFacts.searchUrl("pâte à tartiner"), urls[1])
    }
}
