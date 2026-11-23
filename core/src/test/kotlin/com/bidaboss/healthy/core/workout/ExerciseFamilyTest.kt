package com.bidaboss.healthy.core.workout

import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseFamilyTest {
    @Test
    fun `les noms francais du plan trouvent leur famille`() {
        assertEquals(ExerciseFamily.PUSH_UP, ExerciseFamily.of("Pompes sur les genoux"))
        assertEquals(ExerciseFamily.BRIDGE, ExerciseFamily.of("Pont fessier"))
        assertEquals(ExerciseFamily.SQUAT, ExerciseFamily.of("Squats"))
        assertEquals(ExerciseFamily.LUNGE, ExerciseFamily.of("Fentes arrière"))
        assertEquals(ExerciseFamily.PLANK, ExerciseFamily.of("Gainage latéral"))
        assertEquals(ExerciseFamily.ABS, ExerciseFamily.of("Relevé de buste"))
        assertEquals(ExerciseFamily.JUMP, ExerciseFamily.of("Jumping jacks"))
        assertEquals(ExerciseFamily.STRETCH, ExerciseFamily.of("Étirement des ischios"))
        assertEquals(ExerciseFamily.GENERIC, ExerciseFamily.of("Montées de genoux"))
    }
}
