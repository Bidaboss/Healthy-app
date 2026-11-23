package com.bidaboss.healthy.meals.data

/** La portion de l'autre personne quand on partage un repas : Pareil, La moitié, Un tiers, ou un pourcentage au choix. */
sealed interface SharePortion {
    /** Part du repas envoyée, entre 0 (exclu) et 1 (inclus : on ne partage pas plus que ce qu'on a mangé). */
    val factor: Double

    val label: String

    data object Same : SharePortion {
        override val factor = 1.0
        override val label = "Pareil"
    }

    data object Half : SharePortion {
        override val factor = 0.5
        override val label = "La moitié"
    }

    data object Third : SharePortion {
        override val factor = 1.0 / 3.0
        override val label = "Un tiers"
    }

    /** « Autre » : [percent] % du repas, de 1 à 100. */
    data class Custom(val percent: Int) : SharePortion {
        override val factor = percent / 100.0
        override val label = "Autre ($percent %)"
    }

    companion object {
        /** Lit un pourcentage tapé (« 25 » ou « 25 % ») ; null s'il n'est pas entre 1 et 100. */
        fun parsePercent(text: String): Custom? {
            val value = text.replace("%", "").trim().replace(',', '.').toDoubleOrNull() ?: return null
            if (!value.isFinite() || value < 1 || value > 100) return null
            return Custom(Math.round(value).toInt())
        }
    }
}
