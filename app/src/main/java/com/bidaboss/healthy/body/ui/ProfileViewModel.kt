package com.bidaboss.healthy.body.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.BodyProfile
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.ui.parseNumber
import com.bidaboss.healthy.ui.toInputText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** Le formulaire du profil : les nombres restent du texte jusqu'à l'enregistrement. */
data class ProfileForm(
    val firstName: String = "",
    val sex: Sex? = null,
    /** Date de naissance tapée en JJ/MM/AAAA. */
    val birthDate: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val activity: ActivityLevel? = null,
)

data class ProfileUiState(
    val loading: Boolean = true,
    val form: ProfileForm = ProfileForm(),
    val error: String? = null,
    /** Maintien calculé après l'enregistrement, en kcal par jour. */
    val savedMaintenanceKcal: Int? = null,
    val proteinTargetG: Int? = null,
    val waterTargetMl: Int? = null,
)

/** Vérifie le formulaire. Rend le profil, ou le message à afficher. */
fun ProfileForm.validate(today: LocalDate): Result<BodyProfile> {
    val birth = parseBirthDate(birthDate)
    val height = parseNumber(heightCm)
    val weight = parseNumber(weightKg)
    val message = when {
        firstName.isBlank() -> "Indique ton prénom."
        firstName.trim().length > 50 -> "Ton prénom est trop long (50 lettres au plus)."
        sex == null -> "Choisis homme ou femme (sert au calcul du métabolisme)."
        birth == null || birth.isAfter(today.minusYears(10)) || birth.isBefore(today.minusYears(110)) ->
            "Indique ta date de naissance en jour/mois/année, par exemple 01/03/1991."
        height == null || height < 100 || height > 250 -> "Indique ta taille en centimètres, entre 100 et 250."
        weight == null || weight < 30 || weight > 300 -> "Indique ton poids en kilos, entre 30 et 300."
        activity == null -> "Choisis ton niveau d'activité habituel."
        else -> null
    }
    if (message != null) return Result.failure(IllegalArgumentException(message))
    return Result.success(BodyProfile(sex!!, birth!!, height!!, weight!!, activity!!, firstName = firstName.trim()))
}

private val birthDateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

/** Lit « 01/03/1991 », « 01031991 » (ou « 1/3/1991 », avec des tirets ou des points). null si la date n'existe pas. */
fun parseBirthDate(text: String): LocalDate? {
    val trimmed = text.trim()
    // Le champ de l'écran ne garde que les chiffres : « 07121987 ».
    val parts = if (trimmed.length == 8 && trimmed.all { it.isDigit() }) {
        listOf(trimmed.substring(0, 2), trimmed.substring(2, 4), trimmed.substring(4))
    } else {
        trimmed.split('/', '-', '.', ' ').filter { it.isNotEmpty() }
    }
    if (parts.size != 3) return null
    val normalized = "${parts[0].padStart(2, '0')}/${parts[1].padStart(2, '0')}/${parts[2]}"
    return try {
        LocalDate.parse(normalized, birthDateFormat)
    } catch (_: DateTimeParseException) {
        null
    }
}

fun LocalDate.toBirthDateText(): String = format(birthDateFormat)

class ProfileViewModel(
    private val repository: BodyRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = repository.observeProfile().first()
            _uiState.update {
                it.copy(
                    loading = false,
                    form = profile?.let { p ->
                        ProfileForm(
                            firstName = p.firstName.orEmpty(),
                            sex = p.sex,
                            birthDate = p.birthDate.toBirthDateText(),
                            heightCm = p.heightCm.toInputText(),
                            weightKg = p.weightKg.toInputText(),
                            activity = p.activity,
                        )
                    } ?: ProfileForm(),
                )
            }
        }
    }

    fun edit(change: (ProfileForm) -> ProfileForm) =
        _uiState.update { it.copy(form = change(it.form), error = null, savedMaintenanceKcal = null) }

    fun save() {
        val result = _uiState.value.form.validate(today())
        val profile = result.getOrElse { e ->
            _uiState.update { it.copy(error = e.message) }
            return
        }
        viewModelScope.launch {
            // Le poids du premier profil devient aussi la première pesée datée.
            repository.saveProfileWithFirstWeighIn(profile, today())
            val weighIns = repository.observeWeighIns().first()
            // Le profil relu garde l'ajustement du maintien déjà adopté.
            val saved = repository.observeProfile().first() ?: profile
            val weight = Maintenance.referenceWeightKg(saved.weightKg, weighIns, today())
            _uiState.update {
                it.copy(
                    savedMaintenanceKcal = Maintenance.maintenanceKcal(saved, weighIns, today()),
                    proteinTargetG = Maintenance.proteinTargetG(weight),
                    waterTargetMl = Hydration.targetMl(saved.sex, weight, sessionsToday = 0),
                )
            }
        }
    }
}
