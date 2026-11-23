package com.bidaboss.healthy.update.data

import android.content.Context
import androidx.core.content.edit
import com.bidaboss.healthy.BuildConfig

/**
 * Lien du dossier Drive des mises à jour et heure de la dernière vérification automatique.
 * Le lien est fourni avec l'app ([BuildConfig.UPDATE_FOLDER_LINK]) : il n'est ni affiché ni modifiable
 * dans l'app, un changement passe par le code (demande du propriétaire, 13/11/2026).
 */
class UpdateStore(context: Context, val folderLink: String = BuildConfig.UPDATE_FOLDER_LINK) {
    private val prefs = context.applicationContext.getSharedPreferences("update", Context.MODE_PRIVATE)

    var lastAutoCheckEpochMs: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_CHECK, value) }

    /** Version mise de côté avec « Plus tard », et quand. */
    var snoozedVersionCode: Int
        get() = prefs.getInt(KEY_SNOOZED_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_SNOOZED_VERSION, value) }

    var snoozedAtEpochMs: Long
        get() = prefs.getLong(KEY_SNOOZED_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_SNOOZED_AT, value) }

    private companion object {
        const val KEY_LAST_CHECK = "last_auto_check"
        const val KEY_SNOOZED_VERSION = "snoozed_version"
        const val KEY_SNOOZED_AT = "snoozed_at"
    }
}
