package com.bidaboss.healthy.today

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.today.ui.WaterViewModel
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Supprimer un ajout d'eau propose « Annuler » : l'ajout revient avec son heure d'origine. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WaterDeleteUndoTest {
    private val day = LocalDate.of(2026, 11, 23)
    private lateinit var database: HealthyDatabase
    private lateinit var body: BodyRepository
    private lateinit var viewModel: WaterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        body = BodyRepository(database.bodyDao())
        viewModel = WaterViewModel(body, WorkoutLogRepository(database.workoutLogDao(), RoomTransactionRunner(database))) { day }
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `supprimer puis annuler remet l'ajout avec la meme heure`() = runBlocking {
        body.addWater(day, 250, nowEpochMs = 1_000)
        body.addWater(day, 150, nowEpochMs = 2_000)
        val before = viewModel.uiState.first { it.todayIntakes.size == 2 && it.todayMl == 400 }
        val target = before.todayIntakes.last()

        viewModel.delete(target)
        val after = viewModel.uiState.first { it.lastDeleted != null && it.todayIntakes.size == 1 && it.todayMl == 150 }
        assertEquals(1, after.todayIntakes.size)
        assertEquals(150, after.todayMl)

        viewModel.restoreDeleted()
        val restored = viewModel.uiState.first { it.lastDeleted == null && it.todayIntakes.size == 2 && it.todayMl == 400 }
        assertNull(restored.lastDeleted)
        assertEquals(400, restored.todayMl)
        assertEquals(listOf(2_000L, 1_000L), restored.todayIntakes.map { it.addedAtEpochMs })
    }

    @Test
    fun `sans annuler le message passe et l'ajout reste supprime`() = runBlocking {
        body.addWater(day, 250, nowEpochMs = 1_000)
        viewModel.delete(viewModel.uiState.first { it.todayIntakes.size == 1 && it.todayMl == 250 }.todayIntakes.single())
        viewModel.uiState.first { it.lastDeleted != null && it.todayIntakes.isEmpty() && it.todayMl == 0 }
        viewModel.deletedMessageShown()
        val state = viewModel.uiState.first { it.lastDeleted == null && it.todayMl == 0 }
        assertEquals(0, state.todayMl)
        viewModel.restoreDeleted()
        assertEquals(0, viewModel.uiState.first { it.lastDeleted == null && it.todayIntakes.isEmpty() }.todayMl)
    }
}
