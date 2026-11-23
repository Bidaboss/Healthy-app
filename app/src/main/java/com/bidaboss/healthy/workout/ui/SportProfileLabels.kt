package com.bidaboss.healthy.workout.ui

import com.bidaboss.healthy.workout.data.Equipment
import com.bidaboss.healthy.workout.data.PainZone
import com.bidaboss.healthy.workout.data.SportGoal
import com.bidaboss.healthy.workout.data.SportLevel

val SportLevel.label: String
    get() = when (this) {
        SportLevel.BEGINNER -> "Débutant (pas de sport depuis longtemps)"
        SportLevel.LIGHT -> "Un peu actif (marche, sport occasionnel)"
        SportLevel.REGULAR -> "Régulier (1 à 2 séances par semaine)"
        SportLevel.SPORTY -> "Sportif (3 séances ou plus par semaine)"
    }

val SportGoal.label: String
    get() = when (this) {
        SportGoal.GENERAL -> "Forme générale"
        SportGoal.STRENGTH -> "Renforcement musculaire"
        SportGoal.ENDURANCE -> "Endurance (cardio)"
        SportGoal.MOBILITY -> "Mobilité et souplesse"
    }

val PainZone.label: String
    get() = when (this) {
        PainZone.LOWER_BACK -> "Dos (lombaires)"
        PainZone.KNEES -> "Genoux"
        PainZone.SHOULDERS -> "Épaules"
        PainZone.WRISTS -> "Poignets"
        PainZone.ANKLES -> "Chevilles"
        PainZone.HIPS -> "Hanches"
        PainZone.GROIN -> "Aine (pubalgie)"
        PainZone.NECK -> "Nuque"
        PainZone.OTHER -> "Autre"
    }

val Equipment.label: String
    get() = when (this) {
        Equipment.CHAIR -> "Chaise solide"
        Equipment.WALL -> "Mur"
        Equipment.MAT -> "Tapis"
        Equipment.BAND -> "Élastique"
        Equipment.PULL_UP_BAR -> "Barre de traction"
        Equipment.DUMBBELLS -> "Haltères ou bouteilles d'eau"
        Equipment.STEP -> "Escalier ou marche"
    }
