package com.bidaboss.healthy.health.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.health.data.HealthConnectSource
import com.bidaboss.healthy.ui.PhotoBackground
import com.bidaboss.healthy.ui.PhotoTab
import com.bidaboss.healthy.ui.PhotoTitle
import com.bidaboss.healthy.ui.healthyViewModelFactory

/**
 * Onglet Santé (refonte, lot 4) : 7 jours (par défaut), Sommeil, Montre et Bilan. Sans montre connectée, Sommeil et
 * Montre disparaissent et une carte « Ajouter une montre » invite à la connecter. [initialSection] : la partie ouverte
 * au départ (« Ma montre » d'Aujourd'hui ouvre Montre).
 */
@Composable
fun HealthScreen(
    onOpenWater: () -> Unit,
    onOpenRecap: () -> Unit,
    modifier: Modifier = Modifier,
    initialSection: HealthSection = HealthSection.WEEK,
    viewModel: HealthViewModel = viewModel(
        factory = healthyViewModelFactory { HealthViewModel(it.watchRepository, it.healthWeek, it.weeklyRecap) },
    ),
    trendsViewModel: WatchTrendsViewModel = viewModel(
        factory = healthyViewModelFactory { WatchTrendsViewModel(it.watchRepository, it.sessionSummary) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(initialSection) }
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectSource.permissionContract()) { viewModel.onResume() }
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val connect = { permissionLauncher.launch(HealthConnectSource.PERMISSIONS) }
    // Si la partie choisie disparaît (montre déconnectée), retour à 7 jours.
    val shown = if (selected in state.sections || state.status == null) selected else HealthSection.WEEK

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PhotoBackground(PhotoTab.HEALTH) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoTitle("Santé", modifier = Modifier.padding(top = 24.dp, bottom = 48.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.sections) { section ->
                        FilterChip(selected = shown == section, onClick = { selected = section }, label = { Text(section.label) })
                    }
                }
                when (shown) {
                    HealthSection.WEEK -> WeekSection(state, onOpenWater, connect)
                    HealthSection.SLEEP -> SleepSection(state, connect)
                    HealthSection.WATCH -> WatchTab(state, trendsViewModel, connect)
                    HealthSection.RECAP -> RecapSection(state.recap, onOpenRecap)
                }
            }
        }
    }
}
