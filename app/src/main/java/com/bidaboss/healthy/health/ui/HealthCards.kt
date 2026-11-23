package com.bidaboss.healthy.health.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Carte d'une mesure de la semaine : titre, résumé, barres. [onClick] null = carte non cliquable. */
@Composable
internal fun MetricCard(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { body() } else Card(modifier = modifier.fillMaxWidth()) { body() }
}

/**
 * Sept barres, une par jour, avec la lettre du jour dessous (aujourd'hui en gras). Un jour sans donnée (null) est
 * un trou : un petit trait, jamais une barre à zéro. Toucher une barre affiche la valeur de ce jour (retouches du
 * 23/11/2026) ; [goal] trace l'objectif en pointillé. Les valeurs sont lues en entier par TalkBack via [description].
 */
@Composable
internal fun WeekBars(
    dates: List<LocalDate>,
    values: List<Double?>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    goal: Double? = null,
    format: (Double) -> String = { it.roundToInt().toString() },
) {
    val max = (values.filterNotNull() + listOfNotNull(goal)).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val gap = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    var selected by remember { mutableStateOf<Int?>(null) }
    val barArea = 72.dp
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Lecture du jour touché ; un deuxième toucher sur la même barre la referme.
        val picked = selected
        if (picked != null) {
            Text(
                "${chartDate(dates[picked]).replaceFirstChar(Char::uppercase)} : ${values.getOrNull(picked)?.let(format) ?: "pas de donnée"}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dates.forEachIndexed { i, date ->
                    val value = values.getOrNull(i)
                    Column(
                        modifier = Modifier.weight(1f).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { selected = if (selected == i) null else i },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().height(barArea), contentAlignment = Alignment.BottomCenter) {
                            if (value == null) {
                                Box(Modifier.fillMaxWidth().height(3.dp).background(gap, RoundedCornerShape(2.dp)))
                            } else {
                                val h = (value / max).toFloat().coerceIn(0.04f, 1f)
                                val shade = if (selected == null || selected == i) color else color.copy(alpha = 0.45f)
                                Box(Modifier.fillMaxWidth().height(barArea * h).background(shade, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp)))
                            }
                        }
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.FRENCH).uppercase(Locale.FRENCH),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (i == dates.lastIndex || selected == i) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (goal != null) {
                // Ligne d'objectif en pointillé, à la hauteur de l'objectif dans la zone des barres.
                Canvas(Modifier.fillMaxWidth().height(barArea)) {
                    val y = size.height * (1f - (goal / max).toFloat().coerceIn(0f, 1f))
                    drawLine(
                        color = goalColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                    )
                }
            }
        }
    }
}

/** « jeudi 20 : 2 130 kcal. vendredi 21 : pas de donnée. » pour TalkBack. */
internal fun weekDescription(title: String, dates: List<LocalDate>, values: List<Double?>, format: (Double) -> String): String =
    "$title. " + dates.mapIndexed { i, d ->
        "${chartDate(d)} : ${values.getOrNull(i)?.let(format) ?: "pas de donnée"}"
    }.joinToString(". ")

/** La moyenne des jours qui ont une donnée, ou null s'il n'y en a aucun. */
internal fun List<Double?>.averageOrNull(): Double? = filterNotNull().takeIf { it.isNotEmpty() }?.average()

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun BigNumber(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)

internal fun liters(ml: Double): String = "%.1f L".format(Locale.FRENCH, ml / 1000.0)
