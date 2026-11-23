package com.bidaboss.healthy.core.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityCaloriesTest {
    @Test
    fun `velo electrique passe avant velo`() {
        assertEquals(4.5, ActivityCalories.met("Vélo électrique"))
        assertEquals(6.8, ActivityCalories.met("Vélo"))
    }

    @Test
    fun `accents et majuscules ignores, type inconnu modere`() {
        assertEquals(2.5, ActivityCalories.met("KINE du mardi"))
        assertEquals(ActivityCalories.DEFAULT_MET, ActivityCalories.met("Trampoline"))
    }

    @Test
    fun `formule MET x poids x heures arrondie a 5`() {
        // 3,5 × 80 kg × 0,5 h = 140 kcal
        assertEquals(140, ActivityCalories.estimateKcal("Marche", 30, 80.0))
        // 6,8 × 72 × 0,75 = 367,2 → 365
        assertEquals(365, ActivityCalories.estimateKcal("Vélo", 45, 72.0))
    }

    @Test
    fun `sans poids pas d estimation`() {
        assertNull(ActivityCalories.estimateKcal("Marche", 30, null))
    }
}
