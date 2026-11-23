package com.bidaboss.healthy.today

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.importexport.data.MissingProfileException
import com.bidaboss.healthy.importexport.data.PromptRepository
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.today.ui.TodayViewModel
import com.bidaboss.healthy.today.ui.WaterViewModel
import com.bidaboss.healthy.workout.data.SportProfileRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

/**
 * Sans profil (« J'ai une sauvegarde » touché puis import abandonné) : rien ne plante, les écrans
 * attendent le profil et les prompts personnels le demandent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NoProfileTest {
    private val day = LocalDate.of(2026, 11, 11)
    private lateinit var database: HealthyDatabase
    private lateinit var body: BodyRepository
    private lateinit var workout: WorkoutRepository
    private lateinit var logs: WorkoutLogRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        body = BodyRepository(database.bodyDao())
        workout = WorkoutRepository(database.workoutDao())
        logs = WorkoutLogRepository(database.workoutLogDao(), RoomTransactionRunner(database))
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `une activite ajoutee a la main apparait sur aujourd'hui, pas celle d'hier`() = runBlocking {
        logs.addActivity("Kiné", 30, day, nowEpochMs = 1)
        logs.addActivity("Marche", 20, day.minusDays(1), nowEpochMs = 2)
        val viewModel = TodayViewModel(MealRepository(database.mealDao(), RoomTransactionRunner(database)), workout, body, logs) { day }
        val state = viewModel.uiState.first { it.todayActivities.isNotEmpty() }
        assertEquals(listOf("Kiné"), state.todayActivities.map { it.type })
    }

    @Test
    fun `aujourd'hui sans maintien ni couleur`() = runBlocking {
        val viewModel = TodayViewModel(MealRepository(database.mealDao(), RoomTransactionRunner(database)), workout, body, logs) { day }
        val state = viewModel.uiState.first { it.lastDays.isNotEmpty() }
        assertNull(state.maintenanceKcal)
        assertNull(state.todayBand)
        assertNull(state.proteinTargetG)
    }

    @Test
    fun `les fibres du jour s'additionnent meme sans profil`() = runBlocking {
        val meals = MealRepository(database.mealDao(), RoomTransactionRunner(database))
        meals.saveMeal(
            MealDraft(
                null, MealType.LUNCH, day.atTime(12, 0), null, null,
                listOf(Food("Lentilles", kcal = 230.0, fiberG = 8.0), Food("Pomme", kcal = 80.0, fiberG = 4.5), Food("Riz", kcal = 200.0)),
            ),
        )
        val viewModel = TodayViewModel(meals, workout, body, logs) { day }
        assertEquals(12.5, viewModel.uiState.first { it.mealCount == 1 }.fiberG, 0.001)
    }

    @Test
    fun `eau sans objectif mais les ajouts marchent`() = runBlocking {
        val viewModel = WaterViewModel(body, logs) { day }
        assertNull(viewModel.uiState.first { it.lastDays.isNotEmpty() }.targetMl)
        assertTrue(body.addWater(day, 250))
        val state = viewModel.uiState.first { it.todayMl == 250 }
        assertNull(state.targetMl)
    }

    @Test
    fun `prompt repas copie, prompts personnels demandent le profil`() = runBlocking {
        val prompts = PromptRepository(
            ApplicationProvider.getApplicationContext(),
            body,
            SportProfileRepository(database.sportProfileDao()),
            workout,
            logs,
        )
        assertTrue(prompts.mealPrompt().isNotBlank())
        assertEquals(MissingProfileException::class, runCatching { prompts.sportPrompt(day) }.exceptionOrNull()?.let { it::class })
        assertEquals(MissingProfileException::class, runCatching { prompts.adjustmentPrompt(day) }.exceptionOrNull()?.let { it::class })
    }
}
