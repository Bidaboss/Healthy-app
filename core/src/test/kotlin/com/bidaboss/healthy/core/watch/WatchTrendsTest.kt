package com.bidaboss.healthy.core.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchTrendsTest {
    @Test
    fun `les trous ne comptent pas dans la moyenne et ne sont pas des zeros`() {
        val s = WatchTrends.summary(listOf(8000.0, null, 9000.0, null), listOf(7000.0, null))
        assertEquals(8500.0, s.average)
        assertEquals(7000.0, s.previousAverage)
        assertEquals(1500.0, s.delta)
        assertEquals(2, s.daysWithData)
    }

    @Test
    fun `sans aucune donnee, pas de moyenne ni d'ecart`() {
        val s = WatchTrends.summary(listOf(null, null), listOf(5000.0))
        assertNull(s.average)
        assertNull(s.delta)
        assertEquals(0, s.daysWithData)
    }

    @Test
    fun `sans periode precedente, la moyenne existe mais pas l'ecart`() {
        val s = WatchTrends.summary(listOf(6000.0), listOf(null, null))
        assertEquals(6000.0, s.average)
        assertNull(s.previousAverage)
        assertNull(s.delta)
    }

    @Test
    fun `un ecart peut etre negatif`() {
        assertEquals(-500.0, WatchTrends.summary(listOf(7000.0), listOf(7500.0)).delta)
    }
}
