package com.bidaboss.healthy.body

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.data.HealthyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

/** Eau bue : ajouts, annulation du dernier ajout, liste et suppression des ajouts, total par jour. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WaterRepositoryTest {
    private val day = LocalDate.of(2026, 11, 11)
    private lateinit var database: HealthyDatabase
    private lateinit var body: BodyRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        body = BodyRepository(database.bodyDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `ajouts additionnes par jour et dernier ajout annulable`() = runBlocking {
        assertTrue(body.addWater(day, 250))
        assertTrue(body.addWater(day, 500))
        assertTrue(body.addWater(day.minusDays(1), 250))
        assertEquals(mapOf(day to 750, day.minusDays(1) to 250), body.observeDailyWater(day.minusDays(6), day).first())

        assertTrue(body.undoLastWater(day))
        assertEquals(250, body.observeDailyWater(day, day).first()[day])
        assertTrue(body.undoLastWater(day))
        assertFalse(body.undoLastWater(day))
        // La veille n'est pas touchée.
        assertEquals(250, body.observeDailyWater(day.minusDays(1), day.minusDays(1)).first()[day.minusDays(1)])
    }

    @Test
    fun `liste des ajouts du jour, du plus recent, et suppression d'un ajout precis`() = runBlocking {
        body.addWater(day, 200, nowEpochMs = 1_000)
        body.addWater(day, 250, nowEpochMs = 3_000)
        body.addWater(day, 120, nowEpochMs = 2_000)
        body.addWater(day.minusDays(1), 500, nowEpochMs = 500)

        val intakes = body.observeWaterIntakes(day).first()
        assertEquals(listOf(250, 120, 200), intakes.map { it.ml })

        assertTrue(body.deleteWater(intakes[1].id))
        assertEquals(listOf(250, 200), body.observeWaterIntakes(day).first().map { it.ml })
        assertEquals(450, body.observeDailyWater(day, day).first()[day])
    }

    @Test
    fun `nom du bouton retrouve depuis la quantite`() {
        assertEquals("Verre", Hydration.quickLabel(200))
        assertEquals("Thé", Hydration.quickLabel(250))
        assertEquals("Café", Hydration.quickLabel(150))
        // Un café noté avant la refonte (12 cL) garde son nom.
        assertEquals("Café", Hydration.quickLabel(120))
        assertEquals("Bouteille", Hydration.quickLabel(500))
        assertEquals(null, Hydration.quickLabel(330))
    }

    @Test
    fun `quantite absurde refusee`() = runBlocking {
        assertFalse(body.addWater(day, 0))
        assertFalse(body.addWater(day, 3001))
    }
}
