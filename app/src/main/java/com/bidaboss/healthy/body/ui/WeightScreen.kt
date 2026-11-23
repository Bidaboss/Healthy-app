package com.bidaboss.healthy.body.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.body.WeighIn
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.toInputText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

/** Pesée du jour, courbe du poids (avec la moyenne sur 7 jours) et historique. */
@Composable
fun WeightScreen(
    onClose: () -> Unit,
    viewModel: WeightViewModel = viewModel(factory = healthyViewModelFactory { WeightViewModel(it.bodyRepository) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Poids et tour de taille", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.todayDone) "Pesée du jour (tu peux la corriger)" else "Pesée du jour", style = MaterialTheme.typography.titleMedium)
                Field("Poids (kg)", state.form.weightKg, viewModel::setWeight)
                Field("Tour de taille (cm, facultatif)", state.form.waistCm, viewModel::setWaist)
                Text(
                    "Conseil : pèse-toi le matin, à jeun, après être passé aux toilettes. Tour de taille au niveau du nombril.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
            }
        }

        if (state.trend.size >= 2) {
            Text("Évolution", style = MaterialTheme.typography.titleLarge)
            WeightChart(state.weighIns, state.trend)
            Text(
                "Points : chaque pesée. Ligne : moyenne sur 7 jours, plus fiable que la pesée d'un seul jour.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.weighIns.isNotEmpty()) {
            Text("Mes pesées", style = MaterialTheme.typography.titleLarge)
            state.weighIns.take(60).forEach { w -> WeighInRow(w, onDelete = { viewModel.delete(w.date) }) }
        }

        TextButton(onClick = onClose) { Text("Retour") }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WeighInRow(weighIn: WeighIn, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(weighIn.date.format(dayFormat), style = MaterialTheme.typography.labelLarge)
            val waist = weighIn.waistCm?.let { " · taille ${it.toInputText()} cm" }.orEmpty()
            Text("${weighIn.weightKg.toInputText()} kg$waist", style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer cette pesée") }
    }
}

/** Courbe simple : les pesées en points, la moyenne sur 7 jours en ligne. */
@Composable
private fun WeightChart(weighIns: List<WeighIn>, trend: List<Pair<LocalDate, Double>>) {
    val pointColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.primary
    val first = trend.first().first
    val spanDays = ChronoUnit.DAYS.between(first, trend.last().first).coerceAtLeast(1).toFloat()
    val values = weighIns.map { it.weightKg } + trend.map { it.second }
    val min = values.min() - 0.5
    val max = values.max() + 0.5

    Column {
        Text("${max.toInputText1()} kg", style = MaterialTheme.typography.labelSmall)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            fun x(date: LocalDate) = ChronoUnit.DAYS.between(first, date) / spanDays * size.width
            fun y(kg: Double) = ((max - kg) / (max - min) * size.height).toFloat()

            weighIns.filter { !it.date.isBefore(first) }.forEach {
                drawCircle(pointColor, radius = 4.dp.toPx(), center = Offset(x(it.date), y(it.weightKg)))
            }
            val path = Path()
            trend.forEachIndexed { i, (date, kg) ->
                if (i == 0) path.moveTo(x(date), y(kg)) else path.lineTo(x(date), y(kg))
            }
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height))
        }
        Text("${min.toInputText1()} kg", style = MaterialTheme.typography.labelSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(first.format(dayFormat), style = MaterialTheme.typography.labelSmall)
            Text(trend.last().first.format(dayFormat), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Double.toInputText1(): String = (Math.round(this * 10) / 10.0).toInputText()
