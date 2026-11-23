package com.bidaboss.healthy.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations de la base. Une entrée par changement de version, jamais de migration destructive.
 * Chaque migration est rejouée par MigrationTest à partir des schémas de app/schemas.
 */
object Migrations {

    /** v2 : historique des séances (copie des exercices) et activités libres. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `workout_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`started_at_epoch_ms` INTEGER NOT NULL, " +
                    "`finished_at_epoch_ms` INTEGER NOT NULL, " +
                    "`local_date` TEXT NOT NULL, " +
                    "`plan_name` TEXT NOT NULL, " +
                    "`session_title` TEXT NOT NULL, " +
                    "`duration_min` INTEGER NOT NULL, " +
                    "`feeling` TEXT, " +
                    "`pain_zone` TEXT, " +
                    "`notes` TEXT)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_logs_local_date` ON `workout_logs` (`local_date`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `workout_log_exercises` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`log_id` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`planned_sets` INTEGER NOT NULL, " +
                    "`planned_reps` INTEGER, " +
                    "`planned_duration_s` INTEGER, " +
                    "`done_values` TEXT NOT NULL, " +
                    "`skipped` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`log_id`) REFERENCES `workout_logs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_log_exercises_log_id` ON `workout_log_exercises` (`log_id`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `activities` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`local_date` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`duration_min` INTEGER NOT NULL, " +
                    "`notes` TEXT, " +
                    "`created_at_epoch_ms` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_activities_local_date` ON `activities` (`local_date`)")
        }
    }

    /** v3 : liens vidéo facultatifs des exercices (et de leurs variantes) dans le plan. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workout_exercises` ADD COLUMN `video_url` TEXT")
            db.execSQL("ALTER TABLE `workout_exercises` ADD COLUMN `easier_video_url` TEXT")
            db.execSQL("ALTER TABLE `workout_exercises` ADD COLUMN `harder_video_url` TEXT")
        }
    }

    /** v4 : profil (une ligne) et pesées datées avec tour de taille facultatif. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `profile` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`sex` TEXT NOT NULL, " +
                    "`first_name` TEXT, " +
                    "`birth_date` TEXT NOT NULL, " +
                    "`height_cm` REAL NOT NULL, " +
                    "`weight_kg` REAL NOT NULL, " +
                    "`activity` TEXT NOT NULL, " +
                    "`goal` TEXT NOT NULL, " +
                    "`updated_at_epoch_ms` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `weigh_ins` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`local_date` TEXT NOT NULL, " +
                    "`weight_kg` REAL NOT NULL, " +
                    "`waist_cm` REAL, " +
                    "`created_at_epoch_ms` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weigh_ins_local_date` ON `weigh_ins` (`local_date`)")
        }
    }

    /** v5 : eau bue (un ajout par ligne). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `water_intakes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`local_date` TEXT NOT NULL, " +
                    "`ml` INTEGER NOT NULL, " +
                    "`created_at_epoch_ms` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_intakes_local_date` ON `water_intakes` (`local_date`)")
        }
    }

    /** v6 : profil sportif (une ligne), réponses sport de l'accueil. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sport_profile` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`level` TEXT NOT NULL, " +
                    "`sessions_per_week` INTEGER NOT NULL, " +
                    "`days` TEXT NOT NULL, " +
                    "`duration_min` INTEGER NOT NULL, " +
                    "`goal` TEXT NOT NULL, " +
                    "`pain_zones` TEXT NOT NULL, " +
                    "`pain_other` TEXT NOT NULL, " +
                    "`medical_follow_up` INTEGER NOT NULL, " +
                    "`medical_notes` TEXT NOT NULL, " +
                    "`equipment` TEXT NOT NULL, " +
                    "`avoid` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
        }
    }

    /** v7 : zones du corps travaillées pendant une activité libre. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `activities` ADD COLUMN `body_parts` TEXT")
        }
    }

    /** v8 : repas en favori. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `meals` ADD COLUMN `is_favorite` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v9 : « Mes plats » (recettes et leurs ingrédients). */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recipes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `cooked_weight_g` REAL NOT NULL, `servings` INTEGER, " +
                    "`created_at_epoch_ms` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recipe_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`recipe_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`quantity_g` REAL, `kcal` REAL NOT NULL, `protein_g` REAL, `carbs_g` REAL, `fat_g` REAL, " +
                    "`fiber_g` REAL, FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_items_recipe_id` ON `recipe_items` (`recipe_id`)")
        }
    }

    /** v10 : séance de la montre (Health Connect) d'où vient une activité libre, ajoutée une seule fois. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `activities` ADD COLUMN `health_id` TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_activities_health_id` ON `activities` (`health_id`)")
        }
    }

    /** v11 : produits scannés retenus sur le téléphone (code-barres). */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scanned_products` (`code` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`brand` TEXT, `kcal_per_100g` REAL NOT NULL, `protein_per_100g` REAL, `carbs_per_100g` REAL, " +
                    "`fat_per_100g` REAL, `fiber_per_100g` REAL, `serving_g` REAL, " +
                    "`from_open_food_facts` INTEGER NOT NULL, `last_used_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`code`))",
            )
        }
    }

    /** v12 : réglage « boisson » ou « alcool » par aliment (null = aliment ordinaire) ; les anciens repas ne sont pas reclassés. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `meal_items` ADD COLUMN `drink` TEXT")
        }
    }

    /** v13 : relevé par jour de la montre (historique des tendances). */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `watch_days` (`local_date` TEXT NOT NULL, `steps` INTEGER, `active_kcal` INTEGER, " +
                    "`sleep_min` INTEGER, `avg_bpm` INTEGER, `min_bpm` INTEGER, `updated_at_epoch_ms` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`local_date`))",
            )
        }
    }

    /**
     * v14 : ajustement du maintien adopté (kcal, 0 = formule seule), niveau d'activité de ce moment-là,
     * et décisions de proposition (pour ne pas reproposer en boucle).
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `maintenance_adjustment_kcal` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `adjustment_activity` TEXT")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `proposal_decided_on` TEXT")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `proposal_snoozed_until` TEXT")
        }
    }

    /** v15 : « Mes aliments » (favoris d'aliments, un par nom). */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_foods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`name_key` TEXT NOT NULL, `default_portion_g` REAL, `kcal` REAL NOT NULL, `protein_g` REAL, `carbs_g` REAL, " +
                    "`fat_g` REAL, `fiber_g` REAL, `source` TEXT NOT NULL, `drink` TEXT, `created_at_epoch_ms` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_foods_name_key` ON `saved_foods` (`name_key`)")
        }
    }

    val ALL = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
        MIGRATION_14_15,
    )
}
