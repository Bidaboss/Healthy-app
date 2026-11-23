package com.bidaboss.healthy.meals.data

import com.bidaboss.healthy.core.importformat.DrinkKind
import com.bidaboss.healthy.core.importformat.FoodSource
import java.text.Normalizer

/**
 * Un aliment « déjà mangé » : sa dernière version ([food], avec sa dernière quantité), combien de fois il a été
 * mangé ([timesEaten] au total, [timesRecently] sur les 28 derniers jours).
 */
data class HistoryFood(
    val key: String,
    val food: Food,
    val timesEaten: Int,
    val timesRecently: Int,
    val lastEatenAtEpochMs: Long,
)

/**
 * « Déjà mangés » : déduit des aliments des repas enregistrés, sans table ni réseau. Un repas supprimé en sort,
 * un aliment corrigé garde sa dernière version. Le même aliment saisi plusieurs fois n'apparaît qu'une fois
 * (regroupé par nom, sans tenir compte des majuscules ni des accents).
 */
object FoodHistory {
    const val RECENT_DAYS = 28L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Nom réduit pour comparer : minuscules, sans accents, un seul espace entre les mots. */
    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /** Les aliments des 28 derniers jours d'abord (les plus fréquents en tête), puis les plus anciens, du plus récent au plus ancien. */
    fun build(rows: List<FoodHistoryRow>, nowEpochMs: Long): List<HistoryFood> {
        val recentFrom = nowEpochMs - RECENT_DAYS * DAY_MS
        return rows
            .filter { normalize(it.name).isNotEmpty() }
            .groupBy { normalize(it.name) }
            .map { (key, group) ->
                val last = group.maxBy { it.eatenAtEpochMs }
                HistoryFood(
                    key = key,
                    food = last.toFood(),
                    timesEaten = group.size,
                    timesRecently = group.count { it.eatenAtEpochMs >= recentFrom },
                    lastEatenAtEpochMs = last.eatenAtEpochMs,
                )
            }
            .sortedWith(
                compareByDescending<HistoryFood> { it.timesRecently > 0 }
                    .thenByDescending { it.timesRecently }
                    .thenByDescending { it.lastEatenAtEpochMs },
            )
    }

    /** Garde les aliments dont le nom contient tous les mots tapés (sans accents ni majuscules). */
    fun filter(history: List<HistoryFood>, query: String): List<HistoryFood> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return history
        return history.filter { item -> words.all { it in item.key } }
    }

    private fun FoodHistoryRow.toFood() = Food(
        name = name,
        quantityG = quantityG,
        portion = portion,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        fiberG = fiberG,
        source = FoodSource.fromCode(source) ?: FoodSource.MANUAL,
        offCode = offCode,
        drink = drink?.let(DrinkKind::fromCode),
    )
}
