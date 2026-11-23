package com.bidaboss.healthy.body.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.body.WeighIn
import com.bidaboss.healthy.ui.parseNumber
import com.bidaboss.healthy.ui.toInputText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WeighInForm(val weightKg: String = "", val waistCm: String = "", val error: String? = null)

data class WeightUiState(
    val form: WeighInForm = WeighInForm(),
    /** Pesées de la plus récente à la plus ancienne. */
    val weighIns: List<WeighIn> = emptyList(),
    /** Courbe lissée (moyenne sur 7 jours), de la plus ancienne à la plus récente. */
    val trend: List<Pair<LocalDate, Double>> = emptyList(),
    val todayDone: Boolean = false,
)

/** Vérifie la pesée saisie : poids obligatoire, tour de taille facultatif. */
fun WeighInForm.toWeighIn(date: LocalDate): Result<WeighIn> {
    val weight = parseNumber(weightKg)
    val waist = waistCm.takeIf { it.isNotBlank() }?.let { parseNumber(it) ?: -1.0 }
    val message = when {
        weight == null || weight < 30 || weight > 300 -> "Indique ton poids en kilos, entre 30 et 300."
        waist != null && (waist < 40 || waist > 250) -> "Le tour de taille se note en centimètres, entre 40 et 250 (ou laisse vide)."
        else -> null
    }
    return if (message != null) Result.failure(IllegalArgumentException(message)) else Result.success(WeighIn(date, weight!!, waist))
}

class WeightViewModel(
    private val repository: BodyRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val form = MutableStateFlow(WeighInForm())

    val uiState: StateFlow<WeightUiState> = combine(form, repository.observeWeighIns()) { form, weighIns ->
        WeightUiState(
            form = form,
            weighIns = weighIns.sortedByDescending { it.date },
            trend = Maintenance.weightTrend(weighIns),
            todayDone = weighIns.any { it.date == today() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    init {
        // Si la pesée du jour existe déjà, on la montre pour pouvoir la corriger.
        viewModelScope.launch {
            repository.observeWeighIns().first().firstOrNull { it.date == today() }?.let { w ->
                form.value = WeighInForm(w.weightKg.toInputText(), w.waistCm.toInputText())
            }
        }
    }

    fun setWeight(text: String) = form.update { it.copy(weightKg = text, error = null) }

    fun setWaist(text: String) = form.update { it.copy(waistCm = text, error = null) }

    fun save() {
        val weighIn = form.value.toWeighIn(today()).getOrElse { e ->
            form.update { it.copy(error = e.message) }
            return
        }
        viewModelScope.launch { repository.saveWeighIn(weighIn) }
    }

    fun delete(date: LocalDate) {
        viewModelScope.launch { repository.deleteWeighIn(date) }
    }
}
