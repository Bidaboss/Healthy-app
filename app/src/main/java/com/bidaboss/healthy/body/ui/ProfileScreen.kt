package com.bidaboss.healthy.body.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.ui.healthyViewModelFactory

/**
 * Profil : sert à calculer le maintien (Mifflin-St Jeor × niveau d'activité).
 * Avec [onboarding], c'est la première étape de l'accueil : pas de retour, [onClose] après l'enregistrement.
 */
@Composable
fun ProfileScreen(
    onClose: () -> Unit,
    onboarding: Boolean = false,
    /** Accueil seulement : « J'ai une sauvegarde » ouvre l'import sans remplir de profil. */
    onRestoreBackup: (() -> Unit)? = null,
    viewModel: ProfileViewModel = viewModel(factory = healthyViewModelFactory { ProfileViewModel(it.bodyRepository) }),
    maintenanceViewModel: MaintenanceViewModel = viewModel(
        factory = healthyViewModelFactory { MaintenanceViewModel(it.maintenanceInfo, it.bodyRepository) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (onboarding) "Bienvenue dans Healthy" else "Mon profil", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ces informations restent sur ton téléphone. Elles servent seulement à calculer tes calories de maintien.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loading) return@Column

        // Accueil : une fois le profil enregistré, on passe à l'étape suivante.
        if (onboarding && state.savedMaintenanceKcal != null) {
            LaunchedEffect(Unit) { onClose() }
            return@Column
        }

        OutlinedTextField(
            value = form.firstName,
            onValueChange = { v -> viewModel.edit { it.copy(firstName = v) } },
            label = { Text("Prénom") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { sex ->
                FilterChip(
                    selected = form.sex == sex,
                    onClick = { viewModel.edit { it.copy(sex = sex) } },
                    label = { Text(sex.label) },
                )
            }
        }
        OutlinedTextField(
            value = birthDateDigits(form.birthDate),
            onValueChange = { v -> viewModel.edit { it.copy(birthDate = birthDateDigits(v)) } },
            label = { Text("Date de naissance") },
            placeholder = { Text("JJ/MM/AAAA") },
            visualTransformation = BirthDateTransformation,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField("Taille (cm)", form.heightCm, KeyboardType.Decimal) { v -> viewModel.edit { it.copy(heightCm = v) } }
        NumberField("Poids (kg)", form.weightKg, KeyboardType.Decimal) { v -> viewModel.edit { it.copy(weightKg = v) } }
        Text(
            if (onboarding) "Ce poids sera ta première pesée." else "Tes pesées remplacent ce poids dès que tu en notes une.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Activité habituelle, sans compter le sport", style = MaterialTheme.typography.titleMedium)
        ActivityLevel.entries.forEach { level ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = form.activity == level,
                        onClick = { viewModel.edit { it.copy(activity = level) } },
                        role = Role.RadioButton,
                    ),
            ) {
                RadioButton(selected = form.activity == level, onClick = null)
                Text(level.label, modifier = Modifier.padding(start = 8.dp))
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!onboarding) state.savedMaintenanceKcal?.let {
            Text(
                "Enregistré. Ton maintien : environ $it kcal par jour.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
            Text(if (onboarding) "Continuer" else "Enregistrer")
        }
        if (!onboarding) {
            val maintenanceState by maintenanceViewModel.uiState.collectAsStateWithLifecycle()
            MaintenanceSection(
                maintenanceState.info,
                onAdopt = maintenanceViewModel::adopt,
                onKeepFormula = maintenanceViewModel::keepFormula,
                onRevert = maintenanceViewModel::revertToFormula,
            )
        }
        if (!onboarding) TextButton(onClick = onClose) { Text("Retour") }
        if (onboarding && onRestoreBackup != null) {
            TextButton(onClick = onRestoreBackup) { Text("J'ai une sauvegarde : la restaurer") }
        }
    }
}

@Composable
internal fun NumberField(label: String, value: String, type: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(),
    )
}
