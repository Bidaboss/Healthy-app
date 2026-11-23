package com.bidaboss.healthy.body.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.ui.ListRow
import com.bidaboss.healthy.ui.ListRowCard

/**
 * Onglet Moi : coquille de la refonte (lot 0). Le lot 4 y met le profil, Mon poids et Mes badges ; en
 * attendant, elle garde accessibles les écrans déjà là. L'icône à curseurs ouvre Réglages.
 */
@Composable
fun MeScreen(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWeight: () -> Unit,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text("Moi", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterStart))
            IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Rounded.Tune, contentDescription = "Réglages")
            }
        }
        ListRowCard(
            listOf(
                ListRow(Icons.Rounded.Person, "Mon profil", "Âge, taille, poids et activité", onOpenProfile),
                ListRow(Icons.Rounded.MonitorWeight, "Mon poids", "Pesées et tour de taille", onOpenWeight),
                ListRow(Icons.Rounded.EmojiEvents, "Mes badges", null, onOpenBadges),
            ),
        )
    }
}
