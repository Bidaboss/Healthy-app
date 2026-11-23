package com.bidaboss.healthy.today.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.progress.Badge
import com.bidaboss.healthy.core.progress.BadgeProgress
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.ui.BadgeIcon
import com.bidaboss.healthy.ui.Pousse
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.ThemeTexts
import com.bidaboss.healthy.ui.theme.Ambiance
import com.bidaboss.healthy.ui.theme.HealthyTheme
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Un badge à l'écran : débloqué ou non, et où on en est. */
data class BadgeItem(val badge: Badge, val current: Int, val unlocked: Boolean)

class BadgesViewModel(workoutLogRepository: WorkoutLogRepository, mealRepository: MealRepository) : ViewModel() {
    val badges: StateFlow<List<BadgeItem>> = combine(
        workoutLogRepository.observeSessionCount(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)),
        workoutLogRepository.observeActiveDates(),
        mealRepository.observeMealDates(),
    ) { count, activeDates, mealDates ->
        // Semaines actives : séances du plan et activités ajoutées à la main.
        val progress = BadgeProgress(count, activeDates, mealDates)
        Badge.entries.map { BadgeItem(it, it.current(progress), it.isUnlocked(progress)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun BadgesScreen(
    onClose: () -> Unit,
    viewModel: BadgesViewModel = viewModel(
        factory = healthyViewModelFactory { BadgesViewModel(it.workoutLogRepository, it.mealRepository) },
    ),
) {
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val unlocked = badges.count { it.unlocked }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pousse(if (unlocked > 0) Mood.PROUD else Mood.ENCOURAGING, size = 64.dp)
            Column {
                Text(ThemeTexts.current.badgesTitle, style = MaterialTheme.typography.headlineMedium)
                Text("$unlocked sur ${badges.size} débloqués", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        badges.forEach { BadgeCard(it) }
        TextButton(onClick = onClose) { Text("Retour") }
    }
}

@Composable
private fun BadgeCard(item: BadgeItem) {
    val state = if (item.unlocked) "débloqué" else "${item.current} sur ${item.badge.target}"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "${item.badge.title}, $state. ${item.badge.goal}" },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BadgeIcon(item.badge, item.unlocked)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.badge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.badge.goal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!item.unlocked && item.badge.target > 1) {
                    LinearProgressIndicator(
                        progress = { item.current.toFloat() / item.badge.target },
                        modifier = Modifier.fillMaxWidth(),
                        color = HealthyTheme.ambiance.accent,
                        // Pas de point au bout de la barre : à 0, il faisait croire à un début de progression.
                        drawStopIndicator = {},
                    )
                    Text(state, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
