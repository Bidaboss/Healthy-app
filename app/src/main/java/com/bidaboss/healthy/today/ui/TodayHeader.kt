package com.bidaboss.healthy.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.progress.Growth
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.core.progress.Motivation
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.settings.data.PousseStageStore
import com.bidaboss.healthy.ui.Pousse
import com.bidaboss.healthy.ui.StreakFlame
import com.bidaboss.healthy.ui.ThemeTexts
import com.bidaboss.healthy.ui.headerPattern
import com.bidaboss.healthy.ui.theme.HealthyTheme
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HeaderUiState(
    val date: LocalDate = LocalDate.now(),
    val firstName: String? = null,
    val mood: Mood = Mood.HAPPY,
    val mealCount: Int = 0,
    /** Semaines d'affilée avec au moins une séance. */
    val streakWeeks: Int = 0,
    /** Quand la série montera : elle compte les semaines, pas les séances. */
    val streakHint: String = "",
    /** Pousse grandit avec la série (fil Design, B3). */
    val growth: Growth = Growth.LEAF,
    /** Message de montée de niveau, tant que la personne ne l'a pas fermé. */
    val levelUpMessage: String? = null,
)

/** En-tête d'Aujourd'hui : bonjour, humeur de Pousse et flamme des semaines actives. */
@OptIn(ExperimentalCoroutinesApi::class)
class HeaderViewModel(
    bodyRepository: BodyRepository,
    workoutRepository: WorkoutRepository,
    workoutLogRepository: WorkoutLogRepository,
    mealRepository: MealRepository,
    private val stageStore: PousseStageStore,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val date = MutableStateFlow(today())
    private val acknowledgements = MutableStateFlow(0)

    val uiState: StateFlow<HeaderUiState> = date.flatMapLatest { day ->
        combine(
            bodyRepository.observeProfile(),
            workoutRepository.observeActivePlan(),
            workoutLogRepository.observeActiveDates(),
            mealRepository.observeMeals(day),
            acknowledgements,
        ) { profile, plan, activeDates, meals, _ ->
            val growth = Motivation.growth(activeDates, day)
            val levelUp = Growth.levelUp(stageStore.lastSeen(), growth)
            // Premier lancement ou étape qui redescend : on note l'étape sans rien dire.
            if (levelUp == null) stageStore.setLastSeen(growth)
            val planned = plan?.sessions?.any { it.dayOfWeek == day.dayOfWeek.value } == true
            val mood = Motivation.mood(sessionDoneToday = day in activeDates, sessionPlannedToday = planned)
            HeaderUiState(
                date = day,
                firstName = profile?.firstName?.takeIf { it.isNotBlank() },
                mood = mood,
                mealCount = meals.size,
                streakWeeks = Motivation.activeWeekStreak(activeDates, day),
                streakHint = Motivation.streakHint(activeDates, day),
                growth = growth,
                levelUpMessage = levelUp,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeaderUiState(date = today()))

    fun onResume() {
        date.value = today()
    }

    /** La personne a lu le message : l'étape est notée comme vue. */
    fun onLevelUpSeen() {
        stageStore.setLastSeen(uiState.value.growth)
        acknowledgements.value++
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

@Composable
fun TodayHeader(state: HeaderUiState, onOpenBadges: () -> Unit, modifier: Modifier = Modifier) {
    val ambiance = HealthyTheme.ambiance
    val texts = ThemeTexts.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(ambiance.header)
            .headerPattern()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Pousse(state.mood, growth = state.growth)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            texts.dayTitle?.let {
                Text(it, color = ambiance.onHeader, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                state.date.format(dayFormat).replaceFirstChar { it.titlecase(Locale.FRENCH) },
                color = ambiance.onHeader.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                texts.greeting(state.firstName),
                color = ambiance.onHeader,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                texts.headerMessage(state.mood, state.mealCount),
                color = ambiance.onHeader,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.streakHint.isNotEmpty()) {
                Text(state.streakHint, color = ambiance.onHeader.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
            }
        }
        StreakBadge(state.streakWeeks, texts, onOpenBadges)
    }
}

@Composable
private fun StreakBadge(weeks: Int, texts: ThemeTexts, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClickLabel = "Voir mes badges", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clearAndSetSemantics {
                contentDescription = "${texts.streakDescription(weeks)}. Voir mes badges"
                role = Role.Button
                onClick(label = "Voir mes badges") { onClick(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StreakFlame()
        Text(texts.streakValue(weeks), color = Color(0xFF17301F), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(texts.streakUnit(weeks), color = Color(0xFF4A5E50), style = MaterialTheme.typography.labelSmall)
    }
}
