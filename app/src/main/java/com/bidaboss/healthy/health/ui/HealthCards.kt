package com.bidaboss.healthy.health.ui

import androidx.compose.foundation.background
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
 * un trou : un petit trait, jamais une barre à zéro. Les valeurs sont lues en entier par TalkBack via [description].
 */
@Composable
internal fun WeekBars(
    dates: List<LocalDate>,
    values: List<Double?>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val max = (values.filterNotNull().maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val gap = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dates.forEachIndexed { i, date ->
            val value = values.getOrNull(i)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.BottomCenter) {
                    if (value == null) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(gap, RoundedCornerShape(2.dp)))
                    } else {
                        val h = (value / max).toFloat().coerceIn(0.04f, 1f)
                        Box(Modifier.fillMaxWidth().height(72.dp * h).background(color, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp)))
                    }
                }
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.FRENCH).uppercase(Locale.FRENCH),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == dates.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
