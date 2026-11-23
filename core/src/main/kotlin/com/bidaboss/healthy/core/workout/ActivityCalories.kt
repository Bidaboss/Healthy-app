package com.bidaboss.healthy.core.workout

import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * Calories approximatives brûlées pendant une activité libre (demande du propriétaire, 13/11/2026).
 * Formule classique : MET × poids (kg) × durée (h). Les MET viennent du Compendium des activités
 * physiques, pour une intensité modérée. C'est une estimation affichée « ≈ », pas une mesure.
 */
object ActivityCalories {

    /** MET si le type n'est reconnu par aucun mot-clé : effort modéré. */
    const val DEFAULT_MET = 4.0

    /**
     * Mots-clés (sans accent, en minuscules) cherchés dans le type, du plus précis au plus général :
     * « vélo électrique » doit passer avant « vélo ».
     */
    private val metByKeyword: List<Pair<String, Double>> = listOf(
        "velo electrique" to 4.5,
        "vae" to 4.5,
        "kine" to 2.5,
        "etirement" to 2.3,
        "yoga" to 2.5,
        "pilates" to 3.0,
        "marche rapide" to 4.3,
        "marche nordique" to 4.8,
        "randonnee" to 6.0,
        "marche" to 3.5,
        "velo" to 6.8,
        "course" to 8.0,
        "footing" to 8.0,
        "jogging" to 8.0,
        "natation" to 6.0,
        "piscine" to 6.0,
        "renforcement" to 3.8,
        "musculation" to 5.0,
        "danse" to 5.0,
        "football" to 7.0,
        "foot" to 7.0,
        "tennis" to 7.0,
        "badminton" to 5.5,
        "escalade" to 7.5,
        "rameur" to 7.0,
        "elliptique" to 5.0,
        "jardinage" to 3.8,
    )

    fun met(type: String): Double {
        val key = normalize(type)
        return metByKeyword.firstOrNull { (word, _) -> key.contains(word) }?.second ?: DEFAULT_MET
    }

    /** kcal arrondies à 5 près ; null si le poids ou la durée manque. */
    fun estimateKcal(type: String, durationMin: Int, weightKg: Double?): Int? {
        if (weightKg == null || weightKg <= 0 || durationMin <= 0) return null
        val kcal = met(type) * weightKg * durationMin / 60.0
        return ((kcal / 5).roundToInt() * 5).coerceAtLeast(5)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
