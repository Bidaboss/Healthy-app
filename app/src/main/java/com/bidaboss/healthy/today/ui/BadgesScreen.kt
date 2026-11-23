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

/** Badges de série (semaines actives) : médaille ou pièce ; les autres : coffre en pirate. */
private val Badge.isStreak: Boolean
    get() = this == Badge.TWO_WEEKS || this == Badge.FOUR_WEEKS || this == Badge.TWELVE_WEEKS

/**
 * Dessin du badge selon le thème : médaille corail (Classique), coffre ou pièce d'or (Pirate),
 * tampon de passeport (Voyage), planète (Espace), trophée pixel (Arcade rétro), fanion au sommet pour les
 * séries et écusson de refuge pour les autres (Montagne). Gris tant qu'il n'est pas débloqué.
 */
@Composable
private fun BadgeIcon(badge: Badge, unlocked: Boolean) {
    val locked = MaterialTheme.colorScheme.outline
    val ambiance = HealthyTheme.ambiance
    Canvas(Modifier.size(52.dp)) {
        when {
            ambiance.theme == AppTheme.TRAVEL -> stamp(unlocked, locked, ambiance, tilt = (badge.ordinal % 3 - 1) * 8f)
            ambiance.theme == AppTheme.SPACE -> planet(unlocked, locked, badge.ordinal)
            ambiance.theme == AppTheme.ARCADE -> pixelTrophy(unlocked, locked)
            ambiance.theme == AppTheme.MOUNTAIN && badge.isStreak -> summitFlag(unlocked, locked, ambiance)
            ambiance.theme == AppTheme.MOUNTAIN -> crest(unlocked, locked, ambiance)
            ambiance.theme == AppTheme.PIRATE && !badge.isStreak -> chest(unlocked, locked, ambiance)
            else -> medal(unlocked, locked, ambiance)
        }
    }
}

private fun DrawScope.star(c: Offset, outer: Float, inner: Float) = Path().apply {
    for (i in 0 until 10) {
        val angle = Math.toRadians(-90.0 + i * 36.0)
        val radius = if (i % 2 == 0) outer else inner
        val x = c.x + (radius * Math.cos(angle)).toFloat()
        val y = c.y + (radius * Math.sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Médaille : étoile dorée sur fond d'accent une fois débloquée (pièce d'or en Pirate), grise avant. */
private fun DrawScope.medal(unlocked: Boolean, locked: Color, ambiance: Ambiance) {
    val r = size.minDimension / 2
    val c = Offset(r, r)
    val disc = if (ambiance.theme == AppTheme.PIRATE) ambiance.sparkle else ambiance.accent
    val starColor = if (ambiance.theme == AppTheme.PIRATE) Color(0xFF9A6A0B) else ambiance.sparkle
    drawCircle(if (unlocked) disc else locked.copy(alpha = 0.25f), radius = r, center = c)
    drawCircle(if (unlocked) Color.White else locked, radius = r * 0.78f, center = c, style = Stroke(width = r * 0.08f))
    val star = star(c, r * 0.52f, r * 0.22f)
    drawPath(star, if (unlocked) starColor else locked)
    if (!unlocked) drawPath(star, locked, style = Stroke(width = 2f, cap = StrokeCap.Round))
}

/** Coffre au trésor : bois brun, bandes et serrure dorées. */
private fun DrawScope.chest(unlocked: Boolean, locked: Color, ambiance: Ambiance) {
    val w = size.width
    val h = size.height
    val wood = if (unlocked) Color(0xFF8B5A2B) else locked.copy(alpha = 0.25f)
    val band = if (unlocked) ambiance.sparkle else locked
    val top = Offset(w * 0.08f, h * 0.18f)
    drawRoundRect(wood, topLeft = top, size = Size(w * 0.84f, h * 0.3f), cornerRadius = CornerRadius(w * 0.2f, w * 0.2f))
    drawRect(wood, topLeft = Offset(w * 0.08f, h * 0.44f), size = Size(w * 0.84f, h * 0.4f))
    drawLine(band, Offset(w * 0.08f, h * 0.46f), Offset(w * 0.92f, h * 0.46f), strokeWidth = w * 0.06f)
    listOf(0.22f, 0.78f).forEach { f ->
        drawLine(band, Offset(w * f, h * 0.2f), Offset(w * f, h * 0.84f), strokeWidth = w * 0.06f)
    }
    drawRoundRect(band, topLeft = Offset(w * 0.42f, h * 0.4f), size = Size(w * 0.16f, h * 0.2f), cornerRadius = CornerRadius(w * 0.03f))
    if (!unlocked) {
        drawRoundRect(locked, topLeft = top, size = Size(w * 0.84f, h * 0.66f), cornerRadius = CornerRadius(w * 0.08f), style = Stroke(width = 2f))
    }
}

/** Tampon de passeport : double cercle, contour en pointillés et avion au centre, un peu penché. */
private fun DrawScope.stamp(unlocked: Boolean, locked: Color, ambiance: Ambiance, tilt: Float) {
    val r = size.minDimension / 2
    val c = Offset(r, r)
    val ink = if (unlocked) ambiance.sparkle else locked
    rotate(tilt, pivot = c) {
        if (unlocked) drawCircle(ink.copy(alpha = 0.12f), radius = r, center = c)
        drawCircle(ink, radius = r * 0.94f, center = c, style = Stroke(width = r * 0.07f))
        drawCircle(
            ink,
            radius = r * 0.74f,
            center = c,
            style = Stroke(width = r * 0.05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.12f, r * 0.08f))),
        )
        val k = r / 26f
        drawPath(
            Path().apply {
                moveTo(c.x + 14f * k, c.y); lineTo(c.x - 10f * k, c.y - 3f * k); lineTo(c.x - 14f * k, c.y); lineTo(c.x - 10f * k, c.y + 3f * k); close()
                moveTo(c.x + 3f * k, c.y - 2f * k); lineTo(c.x - 5f * k, c.y - 13f * k); lineTo(c.x - 9f * k, c.y - 13f * k); lineTo(c.x - 3f * k, c.y - 2f * k); close()
                moveTo(c.x + 3f * k, c.y + 2f * k); lineTo(c.x - 5f * k, c.y + 13f * k); lineTo(c.x - 9f * k, c.y + 13f * k); lineTo(c.x - 3f * k, c.y + 2f * k); close()
            },
            ink,
        )
    }
}

private val PlanetColors = listOf(Color(0xFF6C4AB6), Color(0xFF3FD0E0), Color(0xFFFFD447), Color(0xFFFF8A65), Color(0xFF7BD99A))

/** Planète à anneau, une couleur par badge. */
private fun DrawScope.planet(unlocked: Boolean, locked: Color, index: Int) {
    val r = size.minDimension / 2
    val c = Offset(r, r)
    val body = if (unlocked) PlanetColors[index % PlanetColors.size] else locked.copy(alpha = 0.25f)
    val ring = if (unlocked) Color(0xFFFFD447) else locked
    drawCircle(body, radius = r * 0.56f, center = c)
    if (!unlocked) drawCircle(locked, radius = r * 0.56f, center = c, style = Stroke(width = 2f))
    rotate(-18f, pivot = c) {
        drawOval(ring, topLeft = Offset(c.x - r * 0.98f, c.y - r * 0.24f), size = Size(r * 1.96f, r * 0.48f), style = Stroke(width = r * 0.08f))
    }
    if (unlocked) drawCircle(Color.White.copy(alpha = 0.4f), radius = r * 0.14f, center = c - Offset(r * 0.2f, r * 0.22f))
}

/** Trophée pixel : coupe faite de cases. */
private fun DrawScope.pixelTrophy(unlocked: Boolean, locked: Color) {
    val rows = listOf("XXXXXXXXXX", "XYYYYYYYYX", ".XYYYYYYX.", ".XYYYYYYX.", "..XYYYYX..", "...XYYX...", "....XX....", "....XX....", "..XXXXXX..", "..XXXXXX..")
    val k = size.minDimension / 10f
    val gold = if (unlocked) Color(0xFFFFE14D) else locked.copy(alpha = 0.25f)
    val edge = if (unlocked) Color(0xFFB88A00) else locked
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch ->
            val color = when (ch) {
                'X' -> edge
                'Y' -> gold
                else -> null
            }
            if (color != null) drawRect(color, Offset(x * k, y * k), Size(k + 0.5f, k + 0.5f))
        }
    }
}

/** Série en Montagne : sommet enneigé et fanion planté. */
private fun DrawScope.summitFlag(unlocked: Boolean, locked: Color, ambiance: Ambiance) {
    val w = size.width
    val h = size.height
    val rock = if (unlocked) Color(0xFF8A8F98) else locked.copy(alpha = 0.25f)
    drawPath(Path().apply { moveTo(w * 0.04f, h * 0.94f); lineTo(w * 0.5f, h * 0.3f); lineTo(w * 0.96f, h * 0.94f); close() }, rock)
    drawPath(Path().apply { moveTo(w * 0.36f, h * 0.5f); lineTo(w * 0.5f, h * 0.3f); lineTo(w * 0.64f, h * 0.5f); lineTo(w * 0.5f, h * 0.44f); close() }, if (unlocked) Color.White else locked)
    drawLine(if (unlocked) Color(0xFF5E636B) else locked, Offset(w * 0.5f, h * 0.32f), Offset(w * 0.5f, h * 0.04f), strokeWidth = w * 0.04f)
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.04f); lineTo(w * 0.82f, h * 0.12f); lineTo(w * 0.5f, h * 0.2f); close() }, if (unlocked) ambiance.accent else locked)
}

/** Écusson de refuge : blason vert sapin bordé d'orange, sapin blanc au centre. */
private fun DrawScope.crest(unlocked: Boolean, locked: Color, ambiance: Ambiance) {
    val w = size.width
    val h = size.height
    val shield = Path().apply {
        moveTo(w * 0.14f, h * 0.08f); lineTo(w * 0.86f, h * 0.08f); lineTo(w * 0.86f, h * 0.5f)
        quadraticTo(w * 0.86f, h * 0.82f, w * 0.5f, h * 0.96f)
        quadraticTo(w * 0.14f, h * 0.82f, w * 0.14f, h * 0.5f); close()
    }
    drawPath(shield, if (unlocked) ambiance.header else locked.copy(alpha = 0.25f))
    drawPath(shield, if (unlocked) ambiance.accent else locked, style = Stroke(width = w * 0.06f))
    drawPath(
        Path().apply { moveTo(w * 0.5f, h * 0.22f); lineTo(w * 0.7f, h * 0.66f); lineTo(w * 0.3f, h * 0.66f); close() },
        if (unlocked) Color.White else locked,
    )
}
