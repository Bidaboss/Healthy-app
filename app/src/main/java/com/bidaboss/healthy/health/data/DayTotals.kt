package com.bidaboss.healthy.health.data

import java.time.LocalDate

/**
 * Ce qui a été noté un jour dans l'app, pour les cartes « 7 jours » de Santé. Un jour sans rien noté est un trou
 * (null), jamais un zéro : on ne parle que des jours notés.
 */
data class DayTotals(
    val date: LocalDate,
    /** Calories des repas notés, null si aucun repas ce jour-là. */
    val kcal: Int?,
    /** Eau bue en mL, null si rien noté. */
    val waterMl: Int?,
    /** Séances du plan et activités de ce jour (leur nombre et leur durée en minutes). */
    val sessions: Int,
    val sportMinutes: Int,
)
