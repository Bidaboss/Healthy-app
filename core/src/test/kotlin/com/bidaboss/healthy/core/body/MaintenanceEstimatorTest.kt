package com.bidaboss.healthy.core.body

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaintenanceEstimatorTest {

    private val today = LocalDate.of(2026, 11, 23)
    private val profile = BodyProfile(Sex.MALE, LocalDate.of(1988, 7, 15), 176.0, 75.0, ActivityLevel.ACTIVE)
    private val start = today.minusDays(21)

    private fun intake(kcal: Double, meals: Int = 3, skip: Set<Int> = emptySet()) =
        (0 until 21).filter { it !in skip }.associate { start.plusDays(it.toLong()) to IntakeDay(kcal, meals) }

    /** Une pesée tous les [every] jours, avec une variation linéaire de [perDay] kg par jour. */
    private fun weights(perDay: Double, every: Int = 2, from: Double = 75.0) =
        (0 until 21 step every).map { WeighIn(start.plusDays(it.toLong()), from + perDay * it) }

    @Test
    fun `poids stable - le maintien est l'apport moyen`() {
        val e = MaintenanceEstimator.estimate(profile, weights(0.0), intake(2900.0), today)
        assertEquals(EstimateStatus.READY, e.status)
        assertEquals(2900, e.estimateKcal)
        assertEquals(2580, e.formulaKcal)
        assertEquals(21, e.daysCounted)
        assertEquals(EstimateConfidence.HIGH, e.confidence)
    }

    @Test
    fun `perte de poids - le maintien est au dessus de l'apport`() {
        // -0,05 kg par jour = 385 kcal par jour de déficit.
        val e = MaintenanceEstimator.estimate(profile, weights(-0.05), intake(2500.0), today)
        assertEquals(EstimateStatus.READY, e.status)
        assertTrue(e.estimateKcal!! in 2800..2920, "estimation ${e.estimateKcal}")
        assertTrue(e.weightChangeKg!! < 0)
    }

    @Test
    fun `prise de poids - le maintien est sous l'apport`() {
        val e = MaintenanceEstimator.estimate(profile, weights(0.04), intake(3000.0), today)
        assertEquals(EstimateStatus.READY, e.status)
        assertTrue(e.estimateKcal!! in 2640..2740, "estimation ${e.estimateKcal}")
    }

    @Test
    fun `trop peu de jours pris en compte`() {
        // 5 jours sans repas sur 21 : 16 jours pris en compte, moins de 80 %.
        val e = MaintenanceEstimator.estimate(profile, weights(0.0), intake(2800.0, skip = setOf(1, 4, 8, 12, 15)), today)
        assertEquals(EstimateStatus.TOO_FEW_DAYS, e.status)
        assertNull(e.estimateKcal)
    }

    @Test
    fun `un jour à moitié noté n'est pas pris en compte`() {
        // 1 repas de 800 kcal : moins de 2 repas et moins de 50 % du maintien.
        val days = intake(2800.0).toMutableMap()
        listOf(0, 1, 2, 3, 4).forEach { days[start.plusDays(it.toLong())] = IntakeDay(800.0, 1) }
        assertEquals(EstimateStatus.TOO_FEW_DAYS, MaintenanceEstimator.estimate(profile, weights(0.0), days, today).status)
        // Un seul repas mais gros (plus de 50 % du maintien) : le jour compte.
        listOf(0, 1, 2, 3, 4).forEach { days[start.plusDays(it.toLong())] = IntakeDay(1500.0, 1) }
        assertEquals(21, MaintenanceEstimator.estimate(profile, weights(0.0), days, today).daysCounted)
    }

    @Test
    fun `trop peu de pesées, ou pas dans la première et la dernière semaine`() {
        val few = listOf(WeighIn(start, 75.0), WeighIn(start.plusDays(10), 75.0), WeighIn(today.minusDays(1), 75.0))
        assertEquals(EstimateStatus.TOO_FEW_WEIGHINS, MaintenanceEstimator.estimate(profile, few, intake(2800.0), today).status)
        // 4 pesées mais aucune dans la dernière semaine.
        val early = (0..3).map { WeighIn(start.plusDays(it * 3L), 75.0) }
        assertEquals(EstimateStatus.TOO_FEW_WEIGHINS, MaintenanceEstimator.estimate(profile, early, intake(2800.0), today).status)
    }

    @Test
    fun `poids très variable - peu fiable`() {
        val jumpy = weights(0.0).mapIndexed { i, w -> if (i == 4) w.copy(weightKg = 79.5) else w }
        val e = MaintenanceEstimator.estimate(profile, jumpy, intake(2800.0), today)
        assertEquals(EstimateStatus.UNSTABLE_WEIGHT, e.status)
        assertNull(e.estimateKcal)
    }

    @Test
    fun `valeur aberrante - hors de plus ou moins 30 pour cent de la formule`() {
        // Apport très élevé et poids stable : 4000 kcal est à +47 % de la formule.
        val e = MaintenanceEstimator.estimate(profile, weights(0.0), intake(4000.0), today)
        assertEquals(EstimateStatus.OUT_OF_RANGE, e.status)
        assertFalse(e.isReady)
    }

    @Test
    fun `les calories de la montre ne comptent pas et l'ajustement n'entre pas dans le seuil de jours`() {
        val adjusted = profile.copy(maintenanceAdjustmentKcal = 200)
        assertEquals(2780, Maintenance.maintenanceKcal(adjusted, emptyList(), today))
        assertEquals(2580, Maintenance.formulaKcal(adjusted, emptyList(), today))
        assertEquals(2580, MaintenanceEstimator.estimate(adjusted, weights(0.0), intake(2900.0), today).formulaKcal)
    }

    @Test
    fun `pente des moindres carrés`() {
        val pts = (0..10).map { today.plusDays(it.toLong()) to 80.0 - 0.1 * it }
        assertEquals(-0.1, MaintenanceEstimator.slopeKgPerDay(pts), 1e-9)
        assertEquals(0.0, MaintenanceEstimator.slopeKgPerDay(pts.take(1)))
    }

    private fun ready(kcal: Int) = MaintenanceEstimator.estimate(profile, weights(0.0), intake(kcal.toDouble()), today)

    @Test
    fun `proposition seulement si l'écart est d'au moins 5 pour cent`() {
        val none = ProposalState()
        assertFalse(MaintenanceProposal.of(ready(2650), 2580, none, today).available) // +2,7 %
        val p = MaintenanceProposal.of(ready(2800), 2580, none, today) // +8,5 %
        assertTrue(p.available && p.showCard)
        assertEquals(220, p.adjustmentKcal)
    }

    @Test
    fun `pas maintenant masque la carte 14 jours mais pas le détail`() {
        val snoozed = ProposalState(snoozedUntil = today.plusDays(14))
        val p = MaintenanceProposal.of(ready(2800), 2580, snoozed, today)
        assertTrue(p.available)
        assertFalse(p.showCard)
        assertTrue(MaintenanceProposal.of(ready(2800), 2580, snoozed, today.plusDays(14)).showCard)
    }

    @Test
    fun `après une décision, rien pendant 21 jours`() {
        val decided = ProposalState(decidedOn = today.minusDays(5))
        val p = MaintenanceProposal.of(ready(2800), 2580, decided, today)
        assertFalse(p.available)
        assertEquals(today.plusDays(16), p.lockedUntil)
        assertTrue(MaintenanceProposal.of(ready(2800), 2580, decided, today.plusDays(16)).available)
    }

    @Test
    fun `l'écart se mesure au maintien actuel, ajustement compris`() {
        // Déjà adopté 2800 : une estimation de 2800 ne propose rien.
        assertFalse(MaintenanceProposal.of(ready(2800), 2800, ProposalState(), today).available)
    }
}
