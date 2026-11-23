package com.bidaboss.healthy.workout

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.workout.BodyPart
import com.bidaboss.healthy.core.workout.ExerciseResult
import com.bidaboss.healthy.core.workout.PlannedExercise
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.workout.data.Feeling
import com.bidaboss.healthy.workout.data.FinishedSession
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/** Historique des séances et activités libres dans une vraie base Room en mémoire. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutLogRepositoryTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val monday = LocalDate.of(2026, 11, 9)

    private lateinit var database: HealthyDatabase
    private lateinit var logs: WorkoutLogRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        logs = WorkoutLogRepository(database.workoutLogDao(), RoomTransactionRunner(database)) { zone }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `une seance enregistree se relit avec ses exercices dans l'ordre`() = runBlocking {
        val start = LocalDateTime.of(2026, 11, 11, 18, 0).atZone(zone).toInstant().toEpochMilli()
        logs.saveSession(
            FinishedSession(
                planName = "Plan test",
                sessionTitle = "Haut du corps",
                durationMin = 30,
                startedAtEpochMs = start,
                finishedAtEpochMs = start + 25 * 60_000,
                feeling = Feeling.OK,
                painZone = "  ",
                exercises = listOf(
                    PlannedExercise("Pompes", sets = 2, reps = 10) to ExerciseResult(listOf(10, 8)),
                    PlannedExercise("Planche", sets = 1, durationS = 40) to ExerciseResult(skipped = true),
                ),
            ),
        )

        val log = logs.observeRecentSessions().first().single()
        assertEquals("Haut du corps", log.sessionTitle)
        assertEquals(LocalDateTime.of(2026, 11, 11, 18, 0), log.startedAt)
        assertEquals(Feeling.OK, log.feeling)
        // Une zone de douleur vide n'est pas enregistrée.
        assertEquals(null, log.painZone)
        assertEquals(listOf("Pompes", "Planche"), log.exercises.map { it.name })
        assertEquals(listOf(10, 8), log.exercises[0].doneValues)
        assertTrue(log.exercises[1].skipped)
        assertTrue(log.exercises[1].doneValues.isEmpty())
        assertEquals(1, logs.observeSessionCount(monday, monday.plusDays(6)).first())
        assertEquals(0, logs.observeSessionCount(monday.plusDays(7), monday.plusDays(13)).first())
    }

    @Test
    fun `une activite libre s'ajoute, se compte dans la semaine et se supprime`() = runBlocking {
        assertTrue(logs.addActivity("  Marche ", 45, monday.plusDays(2), nowEpochMs = 1))
        assertTrue(logs.addActivity("Vélo", 60, monday.minusDays(1), nowEpochMs = 2))

        val activities = logs.observeRecentActivities().first()
        assertEquals(listOf("Marche", "Vélo"), activities.map { it.type })
        assertEquals(monday.plusDays(2), activities[0].date)
        assertEquals(45, activities[0].durationMin)
        assertEquals(1, logs.observeActivityCount(monday, monday.plusDays(6)).first())

        logs.deleteActivity(activities[0].id)
        assertEquals(listOf("Vélo"), logs.observeRecentActivities().first().map { it.type })
    }

    @Test
    fun `une activite ajoutee se modifie, avec les memes limites que l'ajout`() = runBlocking {
        logs.addActivity("Vélo", 60, monday, nowEpochMs = 1)
        val id = logs.observeRecentActivities().first().single().id

        assertTrue(logs.updateActivity(id, " Vélo électrique ", 50, monday.plusDays(1), setOf(BodyPart.LEGS)))
        val edited = logs.observeRecentActivities().first().single()
        assertEquals("Vélo électrique", edited.type)
        assertEquals(50, edited.durationMin)
        assertEquals(monday.plusDays(1), edited.date)
        assertEquals(listOf(BodyPart.LEGS), edited.bodyParts)

        assertFalse(logs.updateActivity(id, "  ", 50, monday))
        assertFalse(logs.updateActivity(id + 99, "Marche", 30, monday))
        assertEquals("Vélo électrique", logs.observeRecentActivities().first().single().type)
    }

    @Test
    fun `une seance de kine garde ses zones travaillees, dans l'ordre et sans doublon`() = runBlocking {
        assertTrue(
            logs.addActivity(
                "Kiné",
                30,
                monday.minusDays(3),
                nowEpochMs = 1,
                bodyParts = listOf(BodyPart.GLUTES, BodyPart.ABS, BodyPart.GLUTES),
            ),
        )
        assertTrue(logs.addActivity("Marche", 20, monday, nowEpochMs = 2))

        val activities = logs.observeRecentActivities().first()
        assertEquals(emptyList<BodyPart>(), activities[0].bodyParts)
        assertEquals("Kiné", activities[1].type)
        assertEquals(monday.minusDays(3), activities[1].date)
        assertEquals(listOf(BodyPart.ABS, BodyPart.GLUTES), activities[1].bodyParts)
    }

    @Test
    fun `les jours actifs comptent les activites ajoutees a la main`() = runBlocking {
        logs.addActivity("Kiné", 30, monday, nowEpochMs = 1)
        logs.addActivity("Marche", 20, monday, nowEpochMs = 2)
        assertEquals(setOf(monday), logs.observeActiveDates().first())
        assertEquals(emptySet<LocalDate>(), logs.observeSessionDates().first())
    }

    @Test
    fun `le bilan couvre 28 jours et contient seances et activites de la periode`() = runBlocking {
        val today = LocalDate.of(2026, 11, 11)
        fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        fun session(day: LocalDate, title: String) = FinishedSession(
            planName = "Plan test",
            sessionTitle = title,
            durationMin = 30,
            startedAtEpochMs = at(day, 18),
            finishedAtEpochMs = at(day, 18) + 26 * 60_000 + 40_000,
            feeling = Feeling.TOO_HARD,
            painZone = "genou droit",
            exercises = listOf(PlannedExercise("Planche", sets = 2, durationS = 30) to ExerciseResult(listOf(30, 12))),
        )
        logs.saveSession(session(today, "Dans la période"))
        logs.saveSession(session(today.minusDays(27), "Premier jour"))
        logs.saveSession(session(today.minusDays(28), "Trop ancien"))
        logs.addActivity("Marche", 45, today.minusDays(3), nowEpochMs = 1)
        logs.addActivity("Vélo", 60, today.minusDays(40), nowEpochMs = 2)

        val text = logs.exportTrainingReport(today, "7c1d9e42-5b3a-4f8e-a6d2-0e9f8b7c6a51", at(today, 19))
        val root = Json.parseToJsonElement(text).jsonObject
        val report = root.getValue("report").jsonObject

        assertEquals("training_report", root.getValue("type").jsonPrimitive.content)
        assertEquals("2026-11-11T19:00:00", root.getValue("created_at").jsonPrimitive.content)
        assertEquals("2026-10-15", report.getValue("period_start").jsonPrimitive.content)
        assertEquals("2026-11-11", report.getValue("period_end").jsonPrimitive.content)

        val sessions = report.getValue("sessions").jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("Premier jour", "Dans la période"),
            sessions.map { it.getValue("session_title").jsonPrimitive.content },
        )
        val last = sessions.last()
        assertEquals("2026-11-11T18:00:00", last.getValue("started_at").jsonPrimitive.content)
        assertEquals(27, last.getValue("actual_duration_min").jsonPrimitive.int)
        assertEquals("trop_dur", last.getValue("feeling").jsonPrimitive.content)
        assertEquals("genou droit", last.getValue("pain_zone").jsonPrimitive.content)
        val exercise = last.getValue("exercises").jsonArray.single().jsonObject
        assertEquals(listOf(30, 12), exercise.getValue("done_duration_s").jsonArray.map { it.jsonPrimitive.int })

        val activities = report.getValue("activities").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Marche"), activities.map { it.getValue("type").jsonPrimitive.content })
    }

    @Test
    fun `une activite sans nom ou a la duree impossible est refusee`() = runBlocking {
        assertFalse(logs.addActivity("   ", 30, monday, nowEpochMs = 1))
        assertFalse(logs.addActivity("Marche", 0, monday, nowEpochMs = 1))
        assertFalse(logs.addActivity("Marche", 1_441, monday, nowEpochMs = 1))
        assertTrue(logs.observeRecentActivities().first().isEmpty())
    }
}
