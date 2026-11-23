package com.bidaboss.healthy.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Lecture du dossier Drive des mises à jour et choix de l'APK le plus récent. */
class AppUpdateTest {

    private val id = "1AbCdEfGhIjKlMnOpQrStUv"

    @Test
    fun `l'identifiant du dossier se lit dans les liens copies depuis Drive`() {
        assertEquals(id, DriveFolder.folderId("https://drive.google.com/drive/folders/$id?usp=sharing"))
        assertEquals(id, DriveFolder.folderId("https://drive.google.com/drive/u/0/folders/$id"))
        assertEquals(id, DriveFolder.folderId("https://drive.google.com/open?id=$id"))
        assertEquals(id, DriveFolder.folderId("  $id "))
        assertNull(DriveFolder.folderId(""))
        assertNull(DriveFolder.folderId("pas un lien"))
    }

    // Extrait réduit de la page publique d'un dossier partagé (embeddedfolderview).
    private val html = """
        <div class="flip-entries">
          <div class="flip-entry" id="entry-FILE_A_123456" tabindex="0"><div class="flip-entry-info">
            <a href="https://drive.google.com/file/d/FILE_A_123456/view?usp=drive_web" target="_blank">
            <div class="flip-entry-title">healthy-55.apk</div></a></div></div>
          <div class="flip-entry" id="entry-FILE_B_123456" tabindex="0"><div class="flip-entry-info">
            <a href="https://drive.google.com/file/d/FILE_B_123456/view?usp=drive_web" target="_blank">
            <div class="flip-entry-title">healthy-57.apk</div></a></div></div>
          <div class="flip-entry" id="entry-FILE_C_123456" tabindex="0"><div class="flip-entry-info">
            <div class="flip-entry-title">Notes &amp; idées.txt</div></div></div>
        </div>
    """.trimIndent()

    @Test
    fun `la page du dossier donne les fichiers et leurs identifiants`() {
        assertEquals(
            listOf(
                DriveFile("FILE_A_123456", "healthy-55.apk"),
                DriveFile("FILE_B_123456", "healthy-57.apk"),
                DriveFile("FILE_C_123456", "Notes & idées.txt"),
            ),
            DriveFolder.parseListing(html),
        )
    }

    @Test
    fun `on propose le plus recent, seulement s'il depasse la version installee`() {
        val files = DriveFolder.parseListing(html)
        assertEquals(RemoteApk("FILE_B_123456", 57, "healthy-57.apk"), AppUpdate.latest(files, 50))
        assertEquals("0.1.57", AppUpdate.latest(files, 50)?.versionName)
        assertNull(AppUpdate.latest(files, 57))
        assertNull(AppUpdate.latest(emptyList(), 1))
    }

    @Test
    fun `seuls les fichiers healthy-numero apk comptent`() {
        assertEquals(57, AppUpdate.versionOf("healthy-57.apk"))
        assertEquals(57, AppUpdate.versionOf("Healthy-57.APK"))
        assertNull(AppUpdate.versionOf("app-release.apk"))
        assertNull(AppUpdate.versionOf("healthy-57 (1).apk"))
        assertNull(AppUpdate.versionOf("healthy-57.apk.zip"))
    }
}
