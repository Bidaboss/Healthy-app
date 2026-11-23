package com.bidaboss.healthy

import android.content.Context
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.importformat.ImportParser
import com.bidaboss.healthy.core.watch.SessionSummary
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.health.data.HealthConnectSource
import com.bidaboss.healthy.health.data.WatchHistory
import com.bidaboss.healthy.health.data.WatchIgnoredStore
import com.bidaboss.healthy.health.data.WatchRepository
import com.bidaboss.healthy.health.data.WatchStatus
import com.bidaboss.healthy.importexport.data.BackupRepository
import com.bidaboss.healthy.importexport.data.ImportRepository
import com.bidaboss.healthy.importexport.data.MealReportRepository
import com.bidaboss.healthy.importexport.data.PromptRepository
import com.bidaboss.healthy.meals.data.FoodSearchRepository
import com.bidaboss.healthy.meals.data.HabitDismissStore
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.meals.data.RecipeRepository
import com.bidaboss.healthy.meals.data.ScannedProductRepository
import com.bidaboss.healthy.settings.data.AppearanceStore
import com.bidaboss.healthy.settings.data.MotivationRepository
import com.bidaboss.healthy.settings.data.MotivationStore
import com.bidaboss.healthy.settings.data.WaterReminderStore
import com.bidaboss.healthy.settings.reminder.MotivationScheduler
import com.bidaboss.healthy.settings.reminder.WaterReminderScheduler
import com.bidaboss.healthy.today.data.MaintenanceRepository
import com.bidaboss.healthy.today.data.TodaySnapshotRepository
import com.bidaboss.healthy.body.data.MaintenanceInfo
import com.bidaboss.healthy.today.data.WeeklyRecapRepository
import com.bidaboss.healthy.today.data.WeeklyRecapStore
import com.bidaboss.healthy.update.data.ApkInspector
import com.bidaboss.healthy.update.data.UpdateRepository
import com.bidaboss.healthy.update.data.UpdateStore
import com.bidaboss.healthy.workout.data.ReminderSettingsStore
import com.bidaboss.healthy.workout.data.SportProfileRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository
import com.bidaboss.healthy.workout.reminder.SessionReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/** Injection de dépendances « à la main » : un seul endroit où les objets partagés sont créés. */
class AppContainer(context: Context) {
    private val database = HealthyDatabase.create(context)
    private val transactions = RoomTransactionRunner(database)

    val importParser = ImportParser()
    val mealRepository = MealRepository(database.mealDao(), transactions)
    val recipeRepository = RecipeRepository(database.recipeDao(), transactions)
    val habitDismissStore = HabitDismissStore(context)
    val foodSearchRepository = FoodSearchRepository()
    val scannedProductRepository = ScannedProductRepository(database.scannedProductDao(), foodSearchRepository)
    val workoutRepository = WorkoutRepository(database.workoutDao())
    val workoutLogRepository = WorkoutLogRepository(database.workoutLogDao(), transactions)
    val backupRepository = BackupRepository(database, transactions)
    val importRepository =
        ImportRepository(transactions, database.importDao(), mealRepository, workoutRepository, backupRepository)
    val bodyRepository = BodyRepository(database.bodyDao())

    /** Maintien du jour pour Repas, qui ne dépend pas du paquet `body`. */
    val maintenanceKcal: (LocalDate) -> Flow<Int?> = bodyRepository::observeMaintenanceKcal

    /** Poids de référence pour les calories des activités de Sport, qui ne dépend pas du paquet `body`. */
    val referenceWeightKg: (LocalDate) -> Flow<Double?> = bodyRepository::observeReferenceWeightKg
    private val mealReportRepository = MealReportRepository(mealRepository, bodyRepository)

    /** Bilan repas des 7 derniers jours pour Repas, qui ne dépend pas du paquet `body`. */
    val exportMealReport: suspend (today: LocalDate, id: String, now: LocalDateTime) -> String = mealReportRepository::export
    val sportProfileRepository = SportProfileRepository(database.sportProfileDao())
    val promptRepository =
        PromptRepository(context, bodyRepository, sportProfileRepository, workoutRepository, workoutLogRepository)
    val reminderSettings = ReminderSettingsStore(context)
    val reminderScheduler = SessionReminderScheduler(context.applicationContext)
    val appearance = AppearanceStore(context)
    val motivationStore = MotivationStore(context)
    val motivationScheduler = MotivationScheduler(context.applicationContext)
    val waterReminderStore = WaterReminderStore(context)
    val waterReminderScheduler = WaterReminderScheduler(context.applicationContext)
    val updateStore = UpdateStore(context)
    val updateRepository = UpdateRepository(updateStore, context.cacheDir, inspectApk = { file -> ApkInspector.read(context, file) })
    val watchRepository = WatchRepository(HealthConnectSource(context), WatchIgnoredStore(context), WatchHistory(database.watchDayDao()))
    /** Ma montre et Sport ne s'importent pas : AppContainer leur passe ce dont ils ont besoin l'un de l'autre. */
    val sessionSummary: (LocalDate, LocalDate) -> Flow<SessionSummary> = workoutLogRepository::observeSessionSummary
    val isWatchConnected: suspend () -> Boolean = { watchRepository.status() == WatchStatus.READY }
    val weeklyRecapRepository = WeeklyRecapRepository(mealRepository, bodyRepository, workoutLogRepository)
    private val maintenanceRepository = MaintenanceRepository(mealRepository, bodyRepository)
    val maintenanceInfo: (LocalDate) -> Flow<MaintenanceInfo?> = maintenanceRepository::observe
    val todaySnapshotRepository = TodaySnapshotRepository(mealRepository, bodyRepository, workoutRepository, workoutLogRepository)
    val weeklyRecapStore = WeeklyRecapStore(context)
    val motivationRepository =
        MotivationRepository(mealRepository, bodyRepository, workoutRepository, workoutLogRepository)
}
