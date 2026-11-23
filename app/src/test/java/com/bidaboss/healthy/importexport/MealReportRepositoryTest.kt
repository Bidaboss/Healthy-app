package com.bidaboss.healthy.importexport

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.BodyProfile
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.importexport.data.MealReportRepository
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/** Bilan repas lu dans une vraie base Room en mémoire : 7 jours, eau, profil. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MealReportRepositoryTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 13)

    private lateinit var database: HealthyDatabase
    private lateinit var meals: MealRepository
    private lateinit var body: BodyRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        meals = MealRepository(database.mealDao(), RoomTransactionRunner(database)) { zone }
        body = BodyRepository(database.bodyDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun meal(day: LocalDate, kcal: Double) = meals.saveMeal(
        MealDraft(null, MealType.LUNCH, day.atTime(12, 0), null, null, listOf(Food("Plat", kcal = kcal, fiberG = 4.0))),
    )

    @Test
    fun `le bilan couvre 7 jours avec repas, eau et reperes du profil`() = runBlocking {
        meal(today, 600.0)
        meal(today.minusDays(6), 500.0)
        meal(today.minusDays(7), 900.0) // hors période
        body.addWater(today, 250)
        body.saveProfile(BodyProfile(Sex.MALE, LocalDate.of(1988, 7, 15), 176.0, 75.0, ActivityLevel.LIGHT))

        val json = MealReportRepository(meals, body).export(today, "3f6a2b81-9c4d-4e7f-8a1b-2d5c6e7f8091", today.atTime(21, 0))
        val report = Json.parseToJsonElement(json).jsonObject["report"]!!.jsonObject
        val days = report["days"]!!.jsonArray.map { it.jsonObject }

        assertEquals(7, days.size)
        assertEquals("2026-11-07", days.first()["date"]!!.jsonPrimitive.content)
        assertEquals(500, days.first()["kcal"]!!.jsonPrimitive.int)
        assertEquals(600, days.last()["kcal"]!!.jsonPrimitive.int)
        assertEquals(250, days.last()["water_ml"]!!.jsonPrimitive.int)
        assertEquals(0, days[3]["meals"]!!.jsonArray.size)
        val profile = report["profile"]!!.jsonObject
        assertEquals(120, profile["protein_target_g"]!!.jsonPrimitive.int)
        assertTrue(profile["maintenance_kcal"]!!.jsonPrimitive.int > 0)
        assertEquals(0, profile["maintenance_adjustment_kcal"]!!.jsonPrimitive.int)
    }
}
