package com.bidaboss.healthy.core.watch

/** Nombre de séances (plan et activités) d'une période et leur durée totale en minutes. */
data class SessionSummary(val count: Int, val minutes: Int)

/** Moyenne d'une période de la montre et écart avec la période précédente de même durée. */
data class TrendSummary(
    /** null si aucun jour de la période n'a de donnée. */
    val average: Double?,
    /** null si la période précédente n'a aucune donnée. */
    val previousAverage: Double?,
    /** Nombre de jours avec une donnée dans la période (les trous ne comptent pas). */
    val daysWithData: Int,
) {
    /** Écart avec la période précédente ; null si l'une des deux moyennes manque. */
    val delta: Double?
        get() = if (average != null && previousAverage != null) average - previousAverage else null
}

/**
 * Tendances de la montre (PRD « Ma montre ») : un jour sans donnée est un trou (null), jamais un zéro,
 * et ne compte pas dans la moyenne. Aucun seuil, aucun jugement : on montre, c'est tout.
 */
object WatchTrends {
    fun summary(current: List<Double?>, previous: List<Double?>): TrendSummary {
        val now = current.filterNotNull()
        return TrendSummary(
            average = now.takeIf { it.isNotEmpty() }?.average(),
            previousAverage = previous.filterNotNull().takeIf { it.isNotEmpty() }?.average(),
            daysWithData = now.size,
        )
    }
}
