package com.bidaboss.healthy.today.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.body.Band
import com.bidaboss.healthy.body.ui.MaintenanceCard
import com.bidaboss.healthy.body.ui.MaintenanceViewModel
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.ui.EmptyState
import com.bidaboss.healthy.ui.ThemeTexts
import com.bidaboss.healthy.ui.bandLabel
import com.bidaboss.healthy.ui.color
import com.bidaboss.healthy.ui.dayName
import com.bidaboss.healthy.ui.formatGrams
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.theme.HealthyTheme
import com.bidaboss.healthy.ui.theme.macroColors
import com.bidaboss.healthy.workout.ui.SessionDurationDialog
import kotlin.math.roundToInt

/** Tableau de bord : calories du jour face au maintien, moyenne sur 7 jours, poids et séance du jour. */
@Composable
fun TodayScreen(
    onStartSession: (sessionId: Long, durationMin: Int) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWeight: () -> Unit,
    onOpenWater: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenWeeklyRecap: () -> Unit,
    onOpenWatch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(
        factory = healthyViewModelFactory { TodayViewModel(it.mealRepository, it.workoutRepository, it.bodyRepository, it.workoutLogRepository) },
    ),
    waterViewModel: WaterViewModel = viewModel(
        factory = healthyViewModelFactory { WaterViewModel(it.bodyRepository, it.workoutLogRepository) },
    ),
    headerViewModel: HeaderViewModel = viewModel(
        factory = healthyViewModelFactory {
            HeaderViewModel(it.bodyRepository, it.workoutRepository, it.workoutLogRepository, it.mealRepository, it.pousseStageStore)
        },
    ),
    watchViewModel: WatchViewModel = viewModel(
        factory = healthyViewModelFactory { WatchViewModel(it.watchRepository, it.workoutLogRepository) },
    ),
    habitViewModel: HabitViewModel = viewModel(
        factory = healthyViewModelFactory { HabitViewModel(it.mealRepository, it.habitDismissStore) },
    ),
    maintenanceViewModel: MaintenanceViewModel = viewModel(
        factory = healthyViewModelFactory { MaintenanceViewModel(it.maintenanceInfo, it.bodyRepository) },
    ),
    recapViewModel: WeeklyRecapViewModel = viewModel(
        factory = healthyViewModelFactory { WeeklyRecapViewModel(it.weeklyRecapRepository, it.weeklyRecapStore) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val water by waterViewModel.uiState.collectAsStateWithLifecycle()
    val header by headerViewModel.uiState.collectAsStateWithLifecycle()
    val watch by watchViewModel.uiState.collectAsStateWithLifecycle()
    val habit by habitViewModel.uiState.collectAsStateWithLifecycle()
    val maintenance by maintenanceViewModel.uiState.collectAsStateWithLifecycle()
    val recapWeek by recapViewModel.availableWeek.collectAsStateWithLifecycle()
    val autoOpenRecap by recapViewModel.autoOpen.collectAsStateWithLifecycle()
    var choosingDuration by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        waterViewModel.onResume()
        headerViewModel.onResume()
        recapViewModel.refresh()
        watchViewModel.onResume()
        habitViewModel.onResume()
        maintenanceViewModel.onResume()
        onPauseOrDispose { }
    }

    // Le lundi, le bilan de la semaine passée s'ouvre une fois tout seul.
    LaunchedEffect(autoOpenRecap) {
        if (autoOpenRecap) {
            recapViewModel.onAutoOpened()
            onOpenWeeklyRecap()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TodayHeader(header, onOpenBadges)
        header.levelUpMessage?.let { LevelUpBanner(it, header.growth, headerViewModel::onLevelUpSeen, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (recapWeek != null) WeeklyRecapCard(onOpenWeeklyRecap)
            CaloriesCard(state, onOpenProfile)

            MaintenanceCard(maintenance.info, onOpenDetail = onOpenProfile, onLater = maintenanceViewModel::later)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Séance du jour", style = MaterialTheme.typography.titleMedium)
                    val session = state.todaySession
                    // Ce qui a été ajouté à la main dans Sport (kiné, marche…) compte aussi pour la journée.
                    state.todayActivities.forEach { activity ->
                        Text(
                            "✓ ${activity.type} · ${activity.durationMin} min" +
                                activity.bodyParts.takeIf { it.isNotEmpty() }
                                    ?.joinToString(", ", prefix = " · ") { it.label.lowercase() }.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (session == null) {
                        if (state.todayActivities.isEmpty()) EmptyState(ThemeTexts.current.restDay, size = 72.dp)
                    } else {
                        Text(session.title, style = MaterialTheme.typography.titleLarge)
                        Text(session.variants.joinToString(" / ") { "${it.durationMin} min" })
                        Button(
                            onClick = { choosingDuration = true },
                            enabled = session.variants.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HealthyTheme.ambiance.accent,
                                contentColor = HealthyTheme.ambiance.onAccent,
                            ),
                        ) { Text("Commencer la séance", fontWeight = FontWeight.Black) }
                    }
                }
            }

            HabitCard(habit.suggestion, onAdd = habitViewModel::add, onDismiss = habitViewModel::dismiss)

            WaterCard(water, onAdd = waterViewModel::add, onUndo = waterViewModel::undo, onOpen = onOpenWater)

            WatchCard(watch, onAdd = watchViewModel::add, onIgnore = watchViewModel::ignore, onOpen = onOpenWatch)

            if (state.maintenanceKcal != null) WeekCard(state)

            WeightCard(state, onOpenWeight)
        }
    }

    val session = state.todaySession
    if (choosingDuration && session != null) {
        SessionDurationDialog(
            session = session,
            onPick = { durationMin ->
                choosingDuration = false
                onStartSession(session.id, durationMin)
            },
            onDismiss = { choosingDuration = false },
        )
    }
}

/**
 * Une barre de macro avec sa cible, dans la couleur propre à la macro. Les protéines sont un minimum :
 * « objectif atteint » une fois la cible passée, sans jamais d'alerte au-delà. Glucides et lipides
 * sont de simples repères (fil Nutrition, 12/11/2026). Seule la couleur des calories juge la journée.
 */
@Composable
private fun MacroBar(
    label: String,
    grams: Double,
    target: Int?,
    minimum: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatGrams(grams), style = MaterialTheme.typography.titleMedium)
        if (target != null && target > 0) {
            val reached = minimum && grams >= target
            LinearProgressIndicator(
                progress = { (grams / target).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                // Une cible minimale atteinte passe au vert ; jamais de rouge pour les macros.
                color = if (reached) Band.GREEN.color() else color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
            Text(
                if (reached) "objectif atteint" else "sur ~$target g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Anneau : calories mangées face au maintien, coloré selon l'écart. */
@Composable
private fun CaloriesCard(state: TodayUiState, onOpenProfile: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val maintenance = state.maintenanceKcal
            val ringColor = state.todayBand?.color() ?: MaterialTheme.colorScheme.primary
            // L'anneau se remplit et le chiffre défile à l'ouverture et à chaque repas ajouté.
            val target = if (maintenance == null) 0f else (state.totalKcal / maintenance).toFloat().coerceIn(0f, 1f)
            val ring = remember { Animatable(0f) }
            LaunchedEffect(target) { ring.animateTo(target, tween(900, easing = FastOutSlowInEasing)) }
            val kcal = remember { Animatable(0f) }
            LaunchedEffect(state.totalKcal) { kcal.animateTo(state.totalKcal.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                CircularProgressIndicator(
                    progress = { ring.value },
                    modifier = Modifier.fillMaxSize(),
                    color = ringColor,
                    strokeWidth = 14.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = kcal.value.roundToInt().toString(),
                        style = MaterialTheme.typography.displayMedium,
                        color = ringColor,
                    )
                    Text(
                        if (maintenance == null) "kcal mangées" else "sur $maintenance kcal",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            when {
                maintenance == null -> {
                    Text("Remplis ton profil pour voir ton maintien.", textAlign = TextAlign.Center)
                    OutlinedButton(onClick = onOpenProfile) { Text("Remplir mon profil") }
                }
                state.mealCount == 0 -> Text("Aucun repas enregistré", color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Le jour en cours ne juge pas l'écart, sauf dépassement net (choix du propriétaire, 12/11/2026).
                else -> Text(
                    "${state.mealCount} ${if (state.mealCount > 1) "repas notés" else "repas noté"}" +
                        state.todayBand?.let { " · ${bandLabel(it, state.totalKcal, maintenance)}" }.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val macros = macroColors()
                MacroBar("Protéines", state.proteinG, state.proteinTargetG, true, macros.protein, Modifier.weight(1f))
                MacroBar("Glucides", state.carbsG, state.carbsTargetG, false, macros.carbs, Modifier.weight(1f))
                MacroBar("Lipides", state.fatG, state.fatTargetG, false, macros.fat, Modifier.weight(1f))
                // Fibres : cible minimale fixe, affichée même sans profil.
                MacroBar("Fibres", state.fiberG, Maintenance.FIBER_TARGET_G, true, macros.fiber, Modifier.weight(1f))
            }
            if (state.caloriesOnlyMeals > 0) {
                Text(
                    if (state.caloriesOnlyMeals == 1) "1 repas sans détail : ses protéines et ses fibres ne sont pas comptées."
                    else "${state.caloriesOnlyMeals} repas sans détail : leurs protéines et leurs fibres ne sont pas comptées.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Les 7 derniers jours en barres colorées, avec la moyenne des jours notés. */
@Composable
private fun WeekCard(state: TodayUiState) {
    val maintenance = state.maintenanceKcal ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("7 derniers jours", style = MaterialTheme.typography.titleMedium)
            val average = state.averageKcal
            if (average == null) {
                Text("Pas encore de repas notés cette semaine.")
            } else {
                Text(
                    "Moyenne : ${average.roundToInt()} kcal par jour",
                    style = MaterialTheme.typography.titleMedium,
                    color = state.averageBand?.color() ?: MaterialTheme.colorScheme.onSurface,
                )
                state.averageBand?.let { band ->
                    Text(
                        bandLabel(band, average, maintenance).replaceFirstChar(Char::uppercase) +
                            ThemeTexts.current.onTarget?.takeIf { band == Band.GREEN }?.let { " · $it" }.orEmpty(),
                    )
                }
            }
            val top = maxOf(maintenance * 1.3, state.lastDays.maxOfOrNull { it.kcal } ?: 0.0)
            val empty = MaterialTheme.colorScheme.surfaceVariant
            val targetLine = MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                state.lastDays.forEach { day ->
                    val barColor = day.band?.color() ?: empty
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            val h = (day.kcal / top * size.height).toFloat().coerceAtLeast(if (day.kcal > 0) 4f else 2f)
                            drawRect(barColor, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                            val y = (size.height - maintenance / top * size.height).toFloat()
                            drawLine(targetLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
                        }
                        Text(dayName(day.date.dayOfWeek.value).take(1), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                "Le trait marque ton maintien. Un jour sans repas noté ne compte pas dans la moyenne.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeightCard(state: TodayUiState, onOpenWeight: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mon poids", style = MaterialTheme.typography.titleMedium)
                Text(
                    state.latestWeightKg?.let { "Dernière pesée : ${formatKg(it)}" } ?: "Aucune pesée pour l'instant.",
                )
            }
            if (state.weighedToday) {
                OutlinedButton(onClick = onOpenWeight) { Text("Voir") }
            } else {
                Button(onClick = onOpenWeight) { Text("Me peser") }
            }
        }
    }
}

private fun formatKg(kg: Double): String = "${(Math.round(kg * 10) / 10.0).toString().replace('.', ',').removeSuffix(",0")} kg"

/** Du dimanche soir au mardi : rappel du bilan de la semaine. */
@Composable
private fun WeeklyRecapCard(onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = HealthyTheme.ambiance.accent,
            contentColor = HealthyTheme.ambiance.onAccent,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Ton bilan de la semaine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Repas, sport et ta pousse en trois pages. Touche pour l'ouvrir.")
        }
    }
}
