package com.bidaboss.healthy.core.progress

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Humeur de Pousse, la mascotte. */
enum class Mood { HAPPY, ENCOURAGING, PROUD }

/**
 * Pousse grandit avec la série de semaines actives (refonte, lot 5) : feuille (0-1 semaine),
 * jeune pousse (2-3), arbuste avec tronc (4-7), arbre en fleur (8 et plus).
 * [label] complète « Pousse est … » ; [levelUpMessage] est dit une seule fois quand le palier monte.
 */
enum class Growth(val label: String, val minWeeks: Int, val levelUpMessage: String) {
    LEAF("une feuille qui germe", 0, "Pousse germe : une première feuille !"),
    SPROUT("une jeune pousse", 2, "Pousse grandit : c'est une jeune pousse ! 2 semaines actives d'affilée."),
    SHRUB("un arbuste", 4, "Pousse a un tronc : c'est un arbuste ! 4 semaines actives d'affilée."),
    TREE("un arbre en fleur", 8, "Pousse est un arbre en fleur ! 8 semaines actives d'affilée, bravo."),
    ;

    companion object {
        fun forStreak(weeks: Int): Growth = entries.last { weeks >= it.minWeeks }

        /**
         * Message de montée de niveau : seulement si le palier [current] est au-dessus du dernier palier
         * vu [lastSeen]. Premier lancement (rien de vu) ou palier qui redescend : pas de message.
         */
        fun levelUp(lastSeen: Growth?, current: Growth): String? =
            if (lastSeen != null && current > lastSeen) current.levelUpMessage else null
    }
}

object Motivation {

    /** Lundi de la semaine de [date] (semaines du lundi au dimanche). */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Flamme : nombre de semaines d'affilée avec au moins une séance (choix du propriétaire, 11/11/2026).
     * La semaine en cours compte si elle a déjà une séance ; sinon elle ne casse pas la série tant
     * qu'elle n'est pas finie.
     */
    fun activeWeekStreak(activeDates: Collection<LocalDate>, today: LocalDate): Int {
        val activeWeeks = activeDates.filter { !it.isAfter(today) }.mapTo(HashSet()) { weekStart(it) }
        var week = weekStart(today)
        if (week !in activeWeeks) week = week.minusWeeks(1)
        var streak = 0
        while (week in activeWeeks) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    /**
     * Phrase sous la série : elle monte d'un cran par semaine, pas par séance (choix du propriétaire,
     * 13/11/2026, après la question d'Alexandrine).
     */
    fun streakHint(activeDates: Collection<LocalDate>, today: LocalDate): String {
        val monday = weekStart(today)
        val activeThisWeek = activeDates.any { !it.isBefore(monday) && !it.isAfter(today) }
        return when {
            activeThisWeek -> "Semaine validée ! Prochain cran : une séance la semaine prochaine."
            activeWeekStreak(activeDates, today) > 0 -> "Une séance avant dimanche pour garder ta série."
            else -> "Une séance cette semaine lance ta série."
        }
    }

    /**
     * Palier de Pousse. Il monte avec la série ; quand la série casse, il retombe d'un seul palier, jamais
     * sous les deux feuilles, puis remonte dès que la nouvelle série dépasse ce palier. Rejoué semaine
     * par semaine depuis la première semaine active : aucun état à enregistrer.
     */
    fun growth(activeDates: Collection<LocalDate>, today: LocalDate): Growth {
        val activeWeeks = activeDates.filter { !it.isAfter(today) }.mapTo(HashSet()) { weekStart(it) }
        val first = activeWeeks.minOrNull() ?: return Growth.LEAF
        val current = weekStart(today)
        var stage = 0
        var streak = 0
        var week = first
        while (!week.isAfter(current)) {
            when {
                week in activeWeeks -> {
                    streak++
                    stage = maxOf(stage, Growth.forStreak(streak).ordinal)
                }
                // La semaine en cours ne casse rien tant qu'elle n'est pas finie.
                week == current -> Unit
                streak > 0 -> {
                    streak = 0
                    stage = (stage - 1).coerceAtLeast(0)
                }
            }
            week = week.plusWeeks(1)
        }
        return Growth.entries[stage]
    }

    /** Fière après une séance du jour, encourageante si une séance prévue reste à faire, contente sinon. */
    fun mood(sessionDoneToday: Boolean, sessionPlannedToday: Boolean): Mood = when {
        sessionDoneToday -> Mood.PROUD
        sessionPlannedToday -> Mood.ENCOURAGING
        else -> Mood.HAPPY
    }

    /** Petite phrase sous le bonjour. */
    fun message(mood: Mood, mealCount: Int): String = when (mood) {
        Mood.PROUD -> "Séance faite, bravo !"
        Mood.ENCOURAGING -> "Allez, ta séance t'attend."
        Mood.HAPPY -> if (mealCount == 0) "Pense à noter ton premier repas." else "Belle journée en vue."
    }
}
