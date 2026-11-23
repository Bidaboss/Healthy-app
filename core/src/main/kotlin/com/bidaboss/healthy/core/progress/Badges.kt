package com.bidaboss.healthy.core.progress

import java.time.LocalDate

/** Ce qui compte pour les badges : séances, jours actifs (séances et activités) et repas déjà notés. */
data class BadgeProgress(
    val sessionCount: Int,
    val activeDates: Set<LocalDate>,
    val mealDates: Set<LocalDate>,
)

/** Badges débloqués au fil de l'usage (fil Design, 11/11/2026). Rien n'est enregistré : tout est recalculé. */
enum class Badge(val title: String, val goal: String, val target: Int) {
    FIRST_MEAL("Premier repas", "Note ton premier repas.", 1),
    MEAL_WEEK("Une semaine de repas", "Note tes repas sur 7 jours différents.", 7),
    FIRST_SESSION("Première séance", "Termine ta première séance.", 1),
    TEN_SESSIONS("10 séances", "Termine 10 séances.", 10),
    TWO_WEEKS("2 semaines actives", "Au moins une séance 2 semaines d'affilée.", 2),
    FOUR_WEEKS("Un mois en forme", "Au moins une séance 4 semaines d'affilée.", 4),
    TWELVE_WEEKS("Trois mois en forme", "Au moins une séance 12 semaines d'affilée.", 12),
    ;

    /** Où on en est pour ce badge (plafonné à [target]). */
    fun current(progress: BadgeProgress): Int = when (this) {
        FIRST_MEAL, MEAL_WEEK -> progress.mealDates.size
        FIRST_SESSION, TEN_SESSIONS -> progress.sessionCount
        TWO_WEEKS, FOUR_WEEKS, TWELVE_WEEKS -> Badges.bestActiveWeekStreak(progress.activeDates)
    }.coerceAtMost(target)

    fun isUnlocked(progress: BadgeProgress): Boolean = current(progress) >= target
}

object Badges {
    /** Plus longue suite de semaines d'affilée avec au moins une séance, depuis le début. */
    fun bestActiveWeekStreak(activeDates: Collection<LocalDate>): Int {
        val weeks = activeDates.map { Motivation.weekStart(it) }.distinct().sorted()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (week in weeks) {
            run = if (previous != null && previous.plusWeeks(1) == week) run + 1 else 1
            best = maxOf(best, run)
            previous = week
        }
        return best
    }

    fun unlocked(progress: BadgeProgress): List<Badge> = Badge.entries.filter { it.isUnlocked(progress) }
}
