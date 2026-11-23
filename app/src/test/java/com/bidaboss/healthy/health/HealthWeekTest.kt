package com.bidaboss.healthy.health

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.core.progress.Badge
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.health.ui.HealthSection
import com.bidaboss.healthy.health.ui.HealthUiState
import com.bidaboss.healthy.health.data.WatchStatus
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.today.data.HealthWeekRepository
import com.bidaboss.healthy.today.data.MeProgressRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

/** Les 7 jours de Santé, la progression de Moi et les parties visibles de Santé (refonte, lot 4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HealthWeekTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 23)
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

    private suspend fun meal(day: LocalDate, kcal: Double) = meals.saveMeal(
        MealDraft(null, MealType.LUNCH, day.atTime(12, 0), null, null, listOf(Food("Plat", kcal = kcal))),
    )

    @Test
    fun `les 7 jours finissent aujourd'hui, un jour sans rien note est un trou et pas un zero`() = runBlocking {
        meal(today, 600.0)
        meal(today, 500.0)
        meal(today.minusDays(6), 1800.0)
        meal(today.minusDays(7), 9999.0) // hors des 7 jours
        body.addWater(today.minusDays(1), 500, 1)
        logs.addActivity("Marche", 40, today.minusDays(2), nowEpochMs = 2)

        val week = HealthWeekRepository(meals, body, logs).observe(today).first()

        assertEquals(7, week.size)
        assertEquals(today.minusDays(6), week.first().date)
        assertEquals(today, week.last().date)
        assertEquals(1800, week.first().kcal)
        assertEquals(1100, week.last().kcal)
        assertNull(week[1].kcal)
        assertNull(week[1].waterMl)
        assertEquals(500, week[5].waterMl)
        assertEquals(1, week[4].sessions)
        assertEquals(40, week[4].sportMinutes)
        assertEquals(0, week[3].sessions)
    }

    @Test
    fun `sans rien de note la progression de Moi est vide mais complete`() = runBlocking {
        val progress = MeProgressRepository(meals, logs) { today }.observe().first()
        assertEquals(0, progress.unlockedBadges)
        assertEquals(Badge.entries.size, progress.totalBadges)
        assertEquals(0, progress.streakWeeks)
        assertEquals(Badge.FIRST_MEAL.title, progress.nextBadgeTitle)
    }

    @Test
    fun `un repas debloque le premier badge`() = runBlocking {
        meal(today, 500.0)
        val progress = MeProgressRepository(meals, logs) { today }.observe().first()
        assertEquals(listOf(Badge.FIRST_MEAL.title), progress.unlockedTitles)
        assertEquals(Badge.MEAL_WEEK.title, progress.nextBadgeTitle)
        assertEquals("1 sur 7", progress.nextBadgeProgress)
    }

    @Test
    fun `sans montre connectee Sommeil et Montre disparaissent`() {
        val none = HealthUiState(status = WatchStatus.NEEDS_PERMISSION, hasHistory = false)
        assertEquals(listOf(HealthSection.WEEK, HealthSection.RECAP), none.sections)

        val connected = HealthUiState(status = WatchStatus.READY)
        assertEquals(HealthSection.entries, connected.sections)

        // Autorisation retirée mais historique gardé : la montre compte encore comme connectée.
        val cut = HealthUiState(status = WatchStatus.NEEDS_PERMISSION, hasHistory = true)
        assertTrue(HealthSection.SLEEP in cut.sections && HealthSection.WATCH in cut.sections)
    }
}
