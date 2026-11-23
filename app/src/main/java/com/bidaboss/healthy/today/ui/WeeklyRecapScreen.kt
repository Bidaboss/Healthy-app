package com.bidaboss.healthy.today.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.core.progress.WeeklyRecap
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.Pousse
import com.bidaboss.healthy.ui.ThemeTexts
import com.bidaboss.healthy.ui.bandLabel
import com.bidaboss.healthy.ui.color
import com.bidaboss.healthy.ui.drawPousse
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.shareFile
import com.bidaboss.healthy.ui.sharedCacheDir
import com.bidaboss.healthy.ui.theme.HealthyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

/** Story plein écran du bilan de la semaine : nutrition, sport, puis Pousse et le badge (fil Design B4). */
@Composable
fun WeeklyRecapScreen(
    onClose: () -> Unit,
    viewModel: WeeklyRecapViewModel = viewModel(
        factory = healthyViewModelFactory { WeeklyRecapViewModel(it.weeklyRecapRepository, it.weeklyRecapStore) },
    ),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    BackHandler(onBack = onClose)
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    val ambiance = HealthyTheme.ambiance
    Box(Modifier.fillMaxSize().background(ambiance.header)) {
        val loaded = recap
        if (loaded == null) {
            Text("Chargement…", color = ambiance.onHeader, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val pager = rememberPagerState(pageCount = { 3 })
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val theme = ambiance.theme
        val texts = ThemeTexts.current
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // Petits traits de progression, comme une story.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(3) { i ->
                    Box(
                        Modifier.weight(1f).height(4.dp).background(
                            ambiance.onHeader.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.3f),
                            CircleShape,
                        ),
                    )
                }
            }
            Text(
                "Ta semaine du ${loaded.weekStart.format(dayMonth)} au ${loaded.weekEnd.format(dayMonth)}",
                color = ambiance.onHeader,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                Column(
                    Modifier.fillMaxSize().padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (page) {
                        0 -> NutritionPage(loaded, ambiance.onHeader)
                        1 -> SportPage(loaded, ambiance.onHeader, texts)
                        else -> PoussePage(loaded, ambiance.onHeader)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Fermer", color = ambiance.onHeader) }
                if (pager.currentPage < 2) {
                    Button(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ambiance.accent, contentColor = ambiance.onAccent),
                    ) { Text("Suivant") }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    writeShareImage(context, loaded, theme, ambiance.sparkle, ambiance.header, ambiance.onHeader, texts)
                                }
                                shareFile(context, file, "image/png", "Partager ma semaine")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ambiance.accent, contentColor = ambiance.onAccent),
                    ) { Text("Partager") }
                }
            }
        }
    }
}

@Composable
private fun BigLine(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
}

@Composable
private fun SmallLine(text: String, color: Color) {
    Text(text, color = color.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
}

@Composable
private fun NutritionPage(recap: WeeklyRecap, color: Color) {
    Text("Repas", color = color, style = MaterialTheme.typography.titleLarge)
    if (recap.mealDays == 0) {
        BigLine("Aucun repas noté", color)
        SmallLine("Cette semaine, on repart de zéro, sans pression.", color)
        return
    }
    val average = recap.averageKcal
    val maintenance = recap.maintenanceKcal
    if (average != null) BigLine("${average.roundToInt()} kcal par jour en moyenne", color)
    val band = recap.band
    if (average != null && maintenance != null && band != null) {
        Box(Modifier.size(14.dp).background(band.color(), CircleShape))
        SmallLine("${bandLabel(band, average, maintenance).replaceFirstChar(Char::uppercase)} (maintien $maintenance kcal)", color)
    }
    val of = "sur ${recap.mealDays} ${if (recap.mealDays > 1) "jours notés" else "jour noté"}"
    if (recap.proteinTargetG != null) SmallLine("Protéines atteintes : ${recap.proteinDays} $of", color)
    SmallLine("Fibres 30 g atteintes : ${recap.fiberDays} $of", color)
    if (recap.maintenanceKcal != null) SmallLine("Eau atteinte : ${recap.waterDays} $of", color)
}

@Composable
private fun SportPage(recap: WeeklyRecap, color: Color, texts: ThemeTexts) {
    Text("Sport", color = color, style = MaterialTheme.typography.titleLarge)
    BigLine(sportLine(recap), color)
    SmallLine(texts.streakDescription(recap.streakWeeks).replaceFirstChar(Char::uppercase), color)
    if (recap.sessions + recap.activities == 0) SmallLine("Une séance cette semaine relance ta série.", color)
}

@Composable
private fun PoussePage(recap: WeeklyRecap, color: Color) {
    Pousse(Mood.PROUD, size = 180.dp, growth = recap.growth)
    BigLine("Pousse est ${recap.growth.label.removePrefix("devenue ").let { if (it.startsWith("avec")) "une pousse $it" else it }}", color)
    recap.badge?.let { badge ->
        SmallLine(if (recap.badgeIsNew) "Nouveau badge : ${badge.title} !" else "Ton badge : ${badge.title}", color)
    }
}

private fun sportLine(recap: WeeklyRecap): String {
    val parts = listOfNotNull(
        recap.sessions.takeIf { it > 0 }?.let { "$it ${if (it > 1) "séances" else "séance"}" },
        recap.activities.takeIf { it > 0 }?.let { "$it ${if (it > 1) "activités" else "activité"}" },
    )
    return if (parts.isEmpty()) "Pas de sport cette semaine" else parts.joinToString(" et ")
}

/**
 * Image à partager : seulement le sport, la série, Pousse et le badge. Rien sur les calories, le poids
 * ou la santé (choix des fils Design et Nutrition, 13/11/2026).
 */
private fun writeShareImage(
    context: Context,
    recap: WeeklyRecap,
    theme: AppTheme,
    sparkle: Color,
    background: Color,
    onBackground: Color,
    texts: ThemeTexts,
): File {
    val w = 1080
    val h = 1350
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(background.toArgb())
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onBackground.toArgb(); textSize = 72f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    val body = Paint(title).apply { textSize = 52f; typeface = Typeface.DEFAULT }
    canvas.drawText("Ma semaine", w / 2f, 170f, title)
    canvas.drawText("du ${recap.weekStart.format(dayMonth)} au ${recap.weekEnd.format(dayMonth)}", w / 2f, 250f, body)
    // Pousse au centre, dessinée avec le même code que dans l'app.
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(canvas), Size(w.toFloat(), h.toFloat())) {
        val size = 520f
        scale(size / 120f, pivot = Offset.Zero) {
            translate((w - size) / 2f / (size / 120f), 300f / (size / 120f)) {
                drawPousse(Mood.PROUD, theme, sparkle, growth = recap.growth)
            }
        }
    }
    canvas.drawText(sportLine(recap), w / 2f, 960f, title)
    canvas.drawText(texts.streakDescription(recap.streakWeeks).replaceFirstChar(Char::uppercase), w / 2f, 1050f, body)
    recap.badge?.let { canvas.drawText("Badge : ${it.title}", w / 2f, 1140f, body) }
    canvas.drawText("Healthy", w / 2f, 1280f, Paint(body).apply { textSize = 40f; alpha = 160 })
    val dir = sharedCacheDir(context)
    val file = File(dir, "ma-semaine.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file
}
