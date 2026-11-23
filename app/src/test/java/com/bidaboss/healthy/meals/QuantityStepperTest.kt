package com.bidaboss.healthy.meals

import com.bidaboss.healthy.meals.ui.FoodInput
import com.bidaboss.healthy.meals.ui.rebased
import com.bidaboss.healthy.meals.ui.stepped
import org.junit.Assert.assertEquals
import org.junit.Test

/** Les boutons − et + de la quantité d'un aliment dans « Modifier le repas ». */
class QuantityStepperTest {

    private val rice = FoodInput(key = 1, name = "Riz", quantityG = "100", kcal = "130", proteinG = "3").rebased()

    @Test
    fun `plus ajoute 10 g et les calories suivent`() {
        val more = rice.stepped(1)
        assertEquals("110", more.quantityG)
        assertEquals("143", more.kcal)
        assertEquals("3,3", more.proteinG)
    }

    @Test
    fun `moins retire 10 g, ou 5 g sous 20 g, sans descendre sous 1 g`() {
        assertEquals("90", rice.stepped(-1).quantityG)
        val small = rice.copy(quantityG = "12").rebased()
        assertEquals("7", small.stepped(-1).quantityG)
        assertEquals("1", small.copy(quantityG = "3").rebased().stepped(-1).quantityG)
    }

    @Test
    fun `sans quantite lisible rien ne bouge`() {
        val none = FoodInput(key = 2, name = "Café", kcal = "5")
        assertEquals(none, none.stepped(1))
    }
}
