package com.bidaboss.healthy.health

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.health.data.WatchDay
import com.bidaboss.healthy.health.data.WatchHistory
import com.bidaboss.healthy.health.data.WatchIgnoredStore
import com.bidaboss.healthy.health.data.WatchRepository
import com.bidaboss.healthy.health.data.WatchSession
import com.bidaboss.healthy.health.data.WatchSource
import com.bidaboss.healthy.health.data.WatchStatus
import com.bidaboss.healthy.today.ui.WatchUiState
import com.bidaboss.healthy.today.ui.WatchViewModel
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import java.time.ZoneId
import java.util.concurrent.Executors

private class FakeWatch(
    var status: WatchStatus = WatchStatus.READY,
    var day: WatchDay = WatchDay(5000, 200),
    var sessions: List<WatchSession> = emptyList(),
    var failing: Boolean = false,
) : WatchSource {
    override suspend fun status() = if (failing) error("boom") else status
    override suspend fun day(date: LocalDate) = if (failing) error("boom") else day
    override suspend fun sessions(from: LocalDate, to: LocalDate) = if (failing) error("boom") else sessions
}

/** Lecture de la montre et préremplissage du journal, dans une vraie base Room en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WatchTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 22)
    private val fake = FakeWatch()

    private lateinit var database: HealthyDatabase
    private lateinit var logs: WorkoutLogRepository
    private lateinit var repository: WatchRepository

    private fun session(id: String, daysAgo: Int, minutes: Int = 30, type: String = "Marche") =
        WatchSession(id, type, today.minusDays(daysAgo.toLong()).atTime(10, 0), minutes)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("watch", 0).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        logs = WorkoutLogRepository(database.workoutLogDao(), RoomTransactionRunner(database)) { zone }
        repository = WatchRepository(fake, WatchIgnoredStore(context), WatchHistory(database.watchDayDao())) { 1_000L }
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `seules les seances d'au moins 10 minutes des 7 derniers jours sont gardees, les plus recentes d'abord`() = runBlocking {
        fake.sessions = listOf(
            session("vieille", daysAgo = 3),
            session("courte", daysAgo = 0, minutes = 9),
            session("recente", daysAgo = 0),
            session("limite", daysAgo = 1, minutes = 10),
        )
        val snapshot = repository.snapshot(today)!!
        assertEquals(listOf("recente", "limite", "vieille"), snapshot.sessions.map { it.id })
        assertEquals(5000L, snapshot.day.steps)
    }

    @Test
    fun `une seance ignoree n'est plus proposee`() = runBlocking {
        fake.sessions = listOf(session("a", 0), session("b", 1))
        repository.ignore("a")
        assertEquals(listOf("b"), repository.snapshot(today)!!.sessions.map { it.id })
    }

    @Test
    fun `une lecture qui echoue ne plante pas`() = runBlocking {
        fake.failing = true
        assertNull(repository.snapshot(today))
        assertEquals(WatchStatus.UNAVAILABLE, repository.status())
    }

    private fun viewModel() = WatchViewModel(repository, logs, today = { today }, now = { 1L })

    private suspend fun WatchViewModel.await(condition: (WatchUiState) -> Boolean): WatchUiState =
        withTimeout(10_000) { uiState.first(condition) }

    @Test
    fun `ajouter une seance cree l'activite avec son identifiant et elle n'est plus proposee`() = runBlocking {
        fake.sessions = listOf(session("a", 0, 45, "Vélo"), session("b", 1))
        val vm = viewModel()
        vm.onResume()
        val before = vm.await { it.proposals.size == 2 }
        assertTrue(before.visible)

        vm.add(before.proposals.first { it.id == "a" })
        val after = vm.await { it.proposals.size == 1 }
        assertEquals("b", after.proposals.single().id)

        val activity = logs.observeRecentActivities().first().single()
        assertEquals("Vélo", activity.type)
        assertEquals(45, activity.durationMin)
    }

    @Test
    fun `ajouter deux fois la meme seance ne la double pas`() = runBlocking {
        val s = session("a", 0)
        assertTrue(logs.addActivity(s.type, s.durationMin, s.date, 1L, healthId = s.id))
        assertFalse(logs.addActivity(s.type, s.durationMin, s.date, 2L, healthId = s.id))
        assertEquals(1, logs.observeRecentActivities().first().size)
    }

    @Test
    fun `sans autorisation la carte reste cachee`() = runBlocking {
        fake.status = WatchStatus.NEEDS_PERMISSION
        val vm = viewModel()
        vm.onResume()
        // Laisse le chargement se faire, puis vérifie que rien n'est montré.
        kotlinx.coroutines.delay(300)
        assertFalse(vm.uiState.first().visible)
    }

    @Test
    fun `la journee n'est vide que sans pas, calories, sommeil ni coeur`() {
        assertTrue(WatchDay(0, null).isEmpty)
        assertFalse(WatchDay(0, null, sleepMin = 420).isEmpty)
        assertFalse(WatchDay(0, null, minBpm = 50, avgBpm = 70).isEmpty)
        assertFalse(WatchDay(0, null, minBpm = 50).hasHeart)
    }

    @Test
    fun `quand la liste des seances ignorees est pleine, ce sont les plus anciennes qui partent`() {
        val store = WatchIgnoredStore(ApplicationProvider.getApplicationContext<Application>())
        (1..205).forEach { store.add("id$it") }
        val ids = store.ids()
        assertEquals(200, ids.size)
        assertFalse("id5" in ids)
        assertTrue("id6" in ids && "id205" in ids)
    }
}
