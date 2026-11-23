package com.bidaboss.healthy.update.data

import com.bidaboss.healthy.BuildConfig
import com.bidaboss.healthy.core.update.AppUpdate
import com.bidaboss.healthy.core.update.DriveFolder
import com.bidaboss.healthy.core.update.RemoteApk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Résultat d'une vérification : le dossier n'est pas réglé, l'app est à jour, ou une version attend. */
sealed interface UpdateCheck {
    data object NoFolder : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Available(val apk: RemoteApk) : UpdateCheck
}

/** Ce qu'Android lit dans un APK téléchargé : nom du paquet et numéro de version. */
data class ApkIdentity(val packageName: String, val versionCode: Long)

/**
 * Cherche dans le dossier Drive partagé un APK `healthy-<numéro>.apk` plus récent que l'app, puis le
 * télécharge dans le cache et vérifie que c'est bien Healthy, à la bonne version, avant de proposer
 * l'installation. Seul réseau avec Open Food Facts (voir docs/architecture.md, « Réseau »).
 */
class UpdateRepository(
    private val store: UpdateStore,
    private val cacheDir: File,
    private val inspectApk: (File) -> ApkIdentity?,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val packageName: String = BuildConfig.APPLICATION_ID,
    private val fetchText: (String) -> String = ::httpGetText,
    private val downloadTo: (String, File, (Float) -> Unit) -> Unit = ::httpDownload,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun check(): UpdateCheck = withContext(io) {
        val folderId = DriveFolder.folderId(store.folderLink) ?: return@withContext UpdateCheck.NoFolder
        val files = DriveFolder.parseListing(fetchText(DriveFolder.listingUrl(folderId)))
        AppUpdate.latest(files, currentVersionCode)?.let { UpdateCheck.Available(it) } ?: UpdateCheck.UpToDate
    }

    /** Télécharge l'APK et le vérifie ; lève une [UpdateException] avec un message en français sinon. */
    suspend fun download(apk: RemoteApk, onProgress: (Float) -> Unit): File = withContext(io) {
        val dir = File(cacheDir, DIR).apply { mkdirs() }
        // Un seul APK gardé à la fois : les anciens téléchargements ne servent plus.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "healthy-${apk.versionCode}.apk")
        try {
            downloadTo(DriveFolder.downloadUrl(apk.fileId), file, onProgress)
        } catch (e: Exception) {
            file.delete() // pas de fichier à moitié téléchargé dans le cache
            throw e
        }
        val identity = inspectApk(file)
        when {
            identity == null -> fail(file, "Le fichier téléchargé n'est pas un APK lisible.")
            identity.packageName != packageName -> fail(file, "Ce fichier n'est pas l'app Healthy.")
            identity.versionCode != apk.versionCode.toLong() ->
                fail(file, "Le fichier ${apk.fileName} contient la version ${identity.versionCode} : renomme-le ou dépose le bon APK.")
        }
        file
    }

    private fun fail(file: File, message: String): Nothing {
        file.delete()
        throw UpdateException(message)
    }

    companion object {
        /** Dossier du cache partagé avec l'installateur (voir res/xml/update_paths.xml). */
        const val DIR = "updates"
    }
}

/**
 * Erreur dont le message, en français, peut être montré tel quel. Les autres erreurs (réseau) ne le
 * sont jamais : leur message peut contenir l'adresse du dossier, qui ne doit pas se voir dans l'app.
 */
class UpdateException(message: String) : IOException(message)

private const val USER_AGENT_PREFIX = "HealthyApp-Android/"

/** Garde-fou : un fichier inattendu dans le dossier ne doit pas remplir le téléphone. */
private const val MAX_APK_BYTES = 100L * 1024 * 1024
private const val TOO_BIG = "Le fichier du dossier dépasse 100 Mo : ce n'est pas l'APK de Healthy."

private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout = 15_000
    readTimeout = 30_000
    instanceFollowRedirects = true
    setRequestProperty("User-Agent", USER_AGENT_PREFIX + BuildConfig.VERSION_NAME)
}

private fun httpGetText(url: String): String {
    val connection = open(url)
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw UpdateException("Le dossier de mise à jour ne répond pas (code ${connection.responseCode}). Vérifie qu'il est partagé par lien.")
        }
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun httpDownload(url: String, target: File, onProgress: (Float) -> Unit) {
    val connection = open(url)
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw UpdateException("Téléchargement impossible (code ${connection.responseCode}).")
        }
        val total = connection.contentLengthLong
        if (total > MAX_APK_BYTES) throw UpdateException(TOO_BIG)
        var done = 0L
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (done > MAX_APK_BYTES) throw UpdateException(TOO_BIG)
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
    } finally {
        connection.disconnect()
    }
}
