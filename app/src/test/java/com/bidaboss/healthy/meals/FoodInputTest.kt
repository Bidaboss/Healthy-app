package com.bidaboss.healthy.meals

import com.bidaboss.healthy.meals.ui.FoodInput
import com.bidaboss.healthy.meals.ui.rebased
import com.bidaboss.healthy.meals.ui.scaledBy
import com.bidaboss.healthy.meals.ui.withQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Recalcul des calories et des macros quand la quantité change dans l'éditeur de repas. */
class FoodInputTest {

    // Riz cuit : 150 g = 195 kcal, 4 g de protéines, 42,5 g de glucides, lipides non renseignés.
    private val rice = FoodInput(
        key = 1,
        name = "Riz",
        quantityG = "150",
        kcal = "195",
        proteinG = "4",
        carbsG = "42,5",
    ).rebased()

    @Test
    fun `changer la quantite recalcule calories et macros en proportion`() {
        val edited = rice.withQuantity("300")
        assertEquals("390", edited.kcal)
        assertEquals("8", edited.proteinG)
        assertEquals("85", edited.carbsG)
        // Un champ vide reste vide.
        assertEquals("", edited.fatG)
    }

    @Test
    fun `taper la quantite chiffre par chiffre ne degrade pas le resultat`() {
        // L'utilisateur efface puis tape 2, 20, 200 : on repart toujours de la référence (150 g).
        val edited = rice.withQuantity("").withQuantity("2").withQuantity("20").withQuantity("200")
        assertEquals("260", edited.kcal)
        assertEquals("5,3", edited.proteinG)
        assertEquals("56,7", edited.carbsG)
    }

    @Test
    fun `quantite vide, nulle ou illisible ne change rien`() {
        listOf("", "0", "abc", "-5").forEach { text ->
            val edited = rice.withQuantity(text)
            assertEquals(text, edited.quantityG)
            assertEquals("195", edited.kcal)
            assertEquals("42,5", edited.carbsG)
        }
    }

    @Test
    fun `vider la quantite reaffiche les valeurs de reference`() {
        // 150 effacé chiffre par chiffre : à « 1 », les valeurs sont celles d'1 g ; une fois vide,
        // on ne doit pas garder ces valeurs d'1 g mais revenir à celles des 150 g d'origine.
        val edited = rice.withQuantity("300").withQuantity("1").withQuantity("")
        assertEquals("", edited.quantityG)
        assertEquals("195", edited.kcal)
        assertEquals("4", edited.proteinG)
        assertEquals("42,5", edited.carbsG)
        assertEquals("", edited.fatG)
        assertEquals("195", rice.withQuantity("1").withQuantity("abc").kcal)
    }

    @Test
    fun `la virgule est acceptee dans la quantite`() {
        assertEquals("97,5", rice.withQuantity("75").kcal)
        assertEquals("28,5", rice.withQuantity("100,5").carbsG)
    }

    @Test
    fun `corriger les calories a la main fixe la nouvelle base`() {
        // À 300 g, l'utilisateur corrige 390 en 420 : c'est la nouvelle vérité pour 300 g.
        val corrected = rice.withQuantity("300").copy(kcal = "420").rebased()
        assertEquals("210", corrected.withQuantity("150").kcal)
        assertEquals("4", corrected.withQuantity("150").proteinG)
    }

    @Test
    fun `sans quantite de depart il n'y a pas de recalcul`() {
        val noQuantity = FoodInput(key = 2, name = "Pomme", kcal = "80").rebased()
        assertNull(noQuantity.reference)
        assertEquals("80", noQuantity.withQuantity("200").kcal)
        // Dès qu'une quantité et des calories sont saisies ensemble, la base existe.
        val based = noQuantity.withQuantity("200").copy(kcal = "100").rebased()
        assertEquals("50", based.withQuantity("100").kcal)
    }

    @Test
    fun `une demi portion divise la quantite et les valeurs`() {
        val half = rice.scaledBy(0.5)
        assertEquals("75", half.quantityG)
        assertEquals("97,5", half.kcal)
        assertEquals("21,3", half.carbsG)
        // Deux gestes de suite repartent de la même référence : ×1,5 après ½ = 112,5 g → 113 g.
        assertEquals("113", half.scaledBy(1.5).quantityG)
    }

    @Test
    fun `sans reference la portion se calcule quand meme a partir des valeurs affichees`() {
        val plain = FoodInput(key = 2, quantityG = "200", kcal = "100")
        val more = plain.scaledBy(1.5)
        assertEquals("300", more.quantityG)
        assertEquals("150", more.kcal)
    }

    @Test
    fun `sans quantite les valeurs sont multipliees directement`() {
        val pizza = FoodInput(key = 3, name = "Pizza", kcal = "800", proteinG = "30")
        val threeQuarters = pizza.scaledBy(0.75)
        assertEquals("", threeQuarters.quantityG)
        assertEquals("600", threeQuarters.kcal)
        assertEquals("22,5", threeQuarters.proteinG)
    }
}
