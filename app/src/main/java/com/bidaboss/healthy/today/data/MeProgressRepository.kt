package com.bidaboss.healthy.today.data

import com.bidaboss.healthy.body.data.MeBadge
import com.bidaboss.healthy.body.data.MeProgress
import com.bidaboss.healthy.core.progress.Badge
import com.bidaboss.healthy.core.progress.BadgeProgress
import com.bidaboss.healthy.core.progress.Growth
import com.bidaboss.healthy.core.progress.Motivation
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** Progression pour l'onglet Moi (Pousse et badges) : réunit repas et sport, comme les écrans Aujourd'hui et Badges. */
class MeProgressRepository(
    private val meals: MealRepository,
    private val logs: WorkoutLogRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    fun observe(): Flow<MeProgress> = combine(
        logs.observeSessionCount(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)),
        logs.observeActiveDates(),
        meals.observeMealDates(),
    ) { count, activeDates, mealDates ->
        val progress = BadgeProgress(count, activeDates, mealDates)
        val streak = Motivation.activeWeekStreak(activeDates, today())
        val unlocked = Badge.entries.filter { it.isUnlocked(progress) }
        val next = Badge.entries.firstOrNull { !it.isUnlocked(progress) }
        MeProgress(
            streakWeeks = streak,
            growth = Growth.forStreak(streak),
            unlockedBadges = unlocked.size,
            totalBadges = Badge.entries.size,
            unlockedTitles = unlocked.asReversed().map { it.title },
            nextBadgeTitle = next?.title,
            nextBadgeProgress = next?.takeIf { it.target > 1 }?.let { "${it.current(progress)} sur ${it.target}" },
            badges = Badge.entries.map { MeBadge(it, it.isUnlocked(progress)) },
        )
    }
}
