package com.bidaboss.healthy.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.core.importformat.MealType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.FRENCH)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

/** Choix du type de repas (petit-déjeuner, déjeuner, dîner, collation). */
@Composable
fun MealTypeChips(selected: MealType, onSelect: (MealType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MealType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(type.label) },
            )
        }
    }
}

/** Deux boutons « date » et « heure » qui ouvrent les sélecteurs Android. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeButtons(dateTime: LocalDateTime, onChange: (LocalDateTime) -> Unit, modifier: Modifier = Modifier) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
            Text(dateTime.format(dateFormat))
        }
        OutlinedButton(onClick = { showTime = true }) {
            Text(dateTime.format(timeFormat))
        }
    }

    if (showDate) {
        // Le sélecteur de date travaille en UTC : on convertit seulement la date, pas l'heure.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onChange(LocalDateTime.of(date, dateTime.toLocalTime()))
                        }
                        showDate = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Annuler") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(dateTime.withHour(state.hour).withMinute(state.minute))
                        showTime = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Annuler") } },
            text = { TimePicker(state = state) },
        )
    }
}
