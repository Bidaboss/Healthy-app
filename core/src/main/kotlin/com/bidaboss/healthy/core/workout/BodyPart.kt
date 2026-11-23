package com.bidaboss.healthy.core.workout

/**
 * Zone du corps travaillée pendant une activité libre (demande du propriétaire, 12/11/2026).
 * [code] est gardé en base et écrit dans le bilan d'entraînement : il ne change jamais.
 */
enum class BodyPart(val code: String, val label: String) {
    ABS("abs", "Abdos"),
    GLUTES("glutes", "Fessiers"),
    CHEST("chest", "Pectoraux"),
    BACK("back", "Dos"),
    SHOULDERS("shoulders", "Épaules"),
    ARMS("arms", "Bras"),
    LEGS("legs", "Jambes"),
    CARDIO("cardio", "Cardio"),
    FULL_BODY("full_body", "Tout le corps"),
    ;

    companion object {
        /** Codes séparés par des virgules (comme en base) ; les codes inconnus sont ignorés. */
        fun parse(codes: String?): List<BodyPart> =
            codes.orEmpty().split(',').mapNotNull { code -> entries.firstOrNull { it.code == code.trim() } }.distinct()

        /** Dans l'ordre de la liste ci-dessus, sans doublon ; null si aucune zone. */
        fun format(parts: Collection<BodyPart>): String? =
            entries.filter { it in parts }.takeIf { it.isNotEmpty() }?.joinToString(",") { it.code }
    }
}
