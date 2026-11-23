package com.bidaboss.healthy.today

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
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.today.data.WeeklyRecapRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/** Bilan de la semaine lu dans une vraie base Room en mémoire. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WeeklyRecapRepositoryTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val monday = LocalDate.of(2026, 11, 9)

    private lateinit var database: HealthyDatabase
    private lateinit var meals: MealRepository
    private lateinit var body: BodyRepository
    private lateinit var logs: WorkoutLogRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        meals = MealRepository(database.mealDao(), RoomTransactionRunner(database)) { zone }
        body = BodyRepository(database.bodyDao())
        logs = WorkoutLogRepository(database.workoutLogDao(), RoomTransactionRunner(database)) { zone }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun meal(day: LocalDate, kcal: Double, fiberG: Double) = meals.saveMeal(
        MealDraft(null, MealType.LUNCH, day.atTime(12, 0), null, null, listOf(Food("Plat", kcal = kcal, fiberG = fiberG))),
    )

    @Test
    fun `le bilan compte les jours notes, les fibres et le sport de la semaine seulement`() = runBlocking {
        meal(monday, 1800.0, 32.0)
        meal(monday.plusDays(2), 2200.0, 10.0)
        meal(monday.minusDays(1), 5000.0, 50.0) // semaine d'avant
        logs.addActivity("Marche", 45, monday.plusDays(3), nowEpochMs = 1)
        logs.addActivity("Vélo", 60, monday.plusDays(7), nowEpochMs = 2) // semaine d'après
        body.saveProfile(BodyProfile(Sex.MALE, LocalDate.of(1988, 7, 15), 176.0, 75.0, ActivityLevel.LIGHT))

        val recap = WeeklyRecapRepository(meals, body, logs).recap(monday)

        assertEquals(2, recap.mealDays)
        assertEquals(2000.0, recap.averageKcal!!, 0.01)
        assertEquals(1, recap.fiberDays)
        assertEquals(1, recap.activities)
        assertEquals(0, recap.sessions)
        assertNotNull(recap.maintenanceKcal)
        assertNotNull(recap.band)
    }

    @Test
    fun `sans profil ni repas le bilan reste vide sans planter`() = runBlocking {
        val recap = WeeklyRecapRepository(meals, body, logs).recap(monday)

        assertEquals(0, recap.mealDays)
        assertEquals(null, recap.averageKcal)
        assertEquals(null, recap.maintenanceKcal)
    }
}
