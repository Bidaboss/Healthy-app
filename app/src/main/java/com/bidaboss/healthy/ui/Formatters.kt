package com.bidaboss.healthy.ui

import java.util.Locale
import kotlin.math.roundToInt

fun formatKcal(value: Double): String = "${value.roundToInt()} kcal"

fun formatGrams(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toInt().toDouble()) "${rounded.toInt()} g" else "${rounded.toString().replace('.', ',')} g"
}

private val dayNames = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/** 1 = lundi … 7 = dimanche. */
fun dayName(dayOfWeek: Int): String = dayNames.getOrElse(dayOfWeek - 1) { "" }

fun formatExerciseDose(sets: Int, reps: Int?, durationS: Int?): String = when {
    reps != null -> "$sets × $reps"
    durationS != null -> "$sets × $durationS s"
    else -> "$sets séries"
}

/** « 3 065 » avec l'espace des milliers, comme partout dans l'app. */
fun formatThousands(value: Int): String =
    String.format(Locale.FRENCH, "%,d", value).replace('\u202F', ' ').replace('\u00A0', ' ')
