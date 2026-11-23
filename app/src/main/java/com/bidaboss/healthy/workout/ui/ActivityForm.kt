package com.bidaboss.healthy.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.core.workout.BodyPart
import com.bidaboss.healthy.workout.data.FreeActivity
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Les types proposés ; « Autre » ouvre un champ libre. Les activités plus anciennes gardent leur type tel quel. */
internal val activityTypes = listOf("Vélo", "Marche", "Course", "Kiné")

/** Types qui peuvent venir d'un trajet enregistré. */
internal fun typeHasRoutes(type: String) = type in listOf("Vélo", "Marche", "Course")

/** Vitesse moyenne proposée pour estimer la durée d'un trajet, en km/h. */
internal fun routeSpeedKmh(type: String): Double = when (type) {
    "Marche" -> 5.0
    "Course" -> 10.0
    else -> 15.0
}

/** Jour proposé : aujourd'hui, hier, ou un autre jour passé choisi au calendrier. */
internal enum class DayChoice { TODAY, YESTERDAY, OTHER }

/** État du formulaire d'activité (type, durée, jour, zones), partagé par l'ajout et la modification. */
@Stable
internal class ActivityFormState(initial: FreeActivity?, private val today: LocalDate) {
    var type by mutableStateOf(initial?.type ?: activityTypes.first())
    var other by mutableStateOf(initial != null && initial.type !in activityTypes)
    var minutesText by mutableStateOf(initial?.durationMin?.toString() ?: "30")
    var dayChoice by mutableStateOf(
        when (initial?.date) {
            null, today -> DayChoice.TODAY
            today.minusDays(1) -> DayChoice.YESTERDAY
            else -> DayChoice.OTHER
        },
    )
    var otherDay by mutableStateOf(initial?.date?.takeIf { it < today.minusDays(1) } ?: today.minusDays(2))
    var parts by mutableStateOf(initial?.bodyParts?.toSet() ?: emptySet())
    var pickingDay by mutableStateOf(false)

    val minutes: Int? get() = minutesText.toIntOrNull()
    val valid: Boolean
        get() = type.isNotBlank() && minutes != null && minutes!! in 1..WorkoutLogRepository.MAX_ACTIVITY_MINUTES
    val date: LocalDate
        get() = when (dayChoice) {
            DayChoice.TODAY -> today
            DayChoice.YESTERDAY -> today.minusDays(1)
            DayChoice.OTHER -> otherDay
        }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityFormFields(form: ActivityFormState, today: LocalDate, showMinutes: Boolean = true) {
    Text("Quoi ?", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        activityTypes.forEach { name ->
            FilterChip(
                selected = !form.other && form.type == name,
                onClick = {
                    form.other = false
                    form.type = name
                },
                label = { Text(name) },
            )
        }
        FilterChip(
            selected = form.other,
            onClick = {
                form.other = true
                form.type = ""
            },
            label = { Text("Autre") },
        )
    }
    if (form.other) {
        OutlinedTextField(
            value = form.type,
            onValueChange = { form.type = it.take(WorkoutLogRepository.MAX_ACTIVITY_TYPE_LENGTH) },
            label = { Text("Quelle activité ?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (showMinutes) {
        OutlinedTextField(
            value = form.minutesText,
            onValueChange = { text -> form.minutesText = text.filter(Char::isDigit).take(4) },
            label = { Text("Durée en minutes") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text("Quand ?", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = form.dayChoice == DayChoice.TODAY,
            onClick = { form.dayChoice = DayChoice.TODAY },
            label = { Text("Aujourd'hui") },
        )
        FilterChip(
            selected = form.dayChoice == DayChoice.YESTERDAY,
            onClick = { form.dayChoice = DayChoice.YESTERDAY },
            label = { Text("Hier") },
        )
        FilterChip(
            selected = form.dayChoice == DayChoice.OTHER,
            onClick = { form.pickingDay = true },
            label = { Text(if (form.dayChoice == DayChoice.OTHER) form.otherDay.format(historyFormat) else "Autre jour") },
        )
    }
    if (showMinutes) {
        Text("Zones travaillées (facultatif)", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyPart.entries.forEach { part ->
                FilterChip(
                    selected = part in form.parts,
                    onClick = { form.parts = if (part in form.parts) form.parts - part else form.parts + part },
                    label = { Text(part.label) },
                )
            }
        }
    }

    if (form.pickingDay) {
        val todayMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = form.otherDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { form.pickingDay = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms ->
                        form.otherDay = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        form.dayChoice = DayChoice.OTHER
                    }
                    form.pickingDay = false
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { form.pickingDay = false }) { Text("Annuler") } },
        ) { DatePicker(state = picker) }
    }
}

/** Modification d'une activité déjà ajoutée. */
@Composable
internal fun EditActivityDialog(
    initial: FreeActivity,
    onSave: (type: String, durationMin: Int, date: LocalDate, bodyParts: Set<BodyPart>) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val form = remember { ActivityFormState(initial, today) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier l'activité") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { ActivityFormFields(form, today) }
        },
        confirmButton = {
            TextButton(
                onClick = { form.minutes?.let { onSave(form.type, it, form.date, form.parts) } },
                enabled = form.valid,
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
