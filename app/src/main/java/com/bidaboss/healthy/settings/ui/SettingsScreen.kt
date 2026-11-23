package com.bidaboss.healthy.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.health.ui.WatchSection
import com.bidaboss.healthy.ui.ListRow
import com.bidaboss.healthy.ui.ListRowCard
import com.bidaboss.healthy.update.ui.UpdateSection

/** Les écrans qui s'ouvrent depuis Réglages (un par ligne). Le [route] sert à la navigation. */
enum class SettingsSection(val route: String, val title: String) {
    APPEARANCE("appearance", "Apparence"),
    REMINDERS("reminders", "Rappels et motivation"),
    BACKUP("backup", "Sauvegarde et export"),
    WATCH("watch", "Ma montre connectée"),
    PROMPTS("prompts", "Prompts à copier"),
    UPDATES("updates", "Mises à jour");

    companion object {
        fun ofRoute(route: String?): SettingsSection? = entries.firstOrNull { it.route == route }
    }
}

/**
 * Réglages (refonte du 23/11/2026), ouverts par l'icône à curseurs de l'onglet Moi : trois groupes,
 * Personnaliser, Mes données, Aide. Chaque ligne ouvre son écran.
 */
@Composable
fun SettingsScreen(
    onOpenSection: (SettingsSection) -> Unit,
    onOpenImport: () -> Unit,
    onOpenSportProfile: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineMedium)

        GroupTitle("Personnaliser")
        ListRowCard(
            listOf(
                ListRow(Icons.Rounded.Palette, "Apparence", "Le thème et les décors") { onOpenSection(SettingsSection.APPEARANCE) },
                ListRow(Icons.Rounded.NotificationsActive, "Rappels et motivation", "Séance, eau, messages") { onOpenSection(SettingsSection.REMINDERS) },
                ListRow(Icons.Rounded.FitnessCenter, "Mon profil sportif", "Pour que ton IA prépare ton plan", onOpenSportProfile),
            ),
        )

        GroupTitle("Mes données")
        ListRowCard(
            listOf(
                ListRow(Icons.Rounded.Storage, "Sauvegarde et export", "Garder ou retrouver tes données") { onOpenSection(SettingsSection.BACKUP) },
                ListRow(Icons.Rounded.UploadFile, "Importer depuis mon IA", "Un repas, un plan ou une sauvegarde", onOpenImport),
                ListRow(Icons.Rounded.Watch, "Ma montre connectée", "Brancher ou débrancher ta montre") { onOpenSection(SettingsSection.WATCH) },
            ),
        )

        GroupTitle("Aide")
        ListRowCard(
            listOf(
                ListRow(Icons.Rounded.AutoAwesome, "Prompts à copier", "Pour préparer tes demandes à ton IA") { onOpenSection(SettingsSection.PROMPTS) },
                ListRow(Icons.Rounded.SystemUpdate, "Mises à jour", "Vérifier si une nouvelle version existe") { onOpenSection(SettingsSection.UPDATES) },
            ),
        )

        TextButton(onClick = onClose) { Text("Retour") }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

/** Le contenu d'une ligne de Réglages : les sections déjà là, dans une carte, sous un titre. */
@Composable
fun SettingsDetailScreen(
    section: SettingsSection,
    onClose: () -> Unit,
    onOpenSportProfile: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(section.title, style = MaterialTheme.typography.headlineMedium)
        when (section) {
            SettingsSection.APPEARANCE -> AppearanceScreenContent()
            SettingsSection.REMINDERS -> SettingsCard {
                ReminderSection()
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                MotivationSection()
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                WaterReminderSection()
            }
            SettingsSection.BACKUP -> SettingsCard { BackupSection() }
            SettingsSection.WATCH -> SettingsCard { WatchSection() }
            SettingsSection.PROMPTS -> SettingsCard { PromptsSection(onOpenSportProfile = onOpenSportProfile) }
            SettingsSection.UPDATES -> SettingsCard { UpdateSection(onCheckNow = onCheckUpdates) }
        }
        TextButton(onClick = onClose) { Text("Retour") }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
