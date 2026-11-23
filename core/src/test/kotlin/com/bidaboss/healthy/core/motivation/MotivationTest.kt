package com.bidaboss.healthy.core.motivation

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Choix de la notification de motivation selon la journée, et textes complets dans chaque ton. */
class MotivationTest {

    private val day = DaySnapshot(mealCount = 2, waterMl = 1800, waterTargetMl = 2000, plannedSession = null, sessionsDone = 0)

    @Test
    fun `le midi, les repas passent avant l'eau`() {
        assertEquals(Situation.NO_MEAL, Motivation.situation(MotivationSlot.NOON, day.copy(mealCount = 0, waterMl = 0)))
        assertEquals(Situation.LOW_WATER, Motivation.situation(MotivationSlot.NOON, day.copy(waterMl = 600)))
        assertEquals(Situation.ALL_GOOD, Motivation.situation(MotivationSlot.NOON, day.copy(waterMl = 800)))
    }

    @Test
    fun `le soir, la séance prévue passe en premier`() {
        val pending = day.copy(mealCount = 0, waterMl = 0, plannedSession = "Haut du corps")
        assertEquals(Situation.SESSION_PENDING, Motivation.situation(MotivationSlot.EVENING, pending))
        assertEquals(Situation.NO_MEAL, Motivation.situation(MotivationSlot.EVENING, pending.copy(sessionsDone = 1)))
        assertEquals(Situation.LOW_WATER, Motivation.situation(MotivationSlot.EVENING, day.copy(waterMl = 1500)))
        assertEquals(Situation.SESSION_DONE, Motivation.situation(MotivationSlot.EVENING, day.copy(sessionsDone = 1)))
        assertEquals(Situation.ALL_GOOD, Motivation.situation(MotivationSlot.EVENING, day))
    }

    @Test
    fun `chaque ton a des textes pour chaque situation`() {
        for (tone in MotivationTone.entries) for (situation in Situation.entries) {
            val texts = MotivationTexts.of(tone, situation)
            assertTrue(texts.size >= 2, "$tone $situation")
            texts.forEach { (title, text) -> assertTrue(title.isNotBlank() && text.isNotBlank()) }
        }
    }

    @Test
    fun `les valeurs du jour remplacent les repères`() {
        val pending = day.copy(waterMl = 200, plannedSession = "Bas du corps")
        for (tone in MotivationTone.entries) for (seed in 0..20) {
            val water = Motivation.message(tone, MotivationSlot.NOON, pending, Random(seed))
            val session = Motivation.message(tone, MotivationSlot.EVENING, pending, Random(seed))
            for (message in listOf(water, session)) {
                assertFalse(message.text.contains('{') || message.title.contains('{'), message.text)
            }
        }
        val message = Motivation.message(MotivationTone.GENTLE, MotivationSlot.NOON, pending, Random(0))
        assertTrue(message.text.contains("20 cL") || !message.text.contains("cL"), message.text)
    }
}
