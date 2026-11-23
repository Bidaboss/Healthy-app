package com.bidaboss.healthy.today.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.reference.ReferenceLine
import com.bidaboss.healthy.core.reference.ReferencePosition
import com.bidaboss.healthy.core.reference.ReferenceReport
import com.bidaboss.healthy.ui.healthyViewModelFactory

private fun referencesViewModelFactory(days: Int) = healthyViewModelFactory {
    ReferencesViewModel(it.referencesRepository::observe, initialDays = days)
}

/** « Mes repères » : chaque valeur du propriétaire face au repère, la source écrite sous chaque ligne (PRD repères nationaux). */
@Composable
fun ReferencesScreen(
    onClose: () -> Unit,
    viewModel: ReferencesViewModel = viewModel(key = "references-screen", factory = referencesViewModelFactory(ReferencesViewModel.DEFAULT_DAYS)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onClose) { Text("Retour") }
        Text("Mes repères", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReferencesViewModel.PERIODS.forEach { days ->
                FilterChip(
                    selected = state.periodDays == days,
                    onClick = { viewModel.setPeriod(days) },
                    label = { Text("$days jours") },
                )
            }
        }
        Text(
            "Moyenne par jour sur la période. Ce sont des repères, pas un avis médical : aucun n'est une alerte.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val report = state.report
        when {
            report == null -> Unit
            report.lines.isEmpty() -> EmptyReferences(report)
            else -> {
                if (report.indicative) Notice("Peu de repas notés sur ces ${report.periodDays} jours : les repères sont indicatifs.")
                if (report.profileMissing) Notice("Remplis ton profil (poids, sexe, âge) pour voir les repères liés à ton poids.")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        report.lines.forEach { ReferenceRow(it, detailed = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReferences(report: ReferenceReport) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Rien à comparer pour l'instant.", fontWeight = FontWeight.SemiBold)
            Text(
                "Note quelques repas, de l'eau ou des séances : tes repères apparaîtront ici." +
                    if (report.profileMissing) " Ton profil (poids, sexe, âge) permet aussi de calculer ceux liés au poids." else "",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Card(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
}

/** Une ligne : nom, valeur, barre avec la zone du repère, mot de position, puis (écran complet) source et statut. */
@Composable
fun ReferenceRow(line: ReferenceLine, detailed: Boolean) {
    val ref = line.reference
    val spoken = buildString {
        append("${ref.name} : ${line.valueText}. ")
        if (line.position != ReferencePosition.TREND) append("${line.position.label}, ${line.ruleText}. ") else append("Tendance, sans repère officiel. ")
        line.appGoal?.let { append("${it.text}. ") }
        if (detailed) append("${ref.status.label}.")
    }
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(ref.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(line.valueText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        ReferenceBar(line)
        val inRef = line.position == ReferencePosition.IN
        Text(
            if (line.position == ReferencePosition.TREND) "tendance · ${line.ruleText}" else "${line.ruleText} · ${line.position.label}",
            style = MaterialTheme.typography.bodySmall,
            color = if (inRef) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        line.appGoal?.let { goal ->
            Text(
                "${goal.text} · ${if (goal.position == ReferencePosition.IN) "atteint" else "pas encore atteint"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (detailed) {
            Text(
                "${ref.source}  [${ref.status.label}${ref.readOn?.let { ", lu le $it" }.orEmpty()}]",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ref.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Piste, zone du repère (contour), et point de la valeur. Aucune couleur d'alerte : primaire dans le repère, neutre sinon. */
@Composable
private fun ReferenceBar(line: ReferenceLine) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val zone = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val zoneLine = MaterialTheme.colorScheme.primary
    val dot = if (line.position == ReferencePosition.IN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.surface
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val barH = 10.dp.toPx()
        val top = (size.height - barH) / 2
        val corner = CornerRadius(barH / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, barH), corner)
        val start = line.zoneStartFraction
        val end = line.zoneEndFraction
        if (start != null && end != null && end > start) {
            val x = size.width * start
            val w = size.width * (end - start)
            drawRoundRect(zone, Offset(x, top), Size(w, barH), corner)
            drawRoundRect(zoneLine, Offset(x, top), Size(w, barH), corner, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        val cx = size.width * line.valueFraction
        drawCircle(ring, 9.dp.toPx(), Offset(cx.coerceIn(9.dp.toPx(), size.width - 9.dp.toPx()), size.height / 2))
        drawCircle(dot, 6.dp.toPx(), Offset(cx.coerceIn(9.dp.toPx(), size.width - 9.dp.toPx()), size.height / 2))
    }
}

/** Carte « Tes repères · 28 jours » d'Aujourd'hui : trois lignes au plus, et le bouton vers l'écran complet. */
@Composable
fun ReferencesCard(
    onOpen: () -> Unit,
    viewModel: ReferencesViewModel = viewModel(key = "references-card", factory = referencesViewModelFactory(ReferencesViewModel.DEFAULT_DAYS)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val report = state.report ?: return
    // Priorité aux repères qui parlent le plus : protéines, fibres, activité, puis les autres lignes disponibles.
    val order = listOf(
        com.bidaboss.healthy.core.reference.ReferenceId.PROTEIN,
        com.bidaboss.healthy.core.reference.ReferenceId.FIBER,
        com.bidaboss.healthy.core.reference.ReferenceId.ACTIVITY,
    )
    val lines = (order.mapNotNull { id -> report.lines.firstOrNull { it.reference.id == id } } +
        report.lines.filterNot { l -> order.contains(l.reference.id) }).take(3)
    if (lines.isEmpty()) return
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().semantics { }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tes repères · ${state.periodDays} jours", style = MaterialTheme.typography.titleMedium)
            if (report.indicative) {
                Text("Peu de repas notés : repères indicatifs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            lines.forEach { ReferenceRow(it, detailed = false) }
            TextButton(onClick = onOpen, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Voir tous les repères") }
        }
    }
}
