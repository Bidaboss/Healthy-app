package com.bidaboss.healthy.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.health.ui.WatchSection
import com.bidaboss.healthy.update.ui.UpdateSection

@Composable
fun SettingsScreen(
    onOpenImport: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWeight: () -> Unit,
    onOpenSportProfile: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineMedium)

        SettingsCard(Icons.Rounded.Person, "Profil") {
            Text("Sexe, âge, taille, poids et activité : ils servent à calculer tes calories de maintien.")
            Button(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) { Text("Mon profil") }
            OutlinedButton(onClick = onOpenWeight, modifier = Modifier.fillMaxWidth()) { Text("Poids et tour de taille") }
        }

        SettingsCard(Icons.Rounded.Palette, "Apparence") { AppearanceSection() }

        SettingsCard(Icons.Rounded.NotificationsActive, "Rappels et motivation") {
            ReminderSection()
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            MotivationSection()
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            WaterReminderSection()
        }

        SettingsCard(Icons.Rounded.Watch, "Ma montre") { WatchSection() }

        SettingsCard(Icons.Rounded.Storage, "Données et sauvegarde") {
            OutlinedButton(onClick = onOpenImport, modifier = Modifier.fillMaxWidth()) {
                Text("Importer un repas, un plan ou une sauvegarde")
            }
            BackupSection()
        }

        SettingsCard(Icons.Rounded.AutoAwesome, "Préparer mes demandes à ton IA") {
            PromptsSection(onOpenSportProfile = onOpenSportProfile)
        }

        SettingsCard(Icons.Rounded.SystemUpdate, "Mises à jour") {
            UpdateSection(onCheckNow = onCheckUpdates)
        }
    }
}

/** Une carte de Réglages : icône, titre, puis le contenu (fil Design, 12/11/2026). */
@Composable
private fun SettingsCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}
