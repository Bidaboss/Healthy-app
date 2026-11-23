package com.bidaboss.healthy.meals.data

import android.content.Context
import androidx.core.content.edit
import com.bidaboss.healthy.core.importformat.MealType
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

/** Un repas que tu fais souvent à cette heure-ci, proposé en un toucher. */
data class HabitualMeal(
    val type: MealType,
    /** Nom du repas, s'il en a un. */
    val title: String?,
    val foods: List<Food>,
    /** Combien de fois ce même repas a été noté sur les 28 derniers jours. */
    val occurrences: Int,
) {
    val kcal: Double get() = foods.sumOf { it.kcal }

    /** « Petit-déjeuner » ou le nom du repas. */
    val label: String get() = title ?: type.label

    /** Le repas tel qu'il serait enregistré maintenant : pour remplir l'éditeur comme « Refaire ce repas ». */
    fun toMeal(index: Int, now: LocalDateTime) = Meal(
        id = -(index + 1L),
        type = type,
        title = title,
        notes = null,
        eatenAt = now,
        localDate = now.toLocalDate(),
        foods = foods,
    )
}

/**
 * Suggestion de repas habituel (PRD saisie rapide) : sur les 28 derniers jours, les repas du même type, commencés
 * à 1 h 30 près de l'heure actuelle, avec le même contenu ; le plus fréquent gagne, à partir de 2 fois.
 * Tout est calculé sur le téléphone, à partir des repas enregistrés.
 */
object HabitualMeals {
    const val RECENT_DAYS = 28L
    const val MIN_OCCURRENCES = 2
    const val WINDOW_MINUTES = 90

    /**
     * [past] : les repas des 28 derniers jours (ceux d'aujourd'hui sont ignorés ici) ; [today] : ceux d'aujourd'hui.
     * [skipTypesNotedToday] : sur Aujourd'hui, pas de suggestion pour un type de repas déjà noté ce jour.
     */
    fun suggest(
        past: List<Meal>,
        today: List<Meal>,
        now: LocalDateTime,
        skipTypesNotedToday: Boolean,
        limit: Int = 3,
    ): List<HabitualMeal> {
        val day = now.toLocalDate()
        val from = day.minusDays(RECENT_DAYS)
        val noted = if (skipTypesNotedToday) today.map { it.type }.toSet() else emptySet()
        val nowMinute = now.toLocalTime().hour * 60 + now.toLocalTime().minute
        return past
            .filter { it.localDate < day && it.localDate >= from && it.foods.isNotEmpty() && it.type !in noted }
            .filter { abs(it.eatenAt.toLocalTime().let { t -> t.hour * 60 + t.minute } - nowMinute) <= WINDOW_MINUTES }
            .groupBy { meal -> meal.type to meal.foods.map { FoodHistory.normalize(it.name) }.sorted() }
            .values
            .filter { it.size >= MIN_OCCURRENCES }
            .sortedWith(compareByDescending<List<Meal>> { it.size }.thenByDescending { group -> group.maxOf { it.eatenAt } })
            .take(limit)
            .map { group ->
                val last = group.maxBy { it.eatenAt }
                HabitualMeal(last.type, last.title, last.foods, group.size)
            }
    }
}

/** « Pas aujourd'hui » : la carte du repas habituel reste masquée jusqu'au lendemain. Préférences du téléphone, hors sauvegarde. */
class HabitDismissStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("habits", Context.MODE_PRIVATE)

    fun dismissedDay(): LocalDate? = prefs.getString(KEY_DISMISSED, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun dismiss(day: LocalDate) = prefs.edit { putString(KEY_DISMISSED, day.toString()) }

    private companion object {
        const val KEY_DISMISSED = "dismissed_on"
    }
}
