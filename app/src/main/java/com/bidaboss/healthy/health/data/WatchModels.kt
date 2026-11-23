package com.bidaboss.healthy.health.data

import java.time.LocalDate
import java.time.LocalDateTime

/** Où en est la connexion à la montre (par Health Connect, lecture seule). */
enum class WatchStatus {
    /** Health Connect n'existe pas sur ce téléphone. */
    UNAVAILABLE,

    /** Health Connect est installé mais trop ancien : mise à jour par le Play Store. */
    UPDATE_REQUIRED,

    /** Disponible, mais le propriétaire n'a pas encore autorisé la lecture. */
    NEEDS_PERMISSION,

    /** Autorisé : on peut lire. */
    READY,
}

/**
 * Ce que la montre a compté aujourd'hui. [activeKcal] est null si la montre n'en envoie pas.
 * [sleepMin] : la nuit qui vient de finir. [minBpm] et [avgBpm] : fréquence cardiaque du jour.
 * Ces trois-là sont null si l'autorisation n'est pas donnée ou si la montre n'a rien envoyé.
 */
data class WatchDay(
    val steps: Long,
    val activeKcal: Int?,
    val sleepMin: Int? = null,
    val minBpm: Int? = null,
    val avgBpm: Int? = null,
) {
    val hasHeart: Boolean get() = minBpm != null && avgBpm != null
    val isEmpty: Boolean get() = steps <= 0 && activeKcal == null && sleepMin == null && !hasHeart
}

/** Le relevé gardé d'un jour (historique). Une mesure null = pas de donnée ce jour-là, jamais zéro. */
data class WatchDayRecord(
    val date: LocalDate,
    val steps: Int?,
    val activeKcal: Int?,
    val sleepMin: Int?,
    val avgBpm: Int?,
    val minBpm: Int?,
)

/** Une séance enregistrée par la montre. [id] est celui de Health Connect, stable d'une lecture à l'autre. */
data class WatchSession(
    val id: String,
    /** Nom français, compatible avec les types du journal d'activités (« Marche », « Vélo »…). */
    val type: String,
    val startedAt: LocalDateTime,
    val durationMin: Int,
) {
    val date: LocalDate get() = startedAt.toLocalDate()
}

/** Source des données de la montre : Health Connect en vrai, une fausse dans les tests. */
interface WatchSource {
    suspend fun status(): WatchStatus

    suspend fun day(date: LocalDate): WatchDay

    suspend fun sessions(from: LocalDate, to: LocalDate): List<WatchSession>

    /** Le sommeil et la fréquence cardiaque sont-ils autorisés ? (Facultatifs : la connexion marche sans.) */
    suspend fun extrasGranted(): Boolean = true
}
