package com.bidaboss.healthy.meals

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.importformat.ImportDocument
import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.core.importformat.ImportResult
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.meals.data.Drink
import com.bidaboss.healthy.meals.data.Food
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/** Favoris, récents et « Refaire ce repas », dans une vraie base Room en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MealFavoritesTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val day = LocalDate.of(2026, 11, 12)

    private lateinit var database: HealthyDatabase
    private lateinit var meals: MealRepository

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

    private suspend fun save(title: String?, hour: Int, vararg foods: String): Long = meals.saveMeal(
        MealDraft(
            id = null,
            type = MealType.BREAKFAST,
            eatenAt = day.atTime(hour, 0),
            title = title,
            notes = null,
            foods = foods.map { Food(name = it, kcal = 100.0) },
        ),
    )

    @Test
    fun `un favori se met, s'enleve et survit a une modification du repas`() = runBlocking {
        val id = save("Petit-déj", 8, "Pain", "Beurre")
        meals.setFavorite(id, true)
        assertEquals(listOf("Petit-déj"), meals.observeFavorites().first().map { it.title })

        meals.saveMeal(MealDraft(id, MealType.BREAKFAST, day.atTime(8, 30), "Petit-déj", null, listOf(Food("Pain", kcal = 90.0))))
        assertTrue(meals.getMeal(id)!!.isFavorite)

        meals.setFavorite(id, false)
        assertTrue(meals.observeFavorites().first().isEmpty())
    }

    @Test
    fun `les recents ne gardent qu'un exemplaire de chaque repas et laissent les favoris a part`() = runBlocking {
        save("Petit-déj", 7, "Pain", "Beurre")
        save("Petit-déj", 8, "Pain", "Beurre")
        save(null, 12, "Riz", "Poulet")
        val favorite = save("Soupe", 19, "Soupe")
        meals.setFavorite(favorite, true)

        val recents = meals.observeRecentMeals().first()
        assertEquals(listOf(null, "Petit-déj"), recents.map { it.title })
    }

    @Test
    fun `refaire un repas cree un nouveau repas a l'heure actuelle sans toucher l'ancien`() = runBlocking {
        val id = save("Petit-déj", 8, "Pain", "Beurre")
        val now = LocalDateTime.of(2026, 11, 13, 12, 41)
        val editor = MealEditorViewModel(meals, id, day, now = { now })
        // Laisse le chargement du repas se terminer (base Room sur un autre fil).
        repeat(50) { if (editor.uiState.value.loading) Thread.sleep(20) }

        editor.redo()
        val state = editor.uiState.value
        assertTrue(state.isNew)
        assertEquals(MealType.LUNCH, state.type)
        assertEquals(now, state.eatenAt)

        editor.save()
        repeat(50) { if (!editor.uiState.value.finished) Thread.sleep(20) }
        assertEquals(1, meals.observeMeals(now.toLocalDate()).first().size)
        assertEquals(day.atTime(8, 0), meals.getMeal(id)!!.eatenAt)
        assertFalse(meals.observeMeals(now.toLocalDate()).first().single().isFavorite)
    }

    @Test
    fun `un repas partage est un fichier d'import qui se relit`() = runBlocking {
        val id = save("Petit-déj", 8, "Pain", "Beurre")
        val text = meals.shareMeal(id, "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", OffsetDateTime.parse("2026-11-13T22:00:00+01:00"))!!
        val document = (ImportParser(zone).parse(text) as ImportResult.Success).document as ImportDocument.Meal
        assertEquals(listOf("Pain", "Beurre"), document.meal.items.map { it.name })
        assertEquals(day.atTime(8, 0), document.meal.eatenAt.atZoneSameInstant(zone).toLocalDateTime())
    }

    @Test
    fun `une boisson va au repas en cours, sinon en collation`() = runBlocking {
        val lunch = meals.saveMeal(
            MealDraft(null, MealType.LUNCH, day.atTime(12, 15), null, null, listOf(Food("Pâtes", kcal = 400.0))),
        )
        assertEquals(lunch, meals.addDrink(Drink.WINE, day.atTime(13, 0)))
        assertEquals(listOf("Pâtes", "Vin"), meals.getMeal(lunch)!!.foods.map { it.name })

        // 16 h : pas de repas en cours, une collation est créée.
        val snack = meals.addDrink(Drink.SODA, day.atTime(16, 0))
        val created = meals.getMeal(snack)!!
        assertEquals(MealType.SNACK, created.type)
        assertEquals(140.0, created.totalKcal, 0.0)
        assertEquals("33 cL", created.foods.single().portion)
    }

    @Test
    fun `un produit scanne va au repas en cours, sinon a un nouveau repas du type de l'heure, et l'annulation le retire`() = runBlocking {
        val lunch = meals.saveMeal(
            MealDraft(null, MealType.LUNCH, day.atTime(12, 15), null, null, listOf(Food("Pâtes", kcal = 400.0))),
        )
        val bar = Food("Barre", kcal = 100.0)
        val joined = meals.addFoodToCurrentMeal(bar, day.atTime(13, 0))
        assertEquals(lunch, joined.mealId)
        assertEquals(MealType.LUNCH, joined.type)
        assertFalse(joined.createdMeal)
        assertEquals(listOf("Pâtes", "Barre"), meals.getMeal(lunch)!!.foods.map { it.name })

        meals.undoAdd(joined)
        assertEquals(listOf("Pâtes"), meals.getMeal(lunch)!!.foods.map { it.name })

        // 19 h 30 : pas de dîner en cours, un dîner est créé (et non une collation comme pour les boissons).
        val created = meals.addFoodToCurrentMeal(bar, day.atTime(19, 30))
        assertTrue(created.createdMeal)
        assertEquals(MealType.DINNER, meals.getMeal(created.mealId)!!.type)
        meals.undoAdd(created)
        assertEquals(null, meals.getMeal(created.mealId))
    }
}
