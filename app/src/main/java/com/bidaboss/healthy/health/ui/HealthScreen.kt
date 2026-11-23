package com.bidaboss.healthy.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.ui.ListRow
import com.bidaboss.healthy.ui.ListRowCard
import com.bidaboss.healthy.ui.PhotoBackground
import com.bidaboss.healthy.ui.PhotoTab
import com.bidaboss.healthy.ui.PhotoTitle

/**
 * Onglet Santé : coquille de la refonte (lot 0). Le lot 4 y met les 7 jours, le sommeil, la montre et le bilan ;
 * en attendant, elle garde accessibles les écrans de santé déjà là.
 */
@Composable
fun HealthScreen(
    onOpenWatch: () -> Unit,
    onOpenWater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PhotoBackground(PhotoTab.HEALTH) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PhotoTitle("Santé", modifier = Modifier.padding(top = 24.dp, bottom = 96.dp))
                ListRowCard(
                    listOf(
                        ListRow(Icons.Rounded.Watch, "Ma montre", "Pas, calories, sommeil et cœur", onOpenWatch),
                        ListRow(Icons.Rounded.WaterDrop, "Mon eau", "Ce que tu as bu, jour après jour", onOpenWater),
                    ),
                )
            }
        }
    }
}
