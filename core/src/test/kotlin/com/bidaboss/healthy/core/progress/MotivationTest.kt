package com.bidaboss.healthy.core.progress

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MotivationTest {
    // Mercredi 11 novembre 2026 : semaine du lundi 9.
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `aucune seance, pas de flamme`() {
        assertEquals(0, Motivation.activeWeekStreak(emptyList(), today))
    }

    @Test
    fun `semaine en cours active comptee avec les precedentes`() {
        val dates = listOf(today, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27))
        assertEquals(3, Motivation.activeWeekStreak(dates, today))
    }

    @Test
    fun `semaine en cours vide ne casse pas la serie`() {
        val dates = listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 10, 28))
        assertEquals(2, Motivation.activeWeekStreak(dates, today))
    }

    @Test
    fun `une semaine sans seance casse la serie`() {
        val dates = listOf(today, LocalDate.of(2026, 10, 28))
        assertEquals(1, Motivation.activeWeekStreak(dates, today))
    }

    @Test
    fun `dimanche et lundi sont deux semaines`() {
        val dates = listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9))
        assertEquals(2, Motivation.activeWeekStreak(dates, today))
    }

    @Test
    fun `plusieurs seances la meme semaine ne font monter la serie que d'un cran`() {
        // Cas d'Alexandrine (13/11/2026) : séances mercredi, jeudi et vendredi de la même semaine.
        val friday = LocalDate.of(2026, 11, 13)
        val dates = listOf(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12), friday)
        assertEquals(1, Motivation.activeWeekStreak(dates, friday))
        // Une séance la semaine suivante : la série passe à 2.
        val nextMonday = LocalDate.of(2026, 11, 16)
        assertEquals(2, Motivation.activeWeekStreak(dates + nextMonday, nextMonday))
    }

    @Test
    fun `phrase sous la serie selon la semaine`() {
        val friday = LocalDate.of(2026, 11, 13)
        assertEquals(
            "Semaine validée ! Prochain cran : une séance la semaine prochaine.",
            Motivation.streakHint(listOf(LocalDate.of(2026, 11, 11)), friday),
        )
        assertEquals("Une séance avant dimanche pour garder ta série.", Motivation.streakHint(listOf(LocalDate.of(2026, 11, 5)), friday))
        assertEquals("Une séance cette semaine lance ta série.", Motivation.streakHint(emptyList(), friday))
    }

    @Test
    fun `humeur selon la seance du jour`() {
        assertEquals(Mood.PROUD, Motivation.mood(sessionDoneToday = true, sessionPlannedToday = true))
        assertEquals(Mood.ENCOURAGING, Motivation.mood(sessionDoneToday = false, sessionPlannedToday = true))
        assertEquals(Mood.HAPPY, Motivation.mood(sessionDoneToday = false, sessionPlannedToday = false))
        assertEquals("Pense à noter ton premier repas.", Motivation.message(Mood.HAPPY, 0))
    }

    @Test
    fun `pousse grandit avec la serie et ne retombe que d'un palier`() {
        val monday = LocalDate.of(2026, 9, 7)
        fun weeks(vararg w: Long) = w.map { monday.plusWeeks(it) }
        // Rien : deux feuilles.
        assertEquals(Growth.SPROUT, Motivation.growth(emptyList(), monday))
        // 2 semaines : quatre feuilles ; 4 : petite plante ; 8 : arbuste.
        assertEquals(Growth.LEAVES, Motivation.growth(weeks(0, 1), monday.plusWeeks(1)))
        assertEquals(Growth.PLANT, Motivation.growth(weeks(0, 1, 2, 3), monday.plusWeeks(3)))
        val eight = weeks(0, 1, 2, 3, 4, 5, 6, 7)
        assertEquals(Growth.SHRUB, Motivation.growth(eight, monday.plusWeeks(7)))
        // La semaine en cours sans séance ne casse rien.
        assertEquals(Growth.SHRUB, Motivation.growth(eight, monday.plusWeeks(8)))
        // Une semaine manquée : un seul palier de moins, même après plusieurs semaines vides.
        assertEquals(Growth.PLANT, Motivation.growth(eight, monday.plusWeeks(9)))
        assertEquals(Growth.PLANT, Motivation.growth(eight, monday.plusWeeks(12)))
        // Nouvelle série de 8 semaines : il redevient arbuste.
        val again = eight + weeks(13, 14, 15, 16, 17, 18, 19, 20)
        assertEquals(Growth.SHRUB, Motivation.growth(again, monday.plusWeeks(20)))
        // Jamais sous les deux feuilles.
        assertEquals(Growth.SPROUT, Motivation.growth(weeks(0), monday.plusWeeks(10)))
    }
}
