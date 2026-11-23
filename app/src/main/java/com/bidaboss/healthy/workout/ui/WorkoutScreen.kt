package com.bidaboss.healthy.workout.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.route.RoutePlanning
import com.bidaboss.healthy.core.workout.BodyPart
import com.bidaboss.healthy.ui.EmptyState
import com.bidaboss.healthy.ui.StreakFlame
import com.bidaboss.healthy.ui.ThemeTexts
import com.bidaboss.healthy.ui.dayName
import com.bidaboss.healthy.ui.formatExerciseDose
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.theme.HealthyTheme
import com.bidaboss.healthy.ui.theme.healthyTonalIconButtonColors
import com.bidaboss.healthy.ui.PhotoBackground
import com.bidaboss.healthy.ui.PhotoTab
import com.bidaboss.healthy.ui.PhotoOutlinedButton
import com.bidaboss.healthy.ui.PhotoTitle
import com.bidaboss.healthy.workout.data.FreeActivity
import com.bidaboss.healthy.workout.data.SessionLog
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val historyFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

@Composable
fun WorkoutScreen(
    onStartSession: (sessionId: Long, durationMin: Int) -> Unit,
    onImport: () -> Unit,
    onOpenWatch: () -> Unit,
    onAddActivity: () -> Unit,
    onOpenRoutes: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = viewModel(
        factory = healthyViewModelFactory { WorkoutViewModel(it.workoutRepository, it.workoutLogRepository, it.referenceWeightKg, it.isWatchConnected, it.savedRouteRepository) },
    ),
) {
    val hasWatch by viewModel.hasWatch.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val plan = state.plan
    var sessionToStart by remember { mutableStateOf<WorkoutSession?>(null) }
    var activityToDelete by remember { mutableStateOf<FreeActivity?>(null) }
    var activityToEdit by remember { mutableStateOf<FreeActivity?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    // Le bilan part par le partage Android (vers l'app Claude par exemple) : aucun réseau ni permission ici.
    val reportToShare by viewModel.reportToShare.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(reportToShare) {
        val report = reportToShare ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Bilan d'entraînement Healthy")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(send, "Envoyer mon bilan"))
        viewModel.onReportShared()
    }

    val monday = state.monday
    val (thisWeek, earlier) = state.activities.partition { !it.date.isBefore(monday) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PhotoBackground(PhotoTab.WORKOUT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhotoTitle("Sport", modifier = Modifier.weight(1f))
                    Button(onClick = onAddActivity) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Activité")
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        WeekStrip(state)
                        Text(
                            weekSummary(state.sessionsThisWeek, state.activitiesThisWeek),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        // Visible seulement si la montre est connectée.
                        if (hasWatch) {
                            OutlinedButton(onClick = onOpenWatch) { Text("Mes stats montre") }
                        }
                    }
                }
                PhotoOutlinedButton(onClick = onOpenRoutes, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Mes trajets")
                }

                // Liste unique : séances prévues et activités ajoutées, mêmes cartes, comptées pareil dans la série.
                PhotoTitle("Cette semaine", style = MaterialTheme.typography.titleLarge)
                if (plan == null) {
                    EmptyState(ThemeTexts.current.noPlan, action = "Importer mon plan", onAction = onImport)
                } else {
                    plan.sessions.forEach { session ->
                        key("session-${session.id}") { SessionCard(session, onStart = { sessionToStart = session }) }
                    }
                }
                thisWeek.forEach { activity ->
                    key("activity-${activity.id}") {
                        ActivityCard(activity, state.activityKcal[activity.id], onEdit = { activityToEdit = activity }, onDelete = { activityToDelete = activity })
                    }
                }
                if (earlier.isNotEmpty()) {
                    Text("Activités des semaines passées", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    earlier.forEach { activity ->
                        key("activity-${activity.id}") {
                            ActivityCard(activity, state.activityKcal[activity.id], onEdit = { activityToEdit = activity }, onDelete = { activityToDelete = activity })
                        }
                    }
                }
                if (state.history.isNotEmpty()) {
                    Text("Historique", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Pour faire évoluer ton plan, envoie ton bilan des 4 dernières semaines à Claude.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    OutlinedButton(onClick = viewModel::exportReport, modifier = Modifier.fillMaxWidth()) {
                        Text("Envoyer mon bilan à Claude")
                    }
                    state.history.forEach { log -> key("log-${log.id}") { HistoryCard(log) } }
                }
            }
        }
    }

    sessionToStart?.let { session ->
        SessionDurationDialog(
            session = session,
            onPick = { durationMin ->
                sessionToStart = null
                onStartSession(session.id, durationMin)
            },
            onDismiss = { sessionToStart = null },
        )
    }

    activityToEdit?.let { activity ->
        EditActivityDialog(
            initial = activity,
            onSave = { type, durationMin, date, bodyParts ->
                activityToEdit = null
                viewModel.updateActivity(activity.id, type, durationMin, date, bodyParts)
            },
            onDismiss = { activityToEdit = null },
        )
    }

    activityToDelete?.let { activity ->
        AlertDialog(
            onDismissRequest = { activityToDelete = null },
            title = { Text("Supprimer cette activité ?") },
            text = { Text("${activity.type}, ${activity.durationMin} min") },
            confirmButton = {
                TextButton(
                    onClick = {
                        activityToDelete = null
                        viewModel.deleteActivity(activity.id)
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { activityToDelete = null }) { Text("Annuler") } },
        )
    }
}

/** Choix de la durée (15, 30 ou 45 min) avant de lancer une séance. Aussi utilisé par l'onglet Aujourd'hui. */
@Composable
fun SessionDurationDialog(session: WorkoutSession, onPick: (durationMin: Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Combien de temps as-tu ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                session.variants.forEach { variant ->
                    Button(onClick = { onPick(variant.durationMin) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${variant.durationMin} minutes", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ActivityCard(activity: FreeActivity, kcal: Int?, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Modifier l'activité", onClick = onEdit)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(activity.type, style = MaterialTheme.typography.titleMedium)
                    StatusPill("Ajoutée")
                }
                activity.routeName?.let { name ->
                    Text(
                        listOfNotNull(name, activity.routeDirection?.label).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "${activity.date.format(historyFormat)} · ${activity.durationMin} min" +
                        (activity.distanceKm?.let { " · ${RoutePlanning.formatKm(it)}" } ?: "") +
                        (kcal?.let { " · ≈ $it kcal" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activity.bodyParts.isNotEmpty()) {
                    Text(
                        activity.bodyParts.joinToString(", ") { it.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Supprimer reste discret, derrière un menu ⋮ (fil Design, 12/11/2026) ; la confirmation suit.
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options de l'activité")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Modifier") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Bilan de la semaine en phrase naturelle : « 1 séance et 2 activités cette semaine ». */
internal fun weekSummary(sessions: Int, activities: Int): String {
    val parts = listOfNotNull(
        sessions.takeIf { it > 0 }?.let { "$it ${if (it > 1) "séances" else "séance"}" },
        activities.takeIf { it > 0 }?.let { "$it ${if (it > 1) "activités" else "activité"}" },
    )
    return if (parts.isEmpty()) "Rien de fait cette semaine pour l'instant" else parts.joinToString(" et ") + " cette semaine"
}

/** Pastille « Prévue » (séance du plan) ou « Ajoutée » (activité ajoutée) : les deux comptent pareil dans la série. */
@Composable
private fun StatusPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun SessionCard(session: WorkoutSession, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                StatusPill("Prévue")
            }
            val details = listOfNotNull(
                session.dayOfWeek?.let { dayName(it) },
                session.variants.joinToString(" / ") { "${it.durationMin} min" }.takeIf { it.isNotEmpty() },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Aperçu : la version la plus proche de 30 minutes.
            val preview = session.variants.minByOrNull { kotlin.math.abs(it.durationMin - 30) }
            preview?.exercises?.forEach { exercise ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(exercise.name, modifier = Modifier.weight(1f))
                    Text(formatExerciseDose(exercise.sets, exercise.reps, exercise.durationS))
                }
            }
            Button(onClick = onStart, enabled = session.variants.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Commencer")
            }
        }
    }
}

@Composable
private fun HistoryCard(log: SessionLog) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(log.sessionTitle, style = MaterialTheme.typography.titleMedium)
            val done = log.exercises.count { !it.skipped }
            val details = listOfNotNull(
                log.startedAt.format(historyFormat),
                "${log.durationMin} min",
                "$done exercice(s) sur ${log.exercises.size}",
                log.feeling?.label,
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            log.painZone?.let {
                Text("Douleur : $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Frise de la semaine L M M J V S D : pastille pleine = jour avec séance ou activité, aujourd'hui
 * cerclé ; la flamme des semaines actives à côté (fil Design, 12/11/2026).
 */
@Composable
private fun WeekStrip(state: WorkoutUiState) {
    val today = LocalDate.now()
    val active = HealthyTheme.ambiance.accent
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0 until 7).forEach { i ->
            val day = state.monday.plusDays(i.toLong())
            val done = day in state.activeDaysThisWeek
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(dayName(i + 1).take(1), style = MaterialTheme.typography.labelMedium)
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(28.dp)
                        .then(
                            if (day == today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
                        )
                        .padding(4.dp)
                        .background(if (done) active else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreakFlame(size = 20.dp)
                Text(" " + ThemeTexts.current.streakValue(state.streakWeeks), style = MaterialTheme.typography.titleMedium)
            }
            Text(ThemeTexts.current.streakUnit(state.streakWeeks), style = MaterialTheme.typography.labelSmall)
        }
    }
}
