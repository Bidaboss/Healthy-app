package com.bidaboss.healthy.body.ui

import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.Sex

val Sex.label: String
    get() = when (this) {
        Sex.MALE -> "Homme"
        Sex.FEMALE -> "Femme"
    }

/** Libellé et exemple simple pour chaque niveau d'activité (hors séances du plan). */
val ActivityLevel.label: String
    get() = when (this) {
        ActivityLevel.SEDENTARY -> "Sédentaire : assis presque toute la journée"
        ActivityLevel.LIGHT -> "Un peu actif : un peu de marche chaque jour"
        ActivityLevel.ACTIVE -> "Actif : debout ou en mouvement une bonne partie de la journée"
        ActivityLevel.VERY_ACTIVE -> "Très actif : métier physique"
    }
