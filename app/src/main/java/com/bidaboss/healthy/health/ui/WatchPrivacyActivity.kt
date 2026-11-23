package com.bidaboss.healthy.health.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.HealthyApplication
import com.bidaboss.healthy.ui.theme.HealthyTheme

/**
 * Ce qu'Android montre quand on touche « politique de confidentialité » dans la demande d'accès de
 * Health Connect : ce que Healthy lit, et ce qu'il en fait.
 */
class WatchPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by (application as HealthyApplication).container.appearance.theme.collectAsState()
            HealthyTheme(theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Healthy et ta montre", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Avec ton autorisation, Healthy lit dans Health Connect tes pas, tes calories actives, " +
                                "tes séances d'entraînement, ta distance, ton sommeil (avec ses phases si ta montre les envoie) et ta fréquence cardiaque.",
                        )
                        Text("Ces données restent sur ton téléphone : Healthy ne les envoie nulle part et n'a pas de compte.")
                        Text(
                            "Healthy n'écrit rien dans Health Connect. Une séance que tu ajoutes à ton journal est copiée " +
                                "dans Healthy : elle fait alors partie de ta sauvegarde.",
                        )
                        Text("Tu peux retirer l'accès à tout moment dans Health Connect, ou dans Réglages, Ma montre.")
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                    }
                }
            }
        }
    }
}
