package com.bidaboss.healthy.workout.data

/** Réponses sport de l'accueil (spec du fil Coach). Chaque champ a une valeur par défaut. */
data class SportProfile(
    val level: SportLevel = SportLevel.BEGINNER,
    val sessionsPerWeek: Int = 3,
    /** Jours préférés, 1 = lundi … 7 = dimanche, triés. */
    val days: List<Int> = listOf(1, 3, 5),
    /** Version de séance proposée par défaut : 15, 30 ou 45. */
    val durationMin: Int = 30,
    val goal: SportGoal = SportGoal.GENERAL,
    val painZones: Set<PainZone> = emptySet(),
    /** Texte libre pour la zone « Autre ». */
    val painOther: String = "",
    val medicalFollowUp: Boolean = false,
    val medicalNotes: String = "",
    val equipment: Set<Equipment> = emptySet(),
    val avoid: String = "",
) {
    /** Le nombre de jours cochés doit égaler le nombre de séances (l'écran le signale, sans bloquer). */
    val daysMatchSessions: Boolean get() = days.size == sessionsPerWeek

    companion object {
        val SESSION_CHOICES = listOf(2, 3, 4, 5)
        val DURATION_CHOICES = listOf(15, 30, 45)
    }
}

enum class SportLevel(val code: String) { BEGINNER("debutant"), LIGHT("un_peu_actif"), REGULAR("regulier"), SPORTY("sportif") }

enum class SportGoal(val code: String) { GENERAL("forme"), STRENGTH("renforcement"), ENDURANCE("endurance"), MOBILITY("mobilite") }

enum class PainZone(val code: String) {
    LOWER_BACK("dos"), KNEES("genoux"), SHOULDERS("epaules"), WRISTS("poignets"), ANKLES("chevilles"),
    HIPS("hanches"), GROIN("aine"), NECK("nuque"), OTHER("autre"),
}

enum class Equipment(val code: String) {
    CHAIR("chaise"), WALL("mur"), MAT("tapis"), BAND("elastique"), PULL_UP_BAR("barre_traction"),
    DUMBBELLS("halteres"), STEP("marche"),
}

/** Lecture tolérante des codes enregistrés : un code inconnu est ignoré. */
internal inline fun <reified E : Enum<E>> codes(text: String, code: (E) -> String): Set<E> {
    val values = enumValues<E>()
    return text.split(',').mapNotNull { c -> values.firstOrNull { code(it) == c.trim() } }.toSet()
}
