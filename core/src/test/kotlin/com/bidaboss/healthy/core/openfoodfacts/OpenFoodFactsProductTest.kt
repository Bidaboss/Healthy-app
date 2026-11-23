package com.bidaboss.healthy.core.openfoodfacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenFoodFactsProductTest {

    @Test
    fun `un code EAN-13 valable est garde, un UPC-A est ecrit sur 13 chiffres`() {
        assertEquals("3017620422003", Barcodes.normalize("3017620422003"))
        assertEquals("0012345678905", Barcodes.normalize("012345678905"))
        assertEquals("96385074", Barcodes.normalize(" 96385074 "))
    }

    @Test
    fun `un code mal lu est refuse`() {
        assertNull(Barcodes.normalize("3017620422004")) // clé de contrôle fausse
        assertNull(Barcodes.normalize("12345"))
        assertNull(Barcodes.normalize("30176204220A3"))
        assertNull(Barcodes.normalize(""))
    }

    @Test
    fun `l'adresse de la fiche ne contient que le code et les champs utiles`() {
        val url = OpenFoodFacts.productUrl("3017620422003")
        assertTrue(url.startsWith("https://world.openfoodfacts.org/api/v2/product/3017620422003.json?fields="))
    }

    @Test
    fun `un produit complet est lu avec sa portion`() {
        val body = """{"code":"3017620422003","status":1,"product":{"product_name":"Pâte à tartiner","product_name_fr":"Nutella",
            "brands":"Ferrero,Autre","serving_quantity":"15","nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,
            "carbohydrates_100g":57.5,"fat_100g":30.9,"fiber_100g":0}}}"""
        val found = OpenFoodFacts.parseProductResponse(body, "3017620422003") as ProductLookup.Found
        assertEquals("Nutella", found.product.name)
        assertEquals("Ferrero", found.product.brand)
        assertEquals(539.0, found.product.kcalPer100g, 0.0)
        assertEquals(15.0, found.product.servingG!!, 0.0)
        assertEquals(0.0, found.product.fiberPer100g!!, 0.0)
    }

    @Test
    fun `sans kcal, les kJ sont convertis`() {
        val body = """{"status":1,"product":{"product_name":"X","nutriments":{"energy-kj_100g":418.4}}}"""
        val found = OpenFoodFacts.parseProductResponse(body, "96385074") as ProductLookup.Found
        assertEquals(100.0, found.product.kcalPer100g, 0.0)
        assertNull(found.product.proteinPer100g)
        assertNull(found.product.servingG)
    }

    @Test
    fun `sans calories, le produit est incomplet et garde ce qui est connu`() {
        val body = """{"status":1,"product":{"product_name":"Bidule","brands":"Marque","nutriments":{"proteins_100g":3}}}"""
        val incomplete = OpenFoodFacts.parseProductResponse(body, "96385074") as ProductLookup.Incomplete
        assertEquals("Bidule", incomplete.draft.name)
        assertNull(incomplete.draft.kcalPer100g)
        assertEquals(3.0, incomplete.draft.proteinPer100g!!, 0.0)
    }

    @Test
    fun `un code inconnu est signale, une reponse illisible rend null`() {
        assertEquals(
            ProductLookup.NotFound,
            OpenFoodFacts.parseProductResponse("""{"status":0,"status_verbose":"product not found"}""", "96385074"),
        )
        assertNull(OpenFoodFacts.parseProductResponse("pas du json", "96385074"))
        assertNull(OpenFoodFacts.parseProductResponse("""[1,2]""", "96385074"))
    }
}
