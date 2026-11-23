package com.bidaboss.healthy.body.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.body.data.MaintenanceInfo
import com.bidaboss.healthy.core.body.EstimateConfidence
import com.bidaboss.healthy.core.body.EstimateStatus
import com.bidaboss.healthy.core.body.MaintenanceEstimate
import com.bidaboss.healthy.ui.formatThousands
import java.util.Locale

/**
 * Carte d'Aujourd'hui : l'app propose un maintien plus juste, sans rien changer seule.
 * Pas de notification, pas de couleur de jugement.
 */
@Composable
fun MaintenanceCard(info: MaintenanceInfo?, onOpenDetail: () -> Unit, onLater: () -> Unit) {
    if (info == null || !info.showCard) return
    val proposal = info.proposal
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mon maintien", style = MaterialTheme.typography.titleMedium)
            if (proposal.showCard) {
                Text(
                    "Ton maintien réel semble être de ${formatThousands(proposal.estimate.estimateKcal ?: 0)} kcal, " +
                        "pas ${formatThousands(proposal.currentKcal)}.",
                )
                Text(
                    "C'est une estimation à partir de tes repas et de tes pesées. Rien ne change sans ton accord.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Tu as changé ton niveau d'activité depuis l'ajustement de ton maintien. Vérifie qu'il est toujours juste.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenDetail) { Text("Voir le détail") }
                TextButton(onClick = onLater) { Text("Pas maintenant") }
            }
        }
    }
}

/** Section « Mon maintien » de Mon profil : chiffres utilisés, et les boutons pour adopter ou revenir. */
@Composable
fun MaintenanceSection(info: MaintenanceInfo?, onAdopt: () -> Unit, onKeepFormula: () -> Unit, onRevert: () -> Unit) {
    if (info == null) return
    val proposal = info.proposal
    val estimate = proposal.estimate
    var confirmRevert by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text("Mon maintien", style = MaterialTheme.typography.titleLarge)
        Text(
            "Maintien de la formule : ${formatThousands(estimate.formulaKcal)} kcal par jour.",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (info.adjustmentKcal != 0) {
            Text(
                "Ajustement adopté : ${signed(info.adjustmentKcal)} kcal. Maintien utilisé : " +
                    "${formatThousands(proposal.currentKcal)} kcal par jour.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (info.activityChanged) {
            Text(
                "Ton niveau d'activité a changé depuis cet ajustement : vérifie qu'il est toujours juste, " +
                    "ou reviens à la formule.",
            )
        }

        EstimateBlock(estimate)

        if (proposal.available) {
            Text(
                "Ton maintien réel semble être de ${formatThousands(estimate.estimateKcal ?: 0)} kcal.",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = onAdopt, modifier = Modifier.fillMaxWidth()) {
                Text("Adopter ${formatThousands(estimate.estimateKcal ?: 0)} kcal")
            }
            OutlinedButton(onClick = onKeepFormula, modifier = Modifier.fillMaxWidth()) { Text("Garder la formule") }
        } else if (estimate.isReady && proposal.lockedUntil == null) {
            Text("Ton maintien semble juste.", color = MaterialTheme.colorScheme.primary)
        } else if (proposal.lockedUntil != null) {
            Text(
                "Tu as déjà décidé : une nouvelle proposition sera possible à partir du ${proposal.lockedUntil}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (info.adjustmentKcal != 0) {
            OutlinedButton(onClick = { confirmRevert = true }, modifier = Modifier.fillMaxWidth()) { Text("Revenir à la formule") }
        }
        Text(
            "C'est une estimation : les calories des repas photographiés sont elles-mêmes approximatives " +
                "(20 à 30 % d'écart), et le poids varie avec l'eau et le sel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmRevert) {
        AlertDialog(
            onDismissRequest = { confirmRevert = false },
            title = { Text("Revenir à la formule ?") },
            text = { Text("L'ajustement de ${signed(info.adjustmentKcal)} kcal sera retiré. Ton maintien redeviendra ${formatThousands(estimate.formulaKcal)} kcal.") },
            confirmButton = { TextButton(onClick = { confirmRevert = false; onRevert() }) { Text("Revenir à la formule") } },
            dismissButton = { TextButton(onClick = { confirmRevert = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun EstimateBlock(e: MaintenanceEstimate) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (e.status) {
        EstimateStatus.TOO_FEW_DAYS -> Text(
            "Pas encore assez de repas notés : ${e.daysCounted} jours pris en compte sur ${e.windowDays} " +
                "(il en faut 17). Note tes repas chaque jour, ou au moins 2 repas par jour.",
            color = muted,
        )

        EstimateStatus.TOO_FEW_WEIGHINS -> Text(
            "Pas encore assez de pesées : ${e.weighInCount} sur les ${e.windowDays} derniers jours " +
                "(il en faut 4, dont une dans la première semaine et une dans la dernière).",
            color = muted,
        )

        EstimateStatus.UNSTABLE_WEIGHT -> Text(
            "Ton poids a beaucoup varié sur ${e.windowDays} jours : l'estimation serait peu fiable, elle n'est pas proposée.",
            color = muted,
        )

        EstimateStatus.OUT_OF_RANGE -> Text(
            "L'estimation s'écarte trop de la formule (plus de 30 %) : des repas oubliés ou une erreur de saisie, " +
                "peut-être. Elle n'est pas proposée.",
            color = muted,
        )

        EstimateStatus.READY -> {
            Text(
                "Estimation : ${formatThousands(e.estimateKcal ?: 0)} kcal par jour. Confiance : " +
                    if (e.confidence == EstimateConfidence.HIGH) "haute." else "moyenne.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    if (e.averageIntakeKcal != null) {
        Text(
            "Sur les ${e.windowDays} derniers jours : ${e.daysCounted} jours pris en compte, ${e.weighInCount} pesées, " +
                "apport moyen ${formatThousands(e.averageIntakeKcal!!)} kcal" +
                (e.weightChangeKg?.let { ", poids ${signedKg(it)}" } ?: "") + ".",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

private fun signed(kcal: Int) = if (kcal > 0) "+$kcal" else "$kcal"

private fun signedKg(kg: Double): String {
    val text = String.format(Locale.FRANCE, "%.1f", kg)
    return (if (kg > 0) "+" else "") + text + " kg"
}
