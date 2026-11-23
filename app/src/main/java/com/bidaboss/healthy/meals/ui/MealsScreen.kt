package com.bidaboss.healthy.meals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.meals.data.Drink
import com.bidaboss.healthy.meals.data.Meal
import com.bidaboss.healthy.ui.EmptyState
import com.bidaboss.healthy.ui.ThemeTexts
import com.bidaboss.healthy.ui.sharedCacheDir
import com.bidaboss.healthy.ui.shareFile
import com.bidaboss.healthy.ui.WaterFeedback
import com.bidaboss.healthy.ui.color
import com.bidaboss.healthy.ui.formatGrams
import com.bidaboss.healthy.ui.formatKcal
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.mealsBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

@Composable
fun MealsScreen(
    onAddMeal: (LocalDate) -> Unit,
    onEditMeal: (Long) -> Unit,
    onImport: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenScannedProducts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealsViewModel = viewModel(factory = healthyViewModelFactory { MealsViewModel(it.mealRepository, it.maintenanceKcal, it.exportMealReport) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    // Le bilan part en fichier joint par le partage Android (vers l'app Claude par exemple) : collé en
    // texte, il était coupé vers 8 Ko. Aucun réseau ni permission ici.
    val reportToShare by viewModel.reportToShare.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(reportToShare) {
        val report = reportToShare ?: return@LaunchedEffect
        val file = withContext(Dispatchers.IO) {
            File(sharedCacheDir(context), "bilan-repas-${LocalDate.now()}.json").apply { writeText(report) }
        }
        // text/plain : le plus de cibles possibles (Claude, Gmail…), qui reçoivent bien un fichier joint.
        shareFile(context, file, "text/plain", "Envoyer mon bilan repas")
        viewModel.onReportShared()
    }

    // Scan depuis l'onglet : le produit va au repas en cours, avec un message et « Annuler ».
    var scanning by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val lastAdded by viewModel.lastAdded.collectAsStateWithLifecycle()
    LaunchedEffect(lastAdded) {
        val added = lastAdded ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = "Ajouté ${addedToText(added.type)}",
            actionLabel = "Annuler",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoLastAdded() else viewModel.onAddedShown()
    }

    Box(modifier = modifier.fillMaxSize().mealsBackground()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previousDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Jour précédent")
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.isToday) "Aujourd'hui" else state.date.format(dayFormat),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "Total : ${formatKcal(state.totalKcal)}" +
                                state.maintenanceKcal?.let { " sur $it kcal" }.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = viewModel::nextDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Jour suivant")
                    }
                }
                if (state.caloriesOnlyMeals > 0) {
                    // Un repas en calories seules compte dans le total, mais pas dans les protéines ni les fibres.
                    Text(
                        if (state.caloriesOnlyMeals == 1) "1 repas sans détail : ses protéines et ses fibres ne sont pas comptées."
                        else "${state.caloriesOnlyMeals} repas sans détail : leurs protéines et leurs fibres ne sont pas comptées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                state.maintenanceKcal?.let { maintenance ->
                    // Petite barre du total face au maintien : couleur d'écart pour un jour passé, neutre pour
                    // aujourd'hui sauf dépassement net, comme sur Aujourd'hui.
                    val band = when {
                        state.totalKcal <= 0 -> null
                        state.isToday -> Maintenance.bandSoFar(state.totalKcal, maintenance)
                        else -> Maintenance.band(state.totalKcal, maintenance)
                    }
                    LinearProgressIndicator(
                        progress = { (state.totalKcal / maintenance).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(8.dp),
                        color = band?.color() ?: MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {},
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!state.isToday) {
                        TextButton(onClick = viewModel::goToToday) { Text("Revenir à aujourd'hui") }
                    } else {
                        Row {
                            TextButton(onClick = onOpenRecipes) { Text("Mes plats") }
                            TextButton(onClick = onOpenScannedProducts) { Text("Mes produits") }
                        }
                    }
                    // Action secondaire, discrète : le bouton principal reste « Ajouter un repas ».
                    TextButton(onClick = onImport) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Importer depuis ton IA")
                    }
                }
            }
            if (state.isToday) {
                item(key = "scan") {
                    OutlinedButton(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scanner un produit")
                    }
                }
                item(key = "drinks") { DrinkButtons(onAdd = viewModel::addDrink) }
            }
            if (state.groups.isEmpty()) {
                item {
                    EmptyState(ThemeTexts.current.noMeal + " Ajoute un repas avec le bouton en bas.")
                }
            }
            state.groups.forEach { group ->
                item(key = "header-${group.type.code}") { MealTypeHeader(group.type) }
                items(group.meals, key = { it.id }) { meal ->
                    MealCard(meal, onClick = { onEditMeal(meal.id) }, onToggleFavorite = { viewModel.toggleFavorite(meal) })
                }
            }
            item(key = "report") {
                OutlinedButton(onClick = viewModel::exportReport, modifier = Modifier.fillMaxWidth()) {
                    Text("Envoyer mon bilan repas à Claude")
                }
                Text(
                    "Les 7 derniers jours (repas, eau, protéines, fibres), pour que Claude t'aide à ajuster.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 88.dp, end = 16.dp),
        )

        ExtendedFloatingActionButton(
            onClick = { onAddMeal(state.date) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Ajouter un repas") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (scanning) {
        ScanDialog(
            onFoodChosen = { food ->
                viewModel.addScannedFood(food)
                scanning = false
            },
            onDismiss = { scanning = false },
        )
    }
}

/** « au déjeuner », « à la collation » : où est allé l'aliment ajouté. */
internal fun addedToText(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "au petit-déjeuner"
    MealType.LUNCH -> "au déjeuner"
    MealType.DINNER -> "au dîner"
    MealType.SNACK -> "à la collation"
}

@Composable
private fun MealCard(meal: Meal, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = meal.title ?: meal.type.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(formatKcal(meal.totalKcal), style = MaterialTheme.typography.titleMedium)
                // Favori : proposé ensuite dans « Mes favoris » à l'ajout d'un repas.
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (meal.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (meal.isFavorite) "Retirer des favoris" else "Mettre en favori",
                        tint = if (meal.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            meal.foods.forEach { food ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val quantity = food.quantityG?.let { " (${formatGrams(it)})" }.orEmpty()
                        Text("${food.name}$quantity", modifier = Modifier.weight(1f))
                        if (food.needsCheck()) ToCheckBadge()
                        Text(formatKcal(food.kcal))
                    }
                    food.assumptions?.let { AssumptionText(it) }
                }
            }
            Text(
                text = "Protéines ${formatGrams(meal.totalProteinG)} · Glucides ${formatGrams(meal.totalCarbsG)} · Lipides ${formatGrams(meal.totalFatG)}" +
                    " · Fibres ${formatGrams(meal.totalFiberG)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Boissons caloriques en un geste (soda et jus comptent dans l'eau, bière et vin non). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrinkButtons(onAdd: (Drink) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Une boisson ?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Drink.entries.forEach { drink ->
                AssistChip(
                    onClick = {
                        WaterFeedback.tick(context)
                        onAdd(drink)
                    },
                    label = { Text("${drink.label} ${drink.volumeLabel}") },
                    leadingIcon = { Icon(Icons.Rounded.LocalBar, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        Text(
            "Ajoutée au repas en cours, sinon en collation. Le soda et le jus comptent dans l'eau, pas la bière ni le vin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Couleur et icône de chaque type de repas (maquette du fil Design, 12/11/2026). */
private fun MealType.color(): Color = when (this) {
    MealType.BREAKFAST -> Color(0xFFF9A825)
    MealType.LUNCH -> Color(0xFF43A047)
    MealType.DINNER -> Color(0xFF1E88E5)
    MealType.SNACK -> Color(0xFFE53935)
}

private fun MealType.icon(): ImageVector = when (this) {
    MealType.BREAKFAST -> Icons.Rounded.FreeBreakfast
    MealType.LUNCH -> Icons.Rounded.LunchDining
    MealType.DINNER -> Icons.Rounded.DinnerDining
    MealType.SNACK -> Icons.Rounded.Cookie
}

@Composable
private fun MealTypeHeader(type: MealType) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(36.dp).background(type.color(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(type.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(type.label, style = MaterialTheme.typography.titleLarge)
    }
}
