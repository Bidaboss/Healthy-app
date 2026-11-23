package com.bidaboss.healthy.meals

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.importformat.Confidence
import com.bidaboss.healthy.core.importformat.FoodSource
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.FoodHistory
import com.bidaboss.healthy.meals.data.FoodHistoryRow
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.meals.ui.MealEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/** « Déjà mangés » (calculé depuis les repas) et repas en calories seules. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FoodHistoryTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val day = LocalDate.of(2026, 11, 20)
    private val dayMs = 24L * 60 * 60 * 1000
    private val now = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private lateinit var database: HealthyDatabase
    private lateinit var meals: MealRepository

    private fun row(name: String, daysAgo: Int, kcal: Double = 100.0, quantity: Double? = 100.0) = FoodHistoryRow(
        name = name, quantityG = quantity, portion = null, kcal = kcal, proteinG = null, carbsG = null, fatG = null,
        fiberG = null, source = "manuel", offCode = null, drink = null, eatenAtEpochMs = now - daysAgo * dayMs,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        meals = MealRepository(database.mealDao(), RoomTransactionRunner(database)) { zone }
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `le meme aliment ecrit autrement n'apparait qu'une fois, avec sa derniere quantite`() {
        val history = FoodHistory.build(
            listOf(
                row("Yaourt nature", 5, kcal = 60.0, quantity = 125.0),
                row("YAOURT  Nature", 1, kcal = 120.0, quantity = 250.0),
                row("Crème brûlée", 2),
            ),
            now,
        )
        assertEquals(2, history.size)
        val yogurt = history.first { it.key == "yaourt nature" }
        assertEquals(2, yogurt.timesEaten)
        assertEquals(250.0, yogurt.food.quantityG!!, 0.0) // la dernière fois, pas la première
        assertEquals(120.0, yogurt.food.kcal, 0.0)
    }

    @Test
    fun `les 28 derniers jours passent avant, les plus frequents en tete`() {
        val history = FoodHistory.build(
            listOf(
                row("Ancien", 60),
                row("Pomme", 3),
                row("Riz", 10),
                row("Riz", 12),
                row("Riz", 20),
            ),
            now,
        )
        assertEquals(listOf("riz", "pomme", "ancien"), history.map { it.key })
    }

    @Test
    fun `la liste se filtre sans accents ni majuscules, mot par mot`() {
        val history = FoodHistory.build(listOf(row("Crème brûlée", 1), row("Crème fraîche", 2), row("Pain", 3)), now)
        assertEquals(listOf("creme brulee"), FoodHistory.filter(history, "BRU").map { it.key })
        assertEquals(2, FoodHistory.filter(history, "creme").size)
        assertEquals(1, FoodHistory.filter(history, "fraiche creme").size)
        assertEquals(3, FoodHistory.filter(history, "  ").size)
        assertTrue(FoodHistory.filter(history, "xyz").isEmpty())
    }

    @Test
    fun `l'historique vient des repas enregistres, et un repas supprime en sort`() = runBlocking {
        val lunch = meals.saveMeal(
            MealDraft(null, MealType.LUNCH, day.atTime(12, 0), null, null, listOf(Food("Pâtes", quantityG = 200.0, kcal = 300.0))),
        )
        val history = meals.observeFoodHistory { now }.first()
        assertEquals("Pâtes", history.single().food.name)
        assertEquals(200.0, history.single().food.quantityG!!, 0.0)

        meals.deleteMeal(lunch)
        assertTrue(meals.observeFoodHistory { now }.first().isEmpty())
    }

    @Test
    fun `un repas en calories seules a des macros vides, la pastille, et se detecte`() = runBlocking {
        val vm = MealEditorViewModel(meals, null, day)
        assertNotNull(vm.addCaloriesOnly("", "0")) // calories invalides : message d'erreur
        assertNull(vm.addCaloriesOnly("  ", "850"))
        val food = vm.uiState.value.foods.single().original!!
        assertEquals("Repas au restaurant", food.name)
        assertEquals(850.0, food.kcal, 0.0)
        assertEquals(FoodSource.MANUAL, food.source)
        assertEquals(Confidence.LOW, food.confidence)
        assertNull(food.proteinG)
        assertNull(food.fiberG)

        val id = meals.saveMeal(MealDraft(null, MealType.LUNCH, day.atTime(13, 0), null, null, listOf(food)))
        val saved = meals.getMeal(id)!!
        assertTrue(saved.isCaloriesOnly)
        assertNull(saved.foods.single().proteinG)
        assertEquals(850.0, saved.totalKcal, 0.0)

        val detailed = meals.saveMeal(
            MealDraft(null, MealType.DINNER, day.atTime(19, 0), null, null, listOf(Food("Riz", kcal = 200.0, proteinG = 4.0))),
        )
        assertFalse(meals.getMeal(detailed)!!.isCaloriesOnly)
    }
}
