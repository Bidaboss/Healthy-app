package com.bidaboss.healthy.body.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.ui.Pousse
import com.bidaboss.healthy.ui.ThemeTexts
import com.bidaboss.healthy.ui.formatThousands
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.toInputText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/**
 * Onglet Moi (refonte, lot 4) : profil avec Pousse et maintien estimé, Mon poids avec « + Pesée », Mes badges.
 * L'icône à curseurs ouvre Réglages. Pas de photo de fond sur Moi.
 */
@Composable
fun MeScreen(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWeight: () -> Unit,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeViewModel = viewModel(
        factory = healthyViewModelFactory { MeViewModel(it.bodyRepository, it.maintenanceKcal, it.maintenanceInfo, it.meProgress) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
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
        ProfileCard(state, onOpenProfile)
        WeightCard(state, onOpenWeight, onAdd = viewModel::openWeighIn)
        BadgesCard(state, onOpenBadges)
    }
    if (state.formOpen) {
        WeighInDialog(state, viewModel::setWeight, viewModel::setWaist, viewModel::saveWeighIn, viewModel::closeWeighIn)
    }
}

@Composable
private fun ProfileCard(state: MeUiState, onOpen: () -> Unit) {
    val profile = state.profile
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Pousse(if (state.progress.unlockedBadges > 0) Mood.PROUD else Mood.HAPPY, size = 96.dp, growth = state.progress.growth)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile?.firstName?.takeIf { it.isNotBlank() } ?: "Mon profil", style = MaterialTheme.typography.titleLarge)
                Text(
                    ThemeTexts.current.streakDescription(state.progress.streakWeeks).replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profile == null) {
                    Text("Complète ton profil pour voir ton maintien.")
                } else {
                    val age = Maintenance.ageYears(profile.birthDate, state.today)
                    Text(
                        "$age ans · ${profile.heightCm.toInputText()} cm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.maintenanceKcal?.let { MaintenanceLine(it, state.maintenanceAdjusted) }
                }
            }
        }
    }
}

@Composable
private fun MaintenanceLine(kcal: Int, adjusted: Boolean) {
    Text("Maintien estimé : ${formatThousands(kcal)} kcal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    if (adjusted) {
        Text(
            "Ajusté d'après tes repas et tes pesées.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeightCard(state: MeUiState, onOpen: () -> Unit, onAdd: () -> Unit) {
    val last = state.lastWeighIn
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mon poids", style = MaterialTheme.typography.titleMedium)
            if (last == null) {
                Text("Aucune pesée pour l'instant.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${last.weightKg.toInputText()} kg", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Dernière pesée : ${last.date.format(dayFormat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.trend.size >= 2) {
                WeightCurve(state.trend)
                Text(
                    "Ligne : moyenne sur 7 jours, plus fiable que la pesée d'un seul jour.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.maintenanceKcal?.let { MaintenanceLine(it, state.maintenanceAdjusted) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text("+ Pesée") }
                OutlinedButton(onClick = onOpen) { Text("Toutes mes pesées") }
            }
        }
    }
}

/** Petite courbe de la moyenne sur 7 jours, sur les 60 derniers jours au plus. */
@Composable
private fun WeightCurve(trend: List<Pair<LocalDate, Double>>) {
    val points = trend.takeLast(60)
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val first = points.first().first
    val span = ChronoUnit.DAYS.between(first, points.last().first).coerceAtLeast(1).toFloat()
    val min = points.minOf { it.second } - 0.3
    val max = points.maxOf { it.second } + 0.3
    val description = "Courbe du poids de ${points.first().second.toInputText()} kg à ${points.last().second.toInputText()} kg " +
        "du ${points.first().first.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))} au ${points.last().first.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))}."
    Canvas(Modifier.fillMaxWidth().height(96.dp).clearAndSetSemantics { contentDescription = description }) {
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        val path = Path()
        points.forEachIndexed { i, (date, kg) ->
            val x = ChronoUnit.DAYS.between(first, date) / span * size.width
            val y = ((max - kg) / (max - min) * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun BadgesCard(state: MeUiState, onOpen: () -> Unit) {
    val progress = state.progress
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ThemeTexts.current.badgesTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                "${progress.unlockedBadges} sur ${progress.totalBadges} débloqués",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (progress.unlockedTitles.isNotEmpty()) Text(progress.unlockedTitles.take(3).joinToString(" · "))
            progress.nextBadgeTitle?.let { next ->
                Text(
                    "Prochain : $next" + (progress.nextBadgeProgress?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeighInDialog(
    state: MeUiState,
    onWeight: (String) -> Unit,
    onWaist: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val done = state.weighIns.any { it.date == state.today }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (done) "Corriger la pesée du jour" else "Pesée du jour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.form.weightKg,
                    onValueChange = onWeight,
                    label = { Text("Poids (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.form.waistCm,
                    onValueChange = onWaist,
                    label = { Text("Tour de taille (cm, facultatif)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Conseil : pèse-toi le matin, à jeun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Annuler") } },
    )
}
