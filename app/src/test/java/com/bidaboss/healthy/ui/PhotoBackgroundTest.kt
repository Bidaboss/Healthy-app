package com.bidaboss.healthy.ui

import com.bidaboss.healthy.settings.data.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Les 12 décors de la refonte : un par onglet et par thème, tous différents, et assez légers pour l'APK. */
class PhotoBackgroundTest {

    @Test
    fun `chaque onglet et chaque theme a son decor, sans doublon`() {
        val ids = AppTheme.entries.flatMap { theme -> PhotoTab.entries.map { tab -> PhotoBackgrounds.drawableOf(tab, theme) } }
        assertEquals(12, ids.size)
        assertEquals("Un décor est utilisé deux fois", 12, ids.toSet().size)
        assertTrue(ids.all { it != 0 })
    }

    @Test
    fun `les decors pesent moins de 3 Mo en tout`() {
        val dir = listOf("src/main/res/drawable-nodpi", "app/src/main/res/drawable-nodpi").map(::File).first { it.isDirectory }
        val files = dir.listFiles { f -> f.name.startsWith("decor_") }.orEmpty()
        assertEquals(12, files.size)
        assertTrue("Poids total : ${files.sumOf { it.length() }} octets", files.sumOf { it.length() } < 3_000_000)
    }
}
