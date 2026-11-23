package com.bidaboss.healthy.core.watch

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import java.time.LocalDateTime

class SleepTest {

    private val night = listOf(
        SleepSegment(SleepStage.AWAKE, 5),
        SleepSegment(SleepStage.LIGHT, 45),
        SleepSegment(SleepStage.DEEP, 30),
        SleepSegment(SleepStage.AWAKE, 4),
        SleepSegment(SleepStage.REM, 20),
        SleepSegment(SleepStage.LIGHT, 60),
        SleepSegment(SleepStage.AWAKE, 6),
    )

    @Test
    fun `les phases font l'aller-retour dans une chaine`() {
        val text = SleepStages.encode(night)
        assertEquals("A5,L45,D30,A4,R20,L60,A6", text)
        assertEquals(night, SleepStages.decode(text))
    }

    @Test
    fun `une chaine abimee ou vide ne plante pas`() {
        assertEquals(emptyList<SleepSegment>(), SleepStages.decode(null))
        assertEquals(emptyList<SleepSegment>(), SleepStages.decode(""))
        assertEquals(
            listOf(SleepSegment(SleepStage.DEEP, 30), SleepSegment(SleepStage.REM, 10)),
            SleepStages.decode("D30,X5,L,L0,L-4,,Rabc,R10"),
        )
    }

    @Test
    fun `les reveils ne comptent ni l'endormissement ni le lever`() {
        assertEquals(1, SleepStages.awakenings(night))
        assertEquals(0, SleepStages.awakenings(listOf(SleepSegment(SleepStage.LIGHT, 60))))
        assertEquals(0, SleepStages.awakenings(listOf(SleepSegment(SleepStage.AWAKE, 20))))
        assertEquals(0, SleepStages.awakenings(emptyList()))
    }

    @Test
    fun `les durees par phase s'additionnent`() {
        val totals = SleepStages.totals(night)
        assertEquals(105, totals[SleepStage.LIGHT])
        assertEquals(30, totals[SleepStage.DEEP])
        assertEquals(20, totals[SleepStage.REM])
        assertEquals(15, totals[SleepStage.AWAKE])
    }

    @Test
    fun `des morceaux voisins de meme phase sont regroupes`() {
        val merged = SleepStages.merge(
            listOf(
                SleepSegment(SleepStage.LIGHT, 10),
                SleepSegment(SleepStage.LIGHT, 15),
                SleepSegment(SleepStage.DEEP, 0),
                SleepSegment(SleepStage.DEEP, 20),
            ),
        )
        assertEquals(listOf(SleepSegment(SleepStage.LIGHT, 25), SleepSegment(SleepStage.DEEP, 20)), merged)
    }

    @Test
    fun `une nuit sans aucune phase de sommeil n'a pas de phases a montrer`() {
        assertFalse(SleepStages.hasPhases(emptyList()))
        assertFalse(SleepStages.hasPhases(listOf(SleepSegment(SleepStage.AWAKE, 30))))
        assertTrue(SleepStages.hasPhases(night))
    }

    @Test
    fun `la regularite du coucher a besoin d'au moins trois nuits`() {
        val two = listOf(LocalDateTime.of(2026, 11, 20, 23, 0), LocalDateTime.of(2026, 11, 21, 23, 0))
        assertNull(BedtimeRegularity.spreadMinutes(two))
    }

    @Test
    fun `des couchers a la meme heure donnent un ecart nul`() {
        val same = (20..22).map { LocalDateTime.of(2026, 11, it, 23, 0) }
        assertEquals(0, BedtimeRegularity.spreadMinutes(same))
    }

    @Test
    fun `23 h 30 et 0 h 30 sont voisins, pas distants de 23 heures`() {
        val nights = listOf(
            LocalDateTime.of(2026, 11, 20, 23, 30),
            LocalDateTime.of(2026, 11, 22, 0, 30),
            LocalDateTime.of(2026, 11, 22, 23, 30),
        )
        // Écarts à 23 h 30 : 0, +60, 0 -> moyenne 20, écart-type = sqrt(((20)^2 + 40^2 + 20^2) / 3) = 28,28
        assertEquals(28, BedtimeRegularity.spreadMinutes(nights))
    }
}
