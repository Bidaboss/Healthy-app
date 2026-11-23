package com.bidaboss.healthy.core.body

import kotlin.math.roundToInt

/** Objectif d'eau du jour (boissons seulement : eau, thé, café, tisanes ; pas l'alcool). */
object Hydration {
    const val MIN_ML = 1500
    const val MAX_ML = 3000
    const val PER_KG_ML = 30
    const val PER_SESSION_ML = 500
    const val GLASS_ML = 200
    /** Un café de machine automatique d'entreprise (gobelet standard, environ 12 cL). */
    const val COFFEE_ML = 120
    const val BOTTLE_ML = 500
    /** Tasses du petit-déjeuner (demande du propriétaire, 13/11/2026) : petite tasse et grand mug ou bol. */
    const val SMALL_CUP_ML = 150
    const val LARGE_CUP_ML = 250

    /** Nom du bouton rapide qui ajoute cette quantité, ou null pour une quantité libre. */
    fun quickLabel(ml: Int): String? = when (ml) {
        GLASS_ML -> "Verre"
        COFFEE_ML -> "Café"
        SMALL_CUP_ML -> "Petite tasse"
        LARGE_CUP_ML -> "Grande tasse"
        BOTTLE_ML -> "Bouteille"
        else -> null
    }

    /** Base selon le sexe (repères EFSA, boissons seulement). */
    fun baseMl(sex: Sex): Int = when (sex) {
        Sex.FEMALE -> 1600
        Sex.MALE -> 2000
    }

    /**
     * Moyenne entre la base selon le sexe et 30 mL par kilo, bornée entre 1,5 et 3 L,
     * puis + 0,5 L par séance enregistrée dans la journée. Arrondi à 0,1 L.
     */
    fun targetMl(sex: Sex, referenceWeightKg: Double, sessionsToday: Int): Int {
        val mixed = (baseMl(sex) + PER_KG_ML * referenceWeightKg) / 2
        val bounded = mixed.coerceIn(MIN_ML.toDouble(), MAX_ML.toDouble())
        val withSport = bounded + PER_SESSION_ML * sessionsToday.coerceAtLeast(0)
        return (withSport / 100).roundToInt() * 100
    }

    /**
     * Quantité d'eau lisible et exacte : en cL sous 1 L (un verre = « 20 cL »), sinon en litres avec
     * jusqu'à deux décimales sans zéros inutiles (« 1,75 L », « 1,8 L », « 2 L »).
     */
    fun formatLiters(ml: Int): String {
        val hundredths = (ml / 10.0).roundToInt()
        // 995 à 999 mL s'arrondissent à 100 cL : on l'écrit « 1 L ».
        if (hundredths < 100) return "$hundredths cL"
        val decimals = "%02d".format(hundredths % 100).trimEnd('0')
        return "${hundredths / 100}" + (if (decimals.isEmpty()) "" else ",$decimals") + " L"
    }
}
