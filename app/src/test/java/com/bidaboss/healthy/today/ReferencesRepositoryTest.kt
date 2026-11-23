package com.bidaboss.healthy.today

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.BodyProfile
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.core.reference.ReferenceId
import com.bidaboss.healthy.core.reference.ReferencePosition
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.health.data.HealthConnectSource
import com.bidaboss.healthy.health.data.WatchHistory
import com.bidaboss.healthy.health.data.WatchIgnoredStore
import com.bidaboss.healthy.health.data.WatchRepository
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.today.data.ReferencesRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
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
import java.time.LocalDateTime
import java.time.LocalTime

/** Repères : les repas, l'eau, les séances et le profil de l'app mis face à la table, sur 7 et 28 jours. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReferencesRepositoryTest {
    private val today = LocalDate.of(2026, 11, 23)
    private lateinit var db: HealthyDatabase
    private lateinit var body: BodyRepository
    private lateinit var meals: MealRepository
    private lateinit var repo: ReferencesRepository
    private val profile = BodyProfile(Sex.MALE, LocalDate.of(1988, 7, 15), 176.0, 75.0, ActivityLevel.ACTIVE, firstName = "Alex")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, HealthyDatabase::class.java).allowMainThreadQueries().build()
        val transactions = RoomTransactionRunner(db)
        body = BodyRepository(db.bodyDao())
        meals = MealRepository(db.mealDao(), transactions)
        val watch = WatchRepository(HealthConnectSource(context), WatchIgnoredStore(context), WatchHistory(db.watchDayDao()))
        repo = ReferencesRepository(meals, body, WorkoutLogRepository(db.workoutLogDao(), transactions), watch)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun eat(day: LocalDate, kcal: Double, protein: Double? = 100.0, fiber: Double? = 25.0, carbs: Double? = 300.0, fat: Double? = 90.0) {
        meals.saveMeal(
            MealDraft(null, MealType.LUNCH, LocalDateTime.of(day, LocalTime.NOON), null, null, listOf(Food("repas", kcal = kcal, proteinG = protein, fiberG = fiber, carbsG = carbs, fatG = fat))),
        )
    }

    @Test
    fun `sans rien de note il n'y a aucune ligne, jamais de zero`() = runBlocking {
        val report = repo.observe(today, 28).first()
        assertTrue(report.lines.isEmpty())
        assertTrue(report.profileMissing)
        assertTrue(report.indicative)
    }

    @Test
    fun `les proteines et les fibres des repas sont placees face aux objectifs de l'app`() = runBlocking {
        body.saveProfile(profile)
        (0 until 7).forEach { eat(today.minusDays(it.toLong()), 2600.0, protein = 120.0, fiber = 21.0) }
        val report = repo.observe(today, 7).first()
        val protein = report.lines.first { it.reference.id == ReferenceId.PROTEIN }
        assertEquals(1.6, protein.value, 0.01)
        assertEquals(ReferencePosition.IN, protein.position)
        assertEquals(ReferencePosition.IN, protein.appGoal!!.position)
        val fiber = report.lines.first { it.reference.id == ReferenceId.FIBER }
        // 21 g : sous le repère EFSA (25 g) comme sous l'objectif de l'app (30 g).
        assertEquals(ReferencePosition.BELOW, fiber.position)
        assertEquals(ReferencePosition.BELOW, fiber.appGoal!!.position)
        assertFalse(report.indicative)
        assertFalse(report.profileMissing)
    }

    @Test
    fun `un jour avec un repas en calories seules ne compte pas pour les macros`() = runBlocking {
        body.saveProfile(profile)
        eat(today, 2600.0, protein = 120.0, fiber = 30.0)
        eat(today.minusDays(1), 2600.0, protein = null, fiber = null, carbs = null, fat = null)
        val report = repo.observe(today, 7).first()
        // Seul le jour complet compte : 120 g / 75 kg.
        assertEquals(1.6, report.lines.first { it.reference.id == ReferenceId.PROTEIN }.value, 0.01)
        assertEquals(30.0, report.lines.first { it.reference.id == ReferenceId.FIBER }.value, 0.01)
    }

    @Test
    fun `sans profil les calories et les proteines sont masquees mais les fibres restent`() = runBlocking {
        (0 until 7).forEach { eat(today.minusDays(it.toLong()), 2600.0) }
        val ids = repo.observe(today, 7).first().lines.map { it.reference.id }
        assertTrue(ReferenceId.FIBER in ids)
        assertNull(ids.firstOrNull { it == ReferenceId.PROTEIN })
        assertNull(ids.firstOrNull { it == ReferenceId.CALORIES })
    }
}
