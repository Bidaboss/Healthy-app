package com.bidaboss.healthy.importexport

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.backup.BackupContent
import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.BodyProfile
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.core.body.WeighIn
import com.bidaboss.healthy.core.importformat.ImportDocument
import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.core.importformat.ImportResult
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.importexport.data.BackupRepository
import com.bidaboss.healthy.importexport.data.ImportRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/** Sauvegarde puis restauration sur un « autre téléphone » (une autre base en mémoire). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupRepositoryTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val day = LocalDate.of(2026, 11, 11)
    private val databases = mutableListOf<HealthyDatabase>()

    private fun newDatabase(): HealthyDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
            .also { databases += it }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private fun backups(db: HealthyDatabase) = BackupRepository(db, RoomTransactionRunner(db))

    private fun parse(text: String): BackupContent =
        ((ImportParser(zone).parse(text) as ImportResult.Success).document as ImportDocument.Backup).content

    @Test
    fun `toutes les tables de la base sont sauvegardees`() {
        val db = newDatabase()
        val tables = db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT IN " +
                "('room_master_table', 'sqlite_sequence', 'android_metadata')",
        ).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
        assertEquals(tables, BackupRepository.TABLES.toSet())
    }

    @Test
    fun `les donnees reviennent a l'identique sur un autre telephone`() = runBlocking {
        val phoneA = newDatabase()
        val meals = MealRepository(phoneA.mealDao(), RoomTransactionRunner(phoneA)) { zone }
        val mealId = meals.saveMeal(
            MealDraft(
                id = null,
                type = MealType.LUNCH,
                eatenAt = LocalDateTime.of(day, java.time.LocalTime.of(12, 30)),
                title = "Riz « maison »",
                notes = null,
                foods = listOf(Food(name = "Riz", quantityG = 150.0, kcal = 195.0, proteinG = 4.0)),
            ),
        )
        meals.setFavorite(mealId, true)
        val body = BodyRepository(phoneA.bodyDao())
        body.saveProfile(BodyProfile(Sex.MALE, LocalDate.of(1988, 7, 15), 176.0, 75.0, ActivityLevel.ACTIVE, firstName = "Alex"))
        body.saveWeighIn(WeighIn(day, 79.6, 88.0))
        val exported = parse(backups(phoneA).export())

        val phoneB = newDatabase()
        val summary = backups(phoneB).restore(exported)
        assertEquals(1, summary.meals)
        assertEquals(1, summary.weighIns)

        val mealsB = MealRepository(phoneB.mealDao(), RoomTransactionRunner(phoneB)) { zone }
        val meal = mealsB.observeMeals(day).first().single()
        assertEquals("Riz « maison »", meal.title)
        assertEquals(195.0, meal.totalKcal, 0.0)
        // La sauvegarde copie toutes les colonnes : le favori revient aussi.
        assertTrue(meal.isFavorite)
        val bodyB = BodyRepository(phoneB.bodyDao())
        assertEquals(ActivityLevel.ACTIVE, bodyB.observeProfile().first()?.activity)
        assertEquals("Alex", bodyB.observeProfile().first()?.firstName)
        assertEquals(88.0, bodyB.observeWeighIns().first().single().waistCm!!, 0.0)
        // Exporter à nouveau donne exactement les mêmes tables.
        assertEquals(exported, parse(backups(phoneB).export()))
    }

    @Test
    fun `restaurer par le vrai chemin d'import, transaction imbriquee comprise`() = runBlocking {
        val phoneA = newDatabase()
        BodyRepository(phoneA.bodyDao()).saveWeighIn(WeighIn(day, 79.6, 88.0))
        val text = backups(phoneA).export()

        val phoneB = newDatabase()
        val transactions = RoomTransactionRunner(phoneB)
        val imports = ImportRepository(
            transactions,
            phoneB.importDao(),
            MealRepository(phoneB.mealDao(), transactions) { zone },
            WorkoutRepository(phoneB.workoutDao()),
            backups(phoneB),
        )
        val document = (ImportParser(zone).parse(text) as ImportResult.Success).document
        assertTrue(!imports.isAlreadyImported(document))
        assertTrue(imports.save(document))
        assertEquals(79.6, BodyRepository(phoneB.bodyDao()).observeWeighIns().first().single().weightKg, 0.0)
        // On peut restaurer la même sauvegarde une deuxième fois.
        assertTrue(imports.save(document))
        assertEquals(1, BodyRepository(phoneB.bodyDao()).observeWeighIns().first().size)
    }

    @Test
    fun `restaurer remplace ce qui etait sur le telephone`() = runBlocking {
        val db = newDatabase()
        BodyRepository(db.bodyDao()).saveWeighIn(WeighIn(day, 90.0))
        backups(db).restore(BackupContent(HealthyDatabase.VERSION, emptyMap()))
        assertTrue(BodyRepository(db.bodyDao()).observeWeighIns().first().isEmpty())
    }

    @Test
    fun `une sauvegarde d'une version plus ancienne se restaure, colonnes absentes vides`() = runBlocking {
        // Version 2 : pas encore de liens vidéo, ni de profil, ni de pesées.
        val old = BackupContent(
            databaseVersion = 2,
            tables = mapOf(
                "workout_plans" to listOf(
                    mapOf("id" to 1L, "import_id" to null, "name" to "Plan", "description" to null, "weeks" to 4L,
                        "is_active" to 1L, "imported_at_epoch_ms" to 1L),
                ),
            ),
        )
        val db = newDatabase()
        assertTrue(!backups(db).isTooRecent(old))
        backups(db).restore(old)
        db.openHelper.readableDatabase.query("SELECT name FROM workout_plans").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Plan", c.getString(0))
        }
        assertTrue(backups(db).isTooRecent(old.copy(databaseVersion = HealthyDatabase.VERSION + 1)))
    }

    @Test
    fun `une ancienne sauvegarde sans ajustement du maintien se restaure avec un ajustement de zero`() = runBlocking {
        val old = BackupContent(
            databaseVersion = 13,
            tables = mapOf(
                "profile" to listOf(
                    mapOf("id" to 1L, "sex" to "homme", "first_name" to "Alex", "birth_date" to "1988-07-15", "height_cm" to 176.0,
                        "weight_kg" to 75.0, "activity" to "actif", "goal" to "maintien", "updated_at_epoch_ms" to 1L),
                ),
            ),
        )
        val db = newDatabase()
        backups(db).restore(old)
        val profile = BodyRepository(db.bodyDao()).observeProfile().first()!!
        assertEquals(0, profile.maintenanceAdjustmentKcal)
        // Un ajustement adopté part dans la sauvegarde et revient à la restauration.
        BodyRepository(db.bodyDao()).adoptAdjustment(140, day)
        val exported = parse(backups(db).export())
        val other = newDatabase()
        backups(other).restore(exported)
        assertEquals(140, BodyRepository(other.bodyDao()).observeProfile().first()!!.maintenanceAdjustmentKcal)
    }

    @Test
    fun `une sauvegarde d'avant la refonte restaure les releves de la montre sans distance ni nuit detaillee`() = runBlocking {
        val old = BackupContent(
            databaseVersion = 14,
            tables = mapOf(
                "watch_days" to listOf(
                    mapOf("local_date" to "2026-11-22", "steps" to 8000L, "active_kcal" to null, "sleep_min" to 430L,
                        "avg_bpm" to null, "min_bpm" to null, "updated_at_epoch_ms" to 1L),
                ),
            ),
        )
        val db = newDatabase()
        backups(db).restore(old)
        val row = db.watchDayDao().get("2026-11-22")!!
        assertEquals(430, row.sleepMin)
        assertEquals(null, row.distanceM)
        assertEquals(null, row.sleepStages)
        // Un relevé complet part dans la sauvegarde et revient.
        db.watchDayDao().upsert(row.copy(distanceM = 6200, sleepStart = "2026-11-21T23:10", sleepEnd = "2026-11-22T06:40", sleepStages = "L45,D30"))
        val other = newDatabase()
        backups(other).restore(parse(backups(db).export()))
        assertEquals("L45,D30", other.watchDayDao().get("2026-11-22")!!.sleepStages)
    }
}
