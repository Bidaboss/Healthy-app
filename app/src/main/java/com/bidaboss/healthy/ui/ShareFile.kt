package com.bidaboss.healthy.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Dossier du cache servi au partage par le FileProvider (voir res/xml/update_paths.xml). */
fun sharedCacheDir(context: Context): File = File(context.cacheDir, "shared").apply { mkdirs() }

/**
 * Partage un fichier du cache (dossier « shared ») par le partage Android : l'autre app le reçoit en
 * pièce jointe, sans limite de taille de texte (retour du propriétaire, 16/11/2026).
 */
fun shareFile(context: Context, file: File, mimeType: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, title))
}
