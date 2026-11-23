package com.bidaboss.healthy.core.progress

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BadgesTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `rien de fait, aucun badge`() {
        assertEquals(emptyList(), Badges.unlocked(BadgeProgress(0, emptySet(), emptySet())))
    }

    @Test
    fun `meilleure serie retenue meme si elle est finie`() {
        // 3 semaines d'affilée, un trou, puis 1 semaine.
        val dates = setOf(monday, monday.plusWeeks(1).plusDays(3), monday.plusWeeks(2), monday.plusWeeks(4))
        assertEquals(3, Badges.bestActiveWeekStreak(dates))
        val progress = BadgeProgress(4, dates, emptySet())
        assertTrue(Badge.TWO_WEEKS.isUnlocked(progress))
        assertFalse(Badge.FOUR_WEEKS.isUnlocked(progress))
        assertEquals(3, Badge.FOUR_WEEKS.current(progress))
    }

    @Test
    fun `repas et seances comptes et plafonnes`() {
        val meals = (0L until 9L).mapTo(HashSet()) { monday.plusDays(it) }
        val progress = BadgeProgress(12, setOf(monday), meals)
        assertEquals(
            listOf(Badge.FIRST_MEAL, Badge.MEAL_WEEK, Badge.FIRST_SESSION, Badge.TEN_SESSIONS),
            Badges.unlocked(progress),
        )
        assertEquals(7, Badge.MEAL_WEEK.current(progress))
        assertEquals(1, Badge.TWO_WEEKS.current(progress))
    }
}
