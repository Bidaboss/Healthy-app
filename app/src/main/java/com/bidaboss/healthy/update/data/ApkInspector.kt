package com.bidaboss.healthy.update.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/** Lit le nom du paquet et le numéro de version d'un APK téléchargé, sans l'installer. */
object ApkInspector {
    fun read(context: Context, file: File): ApkIdentity? {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return null
        return ApkIdentity(info.packageName, PackageInfoCompat.getLongVersionCode(info))
    }
}
