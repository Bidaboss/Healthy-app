package com.bidaboss.healthy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.bidaboss.healthy.body.data.BodyDao
import com.bidaboss.healthy.body.data.ProfileEntity
import com.bidaboss.healthy.body.data.WaterIntakeEntity
import com.bidaboss.healthy.body.data.WeighInEntity
import com.bidaboss.healthy.health.data.WatchDayDao
import com.bidaboss.healthy.health.data.WatchDayEntity
import com.bidaboss.healthy.importexport.data.ImportDao
import com.bidaboss.healthy.importexport.data.ImportRecordEntity
import com.bidaboss.healthy.meals.data.MealDao
import com.bidaboss.healthy.meals.data.MealEntity
import com.bidaboss.healthy.meals.data.MealItemEntity
import com.bidaboss.healthy.meals.data.RecipeDao
import com.bidaboss.healthy.meals.data.RecipeEntity
import com.bidaboss.healthy.meals.data.RecipeItemEntity
import com.bidaboss.healthy.meals.data.SavedFoodDao
import com.bidaboss.healthy.meals.data.SavedFoodEntity
import com.bidaboss.healthy.meals.data.ScannedProductDao
import com.bidaboss.healthy.meals.data.ScannedProductEntity
import com.bidaboss.healthy.workout.data.ActivityEntity
import com.bidaboss.healthy.workout.data.route.SavedRouteDao
import com.bidaboss.healthy.workout.data.route.SavedRouteEntity
import com.bidaboss.healthy.workout.data.SportProfileDao
import com.bidaboss.healthy.workout.data.SportProfileEntity
import com.bidaboss.healthy.workout.data.WorkoutDao
import com.bidaboss.healthy.workout.data.WorkoutExerciseEntity
import com.bidaboss.healthy.workout.data.WorkoutLogDao
import com.bidaboss.healthy.workout.data.WorkoutLogEntity
import com.bidaboss.healthy.workout.data.WorkoutLogExerciseEntity
import com.bidaboss.healthy.workout.data.WorkoutPlanEntity
import com.bidaboss.healthy.workout.data.WorkoutSessionEntity
import com.bidaboss.healthy.workout.data.WorkoutSessionVariantEntity

/**
 * Base locale. Toute modification du schéma = version + 1 ET une vraie migration
 * (jamais fallbackToDestructiveMigration : les données des proches doivent survivre aux mises à jour).
 */
@Database(
    entities = [
        MealEntity::class,
        MealItemEntity::class,
        WorkoutPlanEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSessionVariantEntity::class,
        WorkoutExerciseEntity::class,
        ImportRecordEntity::class,
        WorkoutLogEntity::class,
        WorkoutLogExerciseEntity::class,
        ActivityEntity::class,
        ProfileEntity::class,
        WeighInEntity::class,
        WaterIntakeEntity::class,
        SportProfileEntity::class,
        RecipeEntity::class,
        RecipeItemEntity::class,
        ScannedProductEntity::class,
        WatchDayEntity::class,
        SavedFoodEntity::class,
        SavedRouteEntity::class,
    ],
    version = HealthyDatabase.VERSION,
    exportSchema = true,
)
abstract class HealthyDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun importDao(): ImportDao
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun bodyDao(): BodyDao
    abstract fun sportProfileDao(): SportProfileDao
    abstract fun recipeDao(): RecipeDao
    abstract fun scannedProductDao(): ScannedProductDao
    abstract fun watchDayDao(): WatchDayDao
    abstract fun savedFoodDao(): SavedFoodDao
    abstract fun savedRouteDao(): SavedRouteDao

    companion object {
        /** Version du schéma. L'augmenter impose d'ajouter une migration dans [Migrations]. */
        const val VERSION = 17

        fun create(context: Context): HealthyDatabase =
            Room.databaseBuilder(context, HealthyDatabase::class.java, "healthy.db")
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

class RoomTransactionRunner(private val database: HealthyDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction { block() }
}
