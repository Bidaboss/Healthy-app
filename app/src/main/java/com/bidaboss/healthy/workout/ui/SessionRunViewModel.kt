package com.bidaboss.healthy.workout.ui

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.core.workout.PlannedExercise
import com.bidaboss.healthy.core.workout.RunStep
import com.bidaboss.healthy.core.workout.SessionRun
import com.bidaboss.healthy.core.workout.SessionRunState
import com.bidaboss.healthy.workout.data.Feeling
import com.bidaboss.healthy.workout.data.FinishedSession
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Signal du chrono : 3, 2, 1 puis la fin (vibration et bip court, fil Design A2). */
enum class TimerCue { COUNTDOWN, END }

data class SessionRunUiState(
    val loading: Boolean = true,
    /** La séance demandée n'existe plus (plan remplacé entre-temps). */
    val notFound: Boolean = false,
    val sessionTitle: String = "",
    val durationMin: Int = 0,
    val step: RunStep? = null,
    /** Exercice de la série en cours, ou de la série qui suit le repos. */
    val exercise: PlannedExercise? = null,
    val exerciseCount: Int = 0,
    /** Répétitions réellement faites pour la série en cours (modifiable avec − et +). */
    val doneReps: Int = 0,
    /** Compte à rebours en secondes (repos ou exercice chronométré), ou null. */
    val secondsLeft: Int? = null,
    val timerRunning: Boolean = false,
    val feeling: Feeling? = null,
    val hasPain: Boolean = false,
    val painZone: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
)

class SessionRunViewModel(
    private val workoutRepository: WorkoutRepository,
    private val logRepository: WorkoutLogRepository,
    private val sessionId: Long,
    private val durationMin: Int,
    /**
     * Garde la séance en cours si Android ferme l'app en arrière-plan (appel, manque de mémoire) :
     * au retour, on reprend à la même étape avec les séries déjà faites.
     */
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val now: () -> Long = { System.currentTimeMillis() },
    /** Horloge des comptes à rebours : continue de tourner écran éteint, insensible au changement d'heure. */
    private val elapsedMs: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionRunUiState())
    val uiState: StateFlow<SessionRunUiState> = _uiState.asStateFlow()

    private var run: SessionRun? = null
    private var runState: SessionRunState? = null
    private var planName = ""
    private var startedAt = 0L
    private var timerJob: Job? = null

    private val _cues = MutableSharedFlow<TimerCue>(extraBufferCapacity = 4)

    /** Signaux du chrono, joués par l'écran (vibration, bip). */
    val cues: SharedFlow<TimerCue> = _cues.asSharedFlow()

    /** Vrai si le chrono de l'exercice en cours a été lancé au moins une fois. */
    private var timerUsed = false

    init {
        viewModelScope.launch {
            val plan = workoutRepository.getActivePlan()
            val session = plan?.sessions?.firstOrNull { it.id == sessionId }
            val variant = session?.variants?.firstOrNull { it.durationMin == durationMin }
            if (plan == null || session == null || variant == null || variant.exercises.isEmpty()) {
                _uiState.value = SessionRunUiState(loading = false, notFound = true)
                return@launch
            }
            planName = plan.name
            val sessionRun = SessionRun(variant.exercises, variant.warmUp, variant.coolDown)
            run = sessionRun
            val restored = savedState.get<String>(KEY_RUN)?.let(sessionRun::decode)
            startedAt = savedState.get<Long>(KEY_STARTED_AT)?.takeIf { restored != null } ?: now()
            savedState[KEY_STARTED_AT] = startedAt
            if (restored == null) {
                // Nouvelle séance : rien ne doit rester d'une séance précédente.
                listOf(KEY_DEADLINE, KEY_FEELING, KEY_HAS_PAIN, KEY_PAIN_ZONE).forEach { savedState.remove<Any>(it) }
            }
            _uiState.value = SessionRunUiState(
                loading = false,
                sessionTitle = session.title,
                durationMin = variant.durationMin,
                exerciseCount = variant.exercises.size,
                // Le ressenti déjà saisi revient aussi : c'est souvent là qu'on est interrompu.
                feeling = Feeling.fromCode(savedState.get<String>(KEY_FEELING)).takeIf { restored != null },
                hasPain = restored != null && savedState.get<Boolean>(KEY_HAS_PAIN) == true,
                painZone = savedState.get<String>(KEY_PAIN_ZONE).orEmpty().takeIf { restored != null }.orEmpty(),
            )
            if (restored != null) {
                show(restored, restoredDeadline = savedState.get<Long>(KEY_DEADLINE))
            } else {
                show(sessionRun.start())
            }
        }
    }

    /** Affiche une nouvelle étape et prépare son compte à rebours. */
    private fun show(state: SessionRunState, restoredDeadline: Long? = null) {
        val sessionRun = run ?: return
        runState = state
        savedState[KEY_RUN] = sessionRun.encode(state)
        timerJob?.cancel()
        timerUsed = false
        if (restoredDeadline == null) savedState.remove<Long>(KEY_DEADLINE)
        when (val step = state.step) {
            is RunStep.Work -> {
                val exercise = sessionRun.exercises[step.exerciseIndex]
                _uiState.update {
                    it.copy(
                        step = step,
                        exercise = exercise,
                        doneReps = exercise.reps ?: 0,
                        secondsLeft = if (exercise.isTimed) exercise.durationS else null,
                        timerRunning = false,
                    )
                }
                // Un chrono d'exercice tournait quand l'app a été fermée : il reprend là où il en est.
                if (exercise.isTimed && restoredDeadline != null) {
                    timerUsed = true
                    _uiState.update { it.copy(timerRunning = true) }
                    countDown(restoredDeadline, exercise.plannedValue) { completeSet() }
                }
            }
            is RunStep.Rest -> {
                _uiState.update {
                    it.copy(
                        step = step,
                        exercise = sessionRun.exercises[step.next.exerciseIndex],
                        secondsLeft = step.seconds,
                        timerRunning = true,
                    )
                }
                countDown(restoredDeadline ?: newDeadline(step.seconds), step.seconds) { next() }
            }
            else -> _uiState.update { it.copy(step = step, exercise = null, secondsLeft = null, timerRunning = false) }
        }
    }

    private fun newDeadline(seconds: Int): Long = (elapsedMs() + seconds * 1_000L).also { savedState[KEY_DEADLINE] = it }

    /**
     * Compte à rebours jusqu'à [deadline] (sur l'horloge [elapsedMs]). Le temps restant est recalculé
     * à chaque tour à partir de l'échéance : il reste juste même si l'app a été mise en pause.
     */
    private fun countDown(deadline: Long, maxSeconds: Int, onFinished: () -> Unit) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var lastLeft: Int? = null
            while (true) {
                val remainingMs = deadline - elapsedMs()
                // Arrondi vers le haut : on affiche « 1 » pendant toute la dernière seconde.
                val left = ((remainingMs + 999) / 1_000).toInt().coerceIn(0, maxSeconds)
                _uiState.update { it.copy(secondsLeft = left) }
                // Un signal par seconde affichée : 3, 2, 1 puis la fin (jamais deux fois la même).
                if (left != lastLeft && lastLeft != null) {
                    when (left) {
                        0 -> _cues.tryEmit(TimerCue.END)
                        in 1..COUNTDOWN_CUES -> _cues.tryEmit(TimerCue.COUNTDOWN)
                    }
                }
                lastLeft = left
                if (left <= 0) break
                delay(TICK_MS)
            }
            onFinished()
        }
    }

    /** Arrêt avant la fin : on passe au ressenti pour enregistrer ce qui a été fait. */
    fun finishNow() {
        val sessionRun = run ?: return
        val state = runState ?: return
        show(sessionRun.finishNow(state))
    }

    /** Vrai si au moins une série a été faite : sinon il n'y a rien à enregistrer. */
    fun hasDoneSomething(): Boolean = runState?.results?.any { it.doneValues.isNotEmpty() } == true

    /** Bouton « Suivant » : fin de l'échauffement, du repos ou du retour au calme. */
    fun next() {
        val sessionRun = run ?: return
        val state = runState ?: return
        show(sessionRun.next(state))
    }

    fun adjustDoneReps(delta: Int) = _uiState.update { it.copy(doneReps = (it.doneReps + delta).coerceIn(0, 999)) }

    /** Lance le chrono d'un exercice chronométré ; à zéro, la série est validée toute seule. */
    fun startTimer() {
        val state = _uiState.value
        if (state.step !is RunStep.Work || state.exercise?.isTimed != true || state.timerRunning) return
        timerUsed = true
        _uiState.update { it.copy(timerRunning = true) }
        val seconds = state.exercise.plannedValue
        countDown(newDeadline(seconds), seconds) { completeSet() }
    }

    fun completeSet() {
        val sessionRun = run ?: return
        val state = runState ?: return
        val ui = _uiState.value
        val exercise = ui.exercise ?: return
        if (state.step !is RunStep.Work) return
        val done = when {
            !exercise.isTimed -> ui.doneReps
            // Chrono jamais lancé : on considère la durée prévue comme faite.
            !timerUsed -> exercise.plannedValue
            else -> exercise.plannedValue - (ui.secondsLeft ?: 0)
        }
        show(sessionRun.completeSet(state, done))
    }

    fun skipExercise() {
        val sessionRun = run ?: return
        val state = runState ?: return
        show(sessionRun.skipExercise(state))
    }

    fun setFeeling(feeling: Feeling) {
        savedState[KEY_FEELING] = feeling.code
        _uiState.update { it.copy(feeling = feeling) }
    }

    fun setHasPain(hasPain: Boolean) {
        savedState[KEY_HAS_PAIN] = hasPain
        _uiState.update { it.copy(hasPain = hasPain) }
    }

    fun setPainZone(zone: String) {
        val text = zone.take(MAX_PAIN_ZONE_LENGTH)
        savedState[KEY_PAIN_ZONE] = text
        _uiState.update { it.copy(painZone = text) }
    }

    fun save() {
        val sessionRun = run ?: return
        val state = runState ?: return
        val ui = _uiState.value
        if (ui.saving || state.step != RunStep.Feedback) return
        if (ui.hasPain && ui.painZone.isBlank()) {
            _uiState.update { it.copy(error = "Indique où tu as eu mal (ex. genou droit).") }
            return
        }
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                logRepository.saveSession(
                    FinishedSession(
                        planName = planName,
                        sessionTitle = ui.sessionTitle,
                        durationMin = ui.durationMin,
                        startedAtEpochMs = startedAt,
                        finishedAtEpochMs = now(),
                        feeling = ui.feeling,
                        painZone = ui.painZone.takeIf { ui.hasPain },
                        exercises = sessionRun.exercises.zip(state.results),
                    ),
                )
                savedState.remove<String>(KEY_RUN)
                _uiState.update { it.copy(saving = false, finished = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, error = "L'enregistrement a échoué. Réessaie.") }
            }
        }
    }

    private companion object {
        const val KEY_RUN = "run"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_DEADLINE = "deadline"
        const val KEY_FEELING = "feeling"
        const val KEY_HAS_PAIN = "has_pain"
        const val KEY_PAIN_ZONE = "pain_zone"
        const val TICK_MS = 250L

        /** Signaux avant la fin : 3, 2, 1. */
        const val COUNTDOWN_CUES = 3
        const val MAX_PAIN_ZONE_LENGTH = 100
    }
}
