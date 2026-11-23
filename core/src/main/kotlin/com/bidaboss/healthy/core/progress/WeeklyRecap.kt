package com.bidaboss.healthy.core.progress

import com.bidaboss.healthy.core.body.Band
import com.bidaboss.healthy.core.body.Maintenance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Un jour de la semaine résumée : ce qui a été noté. */
data class RecapDay(
    val date: LocalDate,
    val hasMeal: Boolean,
    val kcal: Double,
    val proteinG: Double,
    val fiberG: Double,
    val waterMl: Int,
    /** Objectif d'eau de ce jour-là, null sans profil. */
    val waterTargetMl: Int?,
)

/**
 * Bilan de la semaine (fil Design B4, accordé avec le fil Nutrition le 13/11/2026). Les jours sans repas
 * noté ne comptent pas : on parle des jours notés, jamais d'un jeûne.
 */
data class WeeklyRecap(
    val weekStart: LocalDate,
    val mealDays: Int,
    val averageKcal: Double?,
    val maintenanceKcal: Int?,
    val band: Band?,
    val proteinTargetG: Int?,
    val proteinDays: Int,
    val fiberDays: Int,
    val waterDays: Int,
    val sessions: Int,
    val activities: Int,
    val streakWeeks: Int,
    val growth: Growth,
    /** Badge gagné cette semaine, sinon le plus avancé déjà gagné, sinon null. */
    val badge: Badge?,
    val badgeIsNew: Boolean,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
}

object WeeklyRecaps {
    /** Le bilan s'ouvre du dimanche 18 h au mardi soir. */
    const val SUNDAY_OPEN_HOUR = 18

    /**
     * Semaine résumée à [now] : la semaine qui finit le dimanche soir, ou la précédente le lundi et le mardi.
     * null en dehors de cette fenêtre.
     */
    fun recapWeek(now: LocalDateTime): LocalDate? {
        val today = now.toLocalDate()
        return when (today.dayOfWeek) {
            DayOfWeek.SUNDAY -> if (now.hour >= SUNDAY_OPEN_HOUR) Motivation.weekStart(today) else null
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY -> Motivation.weekStart(today).minusWeeks(1)
            else -> null
        }
    }

    /** Le lundi, à la première ouverture, si ce bilan n'a pas encore été montré. */
    fun shouldAutoOpen(now: LocalDateTime, lastShownWeek: LocalDate?): Boolean {
        if (now.dayOfWeek != DayOfWeek.MONDAY) return false
        val week = recapWeek(now) ?: return false
        return lastShownWeek == null || lastShownWeek.isBefore(week)
    }

    fun build(
        weekStart: LocalDate,
        days: List<RecapDay>,
        maintenanceKcal: Int?,
        proteinTargetG: Int?,
        sessions: Int,
        activities: Int,
        activeDates: Set<LocalDate>,
        sessionCountBefore: Int,
        sessionCountAtEnd: Int,
        mealDates: Set<LocalDate>,
    ): WeeklyRecap {
        val weekEnd = weekStart.plusDays(6)
        val noted = days.filter { it.hasMeal && !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
        val average = noted.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average()
        fun progressAt(date: LocalDate, sessionCount: Int) = BadgeProgress(
            sessionCount,
            activeDates.filterTo(HashSet()) { !it.isAfter(date) },
            mealDates.filterTo(HashSet()) { !it.isAfter(date) },
        )
        val before = Badges.unlocked(progressAt(weekStart.minusDays(1), sessionCountBefore)).toSet()
        val after = Badges.unlocked(progressAt(weekEnd, sessionCountAtEnd))
        val newBadge = after.lastOrNull { it !in before }
        return WeeklyRecap(
            weekStart = weekStart,
            mealDays = noted.size,
            averageKcal = average,
            maintenanceKcal = maintenanceKcal,
            band = if (average != null && maintenanceKcal != null) Maintenance.band(average, maintenanceKcal) else null,
            proteinTargetG = proteinTargetG,
            proteinDays = proteinTargetG?.let { t -> noted.count { it.proteinG >= t } } ?: 0,
            fiberDays = noted.count { it.fiberG >= Maintenance.FIBER_TARGET_G },
            waterDays = noted.count { d -> d.waterTargetMl?.let { d.waterMl >= it } == true },
            sessions = sessions,
            activities = activities,
            streakWeeks = Motivation.activeWeekStreak(activeDates, weekEnd),
            growth = Motivation.growth(activeDates, weekEnd),
            badge = newBadge ?: after.lastOrNull(),
            badgeIsNew = newBadge != null,
        )
    }
}
