package com.bidaboss.healthy.body.data

import com.bidaboss.healthy.core.body.MaintenanceProposal

/** Tout ce que l'interface sait du maintien réel : estimation, proposition et ajustement déjà adopté. */
data class MaintenanceInfo(
    val proposal: MaintenanceProposal,
    /** Ajustement adopté, en kcal par rapport à la formule (0 = formule seule). */
    val adjustmentKcal: Int,
    /** Le niveau d'activité du profil n'est plus celui du moment où l'ajustement a été adopté. */
    val activityChanged: Boolean,
    /** « Pas maintenant » est encore actif : la carte d'Aujourd'hui reste masquée. */
    val snoozed: Boolean,
) {
    /** Carte d'Aujourd'hui : la proposition, ou le rappel de vérifier l'ajustement après un changement d'activité. */
    val showCard: Boolean get() = proposal.showCard || (activityChanged && !snoozed)
}
