package com.bidaboss.healthy.health.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.watch.TrendSummary
import com.bidaboss.healthy.core.watch.WatchTrends
import com.bidaboss.healthy.health.data.HealthConnectSource
import com.bidaboss.healthy.health.data.WatchDayRecord
import com.bidaboss.healthy.health.data.WatchStatus
import com.bidaboss.healthy.today.ui.durationText
import com.bidaboss.healthy.ui.formatThousands
import com.bidaboss.healthy.ui.healthyViewModelFactory
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * « Ma montre » : tendances sur 7 ou 30 jours (PRD du 23/11/2026). Lecture seule, ton neutre : aucun seuil, aucune
 * couleur de jugement, aucun conseil. Un jour sans donnée est un trou, jamais un zéro.
 */
@Composable
fun WatchTrendsScreen(
    onClose: () -> Unit,
    viewModel: WatchTrendsViewModel = viewModel(
        factory = healthyViewModelFactory { WatchTrendsViewModel(it.watchRepository, it.sessionSummary) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectSource.permissionContract()) { viewModel.onResume() }
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val connect = { permissionLauncher.launch(HealthConnectSource.PERMISSIONS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ma montre", style = MaterialTheme.typography.headlineMedium)
        when {
            state.status == null && !state.hasHistory -> Text("Lecture de ta montre…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.notConnected -> NotConnected(state.status, connect)
            else -> {
                if (state.readingCut) ReadingCut(state.status, connect)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrendRange.entries.forEach { range ->
                        FilterChip(selected = state.range == range, onClick = { viewModel.selectRange(range) }, label = { Text(range.label) })
                    }
                }
                Trends(state, connect)
            }
        }
        TextButton(onClick = onClose) { Text("Retour") }
    }
}

@Composable
private fun NotConnected(status: WatchStatus?, onConnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status) {
                WatchStatus.UNAVAILABLE -> Text("Health Connect n'est pas disponible sur ce téléphone, donc ta montre ne peut pas être lue.")
                WatchStatus.UPDATE_REQUIRED -> Text("Health Connect doit être mis à jour : ouvre le Play Store, puis reviens ici.")
                else -> {
                    Text("Connecte ta montre pour suivre tes pas, ton sommeil et ton cœur au fil des jours. C'est en lecture seule et rien ne quitte ton téléphone.")
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Connecter ma montre") }
                }
            }
        }
    }
}

@Composable
private fun ReadingCut(status: WatchStatus?, onConnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("La lecture de la montre est coupée. Ton historique reste là, mais les nouveaux jours n'arrivent plus.")
            if (status == WatchStatus.NEEDS_PERMISSION) {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Connecter ma montre") }
            }
        }
    }
}

@Composable
private fun Trends(state: WatchTrendsUiState, onAddExtras: () -> Unit) {
    val period = state.range.days
    val days = state.days
    val previous = state.previousDays
    val reading = state.status == WatchStatus.READY

    fun series(of: List<WatchDayRecord?>, pick: (WatchDayRecord) -> Int?): List<Double?> = of.map { r -> r?.let(pick)?.toDouble() }

    // Pas
    val steps = series(days) { it.steps }
    TrendBlock("Pas", WatchTrends.summary(steps, series(previous) { it.steps }), period) { summary ->
        var selected by rememberSaveable(period) { mutableStateOf<Int?>(null) }
        Summary("${formatThousands(summary.average!!.roundToInt())} pas par jour", summary.delta?.let { signed(it.roundToInt(), "") }, period)
        BarChart(steps, selected, { selected = it }, chartDescription(state.today, "Pas par jour", steps) { "${formatThousands(it.roundToInt())} pas" })
        Selected(state.today, selected, steps) { "${formatThousands(it.roundToInt())} pas" }
    }

    // Sommeil
    val sleep = series(days) { it.sleepMin }
    if (reading && !state.extrasGranted) {
        AddExtras("Sommeil", onAddExtras)
    } else {
        TrendBlock("Sommeil", WatchTrends.summary(sleep, series(previous) { it.sleepMin }), period) { summary ->
            var selected by rememberSaveable(period) { mutableStateOf<Int?>(null) }
            Summary(
                "${durationText(summary.average!!.roundToInt())} par nuit",
                summary.delta?.let { signedMinutes(it.roundToInt()) },
                period,
            )
            BarChart(sleep, selected, { selected = it }, chartDescription(state.today, "Sommeil par nuit", sleep) { durationText(it.roundToInt()) })
            Selected(state.today, selected, sleep) { durationText(it.roundToInt()) }
        }
    }

    // Cœur
    val avg = series(days) { it.avgBpm }
    val low = series(days) { it.minBpm }
    if (reading && !state.extrasGranted) {
        AddExtras("Cœur", onAddExtras)
    } else {
        TrendBlock("Cœur", WatchTrends.summary(avg, series(previous) { it.avgBpm }), period) { summary ->
            var selected by rememberSaveable(period) { mutableStateOf<Int?>(null) }
            val lowSummary = WatchTrends.summary(low, series(previous) { it.minBpm })
            Summary(
                "${summary.average!!.roundToInt()} battements par minute en moyenne",
                summary.delta?.let { signed(it.roundToInt(), "") },
                period,
            )
            lowSummary.average?.let {
                Text(
                    "Le plus bas du jour : ${it.roundToInt()} en moyenne" + (lowSummary.delta?.let { d -> ", ${signed(d.roundToInt(), "")}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LineChart(
                avg, low, selected, { selected = it },
                chartDescription(state.today, "Cœur par jour, moyenne et plus bas", avg) { "moyenne ${it.roundToInt()}" },
            )
            Text(
                "Trait plein : moyenne du jour. Pointillés : le plus bas du jour.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            selected?.takeIf { it in days.indices }?.let { i ->
                val a = avg[i]
                val l = low[i]
                Text(
                    chartDate(dateOf(state.today, period, i)) + " : " +
                        if (a == null && l == null) "pas de donnée" else listOfNotNull(a?.let { "moyenne ${it.roundToInt()}" }, l?.let { "plus bas ${it.roundToInt()}" }).joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    // Calories actives (indicatives)
    val kcal = series(days) { it.activeKcal }
    TrendBlock("Calories actives", WatchTrends.summary(kcal, series(previous) { it.activeKcal }), period) { summary ->
        var selected by rememberSaveable(period) { mutableStateOf<Int?>(null) }
        Summary("≈ ${formatThousands(summary.average!!.roundToInt())} kcal par jour", summary.delta?.let { signed(it.roundToInt(), "") }, period)
        Text(
            "Indicatif : n'entre pas dans ton objectif.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BarChart(kcal, selected, { selected = it }, chartDescription(state.today, "Calories actives par jour", kcal) { "≈ ${formatThousands(it.roundToInt())} kcal" })
        Selected(state.today, selected, kcal) { "≈ ${formatThousands(it.roundToInt())} kcal" }
    }

    // Séances
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Séances", style = MaterialTheme.typography.titleMedium)
            if (state.sessions.count == 0) {
                Text("Aucune séance sur cette période.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "${state.sessions.count} séance${if (state.sessions.count > 1) "s" else ""} · ${durationText(state.sessions.minutes)} au total",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "Séances du plan et activités, y compris celles de la montre ajoutées à ton journal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Un bloc par mesure : titre, puis [content] avec la moyenne de la période ; « Pas de données » si elle est vide. */
@Composable
private fun TrendBlock(title: String, summary: TrendSummary, period: Int, content: @Composable (TrendSummary) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary.average == null) {
                Text("Pas de données sur cette période", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                content(summary)
            }
        }
    }
}

@Composable
private fun Summary(text: String, delta: String?, period: Int) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    delta?.let {
        Text(
            "$it par rapport aux $period jours d'avant",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** La valeur exacte du jour touché, avec sa date ; « pas de donnée » sur un trou. */
@Composable
private fun Selected(today: LocalDate, selected: Int?, values: List<Double?>, format: (Double) -> String) {
    val i = selected?.takeIf { it in values.indices } ?: return
    val date = dateOf(today, values.size, i)
    Text("${chartDate(date)} : ${values[i]?.let(format) ?: "pas de donnée"}", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun AddExtras(title: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("Le sommeil et le cœur ne sont pas autorisés (facultatif).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Ajouter le sommeil et le cœur") }
        }
    }
}

// Les dates du graphique : le dernier jour est aujourd'hui, donc le premier est (n - 1) jours avant.
private fun dateOf(today: LocalDate, size: Int, index: Int): LocalDate = today.minusDays((size - 1 - index).toLong())

private fun chartDescription(today: LocalDate, title: String, values: List<Double?>, format: (Double) -> String): String {
    val entries = values.mapIndexedNotNull { i, v -> v?.let { "${chartDate(dateOf(today, values.size, i))} : ${format(it)}" } }
    return "$title. " + if (entries.isEmpty()) "Aucune donnée." else entries.joinToString(". ")
}

/** « +520 », « −520 », « inchangé » : jamais de couleur de jugement. */
internal fun signed(value: Int, unit: String): String = when {
    value > 0 -> "+${formatThousands(value)}$unit"
    value < 0 -> "−${formatThousands(abs(value))}$unit"
    else -> "inchangé"
}

internal fun signedMinutes(value: Int): String = when {
    value > 0 -> "+${durationText(value)}"
    value < 0 -> "−${durationText(abs(value))}"
    else -> "inchangé"
}
