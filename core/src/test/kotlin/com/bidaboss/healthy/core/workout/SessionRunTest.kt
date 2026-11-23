package com.bidaboss.healthy.core.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRunTest {

    private val pompes = PlannedExercise(name = "Pompes", sets = 2, reps = 10, restS = 60)
    private val planche = PlannedExercise(name = "Planche", sets = 1, durationS = 40, restS = 45)
    private val squats = PlannedExercise(name = "Squats", sets = 2, reps = 15)

    @Test
    fun `seance complete avec echauffement repos et retour au calme`() {
        val run = SessionRun(listOf(pompes, planche), warmUp = "Sauts", coolDown = "Étirements")
        var state = run.start()
        assertEquals(RunStep.WarmUp("Sauts"), state.step)

        state = run.next(state)
        assertEquals(RunStep.Work(0, 0), state.step)

        state = run.completeSet(state, 10)
        assertEquals(RunStep.Rest(60, RunStep.Work(0, 1)), state.step)

        state = run.next(state)
        state = run.completeSet(state, 8)
        // Repos aussi entre deux exercices.
        assertEquals(RunStep.Rest(60, RunStep.Work(1, 0)), state.step)

        state = run.next(state)
        state = run.completeSet(state, 40)
        // Pas de repos après la dernière série de la séance.
        assertEquals(RunStep.CoolDown("Étirements"), state.step)

        state = run.next(state)
        assertEquals(RunStep.Feedback, state.step)
        assertEquals(listOf(10, 8), state.results[0].doneValues)
        assertEquals(listOf(40), state.results[1].doneValues)
        assertTrue(state.results.none { it.skipped })
    }

    @Test
    fun `sans echauffement ni repos ni retour au calme`() {
        val run = SessionRun(listOf(squats))
        var state = run.start()
        assertEquals(RunStep.Work(0, 0), state.step)
        state = run.completeSet(state, 15)
        assertEquals(RunStep.Work(0, 1), state.step)
        state = run.completeSet(state, 12)
        assertEquals(RunStep.Feedback, state.step)
    }

    @Test
    fun `passer un exercice garde les series deja faites`() {
        val run = SessionRun(listOf(pompes, squats))
        var state = run.completeSet(run.start(), 10)
        assertIs<RunStep.Rest>(state.step)

        state = run.skipExercise(state)
        assertEquals(RunStep.Work(1, 0), state.step)
        assertEquals(ExerciseResult(listOf(10), skipped = true), state.results[0])

        state = run.skipExercise(state)
        assertEquals(RunStep.Feedback, state.step)
        assertEquals(ExerciseResult(emptyList(), skipped = true), state.results[1])
    }

    @Test
    fun `les transitions hors contexte ne changent rien`() {
        val run = SessionRun(listOf(squats))
        val work = run.start()
        assertEquals(work, run.next(work))
        val feedback = run.completeSet(run.completeSet(work, 15), 15)
        assertEquals(feedback, run.completeSet(feedback, 5))
        assertEquals(feedback, run.skipExercise(feedback))
    }

    @Test
    fun `exercice chronometre`() {
        assertTrue(planche.isTimed)
        assertEquals(40, planche.plannedValue)
        assertEquals(10, pompes.plannedValue)
    }

    @Test
    fun `terminer maintenant garde ce qui a ete fait`() {
        val run = SessionRun(listOf(pompes, squats), warmUp = "Sauts")
        var state = run.completeSet(run.next(run.start()), 9)
        state = run.finishNow(state)
        assertEquals(RunStep.Feedback, state.step)
        assertEquals(listOf(9), state.results[0].doneValues)
        assertTrue(state.results[1].doneValues.isEmpty())
    }

    @Test
    fun `chaque etape se retrouve a l'identique apres encodage`() {
        val run = SessionRun(listOf(pompes, planche, squats), warmUp = "Sauts", coolDown = "Étirements")
        val states = mutableListOf(run.start())
        states += run.next(states.last())
        states += run.completeSet(states.last(), 10) // repos
        states += run.next(states.last())
        states += run.skipExercise(states.last()) // pompes passées après une série
        states += run.completeSet(states.last(), 0) // planche tenue 0 s
        states += run.next(states.last())
        states += run.completeSet(states.last(), 15)
        states += run.completeSet(states.last(), 12) // retour au calme
        states += run.next(states.last()) // ressenti
        assertEquals(RunStep.Feedback, states.last().step)
        assertEquals(5, states.map { it.step::class }.distinct().size)
        states.forEach { assertEquals(it, run.decode(run.encode(it))) }
    }

    @Test
    fun `un texte qui ne correspond plus a la seance est refuse`() {
        val run = SessionRun(listOf(pompes, squats), warmUp = "Sauts")
        val saved = run.encode(run.completeSet(run.next(run.start()), 10))

        // Le plan a changé entre-temps : autre nombre d'exercices, ou moins de séries.
        assertNull(SessionRun(listOf(pompes)).decode(saved))
        assertNull(SessionRun(listOf(pompes.copy(sets = 1), squats)).decode(saved))
        // Plus de retour au calme ni d'échauffement dans le plan.
        assertNull(SessionRun(listOf(pompes, squats)).decode("W|d;d"))
        assertNull(run.decode("C|d;d"))
        listOf("", "X,0,0", "X,0,0|d", "X,5,0|d;d", "X,0,9|d;d", "X,0|d;d", "R,0,0,1|d;d", "Z|d;d", "F|d,-1;d", "F|x;d", "F|d,a;d")
            .forEach { assertNull(run.decode(it), it) }
    }
}
