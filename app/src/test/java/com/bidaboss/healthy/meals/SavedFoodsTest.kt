package com.bidaboss.healthy.meals

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.importformat.DrinkKind
import com.bidaboss.healthy.core.importformat.FoodSource
import com.bidaboss.healthy.core.importformat.ImportDocument
import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.core.importformat.ImportResult
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.meals.data.SaveFoodResult
import com.bidaboss.healthy.meals.data.SavedFoodDraft
import com.bidaboss.healthy.meals.data.SavedFoodRepository
import com.bidaboss.healthy.meals.data.SharePortion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/** « Mes aliments » (marque-page, création) et « Partager ce repas » à la portion de l'autre, dans une vraie base en mémoire. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SavedFoodsTest {

    private val zone = ZoneId.of("Europe/Paris")
    private lateinit var database: HealthyDatabase
    private lateinit var saved: SavedFoodRepository
    private lateinit var meals: MealRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        saved = SavedFoodRepository(database.savedFoodDao(), now = { 1_000L })
        meals = MealRepository(database.mealDao(), RoomTransactionRunner(database), zone = { zone })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `le marque-page ajoute un aliment avec ses valeurs, un second appui le retire`() = runBlocking {
        val skyr = Food("Skyr nature", quantityG = 150.0, kcal = 90.0, proteinG = 16.0, source = FoodSource.OPEN_FOOD_FACTS)
        assertTrue(saved.toggle(skyr))
        val list = saved.observeAll().first()
        assertEquals("Skyr nature", list.single().name)
        assertEquals(150.0, list.single().portionG!!, 0.0)
        assertEquals(FoodSource.OPEN_FOOD_FACTS, list.single().source)
        // Même nom écrit autrement (accents, majuscules) : c'est le même aliment.
        assertTrue("skyr nature" in saved.observeKeys().first())
        assertFalse(saved.toggle(skyr.copy(name = "SKYR  nature")))
        assertTrue(saved.observeAll().first().isEmpty())
    }

    @Test
    fun `un aliment garde sa portion et redevient un aliment de repas, boisson comprise`() = runBlocking {
        val result = saved.save(
            SavedFoodDraft(null, "Lait demi-écrémé", portionG = 250.0, kcal = 115.0, proteinG = 8.0, drink = DrinkKind.DRINK),
        )
        val id = (result as SaveFoodResult.Saved).id
        val food = saved.get(id)!!.toFood()
        assertEquals(250.0, food.quantityG!!, 0.0)
        assertEquals(115.0, food.kcal, 0.0)
        assertEquals(DrinkKind.DRINK, food.drink)
        assertNull(food.fatG)
    }

    @Test
    fun `deux aliments ne portent pas le meme nom, mais on peut corriger le sien`() = runBlocking {
        val first = (saved.save(SavedFoodDraft(null, "Pain complet", 40.0, 100.0)) as SaveFoodResult.Saved).id
        assertEquals(SaveFoodResult.NameTaken, saved.save(SavedFoodDraft(null, "pain  COMPLET", 30.0, 80.0)))
        assertEquals(SaveFoodResult.Saved(first), saved.save(SavedFoodDraft(first, "Pain complet", 50.0, 125.0)))
        assertEquals(125.0, saved.get(first)!!.kcal, 0.0)
        val second = (saved.save(SavedFoodDraft(null, "Pain blanc", 40.0, 105.0)) as SaveFoodResult.Saved).id
        assertEquals(SaveFoodResult.NameTaken, saved.save(SavedFoodDraft(second, "Pain complet", 40.0, 105.0)))
        saved.delete(first)
        assertEquals(listOf("Pain blanc"), saved.observeAll().first().map { it.name })
    }

    @Test
    fun `partager la moitie divise quantites, calories et macros, et le fichier se relit`() = runBlocking {
        val id = meals.saveMeal(
            MealDraft(
                null, MealType.DINNER, LocalDate.of(2026, 11, 22).atTime(20, 0), "Pizza", null,
                listOf(Food("Pizza", quantityG = 400.0, portion = "1 pizza", kcal = 900.0, proteinG = 35.0, carbsG = 110.0, fatG = 30.0)),
            ),
        )
        val at = OffsetDateTime.parse("2026-11-22T22:00:00+01:00")
        val half = meals.shareMeal(id, "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", at, SharePortion.Half)!!
        val item = ((ImportParser(zone).parse(half) as ImportResult.Success).document as ImportDocument.Meal).meal.items.single()
        assertEquals(200.0, item.quantityG!!, 0.0)
        assertEquals(450.0, item.kcal, 0.0)
        assertEquals(17.5, item.proteinG!!, 0.0)
        assertEquals(55.0, item.carbsG!!, 0.0)
        assertNull("« 1 pizza » ne serait plus vrai", item.portion)

        val third = meals.shareMeal(id, "1a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", at, SharePortion.Third)!!
        val thirdItem = ((ImportParser(zone).parse(third) as ImportResult.Success).document as ImportDocument.Meal).meal.items.single()
        assertEquals(300.0, thirdItem.kcal, 0.0)

        // « Pareil » : le repas tel quel.
        val same = meals.shareMeal(id, "2a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", at, SharePortion.Same)!!
        val sameItem = ((ImportParser(zone).parse(same) as ImportResult.Success).document as ImportDocument.Meal).meal.items.single()
        assertEquals(900.0, sameItem.kcal, 0.0)
        assertEquals("1 pizza", sameItem.portion)
    }

    @Test
    fun `le pourcentage de la portion Autre doit etre entre 1 et 100`() {
        assertEquals(25, SharePortion.parsePercent("25 %")!!.percent)
        assertEquals(0.25, SharePortion.parsePercent("25")!!.factor, 0.0)
        assertEquals(100, SharePortion.parsePercent("100")!!.percent)
        assertNull(SharePortion.parsePercent("0"))
        assertNull(SharePortion.parsePercent("150"))
        assertNull(SharePortion.parsePercent("beaucoup"))
    }
}
