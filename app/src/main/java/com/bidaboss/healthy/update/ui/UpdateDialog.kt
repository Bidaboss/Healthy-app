package com.bidaboss.healthy.update.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * Fenêtre de mise à jour : nouvelle version trouvée, téléchargement, puis installation. Android
 * demande toujours de confirmer l'installation ; la première fois, il faut aussi autoriser Healthy
 * à installer des applications. Même clé de signature : les données sont gardées.
 */
@Composable
fun UpdateDialog(state: UpdateUiState, viewModel: UpdateViewModel) {
    val context = LocalContext.current
    when (state) {
        UpdateUiState.Hidden -> Unit
        UpdateUiState.Checking -> InfoDialog("Mise à jour", "Je regarde le dossier des mises à jour…", viewModel::dismiss, "Annuler")
        UpdateUiState.UpToDate -> InfoDialog("Mise à jour", "Tu as déjà la dernière version.", viewModel::dismiss)
        UpdateUiState.NoFolder -> InfoDialog(
            "Mise à jour",
            "Cette version de l'app ne sait pas où chercher les mises à jour. Installe la dernière version à la main.",
            viewModel::dismiss,
        )
        is UpdateUiState.Failed -> InfoDialog("Mise à jour", state.message, viewModel::dismiss)
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Nouvelle version disponible") },
            text = { Text("La version ${state.apk.versionName} de Healthy est prête. Tes repas, séances et réglages sont gardés.") },
            confirmButton = { Button(onClick = { viewModel.download(state.apk) }) { Text("Mettre à jour") } },
            dismissButton = { TextButton(onClick = viewModel::later) { Text("Plus tard") } },
        )
        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Téléchargement…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Version ${state.apk.versionName}")
                    val progress = state.progress
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Annuler") } },
        )
        is UpdateUiState.ReadyToInstall -> {
            val allowed = canInstall(context)
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                title = { Text("Prêt à installer") },
                text = {
                    Text(
                        if (allowed) {
                            "Android va te demander de confirmer l'installation de la version ${state.apk.versionName}."
                        } else {
                            "Une seule fois : autorise Healthy à installer des applications, puis reviens ici et appuie sur « Installer »."
                        },
                    )
                },
                confirmButton = {
                    Button(onClick = { if (canInstall(context)) install(context, state.file) else openInstallPermission(context) }) {
                        Text(if (allowed) "Installer" else "Autoriser")
                    }
                },
                dismissButton = { TextButton(onClick = viewModel::later) { Text("Plus tard") } },
            )
        }
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onClose: () -> Unit, closeLabel: String = "OK") {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onClose) { Text(closeLabel) } },
    )
}

private fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

private fun openInstallPermission(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }
}

/** Ouvre l'installateur d'Android sur l'APK téléchargé (partagé par FileProvider). */
private fun install(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
