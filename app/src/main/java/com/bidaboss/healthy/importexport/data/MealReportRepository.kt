package com.bidaboss.healthy.importexport.data

import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.mealreport.MealReport
import com.bidaboss.healthy.core.mealreport.MealReportWriter
import com.bidaboss.healthy.core.mealreport.ReportDay
import com.bidaboss.healthy.core.mealreport.ReportFood
import com.bidaboss.healthy.core.mealreport.ReportMeal
import com.bidaboss.healthy.meals.data.MealRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Bilan repas à envoyer à Claude (type `meal_report`, docs/format-import.md). Ici et non dans `meals`,
 * car il réunit les repas, l'eau et le profil, et `meals` ne dépend pas du paquet `body`.
 */
class MealReportRepository(private val meals: MealRepository, private val body: BodyRepository) {

    suspend fun export(today: LocalDate, id: String, now: LocalDateTime): String {
        val from = today.minusDays(REPORT_DAYS - 1L)
        val byDay = meals.getMealsBetween(from, today).groupBy { it.localDate }
        val water = body.observeDailyWater(from, today).first()
        val profile = body.observeProfile().first()
        val weighIns = body.observeWeighIns().first()
        val report = MealReport(
            periodStart = from,
            periodEnd = today,
            maintenanceKcal = profile?.let { Maintenance.maintenanceKcal(it, weighIns, today) },
            maintenanceAdjustmentKcal = profile?.maintenanceAdjustmentKcal,
            proteinTargetG = profile?.let { Maintenance.proteinTargetG(Maintenance.referenceWeightKg(it.weightKg, weighIns, today)) },
            fiberTargetG = Maintenance.FIBER_TARGET_G,
            days = (0 until REPORT_DAYS).map { i ->
                val day = from.plusDays(i.toLong())
                ReportDay(
                    date = day,
                    waterMl = water[day] ?: 0,
                    meals = byDay[day].orEmpty().map { meal ->
                        ReportMeal(
                            mealType = meal.type.code,
                            eatenAt = meal.eatenAt,
                            title = meal.title,
                            items = meal.foods.map {
                                ReportFood(it.name, it.quantityG, it.portion, it.kcal, it.proteinG, it.carbsG, it.fatG, it.fiberG)
                            },
                        )
                    },
                )
            },
        )
        return MealReportWriter.write(report, id, now)
    }

    companion object {
        /** Période couverte : les 7 derniers jours, celui de l'export compris. */
        const val REPORT_DAYS = 7
    }
}
