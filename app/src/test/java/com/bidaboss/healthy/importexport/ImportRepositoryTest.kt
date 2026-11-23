package com.bidaboss.healthy.importexport

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.importformat.ImportDocument
import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.core.importformat.ImportResult
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.importexport.data.ImportRepository
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/** Import réel dans une base Room en mémoire : doublons, remplacement, saisie manuelle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ImportRepositoryTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val day = LocalDate.of(2026, 11, 11)

    private lateinit var database: HealthyDatabase
    private lateinit var meals: MealRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var imports: ImportRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        val transactions = RoomTransactionRunner(database)
        meals = MealRepository(database.mealDao(), transactions) { zone }
        workouts = WorkoutRepository(database.workoutDao())
        imports = ImportRepository(transactions, database.importDao(), meals, workouts)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun mealJson(id: String, kcal: Int, replaces: String? = null) = """
        {"format":"healthy-import","schema_version":1,"type":"meal","id":"$id",
         ${replaces?.let { "\"replaces\":\"$it\"," }.orEmpty()}
         "created_at":"2026-11-11T13:00:00+01:00",
         "meal":{"eaten_at":"2026-11-11T12:30:00+01:00","meal_type":"dejeuner","title":"Test",
                 "items":[{"name":"Riz","quantity_g":100,"kcal":$kcal,"protein_g":3}]}}
    """.trimIndent()

    private fun parse(json: String): ImportDocument =
        (ImportParser(zone).parse(json) as ImportResult.Success).document

    private val idA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val idB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

    @Test
    fun `un repas importe apparait dans le journal du jour`() = runBlocking {
        assertTrue(imports.save(parse(mealJson(idA, 130))))

        val meal = meals.observeMeals(day).first().single()
        assertEquals(MealType.LUNCH, meal.type)
        assertEquals(130.0, meal.totalKcal, 0.001)
        assertEquals("Riz", meal.foods.single().name)
        assertEquals(LocalDateTime.of(2026, 11, 11, 12, 30), meal.eatenAt)
    }

    @Test
    fun `reimporter le meme id n'ajoute rien`() = runBlocking {
        val document = parse(mealJson(idA, 130))
        assertTrue(imports.save(document))
        assertTrue(imports.isAlreadyImported(idA))
        assertFalse(imports.save(document))
        assertEquals(1, meals.observeMeals(day).first().size)
    }

    @Test
    fun `replaces remplace le repas corrige au lieu d'en ajouter un`() = runBlocking {
        imports.save(parse(mealJson(idA, 130)))
        meals.setFavorite(meals.findImportedMeal(idA)!!.id, true)
        val correction = parse(mealJson(idB, 200, replaces = idA))

        assertNotNull(imports.findReplacedMeal(correction))
        assertTrue(imports.save(correction))

        val meal = meals.observeMeals(day).first().single()
        assertEquals(200.0, meal.totalKcal, 0.001)
        // Le repas corrigé garde son étoile.
        assertTrue(meal.isFavorite)
    }

    @Test
    fun `replaces introuvable ajoute simplement le repas`() = runBlocking {
        val correction = parse(mealJson(idB, 200, replaces = idA))
        assertNull(imports.findReplacedMeal(correction))
        assertTrue(imports.save(correction))
        assertEquals(1, meals.observeMeals(day).first().size)
    }

    @Test
    fun `supprimer un repas importe permet de le reimporter`() = runBlocking {
        val document = parse(mealJson(idA, 130))
        imports.save(document)
        val meal = meals.observeMeals(day).first().single()

        meals.deleteMeal(meal.id)

        assertFalse(imports.isAlreadyImported(idA))
        assertTrue(imports.save(document))
        assertEquals(1, meals.observeMeals(day).first().size)
    }

    @Test
    fun `le jour du journal est la date locale du telephone`() = runBlocking {
        // 02:00 le 12 à Tokyo = 18:00 le 11 à Paris.
        val json = mealJson(idA, 130).replace("2026-11-11T12:30:00+01:00", "2026-11-12T02:00:00+09:00")
        imports.save(parse(json))

        val meal = meals.observeMeals(day).first().single()
        assertEquals(LocalDateTime.of(2026, 11, 11, 18, 0), meal.eatenAt)
        assertEquals(day, meal.localDate)
    }

    @Test
    fun `saisie manuelle puis modification et suppression`() = runBlocking {
        val id = meals.saveMeal(
            MealDraft(
                id = null,
                type = MealType.SNACK,
                eatenAt = LocalDateTime.of(2026, 11, 11, 16, 0),
                title = null,
                notes = null,
                foods = listOf(Food(name = "Pomme", kcal = 78.0)),
            ),
        )
        assertEquals(78.0, meals.observeMeals(day).first().single().totalKcal, 0.001)

        meals.saveMeal(
            MealDraft(
                id = id,
                type = MealType.DINNER,
                eatenAt = LocalDateTime.of(2026, 11, 12, 20, 0),
                title = "Soir",
                notes = null,
                foods = listOf(Food(name = "Pomme", kcal = 78.0), Food(name = "Yaourt", kcal = 60.0)),
            ),
        )
        assertTrue(meals.observeMeals(day).first().isEmpty())
        val moved = meals.observeMeals(day.plusDays(1)).first().single()
        assertEquals(MealType.DINNER, moved.type)
        assertEquals(138.0, moved.totalKcal, 0.001)

        meals.deleteMeal(id)
        assertTrue(meals.observeMeals(day.plusDays(1)).first().isEmpty())
    }

    @Test
    fun `un nouveau plan archive l'ancien`() = runBlocking {
        fun planJson(id: String, name: String) = """
            {"format":"healthy-import","schema_version":1,"type":"workout_plan","id":"$id",
             "created_at":"2026-11-11T13:00:00+01:00",
             "plan":{"name":"$name","sessions":[{"title":"A","day_of_week":1,"variants":[
               {"duration_min":15,"exercises":[{"name":"Squats","sets":2,"reps":10}]},
               {"duration_min":30,"exercises":[{"name":"Squats","sets":4,"reps":10}]}]}]}}
        """.trimIndent()

        assertTrue(imports.save(parse(planJson(idA, "Premier"))))
        assertTrue(imports.save(parse(planJson(idB, "Second"))))

        val active = workouts.observeActivePlan().first()
        assertEquals("Second", active?.name)
        assertEquals(listOf(15, 30), active?.sessions?.single()?.variants?.map { it.durationMin })
    }
}
