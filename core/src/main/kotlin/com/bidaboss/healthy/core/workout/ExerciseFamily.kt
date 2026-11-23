package com.bidaboss.healthy.core.workout

import java.text.Normalizer

/**
 * Famille de mouvement d'un exercice, pour choisir l'illustration de la séance guidée (fil Design, A1).
 * Choisie par mots-clés dans le nom, sans accent ni majuscule ; [GENERIC] si rien ne correspond.
 */
enum class ExerciseFamily {
    PUSH_UP, SQUAT, LUNGE, PLANK, ABS, BRIDGE, JUMP, STRETCH, GENERIC;

    companion object {
        /** Du plus précis au plus général : « pont fessier » avant « fessier », « gainage » avant le reste. */
        private val keywords: List<Pair<String, ExerciseFamily>> = listOf(
            "pont" to BRIDGE,
            "hip thrust" to BRIDGE,
            "pompe" to PUSH_UP,
            "push" to PUSH_UP,
            "dips" to PUSH_UP,
            "fente" to LUNGE,
            "lunge" to LUNGE,
            "squat" to SQUAT,
            "chaise" to SQUAT,
            "flexion" to SQUAT,
            "planche" to PLANK,
            "gainage" to PLANK,
            "plank" to PLANK,
            "crunch" to ABS,
            "abdo" to ABS,
            "releve de buste" to ABS,
            "releve de jambes" to ABS,
            "bicyclette" to ABS,
            "burpee" to JUMP,
            "jumping" to JUMP,
            "jack" to JUMP,
            "saut" to JUMP,
            "corde" to JUMP,
            "etirement" to STRETCH,
            "stretch" to STRETCH,
            "mobilite" to STRETCH,
            "respiration" to STRETCH,
            "yoga" to STRETCH,
        )

        fun of(name: String): ExerciseFamily {
            val key = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            return keywords.firstOrNull { (word, _) -> key.contains(word) }?.second ?: GENERIC
        }
    }
}
