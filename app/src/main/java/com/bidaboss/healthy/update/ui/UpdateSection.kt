package com.bidaboss.healthy.update.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.BuildConfig

/**
 * Réglages, carte « Mises à jour » : version installée et vérification à la demande. Le lien du
 * dossier n'apparaît jamais. La fenêtre de résultat est affichée par l'app.
 */
@Composable
fun UpdateSection(onCheckNow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Healthy version ${BuildConfig.VERSION_NAME}. Au lancement, l'app regarde si une nouvelle version est disponible et te la propose.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onCheckNow) { Text("Vérifier les mises à jour") }
    }
}
