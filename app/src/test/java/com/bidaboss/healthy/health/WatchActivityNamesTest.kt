package com.bidaboss.healthy.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.bidaboss.healthy.health.data.watchActivityName
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchActivityNamesTest {

    @Test
    fun `les types courants ont un nom francais`() {
        assertEquals("Marche", watchActivityName(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, null))
        assertEquals("Course", watchActivityName(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, null))
        assertEquals("Football", watchActivityName(ExerciseSessionRecord.EXERCISE_TYPE_SOCCER, null))
    }

    @Test
    fun `un type inconnu reprend le titre de la montre, sinon Sport`() {
        assertEquals("Cardio maison", watchActivityName(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, "  Cardio maison "))
        assertEquals("Sport", watchActivityName(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, " "))
        assertEquals("Sport", watchActivityName(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, null))
    }
}
