package com.bidaboss.healthy.workout

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.route.RouteAddresses
import com.bidaboss.healthy.core.route.RouteDirection
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.workout.data.RouteUse
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.route.DistanceResult
import com.bidaboss.healthy.workout.data.route.RouteFailure
import com.bidaboss.healthy.workout.data.route.RouteHttpResponse
import com.bidaboss.healthy.workout.data.route.SavedRouteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.UnknownHostException
import java.time.LocalDate
import java.util.concurrent.Executors

/** Trajets dans une vraie base en mémoire, réseau remplacé par de fausses réponses. Adresses fictives. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SavedRouteRepositoryTest {
    private lateinit var database: HealthyDatabase
    private val requested = mutableListOf<String>()
    private var respond: (String) -> RouteHttpResponse = { RouteHttpResponse(200, "") }
    private val pauses = mutableListOf<Long>()
    private lateinit var repository: SavedRouteRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        repository = SavedRouteRepository(
            database.savedRouteDao(),
            RoomTransactionRunner(database),
            fetch = { url ->
                requested += url
                respond(url)
            },
            pause = { pauses += it },
        )
    }

    @After
    fun tearDown() = database.close()

    private val geo = """[{"lat":"10.0","lon":"20.0"}]"""
    private val ok = """{"code":"Ok","routes":[{"distance":12345.0}]}"""
    private val addresses = RouteAddresses("1 rue A", listOf("2 rue B"), "3 rue C")

    @Test
    fun `le calcul fait une recherche par adresse puis un seul calcul, avec une pause entre les recherches`() = runBlocking {
        respond = { url -> RouteHttpResponse(200, if ("nominatim" in url) geo else ok) }
        val result = repository.calculateDistance(addresses)
        assertEquals(DistanceResult.Success(12.3), result)
        assertEquals(4, requested.size)
        assertEquals(3, requested.count { "nominatim.openstreetmap.org" in it })
        assertEquals(1, requested.count { "routing.openstreetmap.de" in it })
        assertTrue(requested.all { it.startsWith("https://") })
        assertEquals(listOf(1_100L, 1_100L), pauses)
    }

    @Test
    fun `une adresse introuvable dit laquelle sans la recopier`() = runBlocking {
        respond = { url -> if ("nominatim" in url && "rue+B" in url) RouteHttpResponse(200, "[]") else RouteHttpResponse(200, geo) }
        val result = repository.calculateDistance(addresses) as DistanceResult.Failure
        assertEquals(RouteFailure.AddressNotFound(1, 3), result.reason)
        assertTrue(requested.none { "routing" in it })
    }

    @Test
    fun `chaque panne a sa raison`() = runBlocking {
        respond = { RouteHttpResponse(429, "") }
        assertEquals(DistanceResult.Failure(RouteFailure.Http(429)), repository.calculateDistance(addresses))
        respond = { throw UnknownHostException("x") }
        assertEquals(DistanceResult.Failure(RouteFailure.NoConnection), repository.calculateDistance(addresses))
        respond = { throw IOException("x") }
        assertTrue((repository.calculateDistance(addresses) as DistanceResult.Failure).reason is RouteFailure.Other)
        respond = { RouteHttpResponse(200, "pas du json") }
        assertEquals(DistanceResult.Failure(RouteFailure.Unreadable), repository.calculateDistance(addresses))
        respond = { url -> RouteHttpResponse(200, if ("nominatim" in url) geo else """{"code":"NoRoute"}""") }
        assertEquals(DistanceResult.Failure(RouteFailure.NoRoute), repository.calculateDistance(addresses))
    }

    @Test
    fun `un trajet enregistre garde ses adresses et sa distance, sans reseau`() = runBlocking {
        val id = repository.save(null, " Boucle ", "1 rue A", listOf("", "2 rue B"), "3 rue C", 12.3, null, 1L)
        assertNotNull(id)
        val route = repository.get(id!!)!!
        assertEquals("Boucle", route.name)
        assertEquals(RouteAddresses("1 rue A", listOf("2 rue B"), "3 rue C"), route.addresses)
        assertEquals(12.3, route.distanceKm)
        assertTrue(route.distanceCalculated)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `une distance saisie a la main est marquee saisie, sans distance le trajet reste enregistrable`() = runBlocking {
        val manual = repository.get(repository.save(null, "A", "x", emptyList(), "y", null, 8.0, 1L)!!)!!
        assertEquals(8.0, manual.distanceKm)
        assertEquals(false, manual.distanceCalculated)
        val none = repository.get(repository.save(null, "B", "x", emptyList(), "y", null, null, 1L)!!)!!
        assertNull(none.distanceKm)
    }

    @Test
    fun `nom, depart ou arrivee manquants ou trop d etapes ne s enregistrent pas`() = runBlocking {
        assertNull(repository.save(null, "", "x", emptyList(), "y", null, null, 1L))
        assertNull(repository.save(null, "A", "", emptyList(), "y", null, null, 1L))
        assertNull(repository.save(null, "A", "x", List(9) { "e$it" }, "y", null, null, 1L))
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `supprimer un trajet garde les activites et leur distance`() = runBlocking {
        val id = repository.save(null, "Boucle", "x", emptyList(), "y", 5.0, null, 1L)!!
        val logs = WorkoutLogRepository(database.workoutLogDao(), RoomTransactionRunner(database))
        logs.addActivity(
            "Vélo", 40, LocalDate.of(2026, 11, 20), 1L,
            route = RouteUse(id, "Boucle", RouteDirection.ROUND_TRIP, RouteDirection.ROUND_TRIP.distanceKm(5.0)),
        )
        repository.delete(id)
        val activity = logs.observeRecentActivities().first().single()
        assertEquals("Boucle", activity.routeName)
        assertEquals(RouteDirection.ROUND_TRIP, activity.routeDirection)
        assertEquals(10.0, activity.distanceKm)
        assertTrue(repository.observeAll().first().isEmpty())
        assertNull(database.workoutLogDao().getActivitiesBetween("2026-11-01", "2026-11-30").single().routeId)
    }
}
