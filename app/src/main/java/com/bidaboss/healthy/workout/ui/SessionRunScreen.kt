package com.bidaboss.healthy.workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import com.bidaboss.healthy.ui.WaterFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.core.workout.PlannedExercise
import com.bidaboss.healthy.core.workout.RunStep
import com.bidaboss.healthy.ui.Confetti
import com.bidaboss.healthy.ui.ExerciseIllustration
import com.bidaboss.healthy.ui.Pousse
import com.bidaboss.healthy.ui.ThemeTexts
import com.bidaboss.healthy.ui.healthySavedStateViewModelFactory
import com.bidaboss.healthy.ui.theme.HealthyTheme
import com.bidaboss.healthy.ui.theme.healthyTonalButtonColors
import com.bidaboss.healthy.workout.data.Feeling
import kotlinx.coroutines.launch

/** Séance guidée plein écran : gros chiffres, un gros bouton par étape. */
@Composable
fun SessionRunScreen(
    sessionId: Long,
    durationMin: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionRunViewModel = viewModel(
        factory = healthySavedStateViewModelFactory { container, savedState ->
            SessionRunViewModel(
                container.workoutRepository,
                container.workoutLogRepository,
                sessionId,
                durationMin,
                savedState,
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Vibration et bip du chrono (3, 2, 1, fin).
    val context = LocalContext.current
    DisposableEffect(context) {
        val feedback = TimerFeedback(context)
        val job = scope.launch { viewModel.cues.collect(feedback::play) }
        onDispose {
            job.cancel()
            feedback.release()
        }
    }

    // L'écran reste allumé pendant la séance.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Séance enregistrée : on fête ça avant de revenir.
    if (state.finished) {
        LaunchedEffect(Unit) { WaterFeedback.tick(context) }
        BackHandler(onBack = onClose)
        SessionCelebration(onClose, modifier)
        return
    }

    // Le bouton retour ne quitte jamais la séance sans demander.
    BackHandler(enabled = !state.loading && !state.notFound && !state.finished) { confirmStop = true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.loading -> Text("Chargement…")
            state.notFound -> {
                Text("Cette séance n'existe plus dans ton plan.", style = MaterialTheme.typography.titleLarge)
                BigButton("Retour", onClose)
            }
            else -> {
                Text(
                    "${state.sessionTitle} · ${state.durationMin} min",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                SessionProgress(state)
                when (val step = state.step) {
                    is RunStep.WarmUp -> TextStep("Échauffement", step.text, "Commencer", viewModel::next)
                    is RunStep.CoolDown -> TextStep("Retour au calme", step.text, "Terminer", viewModel::next)
                    is RunStep.Work -> state.exercise?.let { exercise ->
                        WorkStep(step, exercise, state, viewModel)
                    }
                    is RunStep.Rest -> {
                        Text("Repos", style = MaterialTheme.typography.headlineMedium)
                        val left = state.secondsLeft ?: 0
                        CountdownRing(left, step.seconds, formatSeconds(left))
                        state.exercise?.let { NextUpCard(it, step.next.setIndex) }
                        BigButton("Suivant", viewModel::next)
                    }
                    RunStep.Feedback -> FeedbackStep(state, viewModel)
                    null -> Unit
                }
                TextButton(onClick = { confirmStop = true }) {
                    Text(if (state.step == RunStep.Feedback) "Quitter sans enregistrer" else "Arrêter la séance")
                }
            }
        }
    }

    if (confirmStop) {
        val onFeedback = state.step == RunStep.Feedback
        val canFinishNow = !onFeedback && viewModel.hasDoneSomething()
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Arrêter la séance ?") },
            text = {
                Text(
                    when {
                        onFeedback -> "Si tu quittes maintenant, la séance ne sera pas enregistrée."
                        canFinishNow -> "Tu peux terminer maintenant et enregistrer ce que tu as déjà fait."
                        else -> "La séance en cours ne sera pas enregistrée."
                    },
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (canFinishNow) {
                        TextButton(
                            onClick = {
                                confirmStop = false
                                viewModel.finishNow()
                            },
                        ) { Text("Terminer maintenant") }
                    }
                    TextButton(
                        onClick = {
                            confirmStop = false
                            onClose()
                        },
                    ) { Text("Quitter sans enregistrer") }
                    TextButton(onClick = { confirmStop = false }) { Text("Continuer la séance") }
                }
            },
        )
    }
}

@Composable
private fun TextStep(title: String, text: String, button: String, onClick: () -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    BigButton(button, onClick)
}

@Composable
private fun WorkStep(
    step: RunStep.Work,
    exercise: PlannedExercise,
    state: SessionRunUiState,
    viewModel: SessionRunViewModel,
) {
    Text(
        "Exercice ${step.exerciseIndex + 1} sur ${state.exerciseCount}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(exercise.name, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    ExerciseIllustration(exercise.name)
    Text("Série ${step.setIndex + 1} sur ${exercise.sets}", style = MaterialTheme.typography.titleLarge)

    if (exercise.isTimed) {
        val left = state.secondsLeft ?: 0
        CountdownRing(left, exercise.durationS ?: left, formatSeconds(left))
        if (state.timerRunning) {
            BigButton("Série terminée", viewModel::completeSet)
        } else {
            BigButton("Démarrer le chrono", viewModel::startTimer)
        }
    } else {
        Text("Objectif : ${exercise.reps} répétitions", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            FilledTonalButton(colors = healthyTonalButtonColors(), onClick = { viewModel.adjustDoneReps(-1) }) {
                Text("−", style = MaterialTheme.typography.headlineMedium)
            }
            BigNumber(state.doneReps.toString())
            FilledTonalButton(colors = healthyTonalButtonColors(), onClick = { viewModel.adjustDoneReps(1) }) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Text("répétitions faites", color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton("Série terminée", viewModel::completeSet)
    }

    exercise.videoUrl?.let { ExerciseVideoButton(it) }
    exercise.instructions?.let { Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
    exercise.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
    exercise.easierVariant?.let { VariantLine("Plus facile", it, exercise.easierVideoUrl) }
    exercise.harderVariant?.let { VariantLine("Plus difficile", it, exercise.harderVideoUrl) }

    TextButton(onClick = viewModel::skipExercise) { Text("Passer cet exercice") }
}

@Composable
private fun FeedbackStep(state: SessionRunUiState, viewModel: SessionRunViewModel) {
    Text("Séance terminée, bravo !", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Text("Comment était la séance ?", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Feeling.entries.forEach { feeling ->
            FilterChip(
                selected = state.feeling == feeling,
                onClick = { viewModel.setFeeling(feeling) },
                label = { Text(feeling.label) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(checked = state.hasPain, onCheckedChange = viewModel::setHasPain)
        Text("J'ai eu une douleur")
    }
    if (state.hasPain) {
        OutlinedTextField(
            value = state.painZone,
            onValueChange = viewModel::setPainZone,
            label = { Text("Où ? (ex. genou droit)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = viewModel::save,
        enabled = !state.saving,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) { Text("Enregistrer la séance", style = MaterialTheme.typography.titleLarge) }
}

/** Barre de progression de la séance : exercice n sur le total, séries comprises. */
@Composable
private fun SessionProgress(state: SessionRunUiState) {
    val work = when (val step = state.step) {
        is RunStep.Work -> step
        is RunStep.Rest -> step.next
        else -> return
    }
    val sets = state.exercise?.sets?.coerceAtLeast(1) ?: 1
    val progress = if (state.exerciseCount > 0) (work.exerciseIndex + work.setIndex.toFloat() / sets) / state.exerciseCount else 0f
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        drawStopIndicator = {},
    )
}

/** Pendant le repos : ce qui vient ensuite, avec Pousse qui montre le mouvement. */
@Composable
private fun NextUpCard(exercise: PlannedExercise, setIndex: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Ensuite", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(exercise.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            val dose = exercise.reps?.let { "$it répétitions" } ?: exercise.durationS?.let { formatSeconds(it) }
            Text(
                "Série ${setIndex + 1} sur ${exercise.sets}" + dose?.let { " · $it" }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
            )
            ExerciseIllustration(exercise.name, width = 140.dp)
        }
    }
}

@Composable
private fun BigNumber(text: String) {
    Text(text, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) { Text(label, style = MaterialTheme.typography.headlineSmall) }
}

private fun formatSeconds(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Après l'enregistrement : Pousse fière et pluie de récompense (confettis, pièces ou cartes postales). */
@Composable
private fun SessionCelebration(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Pousse(Mood.PROUD, size = 160.dp)
            val texts = ThemeTexts.current
            Text(
                texts.celebrationTitle,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                texts.celebrationText,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HealthyTheme.ambiance.accent,
                    contentColor = HealthyTheme.ambiance.onAccent,
                ),
            ) { Text("Continuer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        }
        Confetti(Modifier.fillMaxSize())
    }
}
