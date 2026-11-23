package com.bidaboss.healthy.core.body

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class WaterReminderTest {

    @Test
    fun `part attendue selon le moment de la journee`() {
        assertEquals(600, WaterReminder.expectedMl(WaterCheck.LATE_MORNING, 2000))
        assertEquals(1000, WaterReminder.expectedMl(WaterCheck.EARLY_AFTERNOON, 2000))
        assertEquals(1400, WaterReminder.expectedMl(WaterCheck.LATE_AFTERNOON, 2000))
        assertEquals(1800, WaterReminder.expectedMl(WaterCheck.EVENING, 2000))
    }

    @Test
    fun `rien a dire quand on est dans les temps`() {
        assertNull(WaterReminder.message(WaterCheck.LATE_MORNING, 600, 2000))
        assertNull(WaterReminder.message(WaterCheck.EVENING, 2500, 2000))
    }

    @Test
    fun `en retard, on dit ou on en est et combien de verres il manque`() {
        val message = WaterReminder.message(WaterCheck.EARLY_AFTERNOON, 450, 2000)!!
        assertEquals("Début d'après-midi : pense à boire 💧", message.title)
        assertEquals(
            "Tu en es à 45 cL sur 2 L. À cette heure-ci, vise environ 1 L : il te manque 3 verres.",
            message.text,
        )
        val almost = WaterReminder.message(WaterCheck.LATE_MORNING, 550, 2000)!!
        assertEquals(true, almost.text.endsWith("il te manque un verre."))
    }
}
