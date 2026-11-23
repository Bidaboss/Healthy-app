package com.bidaboss.healthy.core.progress

import com.bidaboss.healthy.core.body.Band
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeeklyRecapTest {
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `le bilan s'ouvre du dimanche 18 h au mardi`() {
        assertNull(WeeklyRecaps.recapWeek(monday.plusDays(6).atTime(17, 59)))
        assertEquals(monday, WeeklyRecaps.recapWeek(monday.plusDays(6).atTime(18, 0)))
        assertEquals(monday, WeeklyRecaps.recapWeek(monday.plusDays(7).atTime(8, 0)))
        assertEquals(monday, WeeklyRecaps.recapWeek(monday.plusDays(8).atTime(22, 0)))
        assertNull(WeeklyRecaps.recapWeek(monday.plusDays(9).atTime(8, 0)))
    }

    @Test
    fun `ouverture automatique le lundi, une seule fois`() {
        val nextMonday = LocalDateTime.of(monday.plusDays(7), java.time.LocalTime.of(7, 30))
        assertTrue(WeeklyRecaps.shouldAutoOpen(nextMonday, null))
        assertTrue(WeeklyRecaps.shouldAutoOpen(nextMonday, monday.minusWeeks(1)))
        assertFalse(WeeklyRecaps.shouldAutoOpen(nextMonday, monday))
        assertFalse(WeeklyRecaps.shouldAutoOpen(nextMonday.plusDays(1), null))
    }

    @Test
    fun `seuls les jours notes comptent, et le badge de la semaine est mis en avant`() {
        val days = (0L..6L).map { i ->
            val d = monday.plusDays(i)
            when (i) {
                0L -> RecapDay(d, true, 2000.0, 130.0, 31.0, 2000, 1800)
                1L -> RecapDay(d, true, 2200.0, 90.0, 20.0, 1000, 1800)
                else -> RecapDay(d, false, 0.0, 0.0, 0.0, 0, 1800)
            }
        }
        val recap = WeeklyRecaps.build(
            weekStart = monday,
            days = days,
            maintenanceKcal = 2100,
            proteinTargetG = 128,
            sessions = 1,
            activities = 2,
            activeDates = setOf(monday.minusWeeks(1), monday.plusDays(2)),
            sessionCountBefore = 0,
            sessionCountAtEnd = 1,
            mealDates = setOf(monday, monday.plusDays(1)),
        )
        assertEquals(2, recap.mealDays)
        assertEquals(2100.0, recap.averageKcal!!, 0.0)
        assertEquals(Band.GREEN, recap.band)
        assertEquals(1, recap.proteinDays)
        assertEquals(1, recap.fiberDays)
        assertEquals(1, recap.waterDays)
        assertEquals(2, recap.streakWeeks)
        assertEquals(Growth.SPROUT, recap.growth)
        // Gagnés cette semaine : premier repas, première séance, 2 semaines actives ; le plus avancé est montré.
        assertEquals(Badge.TWO_WEEKS, recap.badge)
        assertTrue(recap.badgeIsNew)
    }
}
