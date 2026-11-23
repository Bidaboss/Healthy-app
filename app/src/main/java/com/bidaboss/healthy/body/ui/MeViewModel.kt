package com.bidaboss.healthy.body.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.body.data.MaintenanceInfo
import com.bidaboss.healthy.body.data.MeProgress
import com.bidaboss.healthy.core.body.BodyProfile
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.body.WeighIn
import com.bidaboss.healthy.ui.toInputText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ce que l'onglet Moi montre : profil, poids, badges. */
data class MeUiState(
    val profile: BodyProfile? = null,
    /** Maintien du jour (avec l'ajustement adopté), null sans profil. */
    val maintenanceKcal: Int? = null,
    /** Le maintien a été ajusté d'après les repas et les pesées. */
    val maintenanceAdjusted: Boolean = false,
    val progress: MeProgress = MeProgress(),
    /** Pesées de la plus récente à la plus ancienne. */
    val weighIns: List<WeighIn> = emptyList(),
    /** Courbe lissée (moyenne sur 7 jours), de la plus ancienne à la plus récente. */
    val trend: List<Pair<LocalDate, Double>> = emptyList(),
    val form: WeighInForm = WeighInForm(),
    val formOpen: Boolean = false,
    val today: LocalDate = LocalDate.now(),
) {
    val lastWeighIn: WeighIn? get() = weighIns.firstOrNull()
}

/**
 * Onglet Moi (refonte, lot 4). `body` ne dépend pas du sport ni des repas : la progression (Pousse, badges) et le
 * maintien arrivent par des fonctions que fournit AppContainer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModel(
    private val body: BodyRepository,
    private val maintenanceKcal: (LocalDate) -> Flow<Int?>,
    private val maintenanceInfo: (LocalDate) -> Flow<MaintenanceInfo?>,
    progress: Flow<MeProgress>,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val today = MutableStateFlow(todayProvider())
    private val form = MutableStateFlow(WeighInForm())
    private val formOpen = MutableStateFlow(false)

    private class Data(val profile: BodyProfile?, val kcal: Int?, val adjusted: Boolean, val weighIns: List<WeighIn>)

    private val data = today.flatMapLatest { day ->
        combine(
            body.observeProfile(),
            maintenanceKcal(day),
            maintenanceInfo(day),
            body.observeWeighIns(),
        ) { profile, kcal, info, weighIns -> Data(profile, kcal, (info?.adjustmentKcal ?: 0) != 0, weighIns) }
    }

    val uiState: StateFlow<MeUiState> = combine(data, progress, form, formOpen, today) { d, p, f, open, day ->
        MeUiState(
            profile = d.profile,
            maintenanceKcal = d.kcal,
            maintenanceAdjusted = d.adjusted,
            progress = p,
            weighIns = d.weighIns.sortedByDescending { it.date },
            trend = Maintenance.weightTrend(d.weighIns),
            form = f,
            formOpen = open,
            today = day,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeUiState())

    /** À chaque retour sur l'écran : si minuit est passé, tout se recalcule pour le nouveau jour. */
    fun onResume() {
        today.value = todayProvider()
    }

    /** « + Pesée » : ouvre la saisie, avec la pesée du jour si elle existe déjà (pour la corriger). */
    fun openWeighIn() {
        val existing = uiState.value.weighIns.firstOrNull { it.date == todayProvider() }
        form.value = existing?.let { WeighInForm(formatNumber(it.weightKg), it.waistCm?.let(::formatNumber).orEmpty()) } ?: WeighInForm()
        formOpen.value = true
    }

    fun closeWeighIn() {
        formOpen.value = false
    }

    fun setWeight(text: String) = form.update { it.copy(weightKg = text, error = null) }

    fun setWaist(text: String) = form.update { it.copy(waistCm = text, error = null) }

    fun saveWeighIn() {
        val weighIn = form.value.toWeighIn(todayProvider()).getOrElse { e ->
            form.update { it.copy(error = e.message) }
            return
        }
        viewModelScope.launch {
            body.saveWeighIn(weighIn)
            formOpen.value = false
        }
    }

    private fun formatNumber(value: Double): String = value.toInputText()
}
