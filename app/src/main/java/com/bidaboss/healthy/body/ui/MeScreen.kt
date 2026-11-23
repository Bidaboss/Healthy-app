package com.bidaboss.healthy.body.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.bidaboss.healthy.ui.BadgeIcon
import com.bidaboss.healthy.ui.PhotoBackground
import com.bidaboss.healthy.ui.PhotoTab
import com.bidaboss.healthy.ui.PhotoTitle
import com.bidaboss.healthy.ui.theme.HealthyTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.ui.Pousse
import com.bidaboss.healthy.ui.ThemeTexts
import com.bidaboss.healthy.ui.formatThousands
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.toInputText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

/**
 * Onglet Moi (refonte, lot 4 ; fond photo et mise en page du prototype aux retouches) : profil, Mon poids, Mes badges.
 * L'icône à curseurs ouvre Réglages.
 */
@Composable
fun MeScreen(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWeight: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenReferences: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeViewModel = viewModel(
        factory = healthyViewModelFactory { MeViewModel(it.bodyRepository, it.maintenanceKcal, it.maintenanceInfo, it.meProgress) },
    ),
    maintenanceViewModel: MaintenanceViewModel = viewModel(
        factory = healthyViewModelFactory { MaintenanceViewModel(it.maintenanceInfo, it.bodyRepository) },
    ),
) {
    val maintenance by maintenanceViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        maintenanceViewModel.onResume()
        onPauseOrDispose { }
    }
    // Photo de fond comme sur Aujourd'hui (prototype) : titre blanc, profil qui chevauche la photo.
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PhotoBackground(PhotoTab.TODAY) {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 40.dp)) {
                    PhotoTitle("Moi", modifier = Modifier.align(Alignment.CenterStart))
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.align(Alignment.CenterEnd).size(44.dp).background(Color.White.copy(alpha = 0.28f), CircleShape),
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Réglages", tint = Color.White)
                    }
                }
                ProfileCard(state, onOpenProfile)
                ReferencesEntry(onOpenReferences)
                // La proposition d'ajuster le maintien (avant la refonte sur Aujourd'hui) : visible seulement quand il y en a une.
                MaintenanceCard(maintenance.info, onOpenDetail = onOpenProfile, onLater = maintenanceViewModel::later)
                SectionLabel("MON POIDS")
                WeightCard(state, onOpenWeight, onAdd = viewModel::openWeighIn)
                state.maintenanceKcal?.let { MaintenanceCardInfo(it, state.maintenanceAdjusted) }
                SectionLabel(ThemeTexts.current.badgesTitle.uppercase(Locale.FRENCH) + " · ${state.progress.unlockedBadges} SUR ${state.progress.totalBadges}")
                BadgesGrid(state, onOpenBadges)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (state.formOpen) {
        WeighInDialog(state, viewModel::setWeight, viewModel::setWaist, viewModel::saveWeighIn, viewModel::closeWeighIn)
    }
}

/** Petit titre de section posé sur la photo : blanc, majuscules, ombre douce. */
@Composable
private fun SectionLabel(text: String) {
    // Sur une pastille sombre : lisible sur toutes les parties de la photo, y compris là où elle se fond dans le fond clair.
    PhotoTitle(
        text,
        modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.5.sp),
    )
}

@Composable
private fun ProfileCard(state: MeUiState, onOpen: () -> Unit) {
    val profile = state.profile
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Pousse(if (state.progress.unlockedBadges > 0) Mood.PROUD else Mood.HAPPY, size = 88.dp, growth = state.progress.growth)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile?.firstName?.takeIf { it.isNotBlank() } ?: "Mon profil", style = MaterialTheme.typography.titleLarge)
                if (profile == null) {
                    Text("Complète ton profil pour voir ton maintien.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val kg = state.lastWeighIn?.weightKg ?: profile.weightKg
                    Text(
                        (state.maintenanceKcal?.let { "Maintien ${formatThousands(it)} kcal · " }.orEmpty()) + "${kg.toInputText()} kg",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        ThemeTexts.current.streakDescription(state.progress.streakWeeks).replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** « Maintien estimé » : petite carte sous le poids, comme le prototype. */
@Composable
private fun MaintenanceCardInfo(kcal: Int, adjusted: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Maintien estimé", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${formatThousands(kcal)} kcal par jour", style = MaterialTheme.typography.titleLarge)
            Text(
                if (adjusted) "Ajusté d'après tes repas et tes pesées." else "Calculé d'après ton profil.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** « hier », « aujourd'hui » ou « lundi 23 novembre ». */
private fun lastWeighInWhen(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "aujourd'hui"
    today.minusDays(1) -> "hier"
    else -> date.format(dayFormat)
}

@Composable
private fun WeightCard(state: MeUiState, onOpen: () -> Unit, onAdd: () -> Unit) {
    val last = state.lastWeighIn
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (last == null) "Aucune pesée pour l'instant" else "Dernière pesée · ${lastWeighInWhen(last.date, state.today)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = HealthyTheme.ambiance.accent, contentColor = HealthyTheme.ambiance.onAccent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp),
                ) { Text("+ Pesée", fontWeight = FontWeight.Black) }
            }
            if (last != null) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${last.weightKg.toInputText()} kg", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    weekDelta(state.trend)?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
            if (state.trend.size >= 2) {
                WeightCurve(state.trend)
                Text(
                    "Ligne : moyenne sur 7 jours, plus fiable que la pesée d'un seul jour.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) { Text("Toutes mes pesées") }
        }
    }
}

/** « −0,4 kg sur 7 jours » : la courbe lissée d'aujourd'hui moins celle d'il y a 7 jours (rien s'il n'y a pas assez d'historique). */
private fun weekDelta(trend: List<Pair<LocalDate, Double>>): String? {
    val latest = trend.lastOrNull() ?: return null
    val before = trend.lastOrNull { !it.first.isAfter(latest.first.minusDays(7)) } ?: return null
    val delta = Math.round((latest.second - before.second) * 10) / 10.0
    val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
    return "$sign${kotlin.math.abs(delta).toInputText()} kg sur 7 jours"
}

/** Courbe de la moyenne sur 7 jours (60 derniers jours au plus) : aire claire, trait, point d'accent au bout. */
@Composable
private fun WeightCurve(trend: List<Pair<LocalDate, Double>>) {
    val points = trend.takeLast(60)
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.surfaceVariant
    val dot = HealthyTheme.ambiance.accent
    val first = points.first().first
    val span = ChronoUnit.DAYS.between(first, points.last().first).coerceAtLeast(1).toFloat()
    val min = points.minOf { it.second } - 0.3
    val max = points.maxOf { it.second } + 0.3
    val description = "Courbe du poids de ${points.first().second.toInputText()} kg à ${points.last().second.toInputText()} kg " +
        "du ${points.first().first.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))} au ${points.last().first.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))}."
    Canvas(Modifier.fillMaxWidth().height(120.dp).clearAndSetSemantics { contentDescription = description }) {
        val pad = 6.dp.toPx()
        fun x(date: LocalDate) = pad + ChronoUnit.DAYS.between(first, date) / span * (size.width - 2 * pad)
        fun y(kg: Double) = pad + ((max - kg) / (max - min) * (size.height - 2 * pad)).toFloat()
        val path = Path()
        points.forEachIndexed { i, (date, kg) -> if (i == 0) path.moveTo(x(date), y(kg)) else path.lineTo(x(date), y(kg)) }
        val area = Path().apply {
            addPath(path)
            lineTo(x(points.last().first), size.height)
            lineTo(x(first), size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(path, line, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(dot, radius = 6.dp.toPx(), center = Offset(x(points.last().first), y(points.last().second)))
    }
}

/** Mes badges : grille de 3 colonnes, un badge par tuile (le détail s'ouvre en touchant). */
@Composable
private fun BadgesGrid(state: MeUiState, onOpen: () -> Unit) {
    val badges = state.progress.badges
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        badges.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    Card(onClick = onOpen, modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BadgeIcon(item.badge, item.unlocked, size = 58.dp)
                            Text(
                                item.badge.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                color = if (item.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WeighInDialog(
    state: MeUiState,
    onWeight: (String) -> Unit,
    onWaist: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val done = state.weighIns.any { it.date == state.today }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (done) "Corriger la pesée du jour" else "Pesée du jour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.form.weightKg,
                    onValueChange = onWeight,
                    label = { Text("Poids (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.form.waistCm,
                    onValueChange = onWaist,
                    label = { Text("Tour de taille (cm, facultatif)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Conseil : pèse-toi le matin, à jeun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Annuler") } },
    )
}

/** Entrée vers « Mes repères » depuis le profil (PRD repères nationaux : Aujourd'hui et Moi, pas de nouvel onglet). */
@Composable
private fun ReferencesEntry(onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Mes repères", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tes chiffres face aux recommandations, sur 7 ou 28 jours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
