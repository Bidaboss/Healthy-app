package com.bidaboss.healthy.body

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.importformat.Confidence
import com.bidaboss.healthy.core.importformat.DrinkKind
import com.bidaboss.healthy.core.importformat.FoodSource
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
import com.bidaboss.healthy.meals.ui.toFood
import com.bidaboss.healthy.meals.ui.toInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

/** Les liquides des repas comptent dans l'eau bue (PRD eau-depuis-repas) : un seul total, l'alcool jamais. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WaterFromMealsTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val day = LocalDate.of(2026, 11, 23)
    private lateinit var database: HealthyDatabase
    private lateinit var body: BodyRepository
    private lateinit var meals: MealRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        body = BodyRepository(database.bodyDao())
        meals = MealRepository(database.mealDao(), RoomTransactionRunner(database)) { zone }
    }

    @After
    fun tearDown() = database.close()

    private suspend fun save(foods: List<Food>, id: Long? = null, date: LocalDate = day, hour: Int = 8) = meals.saveMeal(
        MealDraft(id, MealType.BREAKFAST, date.atTime(hour, 0), null, null, foods),
    )

    private suspend fun waterOf(date: LocalDate = day) = body.observeDailyWater(date, date).first()[date] ?: 0

    @Test
    fun `le total additionne l'ecran Eau et les boissons des repas, sans l'alcool ni les aliments`() = runBlocking {
        body.addWater(day, 250)
        save(
            listOf(
                Food("Lait", 200.0, null, 90.0, drink = DrinkKind.DRINK),
                Food("Soupe", 300.5, null, 120.0, drink = DrinkKind.DRINK),
                Food("Vin", 120.0, null, 85.0, drink = DrinkKind.ALCOHOL),
                Food("Pain", 80.0, null, 200.0),
                Food("Café", null, "1 tasse", 5.0, drink = DrinkKind.DRINK),
            ),
        )
        assertEquals(250 + 200 + 301, waterOf())
    }

    @Test
    fun `un jour sans repas et sans ajout reste absent`() = runBlocking {
        assertNull(body.observeDailyWater(day, day).first()[day])
    }

    @Test
    fun `changer la quantite ou supprimer le repas met l'eau a jour, et chaque jour garde le sien`() = runBlocking {
        val id = save(listOf(Food("Lait", 200.0, null, 90.0, drink = DrinkKind.DRINK)))
        save(listOf(Food("Jus", 150.0, null, 70.0, drink = DrinkKind.DRINK)), date = day.minusDays(1))
        assertEquals(200, waterOf())
        assertEquals(150, waterOf(day.minusDays(1)))

        save(listOf(Food("Lait", 300.0, null, 135.0, drink = DrinkKind.DRINK)), id = id)
        assertEquals(300, waterOf())

        save(listOf(Food("Lait", 300.0, null, 135.0)), id = id)
        assertEquals(0, waterOf())

        save(listOf(Food("Lait", 300.0, null, 135.0, drink = DrinkKind.DRINK)), id = id)
        meals.deleteMeal(id)
        assertEquals(0, waterOf())
    }

    @Test
    fun `la liste Dans les repas donne le nom, la quantite et le repas, sans l'alcool`() = runBlocking {
        body.addWater(day, 250)
        save(
            listOf(
                Food("Lait", 200.0, null, 90.0, drink = DrinkKind.DRINK),
                Food("Bière", 250.0, null, 105.0, drink = DrinkKind.ALCOHOL),
            ),
        )
        val drinks = body.observeMealDrinks(day).first()
        assertEquals(1, drinks.size)
        assertEquals("Lait", drinks.single().name)
        assertEquals(200, drinks.single().ml)
        assertEquals(MealType.BREAKFAST, drinks.single().mealType)
    }

    @Test
    fun `les boutons boissons posent le bon reglage, soda et jus comptent, biere et vin non`() = runBlocking {
        assertEquals(DrinkKind.DRINK, Drink.SODA.toFood().drink)
        assertEquals(DrinkKind.DRINK, Drink.JUICE.toFood().drink)
        assertEquals(DrinkKind.ALCOHOL, Drink.BEER.toFood().drink)
        assertEquals(DrinkKind.ALCOHOL, Drink.WINE.toFood().drink)

        val now = day.atTime(9, 0)
        meals.addDrink(Drink.SODA, now)
        meals.addDrink(Drink.BEER, now)
        assertEquals(Drink.SODA.volumeMl, waterOf())
    }

    @Test
    fun `les anciens repas gardent Aliment, rien n'est reclasse`() = runBlocking {
        val id = save(listOf(Food("Jus de fruits", 200.0, null, 90.0)))
        assertNull(meals.getMeal(id)!!.foods.single().drink)
        assertEquals(0, waterOf())
    }

    @Test
    fun `changer le reglage ne transforme pas une estimation en saisie manuelle, corriger une valeur oui`() {
        val estimated = Food("Lait", 200.0, null, 90.0, source = FoodSource.CLAUDE_ESTIMATE, confidence = Confidence.LOW)
        val errors = mutableListOf<String>()

        val asDrink = estimated.toInput(1).copy(drink = DrinkKind.DRINK).toFood(1, errors)!!
        assertEquals(DrinkKind.DRINK, asDrink.drink)
        assertEquals(FoodSource.CLAUDE_ESTIMATE, asDrink.source)
        assertEquals(Confidence.LOW, asDrink.confidence)

        val corrected = estimated.toInput(1).copy(kcal = "100").toFood(1, errors)!!
        assertEquals(FoodSource.MANUAL, corrected.source)
        assertNull(corrected.confidence)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `le repas partage ecrit drink et se reimporte avec le meme reglage`() = runBlocking {
        val id = save(
            listOf(
                Food("Lait", 200.0, null, 90.0, drink = DrinkKind.DRINK),
                Food("Vin", 120.0, null, 85.0, drink = DrinkKind.ALCOHOL),
                Food("Pain", 80.0, null, 200.0),
            ),
        )
        val json = meals.shareMeal(id, "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", OffsetDateTime.parse("2026-11-23T09:00:00+01:00"))!!
        assertTrue("\"drink\": \"boisson\"" in json && "\"drink\": \"alcool\"" in json)
        assertEquals(2, Regex("\"drink\"").findAll(json).count())

        val parsed = ImportParser(zone).parse(json)
        val items = ((parsed as ImportResult.Success).document as ImportDocument.Meal).meal.items
        assertEquals(listOf(DrinkKind.DRINK, DrinkKind.ALCOHOL, null), items.map { it.drink })
    }

    @Test
    fun `le reglage est garde dans l'historique Deja manges`() = runBlocking {
        save(listOf(Food("Lait", 200.0, null, 90.0, drink = DrinkKind.DRINK)))
        val history = meals.observeFoodHistory { day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() }.first()
        assertNotNull(history.firstOrNull { it.food.name == "Lait" })
        assertEquals(DrinkKind.DRINK, history.single { it.food.name == "Lait" }.food.drink)
    }
}
