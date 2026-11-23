package com.bidaboss.healthy.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.settings.data.AppearanceStore
import com.bidaboss.healthy.ui.PhotoBackgrounds
import com.bidaboss.healthy.ui.PhotoTab
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.theme.ambianceOf
import com.bidaboss.healthy.ui.theme.colorSchemeOf

class AppearanceViewModel(private val store: AppearanceStore) : ViewModel() {
    val theme = store.theme

    fun setTheme(theme: AppTheme) = store.setTheme(theme)
}

private fun AppTheme.label() = when (this) {
    AppTheme.CLASSIC -> "Classique"
    AppTheme.PIRATE -> "Pirate"
    AppTheme.TRAVEL -> "Voyage"
}

private fun AppTheme.description() = when (this) {
    AppTheme.CLASSIC -> "Vert frais, lac et sentiers. Le thème de départ."
    AppTheme.PIRATE -> "Pont de bateau, parchemin et Capitaine Pousse."
    AppTheme.TRAVEL -> "Hublot, marchés et falaises, Pousse en vadrouille."
}

/**
 * Écran Apparence (refonte du 23/11/2026) : trois thèmes, chacun avec l'aperçu de son décor d'Aujourd'hui.
 * Le choix s'applique tout de suite, sans redémarrer. Les onglets et les boutons ne changent jamais de nom.
 */
@Composable
fun AppearanceScreenContent(
    viewModel: AppearanceViewModel = viewModel(factory = healthyViewModelFactory { AppearanceViewModel(it.appearance) }),
) {
    val current by viewModel.theme.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTheme.entries.forEach { theme ->
                val selected = theme == current
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) { viewModel.setTheme(theme) },
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(),
                ) {
                    Image(
                        painter = painterResource(PhotoBackgrounds.drawableOf(PhotoTab.TODAY, theme)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        RadioButton(selected = selected, onClick = null)
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(theme.label(), style = MaterialTheme.typography.titleMedium)
                            Text(
                                theme.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PalettePreview(theme)
                    }
                }
            }
        }
        Text(
            "Le thème change les décors, les couleurs, la tenue de Pousse et les récompenses. " +
                "Les onglets et les boutons gardent les mêmes noms partout.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Trois pastilles d'aperçu de la palette du thème (en-tête, accent, fond), selon le mode clair ou sombre. */
@Composable
private fun PalettePreview(theme: AppTheme) {
    val dark = isSystemInDarkTheme()
    val scheme = colorSchemeOf(theme, dark)
    val ambiance = ambianceOf(theme)
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        listOf(ambiance.header, ambiance.accent, scheme.background).forEach { color ->
            Box(
                Modifier
                    .size(22.dp)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(color, CircleShape),
            )
        }
    }
}
