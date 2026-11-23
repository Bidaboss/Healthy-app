package com.bidaboss.healthy.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.settings.data.AppearanceStore
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.theme.ambianceOf
import com.bidaboss.healthy.ui.theme.colorSchemeOf
import kotlinx.coroutines.flow.StateFlow

class AppearanceViewModel(private val store: AppearanceStore) : ViewModel() {
    val theme: StateFlow<AppTheme> = store.theme

    fun setTheme(theme: AppTheme) = store.setTheme(theme)
}

private fun AppTheme.label() = when (this) {
    AppTheme.CLASSIC -> "Classique"
    AppTheme.PIRATE -> "Pirate"
    AppTheme.TRAVEL -> "Voyage"
    AppTheme.SPACE -> "Espace"
    AppTheme.ARCADE -> "Arcade rétro"
    AppTheme.MOUNTAIN -> "Montagne"
}

private fun AppTheme.description() = when (this) {
    AppTheme.CLASSIC -> "Vert frais, Pousse et confettis."
    AppTheme.PIRATE -> "Parchemin, Capitaine Pousse et pièces d'or."
    AppTheme.TRAVEL -> "Sable et océan, Pousse en vadrouille et cartes postales."
    AppTheme.SPACE -> "Bleu nuit et étoiles, Pousse astronaute et étoiles filantes."
    AppTheme.ARCADE -> "Néons sur fond noir, Pousse en pixels et pièces 8-bit."
    AppTheme.MOUNTAIN -> "Vert sapin et glacier, Pousse en bonnet et fanions."
}

/** Choix du thème : appliqué tout de suite, sans redémarrer. Les onglets et les boutons ne changent pas. */
@Composable
fun AppearanceSection(
    viewModel: AppearanceViewModel = viewModel(factory = healthyViewModelFactory { AppearanceViewModel(it.appearance) }),
) {
    val current by viewModel.theme.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.selectableGroup()) {
            AppTheme.entries.forEach { theme ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = theme == current, role = Role.RadioButton) { viewModel.setTheme(theme) }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(selected = theme == current, onClick = null)
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
        Text(
            "Les onglets et les boutons gardent les mêmes noms dans tous les thèmes.",
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
