package com.bidaboss.healthy.core.importformat

import com.bidaboss.healthy.core.backup.BackupContent
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Modèle métier d'un import déjà validé (voir docs/format-import.md).
 * Les valeurs ici sont garanties cohérentes : le reste de l'app peut s'y fier.
 */
sealed interface ImportDocument {
    val id: String
    val createdAt: OffsetDateTime

    /** Id de l'import que celui-ci corrige et remplace, ou null. */
    val replaces: String?

    data class Meal(
        override val id: String,
        override val createdAt: OffsetDateTime,
        override val replaces: String?,
        val meal: ImportedMeal,
    ) : ImportDocument

    data class WorkoutPlan(
        override val id: String,
        override val createdAt: OffsetDateTime,
        override val replaces: String?,
        val plan: ImportedPlan,
    ) : ImportDocument

    /** Sauvegarde exportée par l'app : sa restauration remplace toutes les données du téléphone. */
    data class Backup(
        override val id: String,
        override val createdAt: OffsetDateTime,
        val content: BackupContent,
    ) : ImportDocument {
        override val replaces: String? get() = null
    }
}

enum class MealType(val code: String, val label: String) {
    BREAKFAST("petit-dejeuner", "Petit-déjeuner"),
    LUNCH("dejeuner", "Déjeuner"),
    DINNER("diner", "Dîner"),
    SNACK("collation", "Collation");

    companion object {
        private val aliases = mapOf(
            "breakfast" to BREAKFAST, "lunch" to LUNCH, "dinner" to DINNER, "snack" to SNACK,
        )

        fun fromCode(value: String): MealType? {
            val v = value.trim().lowercase()
            return entries.firstOrNull { it.code == v } ?: aliases[v]
        }
    }
}

enum class FoodSource(val code: String) {
    CLAUDE_ESTIMATE("estimation_claude"),
    /** Estimation faite par une autre IA (prompt repas copié depuis l'app). */
    AI_ESTIMATE("estimation_ia"),
    OPEN_FOOD_FACTS("openfoodfacts"),
    MANUAL("manuel");

    companion object {
        fun fromCode(value: String): FoodSource? =
            entries.firstOrNull { it.code == value.trim().lowercase() }
    }
}

/**
 * Un aliment qui se boit (champ `drink` du fichier). Absent = un aliment ordinaire.
 * Une [DRINK] avec une quantité compte dans l'eau bue du jour ; l'[ALCOHOL] n'y compte jamais.
 */
enum class DrinkKind(val code: String) {
    DRINK("boisson"),
    ALCOHOL("alcool");

    companion object {
        fun fromCode(value: String): DrinkKind? =
            entries.firstOrNull { it.code == value.trim().lowercase() }
    }
}

enum class Confidence(val code: String, val label: String) {
    HIGH("haute", "Haute"),
    MEDIUM("moyenne", "Moyenne"),
    LOW("faible", "Faible");

    companion object {
        private val aliases = mapOf("high" to HIGH, "medium" to MEDIUM, "low" to LOW)

        fun fromCode(value: String): Confidence? {
            val v = value.trim().lowercase()
            return entries.firstOrNull { it.code == v } ?: aliases[v]
        }
    }
}

data class ImportedMeal(
    val eatenAt: OffsetDateTime,
    val type: MealType,
    val title: String?,
    val notes: String?,
    val items: List<ImportedFood>,
) {
    /** Jour du journal : date locale de l'heure du repas. */
    val localDate: LocalDate get() = eatenAt.toLocalDate()

    /** Totaux calculés par l'app, jamais lus dans le fichier. */
    val totalKcal: Double get() = items.sumOf { it.kcal }
    val totalProteinG: Double get() = items.sumOf { it.proteinG ?: 0.0 }
    val totalCarbsG: Double get() = items.sumOf { it.carbsG ?: 0.0 }
    val totalFatG: Double get() = items.sumOf { it.fatG ?: 0.0 }
}

data class ImportedFood(
    val name: String,
    val quantityG: Double?,
    val portion: String?,
    val kcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val source: FoodSource,
    val confidence: Confidence?,
    val assumptions: String?,
    val offCode: String?,
    /** null = aliment ordinaire. */
    val drink: DrinkKind? = null,
)

data class ImportedPlan(
    val name: String,
    val description: String?,
    val weeks: Int?,
    val sessions: List<ImportedSession>,
)

data class ImportedSession(
    val title: String,
    /** 1 = lundi … 7 = dimanche. */
    val dayOfWeek: Int?,
    val notes: String?,
    /** Versions de la séance par durée (ex. 15, 30, 45 min), triées par durée. */
    val variants: List<ImportedSessionVariant>,
)

/** Une version complète de la séance pour une durée donnée. */
data class ImportedSessionVariant(
    val durationMin: Int,
    val warmUp: String?,
    val coolDown: String?,
    val exercises: List<ImportedExercise>,
)

data class ImportedExercise(
    val name: String,
    val sets: Int,
    val reps: Int?,
    val durationS: Int?,
    val restS: Int?,
    val instructions: String?,
    val easierVariant: String?,
    val harderVariant: String?,
    val notes: String?,
    /** Lien vers une vidéo de démonstration (https), ouvert hors de l'app. */
    val videoUrl: String? = null,
    val easierVideoUrl: String? = null,
    val harderVideoUrl: String? = null,
)
