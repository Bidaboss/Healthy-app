package com.bidaboss.healthy.health.data

import com.bidaboss.healthy.core.watch.SleepSegment
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
 * Le détail d'une nuit (refonte, lot 4) : heures d'endormissement et de réveil, phases dans l'ordre de la nuit
 * (vide si la montre n'envoie pas de phases), et cœur pendant la nuit (null si non lu).
 */
data class SleepDetail(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val segments: List<SleepSegment> = emptyList(),
    val nightAvgBpm: Int? = null,
    val nightMinBpm: Int? = null,
)

/**
 * Ce que la montre a compté aujourd'hui. [activeKcal] est null si la montre n'en envoie pas.
 * [sleepMin] : la nuit qui vient de finir. [minBpm] et [avgBpm] : fréquence cardiaque du jour.
 * Ces trois-là sont null si l'autorisation n'est pas donnée ou si la montre n'a rien envoyé.
 * [distanceM] : distance du jour en mètres (null si la montre n'en envoie pas ou si elle n'est pas autorisée).
 * [sleep] : détail de la nuit (null sans détail).
 */
data class WatchDay(
    val steps: Long,
    val activeKcal: Int?,
    val sleepMin: Int? = null,
    val minBpm: Int? = null,
    val avgBpm: Int? = null,
    val distanceM: Int? = null,
    val sleep: SleepDetail? = null,
) {
    val hasHeart: Boolean get() = minBpm != null && avgBpm != null
    val isEmpty: Boolean get() = steps <= 0 && activeKcal == null && sleepMin == null && !hasHeart && distanceM == null && sleep == null
}

/**
 * Le relevé gardé d'un jour (historique). Une mesure null = pas de donnée ce jour-là, jamais zéro.
 * [sleep] : détail de la nuit qui se termine ce jour-là ; ses phases sont vides si la montre n'en envoie pas.
 */
data class WatchDayRecord(
    val date: LocalDate,
    val steps: Int?,
    val activeKcal: Int?,
    val sleepMin: Int?,
    val avgBpm: Int?,
    val minBpm: Int?,
    val distanceM: Int? = null,
    val sleep: SleepDetail? = null,
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

    /** La distance est-elle autorisée ? (Facultative, ajoutée par la refonte : une ancienne connexion ne l'a pas.) */
    suspend fun distanceGranted(): Boolean = true
}
