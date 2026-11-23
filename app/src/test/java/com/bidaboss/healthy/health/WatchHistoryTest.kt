package com.bidaboss.healthy.health

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.core.watch.SleepSegment
import com.bidaboss.healthy.core.watch.SleepStage
import com.bidaboss.healthy.health.data.SleepDetail
import com.bidaboss.healthy.health.data.WatchDay
import com.bidaboss.healthy.health.data.WatchHistory
import com.bidaboss.healthy.health.data.WatchIgnoredStore
import com.bidaboss.healthy.health.data.WatchRepository
import com.bidaboss.healthy.health.data.WatchSession
import com.bidaboss.healthy.health.data.WatchSource
import com.bidaboss.healthy.health.data.WatchStatus
import com.bidaboss.healthy.health.ui.TrendRange
import com.bidaboss.healthy.health.ui.WatchTrendsViewModel
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

/** Une montre qui rend ce qu'on lui a mis pour chaque jour, et compte les lectures. */
private class DaysWatch(var status: WatchStatus = WatchStatus.READY) : WatchSource {
    val days = mutableMapOf<LocalDate, WatchDay>()
    val reads = mutableListOf<LocalDate>()
    var failing = false
    var extras = true
    override suspend fun status() = status
    override suspend fun day(date: LocalDate): WatchDay {
        reads += date
        if (failing) error("boom")
        return days[date] ?: WatchDay(0, null)
    }
    override suspend fun sessions(from: LocalDate, to: LocalDate): List<WatchSession> = emptyList()
    override suspend fun extrasGranted() = extras
}

/** Historique des relevés de la montre et écran « Ma montre » (PRD du 23/11/2026), dans une vraie base Room en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WatchHistoryTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 23)
    private val watch = DaysWatch()
    private lateinit var database: HealthyDatabase
    private lateinit var logs: WorkoutLogRepository
    private lateinit var repository: WatchRepository

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
        repository = WatchRepository(watch, WatchIgnoredStore(context), WatchHistory(database.watchDayDao())) { 5_000L }
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun kept(from: LocalDate = today.minusDays(60), to: LocalDate = today) = repository.observeHistory(from, to).first()

    @Test
    fun `a la premiere connexion 30 jours sont recuperes, ensuite seulement les 3 derniers`() = runBlocking {
        (0..29).forEach { watch.days[today.minusDays(it.toLong())] = WatchDay(1000L + it, 100) }
        repository.syncHistory(today)
        assertEquals(30, watch.reads.size)
        assertEquals(30, kept().size)

        watch.reads.clear()
        repository.syncHistory(today)
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), watch.reads)
    }

    @Test
    fun `un jour sans rien n'ecrit pas de ligne de zeros, et des pas a zero restent un trou`() = runBlocking {
        watch.days[today] = WatchDay(0, null, sleepMin = 420)
        repository.syncHistory(today)
        val rows = kept()
        assertEquals(listOf(today), rows.map { it.date })
        assertNull(rows.single().steps)
        assertNull(rows.single().activeKcal)
        assertEquals(420, rows.single().sleepMin)
    }

    @Test
    fun `une synchronisation en retard corrige les 3 derniers jours, pas les plus anciens`() = runBlocking {
        (0..29).forEach { watch.days[today.minusDays(it.toLong())] = WatchDay(1000, 100) }
        repository.syncHistory(today)

        watch.days[today.minusDays(1)] = WatchDay(9000, 100)
        watch.days[today.minusDays(10)] = WatchDay(9000, 100)
        repository.syncHistory(today)

        val byDate = kept().associateBy { it.date }
        assertEquals(9000, byDate.getValue(today.minusDays(1)).steps)
        assertEquals(1000, byDate.getValue(today.minusDays(10)).steps)
    }

    @Test
    fun `la distance et le detail de la nuit sont gardes et relus, sans jamais effacer une mesure deja gardee`() = runBlocking {
        val start = LocalDateTime.of(2026, 11, 22, 23, 10)
        val night = SleepDetail(
            start, start.plusMinutes(450),
            listOf(SleepSegment(SleepStage.LIGHT, 45), SleepSegment(SleepStage.DEEP, 30)),
            nightAvgBpm = 52, nightMinBpm = 46,
        )
        watch.days[today] = WatchDay(5000, 200, sleepMin = 450, distanceM = 6200, sleep = night)
        repository.syncHistory(today)
        val row = kept().single()
        assertEquals(6200, row.distanceM)
        assertEquals(night, row.sleep)

        // Distance et sommeil non autorisés au prochain passage : l'historique garde ce qu'il a.
        watch.days[today] = WatchDay(6000, 250)
        repository.syncHistory(today)
        val after = kept().single()
        assertEquals(6000, after.steps)
        assertEquals(6200, after.distanceM)
        assertEquals(night, after.sleep)
    }

    @Test
    fun `une nuit sans phases garde ses heures et son coeur de nuit, sans frise`() = runBlocking {
        val start = LocalDateTime.of(2026, 11, 22, 23, 0)
        watch.days[today] = WatchDay(0, null, sleepMin = 400, sleep = SleepDetail(start, start.plusMinutes(420), nightAvgBpm = 55, nightMinBpm = 49))
        repository.syncHistory(today)
        val sleep = kept().single().sleep!!
        assertTrue(sleep.segments.isEmpty())
        assertEquals(55, sleep.nightAvgBpm)
    }

    @Test
    fun `une mesure absente ne remplace jamais une mesure deja gardee`() = runBlocking {
        watch.days[today] = WatchDay(5000, 200, sleepMin = 450, minBpm = 50, avgBpm = 65)
        repository.syncHistory(today)
        // Autorisation du sommeil et du cœur retirée : la montre ne rend plus que les pas.
        watch.days[today] = WatchDay(6000, 250)
        repository.syncHistory(today)
        val row = kept().single()
        assertEquals(6000, row.steps)
        assertEquals(450, row.sleepMin)
        assertEquals(50, row.minBpm)
        assertEquals(65, row.avgBpm)
    }

    @Test
    fun `une lecture qui echoue ou une montre non connectee n'ecrit rien et ne plante pas`() = runBlocking {
        watch.failing = true
        repository.syncHistory(today)
        assertTrue(kept().isEmpty())

        watch.failing = false
        watch.status = WatchStatus.NEEDS_PERMISSION
        watch.days[today] = WatchDay(5000, 200)
        repository.syncHistory(today)
        assertTrue(kept().isEmpty())
    }

    @Test
    fun `effacer l'historique ne touche pas aux activites`() = runBlocking {
        watch.days[today] = WatchDay(5000, 200)
        repository.syncHistory(today)
        logs.addActivity("Marche", 30, today, nowEpochMs = 1)
        assertEquals(1, repository.historyCount())

        repository.clearHistory()

        assertEquals(0, repository.historyCount())
        assertEquals(1, logs.observeActivityCount(today, today).first())
    }

    @Test
    fun `le resume des seances additionne plan et activites, nombre et minutes`() = runBlocking {
        logs.addActivity("Marche", 30, today, nowEpochMs = 1)
        logs.addActivity("Vélo", 45, today.minusDays(2), nowEpochMs = 2)
        logs.addActivity("Hors période", 60, today.minusDays(20), nowEpochMs = 3)
        val week = logs.observeSessionSummary(today.minusDays(6), today).first()
        assertEquals(2, week.count)
        assertEquals(75, week.minutes)
        assertEquals(3, logs.observeSessionSummary(today.minusDays(29), today).first().count)
    }

    private fun viewModel() = WatchTrendsViewModel(repository, logs::observeSessionSummary) { today }

    @Test
    fun `les tendances ont des trous, une periode precedente et gardent l'historique sans montre connectee`() = runBlocking {
        // Période courante : 3 jours avec des pas, le reste est un trou. Période précédente : un seul jour.
        listOf(0L to 8000L, 2L to 9000L, 4L to 7000L, 8L to 5000L).forEach { (back, steps) ->
            watch.days[today.minusDays(back)] = WatchDay(steps, null)
        }
        repository.syncHistory(today)

        val vm = viewModel()
        vm.onResume()
        val state = vm.uiState.first { it.status != null && it.days.any { d -> d != null } }

        assertEquals(TrendRange.WEEK, state.range)
        assertEquals(7, state.days.size)
        assertEquals(7, state.previousDays.size)
        assertEquals(3, state.days.count { it != null })
        assertNull(state.days[0])
        assertEquals(8000, state.days.last()?.steps)
        // La période précédente va de J-13 à J-7 : le jour J-8 est son avant-dernière case.
        assertEquals(5000, state.previousDays[5]?.steps)
        assertEquals(1, state.previousDays.count { it != null })

        // Autorisation retirée : l'historique reste, avec le message « lecture coupée ».
        watch.status = WatchStatus.NEEDS_PERMISSION
        val cut = viewModel()
        cut.onResume()
        val cutState = cut.uiState.first { it.status == WatchStatus.NEEDS_PERMISSION && it.days.any { d -> d != null } }
        assertTrue(cutState.readingCut)
        assertFalse(cutState.notConnected)
    }

    @Test
    fun `sans montre connectee ni historique, l'ecran propose de connecter`() = runBlocking {
        watch.status = WatchStatus.NEEDS_PERMISSION
        val vm = viewModel()
        vm.onResume()
        val state = vm.uiState.first { it.status != null }
        assertTrue(state.notConnected)
        assertFalse(state.readingCut)
    }

    @Test
    fun `le selecteur passe a 30 jours avec la periode precedente de meme duree`() = runBlocking {
        val vm = viewModel()
        vm.onResume()
        vm.selectRange(TrendRange.MONTH)
        val state = vm.uiState.first { it.range == TrendRange.MONTH && it.days.size == 30 }
        assertEquals(30, state.previousDays.size)
    }
}
