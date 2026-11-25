package com.bidaboss.healthy.workout

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.route.RouteDirection
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.route.SavedRouteRepository
import com.bidaboss.healthy.workout.ui.RouteEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.Executors

/** Un nouveau trajet à vélo avec distance compte comme une activité sportive du jour. Adresses fictives. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RouteEditorActivityTest {
    private lateinit var database: HealthyDatabase
    private lateinit var routes: SavedRouteRepository
    private lateinit var logs: WorkoutLogRepository
    private val day = LocalDate.of(2026, 11, 25)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        val transactions = RoomTransactionRunner(database)
        routes = SavedRouteRepository(database.savedRouteDao(), transactions)
        logs = WorkoutLogRepository(database.workoutLogDao(), transactions)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun editor(routeId: Long?) = RouteEditorViewModel(routes, routeId, now = { 1_000L }, logRepository = logs, today = { day })

    @Test
    fun `un nouveau trajet avec distance ajoute une sortie a velo aller-retour du jour`() = runBlocking {
        val vm = editor(null)
        vm.onName("Boulot")
        vm.onStart("1 rue A")
        vm.onEnd("2 rue B")
        vm.onManualDistance("7,5")
        vm.save()
        val activities = logs.observeRecentActivities().first()
        assertEquals(1, activities.size)
        val a = activities.single()
        assertEquals("Vélo", a.type)
        assertEquals(day, a.date)
        assertEquals(15.0, a.distanceKm!!, 0.001)
        assertEquals(RouteDirection.ROUND_TRIP, a.routeDirection)
        assertEquals(60, a.durationMin)
        assertTrue(logs.observeSessionSummary(day, day).first().minutes >= 60)
    }

    @Test
    fun `sans distance ou en modification, aucune activite n'est ajoutee`() = runBlocking {
        val noDistance = editor(null)
        noDistance.onName("Sans distance")
        noDistance.onStart("1 rue A")
        noDistance.onEnd("2 rue B")
        noDistance.save()
        assertTrue(logs.observeRecentActivities().first().isEmpty())

        val id = routes.save(null, "Existant", "1 rue A", emptyList(), "2 rue B", calculatedKm = null, manualKm = 5.0, nowEpochMs = 1L)!!
        val edit = editor(id)
        edit.onManualDistance("6")
        edit.save()
        assertTrue(logs.observeRecentActivities().first().isEmpty())
    }
}
