package com.bidaboss.healthy.core.watch

import java.time.LocalDateTime
import kotlin.math.sqrt

/** Les phases du sommeil que la montre distingue (refonte, lot 4). Le code d'une lettre sert au stockage. */
enum class SleepStage(val code: Char, val label: String) {
    DEEP('D', "Profond"),
    LIGHT('L', "Léger"),
    REM('R', "Paradoxal"),
    AWAKE('A', "Éveillé"),
    ;

    companion object {
        fun ofCode(code: Char): SleepStage? = entries.firstOrNull { it.code == code }
    }
}

/** Un morceau de la nuit : une phase et sa durée en minutes (toujours positive). */
data class SleepSegment(val stage: SleepStage, val minutes: Int)

/**
 * Phases d'une nuit, stockées en une seule chaîne (« L45,D30,R20,A5 », dans l'ordre de la nuit). Lecture
 * indulgente : un morceau illisible est ignoré, jamais une erreur (une sauvegarde peut venir d'une autre version).
 */
object SleepStages {
    fun encode(segments: List<SleepSegment>): String =
        segments.filter { it.minutes > 0 }.joinToString(",") { "${it.stage.code}${it.minutes}" }

    fun decode(text: String?): List<SleepSegment> = text.orEmpty().split(',').mapNotNull { part ->
        val stage = part.firstOrNull()?.let(SleepStage::ofCode) ?: return@mapNotNull null
        val minutes = part.drop(1).toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        SleepSegment(stage, minutes)
    }

    /** Regroupe les morceaux voisins de même phase et écarte ceux de durée nulle. */
    fun merge(segments: List<SleepSegment>): List<SleepSegment> {
        val merged = mutableListOf<SleepSegment>()
        for (segment in segments) {
            if (segment.minutes <= 0) continue
            val last = merged.lastOrNull()
            if (last != null && last.stage == segment.stage) {
                merged[merged.lastIndex] = last.copy(minutes = last.minutes + segment.minutes)
            } else {
                merged += segment
            }
        }
        return merged
    }

    /** Durée totale de chaque phase présente. */
    fun totals(segments: List<SleepSegment>): Map<SleepStage, Int> =
        segments.groupBy { it.stage }.mapValues { (_, list) -> list.sumOf { it.minutes } }

    /** Les phases sont-elles assez détaillées pour être montrées ? Il faut au moins une phase de sommeil (pas seulement « éveillé »). */
    fun hasPhases(segments: List<SleepSegment>): Boolean = segments.any { it.stage != SleepStage.AWAKE }

    /**
     * Nombre de réveils : les passages « éveillé » entre deux phases de sommeil. L'endormissement (avant la
     * première phase de sommeil) et le lever (après la dernière) ne comptent pas.
     */
    fun awakenings(segments: List<SleepSegment>): Int {
        val first = segments.indexOfFirst { it.stage != SleepStage.AWAKE }
        val last = segments.indexOfLast { it.stage != SleepStage.AWAKE }
        if (first < 0) return 0
        return segments.subList(first, last + 1).count { it.stage == SleepStage.AWAKE }
    }
}

/** Régularité du coucher sur plusieurs nuits. Aucun seuil ni jugement : on donne l'écart, c'est tout. */
object BedtimeRegularity {
    /** Nombre de nuits minimum pour parler de régularité. */
    const val MIN_NIGHTS = 3

    /** Heure de référence : le coucher se mesure à partir de 18 h, pour que 23 h 30 et 0 h 30 soient voisins. */
    private const val REFERENCE_HOUR = 18

    /**
     * Écart-type des heures de coucher, en minutes (arrondi), ou null s'il y a moins de [MIN_NIGHTS] nuits.
     * Un coucher entre 18 h et minuit compte comme « le soir même », entre minuit et 18 h comme « après minuit ».
     */
    fun spreadMinutes(bedtimes: List<LocalDateTime>): Int? {
        if (bedtimes.size < MIN_NIGHTS) return null
        val offsets = bedtimes.map { minutesAfterReference(it) }
        val mean = offsets.average()
        val variance = offsets.sumOf { (it - mean) * (it - mean) } / offsets.size
        return Math.round(sqrt(variance)).toInt()
    }

    internal fun minutesAfterReference(at: LocalDateTime): Double {
        val minuteOfDay = at.hour * 60 + at.minute
        val shifted = minuteOfDay - REFERENCE_HOUR * 60
        return (if (shifted < 0) shifted + 24 * 60 else shifted).toDouble()
    }
}
