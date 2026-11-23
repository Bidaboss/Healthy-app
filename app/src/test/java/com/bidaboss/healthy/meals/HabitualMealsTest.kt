package com.bidaboss.healthy.meals

import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.HabitualMeals
import com.bidaboss.healthy.meals.data.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HabitualMealsTest {

    private val now = LocalDateTime.of(2026, 11, 23, 8, 0)
    private val oats = listOf(Food("Flocons d'avoine", 50.0, null, 190.0), Food("Lait", null, "1 verre", 90.0))

    private fun meal(
        daysAgo: Long,
        hour: Int = 8,
        minute: Int = 0,
        type: MealType = MealType.BREAKFAST,
        foods: List<Food> = oats,
        id: Long = daysAgo * 100 + hour,
    ): Meal {
        val at = LocalDateTime.of(now.toLocalDate().minusDays(daysAgo), java.time.LocalTime.of(hour, minute))
        return Meal(id, type, null, null, at, at.toLocalDate(), foods)
    }

    private fun suggest(past: List<Meal>, today: List<Meal> = emptyList(), skip: Boolean = true) =
        HabitualMeals.suggest(past, today, now, skip)

    @Test
    fun `un repas note deux fois a la meme heure est propose`() {
        val result = suggest(listOf(meal(1), meal(2)))
        assertEquals(1, result.size)
        assertEquals(2, result.single().occurrences)
        assertEquals(280.0, result.single().kcal, 0.01)
    }

    @Test
    fun `une seule fois ne suffit pas`() {
        assertTrue(suggest(listOf(meal(1))).isEmpty())
    }

    @Test
    fun `fenetre d'une heure trente autour de l'heure actuelle`() {
        // 9 h 30 : à la limite, compté ; 9 h 31 : trop loin.
        assertEquals(1, suggest(listOf(meal(1, 9, 30), meal(2, 6, 30))).size)
        assertTrue(suggest(listOf(meal(1, 9, 31), meal(2, 9, 31))).isEmpty())
    }

    @Test
    fun `le type de repas doit etre le meme`() {
        assertTrue(suggest(listOf(meal(1), meal(2, type = MealType.SNACK))).isEmpty())
    }

    @Test
    fun `le contenu doit etre le meme, dans n'importe quel ordre`() {
        assertEquals(1, suggest(listOf(meal(1), meal(2, foods = oats.reversed()))).size)
        assertTrue(suggest(listOf(meal(1), meal(2, foods = listOf(Food("Pain", 80.0, null, 200.0))))).isEmpty())
    }

    @Test
    fun `seulement les 28 derniers jours et jamais aujourd'hui`() {
        assertTrue(suggest(listOf(meal(29), meal(30))).isEmpty())
        assertEquals(1, suggest(listOf(meal(28), meal(27))).size)
        assertTrue(suggest(listOf(meal(0, 7), meal(0, 7, 30, id = 1))).isEmpty())
    }

    @Test
    fun `un type deja note aujourd'hui n'est plus propose sur Aujourd'hui, mais l'est dans l'editeur`() {
        val past = listOf(meal(1), meal(2))
        val today = listOf(meal(0, 7))
        assertTrue(suggest(past, today, skip = true).isEmpty())
        assertEquals(1, suggest(past, today, skip = false).size)
    }

    @Test
    fun `le plus frequent passe en premier, au plus trois`() {
        val a = listOf(Food("A", null, null, 100.0))
        val b = listOf(Food("B", null, null, 100.0))
        val c = listOf(Food("C", null, null, 100.0))
        val d = listOf(Food("D", null, null, 100.0))
        val past = listOf(1L, 2L).map { meal(it, foods = a) } +
            listOf(3L, 4L, 5L).map { meal(it, foods = b) } +
            listOf(6L, 7L).map { meal(it, foods = c) } +
            listOf(8L, 9L).map { meal(it, foods = d) }
        val result = suggest(past)
        assertEquals(3, result.size)
        assertEquals("B", result.first().foods.single().name)
        // À égalité, le plus récent d'abord.
        assertEquals("A", result[1].foods.single().name)
    }

    @Test
    fun `toMeal donne un repas maintenant avec un id negatif`() {
        val habit = suggest(listOf(meal(1), meal(2))).single()
        val m = habit.toMeal(0, now)
        assertEquals(-1L, m.id)
        assertEquals(now, m.eatenAt)
        assertEquals(oats, m.foods)
    }
}
