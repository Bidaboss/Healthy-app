package com.bidaboss.healthy.data

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rejoue chaque migration à partir des schémas versionnés (app/schemas) : les données existantes
 * doivent survivre, et la base obtenue doit être exactement celle que Room attend.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HealthyDatabase::class.java,
    )

    @Test
    fun `de la version 1 a la 2, les repas sont conserves et l'historique sport existe`() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO meals (id, import_id, eaten_at_epoch_ms, local_date, meal_type, created_at_epoch_ms) " +
                    "VALUES (1, 'a', 1000, '2026-11-11', 'dejeuner', 1000)",
            )
            db.execSQL(
                "INSERT INTO meal_items (meal_id, position, name, kcal, source) " +
                    "VALUES (1, 0, 'Riz', 200.0, 'manuel')",
            )
        }

        // Le « true » compare la base migrée au schéma 2.json : toute différence fait échouer le test.
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 2, true, Migrations.MIGRATION_1_2)

        migrated.query("SELECT COUNT(*) FROM meal_items WHERE meal_id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM workout_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun `de la version 2 a la 3, le plan est conserve sans lien video`() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL(
                "INSERT INTO workout_plans (id, import_id, name, weeks, is_active, imported_at_epoch_ms) " +
                    "VALUES (1, 'p', 'Plan', 4, 1, 1000)",
            )
            db.execSQL("INSERT INTO workout_sessions (id, plan_id, position, title) VALUES (1, 1, 0, 'A')")
            db.execSQL("INSERT INTO workout_session_variants (id, session_id, duration_min) VALUES (1, 1, 15)")
            db.execSQL(
                "INSERT INTO workout_exercises (variant_id, position, name, sets, reps) VALUES (1, 0, 'Pompes', 3, 10)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 3, true, Migrations.MIGRATION_2_3)

        migrated.query("SELECT name, video_url, easier_video_url, harder_video_url FROM workout_exercises").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Pompes", cursor.getString(0))
            assertTrue(cursor.isNull(1) && cursor.isNull(2) && cursor.isNull(3))
        }
    }

    @Test
    fun `de la version 3 a la 4, le profil et les pesees existent et le reste est conserve`() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL(
                "INSERT INTO meals (id, import_id, eaten_at_epoch_ms, local_date, meal_type, created_at_epoch_ms) " +
                    "VALUES (1, 'a', 1000, '2026-11-11', 'dejeuner', 1000)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 4, true, Migrations.MIGRATION_3_4)

        migrated.query("SELECT COUNT(*) FROM meals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.execSQL(
            "INSERT INTO weigh_ins (local_date, weight_kg, waist_cm, created_at_epoch_ms) VALUES ('2026-11-11', 80.5, NULL, 1)",
        )
        migrated.query("SELECT weight_kg FROM weigh_ins").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(80.5, cursor.getDouble(0), 0.0)
        }
    }

    @Test
    fun `de la version 4 a la 5, la table de l'eau existe`() {
        helper.createDatabase(DB_NAME, 4).close()
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 5, true, Migrations.MIGRATION_4_5)
        migrated.execSQL("INSERT INTO water_intakes (local_date, ml, created_at_epoch_ms) VALUES ('2026-11-11', 250, 1)")
        migrated.query("SELECT SUM(ml) FROM water_intakes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(250, cursor.getInt(0))
        }
    }

    @Test
    fun `de la version 5 a la 6, le profil sportif existe`() {
        helper.createDatabase(DB_NAME, 5).close()
        helper.runMigrationsAndValidate(DB_NAME, 6, true, Migrations.MIGRATION_5_6).close()
    }

    @Test
    fun `de la version 6 a la 7, les activites gardent leurs donnees et ont des zones vides`() {
        helper.createDatabase(DB_NAME, 6).use { db ->
            db.execSQL(
                "INSERT INTO activities (local_date, type, duration_min, notes, created_at_epoch_ms) " +
                    "VALUES ('2026-11-11', 'Marche', 45, NULL, 1)",
            )
        }
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 7, true, Migrations.MIGRATION_6_7)
        migrated.query("SELECT type, duration_min, body_parts FROM activities").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Marche", cursor.getString(0))
            assertEquals(45, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
        }
    }

    @Test
    fun `de la version 7 a la 8, les repas gardent leurs donnees et ne sont pas en favori`() {
        helper.createDatabase(DB_NAME, 7).use { db ->
            db.execSQL(
                "INSERT INTO meals (import_id, eaten_at_epoch_ms, local_date, meal_type, title, notes, created_at_epoch_ms) " +
                    "VALUES (NULL, 1, '2026-11-12', 'dejeuner', 'Midi', NULL, 1)",
            )
        }
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 8, true, Migrations.MIGRATION_7_8)
        migrated.query("SELECT title, is_favorite FROM meals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Midi", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
    }

    @Test
    fun `de la version 8 a la 9, mes plats existent et les repas sont intacts`() {
        helper.createDatabase(DB_NAME, 8).use { db ->
            db.execSQL(
                "INSERT INTO meals (import_id, eaten_at_epoch_ms, local_date, meal_type, title, notes, created_at_epoch_ms, is_favorite) " +
                    "VALUES (NULL, 1, '2026-11-12', 'dejeuner', 'Midi', NULL, 1, 1)",
            )
        }
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 9, true, Migrations.MIGRATION_8_9)
        migrated.query("SELECT COUNT(*) FROM meals WHERE is_favorite = 1").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        migrated.query("SELECT COUNT(*) FROM recipes").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
    }

    @Test
    fun `de la version 9 a la 10, les activites gardent leurs donnees et une seance de la montre ne s'ajoute qu'une fois`() {
        helper.createDatabase(DB_NAME, 9).use { db ->
            db.execSQL(
                "INSERT INTO activities (local_date, type, duration_min, notes, created_at_epoch_ms, body_parts) " +
                    "VALUES ('2026-11-20', 'Marche', 45, NULL, 1, NULL)",
            )
        }
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 10, true, Migrations.MIGRATION_9_10)
        migrated.query("SELECT type, duration_min, health_id FROM activities").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Marche", cursor.getString(0))
            assertEquals(45, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
        }
        // Les activités faites à la main n'ont pas d'identifiant : plusieurs sont permises.
        migrated.execSQL(
            "INSERT INTO activities (local_date, type, duration_min, notes, created_at_epoch_ms) VALUES ('2026-11-21', 'Vélo', 30, NULL, 2)",
        )
        migrated.query("SELECT COUNT(*) FROM activities").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
    }

    @Test
    fun `de la version 10 a la 11, les repas sont conserves et la table des produits scannes existe`() {
        helper.createDatabase(DB_NAME, 10).use { db ->
            db.execSQL(
                "INSERT INTO meals (id, import_id, eaten_at_epoch_ms, local_date, meal_type, created_at_epoch_ms) " +
                    "VALUES (1, 'a', 1000, '2026-11-22', 'dejeuner', 1000)",
            )
        }
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 11, true, Migrations.MIGRATION_10_11)
        migrated.query("SELECT COUNT(*) FROM meals").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        migrated.execSQL(
            "INSERT INTO scanned_products (code, name, brand, kcal_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, " +
                "fiber_per_100g, serving_g, from_open_food_facts, last_used_epoch_ms) " +
                "VALUES ('3017620422003', 'Pâte', NULL, 539.0, NULL, NULL, NULL, NULL, 15.0, 1, 1)",
        )
        migrated.query("SELECT COUNT(*) FROM scanned_products").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun `de la version 11 a la 12, les aliments restent des aliments ordinaires et le reglage boisson existe`() {
        helper.createDatabase(DB_NAME, 11).use { db ->
            db.execSQL(
                "INSERT INTO meals (id, import_id, eaten_at_epoch_ms, local_date, meal_type, created_at_epoch_ms) " +
                    "VALUES (1, 'a', 1000, '2026-11-22', 'dejeuner', 1000)",
            )
            db.execSQL(
                "INSERT INTO meal_items (meal_id, position, name, quantity_g, kcal, source) " +
                    "VALUES (1, 0, 'Jus de fruits', 200.0, 90.0, 'manuel')",
            )
        }
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 12, true, Migrations.MIGRATION_11_12)
        migrated.query("SELECT name, drink FROM meal_items").use {
            assertTrue(it.moveToFirst())
            assertEquals("Jus de fruits", it.getString(0))
            assertTrue(it.isNull(1))
        }
        migrated.execSQL("UPDATE meal_items SET drink = 'boisson'")
        migrated.query("SELECT COUNT(*) FROM meal_items WHERE drink = 'boisson'").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun `de la version 12 a la 13, les repas sont conserves et la table des releves de la montre existe`() {
        helper.createDatabase(DB_NAME, 12).use { db ->
            db.execSQL(
                "INSERT INTO meals (id, import_id, eaten_at_epoch_ms, local_date, meal_type, created_at_epoch_ms) " +
                    "VALUES (1, 'a', 1000, '2026-11-22', 'dejeuner', 1000)",
            )
        }
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 13, true, Migrations.MIGRATION_12_13)
        migrated.query("SELECT COUNT(*) FROM meals").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        migrated.execSQL(
            "INSERT INTO watch_days (local_date, steps, active_kcal, sleep_min, avg_bpm, min_bpm, updated_at_epoch_ms) " +
                "VALUES ('2026-11-22', 8000, NULL, 430, NULL, NULL, 1)",
        )
        migrated.query("SELECT steps, active_kcal FROM watch_days").use {
            assertTrue(it.moveToFirst())
            assertEquals(8000, it.getInt(0))
            assertTrue(it.isNull(1))
        }
    }

    @Test
    fun `de la version 13 a la 14, le profil est conserve avec un ajustement du maintien a zero`() {
        helper.createDatabase(DB_NAME, 13).use { db ->
            db.execSQL(
                "INSERT INTO profile (id, sex, first_name, birth_date, height_cm, weight_kg, activity, goal, updated_at_epoch_ms) " +
                    "VALUES (1, 'homme', 'Alex', '1988-07-15', 176.0, 75.0, 'actif', 'maintien', 1)",
            )
        }
        val migrated = helper.runMigrationsAndValidate(DB_NAME, 14, true, Migrations.MIGRATION_13_14)
        migrated.query(
            "SELECT first_name, maintenance_adjustment_kcal, adjustment_activity, proposal_decided_on, proposal_snoozed_until FROM profile",
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("Alex", it.getString(0))
            assertEquals(0, it.getInt(1))
            assertTrue(it.isNull(2) && it.isNull(3) && it.isNull(4))
        }
    }

    @Test
    fun `chaque changement de version a sa migration`() {
        val covered = Migrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals((1 until HealthyDatabase.VERSION).map { it to it + 1 }, covered)
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
