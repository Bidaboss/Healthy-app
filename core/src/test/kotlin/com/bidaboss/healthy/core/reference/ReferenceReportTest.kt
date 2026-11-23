package com.bidaboss.healthy.core.reference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceReportTest {

    private fun day(
        kcal: Int? = 2700,
        protein: Double? = 130.0,
        fiber: Double? = 30.0,
        carbs: Double? = 337.0,
        fat: Double? = 90.0,
        water: Int? = 2200,
        sport: Int = 30,
        sleep: Int? = null,
        steps: Int? = null,
    ) = RefDay(kcal, protein, fiber, carbs, fat, water, sport, sleep, steps, waterGoalMl = 2200, maintenanceKcal = 2700)

    private val man = ReferenceProfile(male = true, ageYears = 38)

    private fun line(report: ReferenceReport, id: ReferenceId) = report.lines.firstOrNull { it.reference.id == id }

    @Test
    fun `une valeur dans la zone est dans le repere, sous ou au-dessus sinon`() {
        assertEquals(ReferencePosition.IN, ReferenceReports.position(150.0, 150.0, 300.0))
        assertEquals(ReferencePosition.BELOW, ReferenceReports.position(149.0, 150.0, 300.0))
        assertEquals(ReferencePosition.ABOVE, ReferenceReports.position(301.0, 150.0, 300.0))
    }

    @Test
    fun `un minimum n'est jamais au-dessus du repere`() {
        assertEquals(ReferencePosition.IN, ReferenceReports.position(60.0, 30.0, null))
        assertEquals(ReferencePosition.BELOW, ReferenceReports.position(21.0, 30.0, null))
    }

    @Test
    fun `l'activite est ramenee a une semaine sur 7 comme sur 28 jours`() {
        val week = ReferenceReports.build(List(7) { day(sport = 30) }, 75.0, man)
        assertEquals(210.0, line(week, ReferenceId.ACTIVITY)!!.value, absoluteTolerance = 0.01)
        val month = ReferenceReports.build(List(28) { i -> day(sport = if (i < 14) 60 else 0) }, 75.0, man)
        assertEquals(210.0, line(month, ReferenceId.ACTIVITY)!!.value, absoluteTolerance = 0.01)
        assertEquals(ReferencePosition.IN, line(month, ReferenceId.ACTIVITY)!!.position)
    }

    @Test
    fun `les proteines sont en g par kg du poids, face au repere EFSA puis a l'objectif de l'app`() {
        val report = ReferenceReports.build(List(7) { day(protein = 97.5) }, 75.0, man)
        val protein = line(report, ReferenceId.PROTEIN)!!
        assertEquals(1.3, protein.value, absoluteTolerance = 0.001)
        assertEquals("1,3 g/kg", protein.valueText)
        // Au-dessus du repère officiel (0,83), sous l'objectif de l'app (1,6) : les deux se lisent, jamais confondus.
        assertEquals(ReferencePosition.IN, protein.position)
        assertEquals(ReferenceStatus.VERIFIED, protein.reference.status)
        assertEquals(ReferencePosition.BELOW, protein.appGoal!!.position)
        assertEquals("Objectif de l'app : 1,6 g/kg", protein.appGoal!!.text)
    }

    @Test
    fun `sans poids les reperes lies au poids sont masques et le profil est signale manquant`() {
        val report = ReferenceReports.build(List(7) { day() }, null, null)
        assertNull(line(report, ReferenceId.PROTEIN))
        assertTrue(report.profileMissing)
        assertNotNull(line(report, ReferenceId.FIBER))
    }

    @Test
    fun `moins de 80 pour cent de jours avec repas rend les reperes indicatifs`() {
        val days = List(10) { i -> day(kcal = if (i < 7) 2700 else null, protein = null, fiber = null) }
        assertTrue(ReferenceReports.build(days, 75.0, man).indicative)
        val enough = List(10) { i -> day(kcal = if (i < 8) 2700 else null) }
        assertFalse(ReferenceReports.build(enough, 75.0, man).indicative)
    }

    @Test
    fun `une mesure jamais notee est masquee et jamais mise a zero`() {
        val report = ReferenceReports.build(List(7) { day(kcal = null, protein = null, fiber = null, water = null, sport = 0) }, 75.0, man)
        assertTrue(report.lines.isEmpty())
    }

    @Test
    fun `les pas montrent la tendance sans zone et le sommeil son texte`() {
        val report = ReferenceReports.build(List(7) { day(sleep = 410, steps = 8400) }, 75.0, man)
        val sleep = line(report, ReferenceId.SLEEP)!!
        val steps = line(report, ReferenceId.STEPS)!!
        assertEquals(ReferencePosition.TREND, steps.position)
        assertNull(steps.zoneStart)
        assertEquals("6 h 50", sleep.valueText)
        assertEquals(ReferencePosition.BELOW, sleep.position)
    }

    @Test
    fun `les calories sont placees face au maintien a 10 pour cent pres`() {
        assertEquals(ReferencePosition.IN, line(ReferenceReports.build(List(7) { day(kcal = 2900) }, 75.0, man), ReferenceId.CALORIES)!!.position)
        assertEquals(ReferencePosition.ABOVE, line(ReferenceReports.build(List(7) { day(kcal = 3200) }, 75.0, man), ReferenceId.CALORIES)!!.position)
        assertEquals(ReferencePosition.BELOW, line(ReferenceReports.build(List(7) { day(kcal = 2200) }, 75.0, man), ReferenceId.CALORIES)!!.position)
    }

    @Test
    fun `la zone et la valeur tiennent sur l'echelle de la barre`() {
        val report = ReferenceReports.build(List(28) { day(fiber = 21.0) }, 75.0, man)
        val fiber = line(report, ReferenceId.FIBER)!!
        assertTrue(fiber.valueFraction in 0f..1f)
        assertTrue(fiber.zoneStartFraction!! < 1f)
        assertEquals(1f, fiber.zoneEndFraction!!, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `les glucides et les lipides sont une part de l'energie face aux zones EFSA`() {
        // 337 g x 4 = 1 348 kcal sur 2 700 = 50 % ; 90 g x 9 = 810 kcal = 30 %.
        val report = ReferenceReports.build(List(7) { day() }, 75.0, man)
        assertEquals(50.0, line(report, ReferenceId.CARBS)!!.value, absoluteTolerance = 0.1)
        assertEquals(ReferencePosition.IN, line(report, ReferenceId.CARBS)!!.position)
        assertEquals(30.0, line(report, ReferenceId.FAT)!!.value, absoluteTolerance = 0.1)
        val rich = ReferenceReports.build(List(7) { day(fat = 130.0) }, 75.0, man)
        assertEquals(ReferencePosition.ABOVE, line(rich, ReferenceId.FAT)!!.position)
        val noMacros = ReferenceReports.build(List(7) { day(carbs = null, fat = null) }, 75.0, man)
        assertNull(line(noMacros, ReferenceId.CARBS))
    }

    @Test
    fun `l'eau est comparee a 2,5 L pour un homme et 2 L pour une femme, avec l'objectif de l'app a cote`() {
        val days = List(7) { day(water = 2200) }
        val male = line(ReferenceReports.build(days, 75.0, man), ReferenceId.WATER)!!
        assertEquals(ReferencePosition.BELOW, male.position)
        assertEquals(ReferencePosition.IN, male.appGoal!!.position)
        val female = line(ReferenceReports.build(days, 60.0, ReferenceProfile(male = false, ageYears = 30)), ReferenceId.WATER)!!
        assertEquals(ReferencePosition.IN, female.position)
        assertTrue(References.of(ReferenceId.WATER).note!!.contains("plus large"))
    }

    @Test
    fun `le sommeil a 7 h minimum entre 18 et 64 ans, sinon la tendance seule`() {
        val days = List(7) { day(sleep = 410) }
        assertEquals(ReferencePosition.BELOW, line(ReferenceReports.build(days, 75.0, man), ReferenceId.SLEEP)!!.position)
        val enough = List(7) { day(sleep = 450) }
        assertEquals(ReferencePosition.IN, line(ReferenceReports.build(enough, 75.0, man), ReferenceId.SLEEP)!!.position)
        val senior = line(ReferenceReports.build(days, 75.0, ReferenceProfile(male = true, ageYears = 70)), ReferenceId.SLEEP)!!
        assertEquals(ReferencePosition.TREND, senior.position)
        assertNull(senior.zoneStart)
    }

    @Test
    fun `la table dit ce qui est verifie, ce que l'app choisit, et ne mesure pas ce qu'elle ne sait pas`() {
        assertEquals(ReferenceStatus.VERIFIED, References.of(ReferenceId.ACTIVITY).status)
        assertEquals(0.83, References.of(ReferenceId.PROTEIN).min!!, absoluteTolerance = 0.0)
        assertEquals(25.0, References.of(ReferenceId.FIBER).min!!, absoluteTolerance = 0.0)
        assertEquals(45.0, References.of(ReferenceId.CARBS).min!!, absoluteTolerance = 0.0)
        assertEquals(60.0, References.of(ReferenceId.CARBS).max!!, absoluteTolerance = 0.0)
        assertEquals(20.0, References.of(ReferenceId.FAT).min!!, absoluteTolerance = 0.0)
        assertEquals(35.0, References.of(ReferenceId.FAT).max!!, absoluteTolerance = 0.0)
        assertEquals(7.0, References.of(ReferenceId.SLEEP).min!!, absoluteTolerance = 0.0)
        // Le calcul des calories reste le choix de l'app ; les pas n'ont pas de source.
        assertEquals(ReferenceStatus.APP_GOAL, References.of(ReferenceId.CALORIES).status)
        assertEquals(ReferenceKind.TREND, References.of(ReferenceId.STEPS).kind)
        assertTrue(References.catalog.all { it.url == null })
        // Sodium, aliments par groupe et sucres : dans la table, jamais affichés (l'app ne les mesure pas).
        val unmeasurable = References.catalog.filter { !it.measurable }
        assertTrue(unmeasurable.any { it.name == "Sodium" })
        assertTrue(ReferenceReports.build(List(7) { day() }, 75.0, man).lines.none { !it.reference.measurable })
        assertTrue(References.catalog.none { it.name.contains("mauvais", ignoreCase = true) })
    }
}
