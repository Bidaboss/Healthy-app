package com.bidaboss.healthy.health.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/** « lundi 17 novembre » */
internal fun chartDate(date: LocalDate): String = date.format(dateFormat)

/** Le numéro du jour touché, d'après la position du doigt. */
private fun indexAt(x: Float, width: Float, count: Int): Int = ((x / width) * count).toInt().coerceIn(0, count - 1)

/**
 * Barres simples, une par jour. Un jour sans donnée (null) est un trou : rien n'est dessiné, jamais un zéro.
 * Toucher une barre la sélectionne (contour) ; les valeurs sont lues en entier par TalkBack via [description].
 */
@Composable
internal fun BarChart(
    values: List<Double?>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    val bar = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    val outline = MaterialTheme.colorScheme.onSurface
    val max = (values.filterNotNull().maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(values.size) {
                detectTapGestures { offset ->
                    val index = indexAt(offset.x, size.width.toFloat(), values.size)
                    onSelect(if (index == selected) null else index)
                }
            },
    ) {
        val slot = size.width / values.size
        val barWidth = slot * 0.7f
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        values.forEachIndexed { i, value ->
            if (value != null) {
                val h = (value / max).toFloat() * (size.height - 4f)
                val topLeft = Offset(i * slot + (slot - barWidth) / 2f, size.height - h)
                drawRect(bar, topLeft, Size(barWidth, h))
                if (i == selected) drawRect(outline, topLeft, Size(barWidth, h), style = Stroke(width = 4f))
            }
        }
    }
}

/**
 * Deux courbes sur les mêmes jours : [main] (trait plein) et [second] (pointillés), de sorte que la différence
 * ne repose pas sur la couleur seule. Un jour sans donnée interrompt le trait ; il n'est jamais relié ni mis à zéro.
 */
@Composable
internal fun LineChart(
    main: List<Double?>,
    second: List<Double?>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (main.isEmpty()) return
    val mainColor = MaterialTheme.colorScheme.primary
    val secondColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axis = MaterialTheme.colorScheme.outline
    val outline = MaterialTheme.colorScheme.onSurface
    val all = (main + second).filterNotNull()
    val low = all.minOrNull() ?: 0.0
    val high = (all.maxOrNull() ?: 1.0).let { if (it - low < 1.0) low + 1.0 else it }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(main.size) {
                detectTapGestures { offset ->
                    val index = indexAt(offset.x, size.width.toFloat(), main.size)
                    onSelect(if (index == selected) null else index)
                }
            },
    ) {
        val slot = size.width / main.size
        val pad = 8f
        fun point(i: Int, v: Double) = Offset(i * slot + slot / 2f, pad + ((high - v) / (high - low)).toFloat() * (size.height - 2 * pad))
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)

        fun line(values: List<Double?>, color: Color, dashed: Boolean) {
            val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null
            values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val p = point(i, v)
                drawCircle(color, radius = if (i == selected) 8f else 5f, center = p)
                val next = values.getOrNull(i + 1)
                if (next != null) drawLine(color, p, point(i + 1, next), strokeWidth = 4f, pathEffect = effect)
            }
        }
        line(second, secondColor, dashed = true)
        line(main, mainColor, dashed = false)
        selected?.takeIf { it in main.indices }?.let { i ->
            drawLine(outline, Offset(i * slot + slot / 2f, 0f), Offset(i * slot + slot / 2f, size.height), strokeWidth = 2f)
        }
    }
}
