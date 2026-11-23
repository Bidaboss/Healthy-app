package com.bidaboss.healthy.core.body

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pourquoi l'estimation n'est pas (encore) disponible. */
enum class EstimateStatus { READY, TOO_FEW_DAYS, TOO_FEW_WEIGHINS, UNSTABLE_WEIGHT, OUT_OF_RANGE }

enum class EstimateConfidence { MEDIUM, HIGH }

/** Résultat du calcul du maintien réel (docs/prd/maintien-reel.md, règles dans docs/decisions.md). */
data class MaintenanceEstimate(
    val status: EstimateStatus,
    /** Maintien réel estimé, arrondi à la dizaine ; null si le calcul n'a pas pu se faire. */
    val estimateKcal: Int?,
    /** Maintien de la formule seule, sans ajustement. */
    val formulaKcal: Int,
    val windowDays: Int,
    val daysCounted: Int,
    val weighInCount: Int,
    /** Apport moyen des jours pris en compte (kcal par jour), null sans jour pris en compte. */
    val averageIntakeKcal: Int?,
    /** Variation du poids lissé sur la fenêtre (kg, négatif = baisse), null si pas calculable. */
    val weightChangeKg: Double?,
    val confidence: EstimateConfidence?,
) {
    val isReady: Boolean get() = status == EstimateStatus.READY
}

/** Journée de repas : calories et nombre de repas notés. */
data class IntakeDay(val kcal: Double, val mealCount: Int)

object MaintenanceEstimator {
    const val WINDOW_DAYS = 21
    const val KCAL_PER_KG = 7700.0
    const val MIN_DAYS_SHARE = 0.8
    const val MIN_WEIGH_INS = 4
    const val MAX_WEIGHT_SPREAD_KG = 3.0
    const val MAX_GAP_FROM_FORMULA = 0.30
    const val MIN_KCAL = 1200
    const val MAX_KCAL = 4500
    const val PROPOSAL_MIN_GAP = 0.05
    const val SNOOZE_DAYS = 14L
    const val DECISION_LOCK_DAYS = 21L

    /**
     * Estime le maintien réel sur les 21 derniers jours terminés (la veille et les 20 jours d'avant).
     * Apport moyen moins variation de poids (pente des pesées lissées) × 7 700 kcal par kg.
     * Les calories de la montre ne servent pas.
     */
    fun estimate(profile: BodyProfile, weighIns: List<WeighIn>, intake: Map<LocalDate, IntakeDay>, today: LocalDate): MaintenanceEstimate {
        val formula = Maintenance.formulaKcal(profile, weighIns, today)
        val start = today.minusDays(WINDOW_DAYS.toLong())
        val end = today.minusDays(1)
        val days = (0 until WINDOW_DAYS).map { start.plusDays(it.toLong()) }

        // Un jour à moitié noté fausserait la moyenne : il faut 2 repas ou la moitié du maintien.
        val counted = days.mapNotNull { d -> intake[d]?.takeIf { it.mealCount >= 2 || it.kcal >= formula * 0.5 } }
        val average = counted.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average()
        val inWindow = weighIns.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }

        fun result(status: EstimateStatus, estimate: Int? = null, change: Double? = null, confidence: EstimateConfidence? = null) =
            MaintenanceEstimate(status, estimate, formula, WINDOW_DAYS, counted.size, inWindow.size, average?.roundToInt(), change, confidence)

        if (counted.size < Math.ceil(WINDOW_DAYS * MIN_DAYS_SHARE).toInt()) return result(EstimateStatus.TOO_FEW_DAYS)
        val firstWeek = inWindow.any { it.date.isBefore(start.plusDays(7)) }
        val lastWeek = inWindow.any { !it.date.isBefore(end.minusDays(6)) }
        if (inWindow.size < MIN_WEIGH_INS || !firstWeek || !lastWeek) return result(EstimateStatus.TOO_FEW_WEIGHINS)

        val spread = inWindow.maxOf { it.weightKg } - inWindow.minOf { it.weightKg }
        val trend = Maintenance.weightTrend(weighIns).filter { !it.first.isBefore(start) && !it.first.isAfter(end) }
        val slope = slopeKgPerDay(trend)
        val change = slope * (WINDOW_DAYS - 1)
        if (spread > MAX_WEIGHT_SPREAD_KG) return result(EstimateStatus.UNSTABLE_WEIGHT, change = change)

        val kcal = ((average!! - slope * KCAL_PER_KG) / 10).roundToInt() * 10
        val reliable = kcal in MIN_KCAL..MAX_KCAL && abs(kcal - formula).toDouble() / formula <= MAX_GAP_FROM_FORMULA
        if (!reliable) return result(EstimateStatus.OUT_OF_RANGE, change = change)

        val high = counted.size >= 19 && inWindow.size >= 8
        return result(EstimateStatus.READY, kcal, change, if (high) EstimateConfidence.HIGH else EstimateConfidence.MEDIUM)
    }

    /** Pente des moindres carrés des points (jour, poids lissé), en kg par jour ; 0 s'il y a moins de 2 jours. */
    fun slopeKgPerDay(points: List<Pair<LocalDate, Double>>): Double {
        if (points.size < 2) return 0.0
        val x0 = points.minOf { it.first }
        val xs = points.map { ChronoUnit.DAYS.between(x0, it.first).toDouble() }
        val ys = points.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return 0.0
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
    }
}

/** Ce que le propriétaire a déjà décidé : sert à ne pas reproposer en boucle. */
data class ProposalState(
    /** Jour de la dernière décision (adoption, refus ou retour à la formule). */
    val decidedOn: LocalDate? = null,
    /** « Pas maintenant » : la carte reste masquée jusqu'à ce jour. */
    val snoozedUntil: LocalDate? = null,
)

/** Ce que l'app affiche à partir de l'estimation, de l'ajustement actuel et des décisions passées. */
data class MaintenanceProposal(
    val estimate: MaintenanceEstimate,
    /** Maintien utilisé aujourd'hui (formule + ajustement). */
    val currentKcal: Int,
    /** L'estimation s'écarte d'au moins 5 % du maintien actuel et aucune décision récente ne la bloque. */
    val available: Boolean,
    /** La carte d'Aujourd'hui peut s'afficher (disponible et pas masquée par « Pas maintenant »). */
    val showCard: Boolean,
    /** Écart à enregistrer si le propriétaire adopte (estimation − formule). */
    val adjustmentKcal: Int,
    /** Date à partir de laquelle une nouvelle proposition est possible, si une décision la bloque. */
    val lockedUntil: LocalDate?,
) {
    companion object {
        fun of(estimate: MaintenanceEstimate, currentKcal: Int, state: ProposalState, today: LocalDate): MaintenanceProposal {
            val lock = state.decidedOn?.plusDays(MaintenanceEstimator.DECISION_LOCK_DAYS)?.takeIf { today.isBefore(it) }
            val est = estimate.estimateKcal
            val far = est != null && currentKcal > 0 &&
                abs(est - currentKcal).toDouble() / currentKcal >= MaintenanceEstimator.PROPOSAL_MIN_GAP - 1e-9
            val available = estimate.isReady && far && lock == null
            val snoozed = state.snoozedUntil?.let { today.isBefore(it) } ?: false
            return MaintenanceProposal(
                estimate, currentKcal, available, available && !snoozed,
                (est ?: estimate.formulaKcal) - estimate.formulaKcal, lock,
            )
        }
    }
}
