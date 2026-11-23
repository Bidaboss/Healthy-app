package com.bidaboss.healthy.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.workout.RunStep
import com.bidaboss.healthy.data.TransactionRunner
import com.bidaboss.healthy.workout.data.ActivityEntity
import com.bidaboss.healthy.workout.data.Feeling
import com.bidaboss.healthy.workout.data.LogWithExercises
import com.bidaboss.healthy.workout.data.PlanWithSessions
import com.bidaboss.healthy.workout.data.SessionWithVariants
import com.bidaboss.healthy.workout.data.VariantWithExercises
import com.bidaboss.healthy.workout.data.WorkoutDao
import com.bidaboss.healthy.workout.data.WorkoutExerciseEntity
import com.bidaboss.healthy.workout.data.WorkoutLogDao
import com.bidaboss.healthy.workout.data.WorkoutLogEntity
import com.bidaboss.healthy.workout.data.WorkoutLogExerciseEntity
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutPlanEntity
import com.bidaboss.healthy.workout.data.WorkoutRepository
import com.bidaboss.healthy.workout.data.WorkoutSessionEntity
import com.bidaboss.healthy.workout.data.WorkoutSessionVariantEntity
import com.bidaboss.healthy.workout.ui.SessionRunViewModel
import com.bidaboss.healthy.workout.ui.TimerCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Séance guidée : reprise après fermeture de l'app par Android (même SavedStateHandle, nouveau ViewModel).
 * Les horloges sont fausses et pilotées par le test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionRunViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var wallClock = 1_000_000L
    private var elapsed = 50_000L
    private val logDao = FakeLogDao()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Pompes : 2 séries de 10, 60 s de repos. Planche : 1 série de 40 s. Pas d'échauffement.
    private val plan = PlanWithSessions(
        plan = WorkoutPlanEntity(1, "import", "Plan test", null, null, true, 0),
        sessions = listOf(
            SessionWithVariants(
                session = WorkoutSessionEntity(SESSION_ID, 1, 0, "Haut du corps", null, null),
                variants = listOf(
                    VariantWithExercises(
                        variant = WorkoutSessionVariantEntity(1, SESSION_ID, 30, null, null),
                        exercises = listOf(
                            WorkoutExerciseEntity(1, 1, 0, "Pompes", 2, 10, null, 60, null, null, null, null),
                            WorkoutExerciseEntity(2, 1, 1, "Planche", 1, null, 40, null, null, null, null, null),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun newViewModel(savedState: SavedStateHandle): SessionRunViewModel {
        val viewModel = SessionRunViewModel(
            workoutRepository = WorkoutRepository(FakeWorkoutDao(plan)),
            logRepository = WorkoutLogRepository(logDao, ImmediateTransactions) { ZoneId.of("Europe/Paris") },
            sessionId = SESSION_ID,
            durationMin = 30,
            savedState = savedState,
            now = { wallClock },
            elapsedMs = { elapsed },
        )
        dispatcher.scheduler.runCurrent()
        return viewModel
    }

    /** Android détruit le ViewModel avec le processus : plus rien de lui ne doit tourner ensuite. */
    private fun kill(viewModel: SessionRunViewModel) = viewModel.viewModelScope.cancel()

    /** Fait avancer les deux horloges (celle du chrono et celle des coroutines) du même temps. */
    private fun advance(ms: Long) {
        elapsed += ms
        wallClock += ms
        dispatcher.scheduler.advanceTimeBy(ms)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `apres fermeture de l'app, la seance reprend a la meme etape avec les series faites`() {
        val savedState = SavedStateHandle()
        val first = newViewModel(savedState)
        assertEquals(RunStep.Work(0, 0), first.uiState.value.step)

        first.completeSet() // 10 pompes, puis repos
        first.next() // repos passé à la main
        first.adjustDoneReps(-2)
        first.completeSet() // 8 pompes, puis repos avant la planche
        assertEquals(RunStep.Rest(60, RunStep.Work(1, 0)), first.uiState.value.step)
        advance(20_000)
        assertEquals(40, first.uiState.value.secondsLeft)

        // Android ferme l'app ; 15 s plus tard l'utilisateur revient.
        kill(first)
        wallClock += 15_000
        elapsed += 15_000
        val second = newViewModel(savedState)

        assertEquals(RunStep.Rest(60, RunStep.Work(1, 0)), second.uiState.value.step)
        assertEquals(25, second.uiState.value.secondsLeft)
        assertEquals("Planche", second.uiState.value.exercise?.name)

        // On va au bout et on enregistre : les séries d'avant la fermeture et l'heure de début sont gardées.
        second.next()
        second.completeSet()
        assertEquals(RunStep.Feedback, second.uiState.value.step)
        second.save()
        dispatcher.scheduler.runCurrent()

        assertTrue(second.uiState.value.finished)
        val log = logDao.logs.single()
        assertEquals(1_000_000L, log.startedAtEpochMs)
        assertEquals(listOf("10,8", "40"), logDao.exercises.map { it.doneValues })
    }

    @Test
    fun `un repos deja termine au retour passe tout seul a la serie suivante`() {
        val savedState = SavedStateHandle()
        val first = newViewModel(savedState)
        first.completeSet()
        assertEquals(RunStep.Rest(60, RunStep.Work(0, 1)), first.uiState.value.step)

        // Retour dans l'app bien après la fin du repos.
        kill(first)
        wallClock += 300_000
        elapsed += 300_000
        val second = newViewModel(savedState)

        assertEquals(RunStep.Work(0, 1), second.uiState.value.step)
        assertFalse(second.uiState.value.timerRunning)
    }

    @Test
    fun `le repos se termine tout seul et le chrono ne derive pas`() {
        val viewModel = newViewModel(SavedStateHandle())
        viewModel.completeSet()
        advance(59_000)
        assertEquals(1, viewModel.uiState.value.secondsLeft)
        assertTrue(viewModel.uiState.value.step is RunStep.Rest)
        advance(1_000)
        assertEquals(RunStep.Work(0, 1), viewModel.uiState.value.step)
    }

    @Test
    fun `le chrono du repos signale 3, 2, 1 puis la fin, une seule fois chacun`() {
        val viewModel = newViewModel(SavedStateHandle())
        val cues = mutableListOf<TimerCue>()
        val collector = viewModel.viewModelScope.launch { viewModel.cues.collect { cues += it } }
        dispatcher.scheduler.runCurrent()
        viewModel.completeSet()
        advance(56_000)
        assertTrue(cues.isEmpty())
        repeat(16) { advance(250) }
        assertEquals(listOf(TimerCue.COUNTDOWN, TimerCue.COUNTDOWN, TimerCue.COUNTDOWN, TimerCue.END), cues)
        collector.cancel()
    }

    @Test
    fun `le ressenti deja saisi revient apres fermeture de l'app`() {
        val savedState = SavedStateHandle()
        val first = newViewModel(savedState)
        first.completeSet()
        first.finishNow()
        first.setFeeling(Feeling.TOO_HARD)
        first.setHasPain(true)
        first.setPainZone("genou droit")
        kill(first)

        val second = newViewModel(savedState)

        assertEquals(RunStep.Feedback, second.uiState.value.step)
        assertEquals(Feeling.TOO_HARD, second.uiState.value.feeling)
        assertTrue(second.uiState.value.hasPain)
        assertEquals("genou droit", second.uiState.value.painZone)
    }

    @Test
    fun `une seance enregistree ne revient pas a la prochaine ouverture`() {
        val savedState = SavedStateHandle()
        val first = newViewModel(savedState)
        first.completeSet()
        first.finishNow()
        first.save()
        dispatcher.scheduler.runCurrent()
        assertTrue(first.uiState.value.finished)
        kill(first)

        val second = newViewModel(savedState)

        assertEquals(RunStep.Work(0, 0), second.uiState.value.step)
        assertFalse(second.hasDoneSomething())
    }

    private companion object {
        const val SESSION_ID = 7L
    }
}

private object ImmediateTransactions : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}

private class FakeWorkoutDao(private val plan: PlanWithSessions?) : WorkoutDao {
    override suspend fun insertPlan(plan: WorkoutPlanEntity): Long = error("inutile ici")
    override suspend fun insertSession(session: WorkoutSessionEntity): Long = error("inutile ici")
    override suspend fun insertVariant(variant: WorkoutSessionVariantEntity): Long = error("inutile ici")
    override suspend fun insertExercises(exercises: List<WorkoutExerciseEntity>): Unit = error("inutile ici")
    override suspend fun archiveActivePlans(): Unit = error("inutile ici")
    override fun observeActivePlan(): Flow<PlanWithSessions?> = flowOf(plan)
}

private class FakeLogDao : WorkoutLogDao {
    val logs = mutableListOf<WorkoutLogEntity>()
    val exercises = mutableListOf<WorkoutLogExerciseEntity>()

    override suspend fun insertLog(log: WorkoutLogEntity): Long {
        logs += log
        return logs.size.toLong()
    }

    override suspend fun insertExercises(exercises: List<WorkoutLogExerciseEntity>) {
        this.exercises += exercises
    }

    override fun observeHealthIds(fromDate: String): Flow<List<String>> = flowOf(emptyList())
    override fun observeRecentLogs(limit: Int): Flow<List<LogWithExercises>> = flowOf(emptyList())
    override fun observeLogCount(fromDate: String, toDate: String): Flow<Int> = flowOf(0)
    override fun observeLogMinutes(fromDate: String, toDate: String): Flow<Int> = flowOf(0)
    override fun observeActivityMinutes(fromDate: String, toDate: String): Flow<Int> = flowOf(0)
    override fun observeLogDates(): Flow<List<String>> = flowOf(emptyList())
    override fun observeActiveDates(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun getLogsBetween(fromDate: String, toDate: String): List<LogWithExercises> = emptyList()
    override suspend fun getActivitiesBetween(fromDate: String, toDate: String): List<ActivityEntity> = emptyList()
    override suspend fun insertActivity(activity: ActivityEntity): Long = error("inutile ici")
    override fun observeRecentActivities(limit: Int): Flow<List<ActivityEntity>> = flowOf(emptyList())
    override fun observeActivityCount(fromDate: String, toDate: String): Flow<Int> = flowOf(0)
    override suspend fun deleteActivity(id: Long): Unit = error("inutile ici")
    override suspend fun updateActivity(id: Long, localDate: String, type: String, durationMin: Int, bodyParts: String?): Int =
        error("inutile ici")
}
