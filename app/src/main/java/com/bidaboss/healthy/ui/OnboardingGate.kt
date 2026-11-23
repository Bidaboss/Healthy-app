package com.bidaboss.healthy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.body.ui.OnboardingSummaryScreen
import com.bidaboss.healthy.body.ui.ProfileScreen
import com.bidaboss.healthy.workout.ui.SportProfileScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class OnboardingGateViewModel(repository: BodyRepository) : ViewModel() {
    /** null pendant la lecture de la base, puis vrai si un profil existe. */
    val hasProfile: StateFlow<Boolean?> = repository.observeProfile()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

private enum class Step { PROFILE, SPORT, SUMMARY, DONE }

/**
 * Premier lancement : tant qu'aucun profil n'existe, l'accueil (profil, sport, résumé) remplace l'app.
 * Il ne réapparaît plus ensuite ; tout se modifie dans Réglages.
 */
@Composable
fun OnboardingGate(
    /** Nouveau téléphone : on saute l'accueil pour restaurer une sauvegarde (qui contient le profil). */
    onRestoreBackup: () -> Unit,
    viewModel: OnboardingGateViewModel = viewModel(factory = healthyViewModelFactory { OnboardingGateViewModel(it.bodyRepository) }),
    content: @Composable () -> Unit,
) {
    val hasProfile by viewModel.hasProfile.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf<Step?>(null) }
    if (step == null && hasProfile != null) step = if (hasProfile == true) Step.DONE else Step.PROFILE

    when (step) {
        null -> Unit // lecture de la base : un instant
        Step.DONE -> content()
        else -> Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.safeDrawingPadding()) {
                when (step) {
                    Step.PROFILE -> ProfileScreen(
                        onClose = { step = Step.SPORT },
                        onboarding = true,
                        onRestoreBackup = {
                            step = Step.DONE
                            onRestoreBackup()
                        },
                    )
                    Step.SPORT -> SportProfileScreen(onDone = { step = Step.SUMMARY }, onboarding = true)
                    else -> OnboardingSummaryScreen(onDone = { step = Step.DONE })
                }
            }
        }
    }
}
