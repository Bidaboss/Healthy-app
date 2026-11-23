package com.bidaboss.healthy.core.body

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Sex(val code: String) {
    MALE("homme"),
    FEMALE("femme"),
    ;

    companion object {
        fun fromCode(code: String?): Sex? = entries.firstOrNull { it.code == code }
    }
}

/** Niveau d'activité habituel, hors séances du plan (le sport n'ajoute pas de calories à l'objectif). */
enum class ActivityLevel(val code: String, val factor: Double) {
    SEDENTARY("sedentaire", 1.2),
    LIGHT("un_peu_actif", 1.375),
    ACTIVE("actif", 1.55),
    VERY_ACTIVE("tres_actif", 1.725),
    ;

    companion object {
        fun fromCode(code: String?): ActivityLevel? = entries.firstOrNull { it.code == code }
    }
}

/** Objectif choisi. Seul le maintien existe pour l'instant ; le champ est prévu pour la suite. */
enum class Goal(val code: String) {
    MAINTENANCE("maintien"),
    ;

    companion object {
        fun fromCode(code: String?): Goal? = entries.firstOrNull { it.code == code }
    }
}

/** Ce qu'il faut savoir de la personne pour calculer son maintien. */
data class BodyProfile(
    val sex: Sex,
    /** Date de naissance (et non l'âge, pour que l'âge se mette à jour tout seul). */
    val birthDate: LocalDate,
    val heightCm: Double,
    /** Poids saisi dans le profil, utilisé tant qu'il n'y a aucune pesée. */
    val weightKg: Double,
    val activity: ActivityLevel,
    val firstName: String? = null,
    val goal: Goal = Goal.MAINTENANCE,
    /** Écart adopté par rapport à la formule, en kcal par jour (0 = formule seule). Voir [MaintenanceEstimator]. */
    val maintenanceAdjustmentKcal: Int = 0,
    /** Niveau d'activité au moment de l'adoption, pour rappeler de vérifier l'ajustement si le niveau change. */
    val adjustmentActivity: ActivityLevel? = null,
)

/** Une pesée datée, avec le tour de taille si la personne l'a mesuré. */
data class WeighIn(val date: LocalDate, val weightKg: Double, val waistCm: Double? = null)

/** Couleur d'un jour (ou d'une moyenne) selon l'écart au maintien. */
enum class Band { GREEN, ORANGE, RED }

/** Calculs du maintien, des couleurs et des moyennes (docs/decisions.md, « Repas et calories » et « Suivi du corps »). */
object Maintenance {

    const val AVERAGE_DAYS = 7
    const val PROTEIN_G_PER_KG = 1.6

    /** Métabolisme de base, formule de Mifflin-St Jeor. */
    fun basalKcal(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears
        return base + if (sex == Sex.MALE) 5 else -161
    }

    /** Âge en années révolues. */
    fun ageYears(birthDate: LocalDate, today: LocalDate): Int =
        java.time.Period.between(birthDate, today).years.coerceAtLeast(0)

    /**
     * Poids de référence : moyenne des pesées des 7 derniers jours ([today] compris) ; sinon la dernière
     * pesée ; sinon le poids du profil.
     */
    fun referenceWeightKg(profileWeightKg: Double, weighIns: List<WeighIn>, today: LocalDate): Double {
        val past = weighIns.filter { !it.date.isAfter(today) }
        val recent = past.filter { it.date.isAfter(today.minusDays(AVERAGE_DAYS.toLong())) }
        return when {
            recent.isNotEmpty() -> recent.map { it.weightKg }.average()
            past.isNotEmpty() -> past.maxBy { it.date }.weightKg
            else -> profileWeightKg
        }
    }

    /** Maintien de la formule seule (Mifflin-St Jeor × activité), arrondi à la dizaine, sans ajustement. */
    fun formulaKcal(profile: BodyProfile, weighIns: List<WeighIn>, today: LocalDate): Int {
        val weight = referenceWeightKg(profile.weightKg, weighIns, today)
        val kcal = basalKcal(profile.sex, weight, profile.heightCm, ageYears(profile.birthDate, today)) *
            profile.activity.factor
        return (kcal / 10).roundToInt() * 10
    }

    /**
     * Maintien utilisé partout dans l'app, en kcal par jour : la formule plus l'ajustement adopté.
     * C'est le seul endroit qui applique l'ajustement.
     */
    fun maintenanceKcal(profile: BodyProfile, weighIns: List<WeighIn>, today: LocalDate): Int =
        formulaKcal(profile, weighIns, today) + profile.maintenanceAdjustmentKcal

    /** Cible de protéines, environ 1,6 g par kilo de poids de référence, arrondie au gramme. */
    fun proteinTargetG(weightKg: Double): Int = (weightKg * PROTEIN_G_PER_KG).roundToInt()

    /** Fibres : au moins 30 g par jour, pour tous (fil Nutrition, 12/11/2026). */
    const val FIBER_TARGET_G = 30

    /** Part des calories de maintien venant des lipides (repère de maintien). */
    const val FAT_SHARE = 0.30

    /** Cible de lipides : 30 % des calories de maintien, 9 kcal par gramme. */
    fun fatTargetG(maintenanceKcal: Int): Int = (maintenanceKcal * FAT_SHARE / 9).roundToInt()

    /** Cible de glucides : le reste du maintien après protéines (4 kcal/g) et lipides (9 kcal/g), jamais négatif. */
    fun carbsTargetG(maintenanceKcal: Int, proteinTargetG: Int): Int =
        ((maintenanceKcal - proteinTargetG * 4 - fatTargetG(maintenanceKcal) * 9) / 4.0).roundToInt().coerceAtLeast(0)

    /** Vert à ±10 % du maintien, orange de 10 à 20 %, rouge au-delà. null si le maintien n'est pas positif. */
    fun band(kcal: Double, maintenanceKcal: Int): Band? {
        if (maintenanceKcal <= 0) return null
        val gap = abs(kcal - maintenanceKcal) / maintenanceKcal
        return when {
            gap <= 0.10 + EPSILON -> Band.GREEN
            gap <= 0.20 + EPSILON -> Band.ORANGE
            else -> Band.RED
        }
    }

    /**
     * Couleur du jour en cours (choix du propriétaire, 12/11/2026) : manger peu en journée est normal, donc pas de
     * couleur tant que le jour n'est pas fini, sauf si le total dépasse déjà nettement le maintien
     * (orange ou rouge vers le haut), ce que la suite de la journée ne peut plus corriger.
     */
    fun bandSoFar(kcal: Double, maintenanceKcal: Int): Band? =
        band(kcal, maintenanceKcal)?.takeIf { it != Band.GREEN && kcal > maintenanceKcal }

    /**
     * Moyenne des calories sur les 7 derniers jours ([today] compris), en ne comptant que les jours où
     * au moins un repas est noté : un jour oublié ne doit pas faire croire qu'on a jeûné.
     * null s'il n'y a aucun jour noté.
     */
    fun averageKcal(kcalByDay: Map<LocalDate, Double>, today: LocalDate): Double? {
        val days = kcalByDay.filter { (day, kcal) ->
            kcal > 0 && !day.isAfter(today) && day.isAfter(today.minusDays(AVERAGE_DAYS.toLong()))
        }
        return days.values.takeIf { it.isNotEmpty() }?.average()
    }

    /**
     * Courbe lissée du poids : pour chaque jour pesé, moyenne des pesées des 7 jours qui finissent ce jour-là.
     */
    fun weightTrend(weighIns: List<WeighIn>): List<Pair<LocalDate, Double>> {
        val sorted = weighIns.sortedBy { it.date }
        return sorted.map { it.date }.distinct().map { day ->
            day to sorted.filter { !it.date.isAfter(day) && it.date.isAfter(day.minusDays(AVERAGE_DAYS.toLong())) }
                .map { it.weightKg }.average()
        }
    }

    // Évite qu'un arrondi en virgule flottante fasse passer 10 % pile en orange.
    private const val EPSILON = 1e-9
}
