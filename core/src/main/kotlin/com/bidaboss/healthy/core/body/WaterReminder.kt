package com.bidaboss.healthy.core.body

/**
 * Rappels pour boire (demande du propriétaire, 13/11/2026) : quatre points dans la journée, chacun avec la
 * part de l'objectif qu'on devrait avoir bue à cette heure-là. On ne prévient que si l'on est en dessous.
 */
enum class WaterCheck(val hour: Int, val minute: Int, val expectedPercent: Int, val momentLabel: String) {
    LATE_MORNING(11, 30, 30, "Fin de matinée"),
    EARLY_AFTERNOON(14, 0, 50, "Début d'après-midi"),
    LATE_AFTERNOON(17, 30, 70, "Fin d'après-midi"),
    EVENING(20, 30, 90, "Ce soir"),
}

data class WaterReminderMessage(val title: String, val text: String)

object WaterReminder {

    /** Quantité qu'on devrait avoir bue à ce point de la journée, arrondie à 10 mL. */
    fun expectedMl(check: WaterCheck, targetMl: Int): Int = (targetMl * check.expectedPercent / 100 + 5) / 10 * 10

    /** Le message à afficher, ou null si l'on est dans les temps (rien à dire). */
    fun message(check: WaterCheck, waterMl: Int, targetMl: Int): WaterReminderMessage? {
        val expected = expectedMl(check, targetMl.coerceAtLeast(1))
        if (waterMl >= expected) return null
        val missing = expected - waterMl
        // Un verre = 20 cL ; au moins un verre proposé.
        val glasses = ((missing + Hydration.GLASS_ML - 1) / Hydration.GLASS_ML).coerceAtLeast(1)
        val glassesText = if (glasses == 1) "un verre" else "$glasses verres"
        return WaterReminderMessage(
            title = "${check.momentLabel} : pense à boire 💧",
            text = "Tu en es à ${Hydration.formatLiters(waterMl)} sur ${Hydration.formatLiters(targetMl)}. " +
                "À cette heure-ci, vise environ ${Hydration.formatLiters(expected)} : il te manque $glassesText.",
        )
    }
}
