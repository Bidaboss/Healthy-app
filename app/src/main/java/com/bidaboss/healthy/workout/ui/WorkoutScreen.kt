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
import com.bidaboss.healthy.core.workout.BodyPart
import com.bidaboss.healthy.ui.EmptyState
import com.bidaboss.healthy.ui.StreakFlame
import com.bidaboss.healthy.ui.ThemeTexts
import com.bidaboss.healthy.ui.dayName
import com.bidaboss.healthy.ui.formatExerciseDose
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.theme.HealthyTheme
import com.bidaboss.healthy.ui.theme.healthyTonalIconButtonColors
import com.bidaboss.healthy.ui.workoutBackground
import com.bidaboss.healthy.workout.data.FreeActivity
import com.bidaboss.healthy.workout.data.SessionLog
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val historyFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

@Composable
fun WorkoutScreen(
    onStartSession: (sessionId: Long, durationMin: Int) -> Unit,
    onImport: () -> Unit,
    onOpenWatch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = viewModel(
        factory = healthyViewModelFactory { WorkoutViewModel(it.workoutRepository, it.workoutLogRepository, it.referenceWeightKg, it.isWatchConnected) },
    ),
) {
    val hasWatch by viewModel.hasWatch.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val plan = state.plan
    var sessionToStart by remember { mutableStateOf<WorkoutSession?>(null) }
    var addingActivity by remember { mutableStateOf(false) }
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

    LazyColumn(
        modifier = modifier.fillMaxSize().workoutBackground(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sport", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                // Actions secondaires derrière « + » : le bouton principal de l'écran reste la séance.
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    FilledTonalIconButton(colors = healthyTonalIconButtonColors(), onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Ajouter")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Ajouter une activité (kiné, marche…)") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                addingActivity = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Importer depuis ton IA") },
                            leadingIcon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onImport()
                            },
                        )
                    }
                }
            }
            WeekStrip(state)
            Text(
                weekSummary(state.sessionsThisWeek, state.activitiesThisWeek),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            // Visible seulement si la montre est connectée.
            if (hasWatch) {
                OutlinedButton(onClick = onOpenWatch) { Text("Mes stats montre") }
            }
        }
        if (plan == null) {
            item { EmptyState(ThemeTexts.current.noPlan, action = "Importer mon plan", onAction = onImport) }
        } else {
            item {
                Text(plan.name, style = MaterialTheme.typography.titleLarge)
                plan.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            items(plan.sessions, key = { "session-${it.id}" }) { session ->
                SessionCard(session, onStart = { sessionToStart = session })
            }
        }
        if (state.activities.isNotEmpty()) {
            item { Text("Activités ajoutées", style = MaterialTheme.typography.titleLarge) }
            items(state.activities, key = { "activity-${it.id}" }) { activity ->
                ActivityCard(activity, state.activityKcal[activity.id], onEdit = { activityToEdit = activity }, onDelete = { activityToDelete = activity })
            }
        }
        if (state.history.isNotEmpty()) {
            item {
                Text("Historique", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Pour faire évoluer ton plan, envoie ton bilan des 4 dernières semaines à Claude.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = viewModel::exportReport, modifier = Modifier.fillMaxWidth()) {
                    Text("Envoyer mon bilan à Claude")
                }
            }
            items(state.history, key = { "log-${it.id}" }) { log -> HistoryCard(log) }
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

    if (addingActivity) {
        AddActivityDialog(
            onAdd = { type, durationMin, date, bodyParts ->
                addingActivity = false
                viewModel.addActivity(type, durationMin, date, bodyParts)
            },
            onDismiss = { addingActivity = false },
        )
    }

    activityToEdit?.let { activity ->
        AddActivityDialog(
            initial = activity,
            onAdd = { type, durationMin, date, bodyParts ->
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

private val activityTypes = listOf("Kiné", "Renforcement", "Marche", "Vélo", "Vélo électrique", "Course", "Natation")

/** Jour proposé dans la fenêtre d'ajout : aujourd'hui, hier, ou un autre jour passé choisi au calendrier. */
private enum class DayChoice { TODAY, YESTERDAY, OTHER }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddActivityDialog(
    onAdd: (type: String, durationMin: Int, date: LocalDate, bodyParts: Set<BodyPart>) -> Unit,
    onDismiss: () -> Unit,
    /** Activité à modifier ; null = nouvelle activité. */
    initial: FreeActivity? = null,
) {
    val today = remember { LocalDate.now() }
    var type by remember { mutableStateOf(initial?.type ?: activityTypes.first()) }
    var other by remember { mutableStateOf(initial != null && initial.type !in activityTypes) }
    var minutesText by remember { mutableStateOf(initial?.durationMin?.toString() ?: "30") }
    var dayChoice by remember {
        mutableStateOf(
            when (initial?.date) {
                null, today -> DayChoice.TODAY
                today.minusDays(1) -> DayChoice.YESTERDAY
                else -> DayChoice.OTHER
            },
        )
    }
    var otherDay by remember { mutableStateOf(initial?.date?.takeIf { it < today.minusDays(1) } ?: today.minusDays(2)) }
    var pickingDay by remember { mutableStateOf(false) }
    var parts by remember { mutableStateOf(initial?.bodyParts?.toSet() ?: emptySet()) }
    val minutes = minutesText.toIntOrNull()
    val valid = type.isNotBlank() && minutes != null && minutes in 1..WorkoutLogRepository.MAX_ACTIVITY_MINUTES
    val date = when (dayChoice) {
        DayChoice.TODAY -> today
        DayChoice.YESTERDAY -> today.minusDays(1)
        DayChoice.OTHER -> otherDay
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Activité libre" else "Modifier l'activité") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Quoi ?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activityTypes.forEach { name ->
                        FilterChip(
                            selected = !other && type == name,
                            onClick = {
                                other = false
                                type = name
                            },
                            label = { Text(name) },
                        )
                    }
                    FilterChip(
                        selected = other,
                        onClick = {
                            other = true
                            type = ""
                        },
                        label = { Text("Autre") },
                    )
                }
                if (other) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it.take(WorkoutLogRepository.MAX_ACTIVITY_TYPE_LENGTH) },
                        label = { Text("Quelle activité ?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { text -> minutesText = text.filter(Char::isDigit).take(4) },
                    label = { Text("Durée en minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Quand ?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dayChoice == DayChoice.TODAY,
                        onClick = { dayChoice = DayChoice.TODAY },
                        label = { Text("Aujourd'hui") },
                    )
                    FilterChip(
                        selected = dayChoice == DayChoice.YESTERDAY,
                        onClick = { dayChoice = DayChoice.YESTERDAY },
                        label = { Text("Hier") },
                    )
                    FilterChip(
                        selected = dayChoice == DayChoice.OTHER,
                        onClick = { pickingDay = true },
                        label = { Text(if (dayChoice == DayChoice.OTHER) otherDay.format(historyFormat) else "Autre jour") },
                    )
                }
                Text("Zones travaillées (facultatif)", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BodyPart.entries.forEach { part ->
                        FilterChip(
                            selected = part in parts,
                            onClick = { parts = if (part in parts) parts - part else parts + part },
                            label = { Text(part.label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (minutes != null) onAdd(type, minutes, date, parts) }, enabled = valid) {
                Text(if (initial == null) "Ajouter" else "Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (pickingDay) {
        val todayMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = otherDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDay = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms ->
                        otherDay = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        dayChoice = DayChoice.OTHER
                    }
                    pickingDay = false
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { pickingDay = false }) { Text("Annuler") } },
        ) { DatePicker(state = picker) }
    }
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
                Text(activity.type, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${activity.date.format(historyFormat)} · ${activity.durationMin} min" +
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

@Composable
private fun SessionCard(session: WorkoutSession, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleMedium)
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
