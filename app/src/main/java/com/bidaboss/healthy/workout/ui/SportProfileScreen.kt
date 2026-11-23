package com.bidaboss.healthy.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.ui.dayName
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.workout.data.Equipment
import com.bidaboss.healthy.workout.data.PainZone
import com.bidaboss.healthy.workout.data.SportGoal
import com.bidaboss.healthy.workout.data.SportLevel
import com.bidaboss.healthy.workout.data.SportProfile
import com.bidaboss.healthy.workout.data.SportProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SportProfileUiState(val loading: Boolean = true, val profile: SportProfile = SportProfile(), val saved: Boolean = false)

class SportProfileViewModel(private val repository: SportProfileRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SportProfileUiState())
    val uiState: StateFlow<SportProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.observe().first()
            _uiState.value = SportProfileUiState(loading = false, profile = saved ?: SportProfile())
        }
    }

    fun edit(change: (SportProfile) -> SportProfile) = _uiState.update { it.copy(profile = change(it.profile), saved = false) }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.save(_uiState.value.profile)
            _uiState.update { it.copy(saved = true) }
            onDone()
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

/** Questions sport (accueil et Réglages). Aucune n'est bloquante : tout a une valeur par défaut. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportProfileScreen(
    onDone: () -> Unit,
    onboarding: Boolean = false,
    viewModel: SportProfileViewModel = viewModel(
        factory = healthyViewModelFactory { SportProfileViewModel(it.sportProfileRepository) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val p = state.profile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mon profil sportif", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ces réponses servent à préparer ton programme. Tout a une réponse par défaut, tu pourras changer plus tard.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loading) return@Column

        Question("Niveau sportif")
        SportLevel.entries.forEach { level -> RadioLine(level.label, p.level == level) { viewModel.edit { it.copy(level = level) } } }

        Question("Séances par semaine")
        Chips(SportProfile.SESSION_CHOICES, p.sessionsPerWeek, { "$it" }) { n -> viewModel.edit { it.copy(sessionsPerWeek = n) } }

        Question("Jours préférés (servent aussi aux rappels)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..7).forEach { day ->
                FilterChip(
                    selected = day in p.days,
                    onClick = { viewModel.edit { it.copy(days = (it.days.toSet().toggle(day)).sorted()) } },
                    label = { Text(dayName(day).take(3)) },
                )
            }
        }
        if (!p.daysMatchSessions) {
            Text(
                "Tu as coché ${p.days.size} jour(s) pour ${p.sessionsPerWeek} séances par semaine.",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Question("Durée habituelle d'une séance")
        Chips(SportProfile.DURATION_CHOICES, p.durationMin, { "$it min" }) { d -> viewModel.edit { it.copy(durationMin = d) } }

        Question("Objectif principal")
        SportGoal.entries.forEach { goal -> RadioLine(goal.label, p.goal == goal) { viewModel.edit { it.copy(goal = goal) } } }

        Question("Zones sensibles ou douleurs")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PainZone.entries.forEach { zone ->
                FilterChip(
                    selected = zone in p.painZones,
                    onClick = { viewModel.edit { it.copy(painZones = it.painZones.toggle(zone)) } },
                    label = { Text(zone.label) },
                )
            }
        }
        if (PainZone.OTHER in p.painZones) {
            TextLine("Autre zone", p.painOther) { v -> viewModel.edit { it.copy(painOther = v) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Suivi par un kiné ou un médecin pour ces douleurs", modifier = Modifier.weight(1f))
            Switch(checked = p.medicalFollowUp, onCheckedChange = { on -> viewModel.edit { it.copy(medicalFollowUp = on) } })
        }
        if (p.medicalFollowUp) {
            TextLine("Consignes reçues (facultatif)", p.medicalNotes) { v -> viewModel.edit { it.copy(medicalNotes = v) } }
        }

        Question("Matériel disponible")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.entries.forEach { item ->
                FilterChip(
                    selected = item in p.equipment,
                    onClick = { viewModel.edit { it.copy(equipment = it.equipment.toggle(item)) } },
                    label = { Text(item.label) },
                )
            }
        }

        TextLine("Exercices à éviter ou que tu n'aimes pas (facultatif)", p.avoid) { v -> viewModel.edit { it.copy(avoid = v) } }

        if (state.saved && !onboarding) Text("Enregistré.", color = MaterialTheme.colorScheme.primary)
        Button(onClick = { viewModel.save(onDone = if (onboarding) onDone else ({})) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (onboarding) "Continuer" else "Enregistrer")
        }
        if (!onboarding) TextButton(onClick = onDone) { Text("Retour") }
    }
}

@Composable
private fun Question(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun <T> Chips(choices: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { c -> FilterChip(selected = c == selected, onClick = { onSelect(c) }, label = { Text(label(c)) }) }
    }
}

@Composable
private fun TextLine(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}
