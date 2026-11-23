package com.bidaboss.healthy.workout

import com.bidaboss.healthy.workout.data.WorkoutPlan
import com.bidaboss.healthy.workout.data.WorkoutSession
import com.bidaboss.healthy.workout.data.nextReminderAt
import com.bidaboss.healthy.workout.data.sessionToRemind
import com.bidaboss.healthy.workout.data.trainingDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Quand le rappel de séance doit sonner, et pour quelle séance. */
class SessionReminderTest {

    private fun session(title: String, day: Int?) = WorkoutSession(id = 0, title = title, dayOfWeek = day, variants = emptyList())

    // Lundi : haut du corps, mercredi : bas du corps, plus une séance sans jour.
    private val plan = WorkoutPlan(
        id = 1,
        name = "Plan",
        description = null,
        weeks = null,
        sessions = listOf(session("Haut du corps", 1), session("Bas du corps", 3), session("Libre", null)),
    )
    private val monday = LocalDate.of(2026, 11, 9)
    private val tuesday = LocalDate.of(2026, 11, 10)

    @Test
    fun `le prochain rappel est aujourd'hui si l'heure n'est pas passee, sinon demain`() {
        val at18 = LocalTime.of(18, 0)
        assertEquals(monday.atTime(18, 0), nextReminderAt(LocalDateTime.of(monday, LocalTime.of(9, 30)), at18))
        assertEquals(tuesday.atTime(18, 0), nextReminderAt(LocalDateTime.of(monday, LocalTime.of(18, 0)), at18))
        assertEquals(tuesday.atTime(18, 0), nextReminderAt(LocalDateTime.of(monday, LocalTime.of(20, 0)), at18))
    }

    @Test
    fun `rappel seulement un jour de seance du plan`() {
        assertEquals("Haut du corps", sessionToRemind(plan, monday, sessionsDoneToday = 0)?.title)
        assertNull(sessionToRemind(plan, tuesday, sessionsDoneToday = 0))
    }

    @Test
    fun `pas de rappel si la seance est deja faite ou sans plan`() {
        assertNull(sessionToRemind(plan, monday, sessionsDoneToday = 1))
        assertNull(sessionToRemind(null, monday, sessionsDoneToday = 0))
    }

    @Test
    fun `jours d'entrainement tries sans doublon`() {
        val withDouble = plan.copy(sessions = plan.sessions + session("Gainage", 1))
        assertEquals(listOf(1, 3), trainingDays(withDouble))
        assertEquals(emptyList<Int>(), trainingDays(null))
    }
}
