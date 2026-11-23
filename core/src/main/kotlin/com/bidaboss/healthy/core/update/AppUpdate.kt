package com.bidaboss.healthy.core.update

/**
 * Mises à jour de l'app depuis un dossier Google Drive partagé par lien (choix du propriétaire, 13/11/2026).
 * Le propriétaire y dépose l'APK tel que publié par GitHub Actions, nommé `healthy-<numéro>.apk` ; l'app lit
 * la liste publique du dossier, sans compte ni clé, et propose la version la plus récente.
 */
object DriveFolder {

    private val folderIdPattern = Regex("""/folders/([A-Za-z0-9_-]{10,})""")
    private val openIdPattern = Regex("""[?&]id=([A-Za-z0-9_-]{10,})""")
    private val bareIdPattern = Regex("""^[A-Za-z0-9_-]{10,}$""")

    /**
     * Identifiant du dossier à partir du lien copié dans Drive (« …/drive/folders/ID?usp=sharing »,
     * « …/open?id=ID ») ou de l'identifiant seul. null si le texte n'en contient pas.
     */
    fun folderId(link: String): String? {
        val text = link.trim()
        if (text.isEmpty()) return null
        folderIdPattern.find(text)?.let { return it.groupValues[1] }
        openIdPattern.find(text)?.let { return it.groupValues[1] }
        return text.takeIf { bareIdPattern.matches(it) }
    }

    /** Page publique qui liste le contenu d'un dossier partagé par lien. */
    fun listingUrl(folderId: String) = "https://drive.google.com/embeddedfolderview?id=$folderId#list"

    /** Téléchargement direct d'un fichier partagé, sans la page d'avertissement de Drive. */
    fun downloadUrl(fileId: String) =
        "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"

    private val entryPattern = Regex(
        """id="entry-([A-Za-z0-9_-]+)".*?class="flip-entry-title">([^<]*)<""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** Fichiers listés dans la page du dossier : identifiant et nom. */
    fun parseListing(html: String): List<DriveFile> =
        entryPattern.findAll(html).map { DriveFile(it.groupValues[1], unescape(it.groupValues[2]).trim()) }.toList()

    private fun unescape(text: String) = text
        .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
}

data class DriveFile(val id: String, val name: String)

/** Un APK trouvé dans le dossier, plus récent que l'app installée. */
data class RemoteApk(val fileId: String, val versionCode: Int, val fileName: String) {
    /** Même forme que dans Réglages : « 0.1.57 ». */
    val versionName: String get() = "0.1.$versionCode"
}

object AppUpdate {

    private val apkName = Regex("""^healthy-(\d+)\.apk$""", RegexOption.IGNORE_CASE)

    /** Numéro de version d'un fichier `healthy-<numéro>.apk`, sinon null. */
    fun versionOf(fileName: String): Int? = apkName.matchEntire(fileName.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** L'APK le plus récent du dossier s'il dépasse [currentVersionCode], sinon null. */
    fun latest(files: List<DriveFile>, currentVersionCode: Int): RemoteApk? = files
        .mapNotNull { file -> versionOf(file.name)?.let { RemoteApk(file.id, it, file.name) } }
        .filter { it.versionCode > currentVersionCode }
        .maxByOrNull { it.versionCode }
}
