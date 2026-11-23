package com.bidaboss.healthy.workout.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Ouvre le lien vidéo d'un exercice hors de l'app (YouTube ou le navigateur). L'app elle-même ne
 * télécharge rien : sans internet, les consignes écrites restent le secours.
 */
fun openVideo(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune app ne peut ouvrir la vidéo.", Toast.LENGTH_LONG).show()
    }
}

/** Bouton « Voir la vidéo » de l'exercice en cours. */
@Composable
fun ExerciseVideoButton(url: String) {
    val context = LocalContext.current
    OutlinedButton(onClick = { openVideo(context, url) }) {
        Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Voir la vidéo")
    }
}

/** Ligne « Plus facile : … » ou « Plus difficile : … », avec un lien « Vidéo » s'il y en a un. */
@Composable
fun VariantLine(label: String, text: String, videoUrl: String?) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label : $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        if (videoUrl != null) {
            TextButton(onClick = { openVideo(context, videoUrl) }) { Text("Vidéo") }
        }
    }
}
