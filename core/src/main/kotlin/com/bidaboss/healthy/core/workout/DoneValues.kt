package com.bidaboss.healthy.core.workout

/**
 * Séries réellement faites pour un exercice, rangées dans une seule colonne de texte : une valeur par
 * série (répétitions ou secondes), séparées par des virgules. Seul endroit qui connaît cette écriture.
 */
object DoneValues {
    fun format(values: List<Int>): String = values.joinToString(",")

    /** Les morceaux illisibles ou négatifs sont ignorés : un historique abîmé ne doit rien faire planter. */
    fun parse(text: String): List<Int> =
        text.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { value -> value >= 0 } }
}
