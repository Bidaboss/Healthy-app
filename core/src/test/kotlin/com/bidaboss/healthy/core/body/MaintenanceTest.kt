package com.bidaboss.healthy.core.body

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaintenanceTest {

    private val today = LocalDate.of(2026, 11, 11)
    private val profile = BodyProfile(Sex.MALE, birthDate = LocalDate.of(1988, 7, 15), heightCm = 176.0, weightKg = 75.0, activity = ActivityLevel.ACTIVE)

    @Test
    fun `formule de Mifflin-St Jeor`() {
        // Homme 75 kg, 176 cm, 38 ans : 750 + 1100 - 190 + 5 = 1665.
        assertEquals(1665.0, Maintenance.basalKcal(Sex.MALE, 75.0, 176.0, 38))
        // Femme 60 kg, 165 cm, 30 ans : 600 + 1031,25 - 150 - 161 = 1320,25.
        assertEquals(1320.25, Maintenance.basalKcal(Sex.FEMALE, 60.0, 165.0, 30))
    }

    @Test
    fun `l'age se met a jour a l'anniversaire`() {
        val birth = LocalDate.of(1988, 11, 12)
        assertEquals(37, Maintenance.ageYears(birth, today))
        assertEquals(38, Maintenance.ageYears(birth, today.plusDays(1)))
    }

    @Test
    fun `maintien avec le poids du profil quand il n'y a pas de pesee`() {
        // 1665 x 1,55 = 2580,75, arrondi à 2580.
        assertEquals(2580, Maintenance.maintenanceKcal(profile, emptyList(), today))
    }

    @Test
    fun `maintien recalcule sur la moyenne des pesees des 7 derniers jours`() {
        val weighIns = listOf(
            WeighIn(today.minusDays(10), 85.0), // trop ancienne : ignorée
            WeighIn(today.minusDays(6), 74.0),
            WeighIn(today, 72.0),
        )
        assertEquals(73.0, Maintenance.referenceWeightKg(75.0, weighIns, today))
        // 10 x 73 + 1100 - 190 + 5 = 1645 ; x 1,55 = 2549,75 -> 2550.
        assertEquals(2550, Maintenance.maintenanceKcal(profile, weighIns, today))
    }

    @Test
    fun `sans pesee recente, la derniere pesee sert`() {
        val weighIns = listOf(WeighIn(today.minusDays(30), 77.0), WeighIn(today.minusDays(20), 76.0))
        assertEquals(76.0, Maintenance.referenceWeightKg(75.0, weighIns, today))
    }

    @Test
    fun `couleurs autour du maintien`() {
        assertEquals(Band.GREEN, Maintenance.band(2000.0, 2000))
        assertEquals(Band.GREEN, Maintenance.band(2200.0, 2000))
        assertEquals(Band.GREEN, Maintenance.band(1800.0, 2000))
        assertEquals(Band.ORANGE, Maintenance.band(2201.0, 2000))
        assertEquals(Band.ORANGE, Maintenance.band(1600.0, 2000))
        assertEquals(Band.RED, Maintenance.band(1599.0, 2000))
        assertEquals(Band.RED, Maintenance.band(2500.0, 2000))
        // Garde-fou : jamais de division par zéro.
        assertNull(Maintenance.band(2000.0, 0))
        assertNull(Maintenance.band(2000.0, -5))
    }

    @Test
    fun `moyenne sur 7 jours sans compter les jours sans repas`() {
        val days = mapOf(
            today to 2000.0,
            today.minusDays(1) to 0.0, // rien noté
            today.minusDays(3) to 2400.0,
            today.minusDays(7) to 5000.0, // hors fenêtre
        )
        assertEquals(2200.0, Maintenance.averageKcal(days, today))
        assertNull(Maintenance.averageKcal(emptyMap(), today))
    }

    @Test
    fun `cible de proteines`() {
        assertEquals(125, Maintenance.proteinTargetG(78.0))
        // 2710 kcal : 30 % en lipides = 90 g ; le reste en glucides après 160 g de protéines = 315 g.
        assertEquals(90, Maintenance.fatTargetG(2710))
        assertEquals(315, Maintenance.carbsTargetG(2710, 160))
        assertEquals(0, Maintenance.carbsTargetG(1000, 300))
    }

    @Test
    fun `courbe lissee du poids`() {
        val trend = Maintenance.weightTrend(
            listOf(WeighIn(today.minusDays(2), 80.0), WeighIn(today, 78.0), WeighIn(today.minusDays(9), 84.0)),
        )
        assertEquals(listOf(today.minusDays(9) to 84.0, today.minusDays(2) to 80.0, today to 79.0), trend)
    }

    @Test
    fun `le jour en cours reste neutre sauf s'il depasse deja nettement le maintien`() {
        assertEquals(null, Maintenance.bandSoFar(740.0, 2740))
        assertEquals(null, Maintenance.bandSoFar(2900.0, 2740))
        assertEquals(Band.ORANGE, Maintenance.bandSoFar(3100.0, 2740))
        assertEquals(Band.RED, Maintenance.bandSoFar(3400.0, 2740))
    }
}

class HydrationTest {
    @Test
    fun `exemples du fil nutrition`() {
        // Femme de 60 kg : (1600 + 1800) / 2 = 1700 ; homme de 80 kg : (2000 + 2400) / 2 = 2200.
        assertEquals(1700, Hydration.targetMl(Sex.FEMALE, 60.0, sessionsToday = 0))
        assertEquals(2200, Hydration.targetMl(Sex.MALE, 80.0, sessionsToday = 0))
    }

    @Test
    fun `bornes puis seance en plus`() {
        assertEquals(1500, Hydration.targetMl(Sex.FEMALE, 40.0, 0))
        assertEquals(3000, Hydration.targetMl(Sex.MALE, 200.0, 0))
        // Les 0,5 L de la séance s'ajoutent après les bornes.
        assertEquals(3500, Hydration.targetMl(Sex.MALE, 200.0, 1))
        assertEquals(3200, Hydration.targetMl(Sex.MALE, 80.0, 2))
    }

    @Test
    fun `affichage en litres`() {
        assertEquals("1,7 L", Hydration.formatLiters(1700))
        assertEquals("1,98 L", Hydration.formatLiters(1980))
        assertEquals("1,75 L", Hydration.formatLiters(1750))
        assertEquals("2 L", Hydration.formatLiters(2000))
        // Un verre de 25 cL s'affichait « 0,3 L » (retour du propriétaire, 12/11/2026).
        assertEquals("25 cL", Hydration.formatLiters(250))
        assertEquals("20 cL", Hydration.formatLiters(Hydration.GLASS_ML))
        assertEquals("15 cL", Hydration.formatLiters(Hydration.COFFEE_ML))
        assertEquals("25 cL", Hydration.formatLiters(Hydration.TEA_ML))
        assertEquals("99 cL", Hydration.formatLiters(994))
        assertEquals("1 L", Hydration.formatLiters(997))
        assertEquals("75 cL", Hydration.formatLiters(750))
        assertEquals("0 cL", Hydration.formatLiters(0))
    }
}
