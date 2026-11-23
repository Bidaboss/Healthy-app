package com.bidaboss.healthy.workout

import com.bidaboss.healthy.workout.ui.weekSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phrase du bilan de la semaine dans Sport : accords selon les nombres, sans « (s) ». */
class WeekSummaryTest {

    @Test
    fun `la phrase suit les nombres de seances et d'activites`() {
        assertEquals("Rien de fait cette semaine pour l'instant", weekSummary(0, 0))
        assertEquals("1 séance cette semaine", weekSummary(1, 0))
        assertEquals("2 activités cette semaine", weekSummary(0, 2))
        assertEquals("1 séance et 2 activités cette semaine", weekSummary(1, 2))
        assertEquals("3 séances et 1 activité cette semaine", weekSummary(3, 1))
    }
}
