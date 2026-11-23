package com.bidaboss.healthy.today

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.Band
import com.bidaboss.healthy.core.body.BodyProfile
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.meals.data.Drink
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.today.data.SessionStatus
import com.bidaboss.healthy.today.data.TodaySnapshotRepository
import com.bidaboss.healthy.ui.formatThousands
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.Executors

/** État de la journée montré par le widget, lu dans une vraie base Room en mémoire. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TodaySnapshotRepositoryTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val day = LocalDate.of(2026, 11, 13)

    private lateinit var database: HealthyDatabase
    private lateinit var meals: MealRepository
    private lateinit var body: BodyRepository
    private lateinit var logs: WorkoutLogRepository
    private lateinit var snapshots: TodaySnapshotRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        val transactions = RoomTransactionRunner(database)
        meals = MealRepository(database.mealDao(), transactions) { zone }
        body = BodyRepository(database.bodyDao())
        logs = WorkoutLogRepository(database.workoutLogDao(), transactions) { zone }
        snapshots = TodaySnapshotRepository(meals, body, WorkoutRepository(database.workoutDao()), logs)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `sans profil ni rien de note, la journee est vide et vise le minimum d'eau`() = runBlocking {
        val s = snapshots.snapshot(day)
        assertEquals(0, s.kcal)
        assertNull(s.maintenanceKcal)
        assertEquals(0, s.waterMl)
        assertEquals(Hydration.MIN_ML, s.waterTargetMl)
        assertEquals(SessionStatus.Rest, s.session)
        assertNull(s.lastMeal)
    }

    @Test
    fun `repas, boisson, eau et activite du jour sont comptes, pas ceux d'hier`() = runBlocking {
        meals.saveMeal(MealDraft(null, MealType.LUNCH, day.atTime(12, 30), null, null, listOf(Food("Plat", kcal = 600.0, proteinG = 30.0))))
        meals.saveMeal(MealDraft(null, MealType.DINNER, day.minusDays(1).atTime(20, 0), null, null, listOf(Food("Hier", kcal = 900.0))))
        meals.addDrink(Drink.SODA, day.atTime(13, 0)) // rejoint le déjeuner en cours
        body.addWater(day, Hydration.GLASS_ML)
        logs.addActivity("Marche", 30, day, nowEpochMs = 1)

        val s = snapshots.snapshot(day)

        assertEquals(740, s.kcal)
        assertEquals(30, s.proteinG)
        // Le soda est une boisson : il compte dans l'eau avec le verre ajouté.
        assertEquals(Hydration.GLASS_ML + Drink.SODA.volumeMl, s.waterMl)
        assertEquals(SessionStatus.Done, s.session)
        assertEquals("Déjeuner", s.lastMeal?.label)
        assertEquals(LocalTime.of(12, 30), s.lastMeal?.at)
    }

    @Test
    fun `un depassement net colore l'anneau, sinon il reste neutre`() = runBlocking {
        body.saveProfile(BodyProfile(Sex.MALE, LocalDate.of(1988, 7, 15), 176.0, 75.0, ActivityLevel.LIGHT))
        meals.saveMeal(MealDraft(null, MealType.LUNCH, day.atTime(12, 0), null, null, listOf(Food("Plat", kcal = 1200.0))))
        assertNull(snapshots.snapshot(day).band)

        meals.saveMeal(MealDraft(null, MealType.DINNER, day.atTime(20, 0), null, null, listOf(Food("Gros plat", kcal = 3000.0))))
        assertEquals(Band.RED, snapshots.snapshot(day).band)
    }

    @Test
    fun `les calories du widget ont l'espace des milliers`() {
        assertEquals("3 065", formatThousands(3065))
        assertEquals("980", formatThousands(980))
    }
}
