package com.bidaboss.healthy.body.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.ui.healthyViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class OnboardingSummary(val firstName: String, val maintenanceKcal: Int, val proteinG: Int, val waterMl: Int)

class OnboardingSummaryViewModel(repository: BodyRepository, today: () -> LocalDate = { LocalDate.now() }) : ViewModel() {
    val summary: StateFlow<OnboardingSummary?> =
        combine(repository.observeProfile(), repository.observeWeighIns()) { profile, weighIns ->
            profile?.let {
                val day = today()
                val weight = Maintenance.referenceWeightKg(it.weightKg, weighIns, day)
                OnboardingSummary(
                    firstName = it.firstName.orEmpty(),
                    maintenanceKcal = Maintenance.maintenanceKcal(it, weighIns, day),
                    proteinG = Maintenance.proteinTargetG(weight),
                    waterMl = Hydration.targetMl(it.sex, weight, sessionsToday = 0),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Dernière étape de l'accueil : ce que l'app a calculé. Tout reste modifiable dans Réglages. */
@Composable
fun OnboardingSummaryScreen(
    onDone: () -> Unit,
    viewModel: OnboardingSummaryViewModel = viewModel(
        factory = healthyViewModelFactory { OnboardingSummaryViewModel(it.bodyRepository) },
    ),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val s = summary ?: return@Column
        Text("C'est prêt, ${s.firstName} !", style = MaterialTheme.typography.headlineMedium)
        Text("Ton maintien : environ ${s.maintenanceKcal} kcal par jour.", style = MaterialTheme.typography.titleMedium)
        Text("Protéines : environ ${s.proteinG} g par jour.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Eau : ${Hydration.formatLiters(s.waterMl)} par jour (plus 0,5 L les jours de séance).",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Pour ton programme sportif : Réglages, « Copier le prompt programme sport », puis colle la réponse de ton IA dans « Importer ». Tout reste modifiable dans Réglages.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("C'est parti") }
    }
}
