package com.bidaboss.healthy.today.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bidaboss.healthy.HealthyApplication
import com.bidaboss.healthy.MainActivity
import com.bidaboss.healthy.core.body.Hydration
import com.bidaboss.healthy.meals.data.Drink
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.today.data.SessionStatus
import com.bidaboss.healthy.today.data.TodaySnapshot
import com.bidaboss.healthy.ui.WaterFeedback
import com.bidaboss.healthy.ui.colorFor
import com.bidaboss.healthy.ui.formatThousands
import com.bidaboss.healthy.ui.pousseLabel
import com.bidaboss.healthy.ui.theme.colorSchemeOf
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Widget « Aujourd'hui » (fil Design C5, refait après le retour du propriétaire) : Pousse bien visible,
 * anneau des calories, eau, et boissons en un geste sans ouvrir l'app. La mise en page suit la taille
 * posée : une ligne (4×1), carré (2×2), large (4×2) ou grand (4×3 et plus).
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(ROW, SQUARE, MEDIUM, TALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = load(context)
        provideContent {
            val prefs = currentState<Preferences>()
            // Chaque ajout ou retour à l'écran d'accueil augmente la version : on relit la base.
            val data by produceState(initial, prefs[VersionKey]) { value = load(context) }
            val colors = remember(data.theme) {
                ColorProviders(light = colorSchemeOf(data.theme, dark = false), dark = colorSchemeOf(data.theme, dark = true))
            }
            GlanceTheme(colors = colors) { WidgetContent(data, confirmed = prefs[ConfirmKey]) }
        }
    }

    companion object {
        val ROW = DpSize(250.dp, 56.dp)
        val SQUARE = DpSize(120.dp, 120.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val TALL = DpSize(250.dp, 170.dp)
        val LARGE = DpSize(250.dp, 270.dp)

        /** Redessine tous les widgets posés (après un ajout, ou quand on quitte l'app). */
        suspend fun refresh(context: Context, confirm: String? = null) {
            GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    prefs[VersionKey] = (prefs[VersionKey] ?: 0) + 1
                    if (confirm != null) prefs[ConfirmKey] = confirm else prefs.remove(ConfirmKey)
                }
                TodayWidget().update(context, id)
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

internal data class WidgetData(val snapshot: TodaySnapshot, val theme: AppTheme)

private suspend fun load(context: Context): WidgetData {
    val container = (context.applicationContext as HealthyApplication).container
    return WidgetData(container.todaySnapshotRepository.snapshot(LocalDate.now()), container.appearance.theme.value)
}

private val VersionKey = intPreferencesKey("version")

/** Bouton touché à l'instant : son libellé passe à « ✓ … » un court moment. */
private val ConfirmKey = stringPreferencesKey("confirm")

private const val CONFIRM_MS = 1_500L

private fun openApp(context: Context): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun pasteMeal(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_PASTE_MEAL)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

@Composable
private fun WidgetContent(data: WidgetData, confirmed: String?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val colors = GlanceTheme.colors
    val density = context.resources.displayMetrics.density
    val pattern = remember(data.theme, size) {
        // Motif à basse résolution (1 point par dp) : il est flou de toute façon et pèse peu.
        WidgetArt.pattern(data.theme, size.width.value.toInt(), size.height.value.toInt(), 1f)
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(24.dp)
            .clickable(openApp(context)),
    ) {
        if (size.height >= 90.dp) Image(
            provider = ImageProvider(pattern),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(colors.onSurface),
        )
        val layout = when {
            size.height < 90.dp && size.width >= TodayWidget.ROW.width -> Layout.ROW
            size.width < TodayWidget.MEDIUM.width -> Layout.SQUARE
            size.height < TodayWidget.TALL.height -> Layout.MEDIUM
            size.height < TodayWidget.LARGE.height -> Layout.TALL
            else -> Layout.LARGE
        }
        Box(GlanceModifier.fillMaxSize().padding(if (layout == Layout.ROW) 8.dp else 12.dp)) {
            when (layout) {
                Layout.ROW -> RowLayout(data, confirmed)
                Layout.SQUARE -> SquareLayout(data, confirmed)
                Layout.MEDIUM -> MediumLayout(data, confirmed)
                Layout.TALL -> TallLayout(data, confirmed, large = false)
                Layout.LARGE -> TallLayout(data, confirmed, large = true)
            }
        }
    }
}

private enum class Layout { ROW, SQUARE, MEDIUM, TALL, LARGE }

/** 4×1 : Pousse, calories et eau sur une ligne, puis le bouton. */
@Composable
private fun RowLayout(data: WidgetData, confirmed: String?) {
    val s = data.snapshot
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        PousseImage(data, 40.dp)
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight().background(GlanceTheme.colors.surface)) {
            Text("${formatThousands(s.kcal)} kcal", maxLines = 1, style = TextStyle(color = kcalColor(s), fontWeight = FontWeight.Bold, fontSize = 15.sp))
            Text(waterText(s), maxLines = 1, style = secondary(12.sp))
        }
        Spacer(GlanceModifier.width(8.dp))
        WaterButton(Hydration.GLASS_ML, "+ 20 cL", confirmed)
    }
}

/** 2×2 : Pousse au centre, les calories dessous, puis le bouton. */
@Composable
private fun SquareLayout(data: WidgetData, confirmed: String?) {
    val s = data.snapshot
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        PousseImage(data, 52.dp)
        // Pavé uni : le motif ne passe ni sous le chiffre ni contre le bouton.
        Column(
            GlanceModifier.background(GlanceTheme.colors.surface).cornerRadius(16.dp).padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${formatThousands(s.kcal)} kcal",
                maxLines = 1,
                style = TextStyle(color = kcalColor(s), fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center),
            )
            Spacer(GlanceModifier.height(4.dp))
            WaterButton(Hydration.GLASS_ML, "+ 20 cL", confirmed)
        }
    }
}

/** 4×2 peu haut : Pousse et « Aujourd'hui », l'anneau, puis l'eau et son bouton. */
@Composable
private fun MediumLayout(data: WidgetData, confirmed: String?) {
    val s = data.snapshot
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Title()
            PousseImage(data, 60.dp)
        }
        Spacer(GlanceModifier.width(8.dp))
        Box(GlanceModifier.background(GlanceTheme.colors.surface).cornerRadius(24.dp).padding(4.dp)) { CaloriesRing(s, 76.dp) }
        Spacer(GlanceModifier.width(8.dp))
        WaterPanel(s, confirmed, compact = LocalSize.current.width < 300.dp, modifier = GlanceModifier.defaultWeight())
    }
}

/** 4×2 haut et 4×3 : Pousse à côté du titre, puis l'anneau et l'eau ; en grand, tout le reste. */
@Composable
private fun TallLayout(data: WidgetData, confirmed: String?, large: Boolean) {
    val s = data.snapshot
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PousseImage(data, 60.dp)
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.background(GlanceTheme.colors.surface).cornerRadius(8.dp).padding(horizontal = 4.dp)) {
                Title()
                Text(sessionText(s.session), maxLines = 1, style = secondary(13.sp))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.background(GlanceTheme.colors.surface).cornerRadius(24.dp).padding(4.dp)) { CaloriesRing(s, 84.dp) }
            Spacer(GlanceModifier.width(10.dp))
            WaterPanel(s, confirmed, compact = false, modifier = GlanceModifier.defaultWeight())
        }
        if (large) {
            Spacer(GlanceModifier.height(8.dp))
            s.proteinTargetG?.let {
                Text(
                    "Protéines ${s.proteinG} g sur $it g",
                    maxLines = 1,
                    modifier = GlanceModifier.background(GlanceTheme.colors.surface).cornerRadius(8.dp).padding(horizontal = 4.dp),
                    style = secondary(13.sp),
                )
                Spacer(GlanceModifier.height(6.dp))
            }
            Row(GlanceModifier.fillMaxWidth()) {
                WaterButton(Hydration.COFFEE_ML, "+ café", confirmed)
                Gap()
                WaterButton(Hydration.BOTTLE_ML, "+ bouteille", confirmed)
                Gap()
                Chip("Coller un repas", pasteMeal(context), "Ouvrir l'app et coller le repas copié", primary = true, modifier = GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Drink.entries.forEachIndexed { i, drink ->
                    if (i > 0) Gap()
                    DrinkButton(drink, confirmed)
                }
            }
        }
    }
}

@Composable
private fun Title() {
    Text("Aujourd'hui", maxLines = 1, style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp))
}

@Composable
private fun PousseImage(data: WidgetData, size: Dp) {
    val context = LocalContext.current
    val px = (size.value * context.resources.displayMetrics.density).toInt()
    val s = data.snapshot
    val bitmap = remember(data.theme, s.mood, s.growth, px) { WidgetArt.pousse(data.theme, s.mood, s.growth, px) }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = pousseLabel(data.theme, s.mood, s.growth),
        modifier = GlanceModifier.size(size),
    )
}

/** Anneau des calories, même couleur qu'Aujourd'hui ; le chiffre au centre, le maintien dessous. */
@Composable
private fun CaloriesRing(s: TodaySnapshot, size: Dp) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val px = (size.value * density).toInt()
    val stroke = 7f * density
    val fraction = s.maintenanceKcal?.let { s.kcal.toFloat() / it } ?: 0f
    val track = remember(px) { WidgetArt.ringTrack(px, stroke) }
    val progress = remember(px, fraction) { WidgetArt.ringProgress(px, stroke, fraction) }
    val color = ringColor(s)
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(GlanceTheme.colors.surface)
            .cornerRadius(size / 2)
            .semantics {
                contentDescription = "${formatThousands(s.kcal)} kcal" + (s.maintenanceKcal?.let { " sur ${formatThousands(it)}" } ?: "")
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(track), null, GlanceModifier.size(size), colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant))
        Image(ImageProvider(progress), null, GlanceModifier.size(size), colorFilter = ColorFilter.tint(color))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatThousands(s.kcal), maxLines = 1, style = TextStyle(color = kcalColor(s), fontWeight = FontWeight.Bold, fontSize = 17.sp))
            Text(s.maintenanceKcal?.let { "sur ${formatThousands(it)}" } ?: "kcal", maxLines = 1, style = secondary(11.sp))
        }
    }
}

/** L'eau : verre qui se remplit par quarts, quantité, et le bouton du verre. */
@Composable
private fun WaterPanel(s: TodaySnapshot, confirmed: String?, compact: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val w = (20 * density).toInt()
    val h = (26 * density).toInt()
    val level = WidgetArt.glassLevel(s.waterMl.toFloat() / s.waterTargetMl)
    val outline = remember(w, h) { WidgetArt.glassOutline(w, h) }
    val water = remember(w, h, level) { WidgetArt.glassWater(w, h, level) }
    Column(modifier.background(GlanceTheme.colors.surface).cornerRadius(16.dp).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(20.dp, 26.dp)) {
                Image(ImageProvider(water), null, GlanceModifier.size(20.dp, 26.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.tertiary))
                Image(ImageProvider(outline), "Verre rempli à ${level * 25} %", GlanceModifier.size(20.dp, 26.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface))
            }
            Spacer(GlanceModifier.width(6.dp))
            Column {
                Text(Hydration.formatLiters(s.waterMl), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                Text("sur ${Hydration.formatLiters(s.waterTargetMl)}", maxLines = 1, style = secondary(12.sp))
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        WaterButton(Hydration.GLASS_ML, if (compact) "+ 20 cL" else "+ verre 20 cL", confirmed, GlanceModifier.fillMaxWidth())
    }
}

@Composable
private fun Gap() = Spacer(GlanceModifier.width(6.dp))

@Composable
private fun WaterButton(ml: Int, label: String, confirmed: String?, modifier: GlanceModifier = GlanceModifier) {
    val key = "water:$ml"
    Chip(
        if (confirmed == key) "✓ +${Hydration.formatLiters(ml)}" else label,
        actionRunCallback<AddWaterAction>(actionParametersOf(MlKey to ml)),
        "Ajouter ${Hydration.quickLabel(ml)?.lowercase() ?: "de l'eau"} ${Hydration.formatLiters(ml)}",
        modifier = modifier,
    )
}

@Composable
private fun DrinkButton(drink: Drink, confirmed: String?) {
    val short = drink.label.substringBefore(' ')
    Chip(
        if (confirmed == "drink:${drink.name}") "✓ $short" else short,
        actionRunCallback<AddDrinkAction>(actionParametersOf(DrinkKey to drink.name)),
        "Ajouter ${drink.label.lowercase()} ${drink.volumeLabel}",
    )
}

@Composable
private fun Chip(label: String, action: Action, description: String, primary: Boolean = false, modifier: GlanceModifier = GlanceModifier) {
    val colors = GlanceTheme.colors
    val background: ColorProvider = if (primary) colors.primary else colors.secondaryContainer
    val content: ColorProvider = if (primary) colors.onPrimary else colors.onSecondaryContainer
    Box(
        modifier = modifier
            .background(background)
            .cornerRadius(24.dp) // pilule, comme les boutons de l'app
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = description }
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, maxLines = 1, style = TextStyle(color = content, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center))
    }
}

/** Texte secondaire : couleur du texte principal (contraste 4,5:1 garanti), juste plus petit. */
@Composable
private fun secondary(size: TextUnit) = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = size)

/** Neutre tant que le jour n'est pas fini, couleur de la bande en cas de dépassement net. */
@Composable
private fun kcalColor(s: TodaySnapshot): ColorProvider =
    s.band?.let { ColorProvider(day = it.colorFor(dark = false), night = it.colorFor(dark = true)) } ?: GlanceTheme.colors.onSurface

@Composable
private fun ringColor(s: TodaySnapshot): ColorProvider =
    s.band?.let { ColorProvider(day = it.colorFor(dark = false), night = it.colorFor(dark = true)) } ?: GlanceTheme.colors.primary

private fun waterText(s: TodaySnapshot) = "Eau ${Hydration.formatLiters(s.waterMl)} sur ${Hydration.formatLiters(s.waterTargetMl)}"

private fun sessionText(session: SessionStatus) = when (session) {
    SessionStatus.Done -> "Sport du jour fait ✓"
    is SessionStatus.Planned -> "Séance : ${session.title}"
    SessionStatus.Rest -> "Jour de repos"
}

private val MlKey = ActionParameters.Key<Int>("ml")
private val DrinkKey = ActionParameters.Key<String>("drink")

/** Eau ajoutée depuis le widget, sans ouvrir l'app ; le bouton affiche « ✓ » un court moment. */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ml = parameters[MlKey] ?: return
        val container = (context.applicationContext as HealthyApplication).container
        container.bodyRepository.addWater(LocalDate.now(), ml)
        confirm(context, "water:$ml")
    }
}

/** Boisson (bière, vin, soda, jus) ajoutée au repas en cours, ou en collation, sans ouvrir l'app. */
class AddDrinkAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val drink = parameters[DrinkKey]?.let { name -> Drink.entries.firstOrNull { it.name == name } } ?: return
        val container = (context.applicationContext as HealthyApplication).container
        container.mealRepository.addDrink(drink, LocalDateTime.now())
        confirm(context, "drink:${drink.name}")
    }
}

private suspend fun confirm(context: Context, key: String) {
    WaterFeedback.tick(context)
    TodayWidget.refresh(context, confirm = key)
    delay(CONFIRM_MS)
    TodayWidget.refresh(context)
}
