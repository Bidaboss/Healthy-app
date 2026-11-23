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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
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
import android.content.Context
import com.bidaboss.healthy.meals.data.Drink
import com.bidaboss.healthy.meals.data.Food
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
import com.bidaboss.healthy.ui.photoBackdrop
import com.bidaboss.healthy.ui.PhotoTab
import com.bidaboss.healthy.ui.PhotoTitle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsScreen(
    onAddMeal: (LocalDate) -> Unit,
    onEditMeal: (Long) -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenSavedFoods: () -> Unit,
    onCreateFood: () -> Unit,
    /** Colle le presse-papiers dans l'import : l'aperçu apparaît dans la carte en tête de liste. */
    onImportFromAi: () -> Unit,
    /** La carte « Importer un repas de mon IA » (fournie par l'écran qui sait importer : Repas ne dépend pas d'importexport). */
    importCard: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealsViewModel = viewModel(
        factory = healthyViewModelFactory {
            MealsViewModel(it.mealRepository, it.maintenanceKcal, it.exportMealReport, it.savedFoodRepository)
        },
    ),
    savedFoodsViewModel: SavedFoodsViewModel = viewModel(factory = healthyViewModelFactory { SavedFoodsViewModel(it.savedFoodRepository) }),
    recipesViewModel: RecipesViewModel = viewModel(factory = healthyViewModelFactory { RecipesViewModel(it.recipeRepository) }),
    habitViewModel: HabitViewModel = viewModel(
        factory = healthyViewModelFactory { HabitViewModel(it.mealRepository, it.habitDismissStore) },
    ),
) {
    val habit by habitViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedKeys by viewModel.savedKeys.collectAsStateWithLifecycle()
    val savedFoods by savedFoodsViewModel.foods.collectAsStateWithLifecycle()
    val recipes by recipesViewModel.recipes.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        habitViewModel.onResume()
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

    // « Partager ce repas » : un fichier au format d'import, à la portion de l'autre personne.
    val mealToShare by viewModel.mealToShare.collectAsStateWithLifecycle()
    LaunchedEffect(mealToShare) {
        val json = mealToShare ?: return@LaunchedEffect
        shareMealFile(context, json)
        viewModel.onMealShared()
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var quickEntry by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var pickingSaved by remember { mutableStateOf(false) }
    var pickingRecipe by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<Meal?>(null) }

    // Un aliment ajouté en un geste va au repas en cours, avec un message et « Annuler ».
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

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Photo derrière toute la liste, comme sur les autres onglets : elle suit le défilement de la liste.
    Box(
        modifier = modifier.fillMaxSize().photoBackdrop(PhotoTab.MEALS) {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else Int.MAX_VALUE
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Box {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                        PhotoTitle("Repas", modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = viewModel::previousDay) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Jour précédent", tint = Color.White)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                PhotoTitle(
                                    text = if (state.isToday) "Aujourd'hui" else state.date.format(dayFormat),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                PhotoTitle(
                                    text = "Total : ${formatKcal(state.totalKcal)}" +
                                        state.maintenanceKcal?.let { " sur $it kcal" }.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            IconButton(onClick = viewModel::nextDay) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Jour suivant", tint = Color.White)
                            }
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
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(8.dp),
                                color = band?.color() ?: Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f),
                                drawStopIndicator = {},
                            )
                        }
                        if (!state.isToday) {
                            TextButton(onClick = viewModel::goToToday) { Text("Revenir à aujourd'hui", color = Color.White) }
                        }
                    }
                }
            }
            item(key = "import") {
                Box(Modifier.padding(horizontal = 16.dp)) { importCard() }
            }
            // « Ton petit-déjeuner habituel ? » (carte qui était sur Aujourd'hui avant la refonte) : seulement le jour même.
            if (state.isToday && habit.suggestion != null) {
                item(key = "habit") {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        HabitCard(habit.suggestion, onAdd = habitViewModel::add, onDismiss = habitViewModel::dismiss)
                    }
                }
            }
            if (state.caloriesOnlyMeals > 0) {
                item(key = "calories-only") {
                    // Un repas en calories seules compte dans le total, mais pas dans les protéines ni les fibres.
                    Text(
                        if (state.caloriesOnlyMeals == 1) "1 repas sans détail : ses protéines et ses fibres ne sont pas comptées."
                        else "${state.caloriesOnlyMeals} repas sans détail : leurs protéines et leurs fibres ne sont pas comptées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    )
                }
            }
            if (state.isToday) {
                item(key = "drinks") { Box(Modifier.padding(horizontal = 16.dp)) { DrinkButtons(onAdd = viewModel::addDrink) } }
            }
            if (state.groups.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        EmptyState(ThemeTexts.current.noMeal + " Ajoute un repas avec le bouton en bas.")
                    }
                }
            }
            state.groups.forEach { group ->
                item(key = "header-${group.type.code}") {
                    Box(Modifier.padding(horizontal = 16.dp)) { MealTypeHeader(group.type) }
                }
                items(group.meals, key = { it.id }) { meal ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        MealCard(
                            meal = meal,
                            savedKeys = savedKeys,
                            onClick = { onEditMeal(meal.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(meal) },
                            onToggleSaved = viewModel::toggleSaved,
                            onShare = { sharing = meal },
                        )
                    }
                }
            }
            item(key = "report") {
                Column(Modifier.padding(horizontal = 16.dp)) {
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
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 88.dp, end = 16.dp),
        )

        ExtendedFloatingActionButton(
            onClick = { showAddSheet = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Ajouter") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (showAddSheet) {
        // Les ajouts en un geste vont au repas en cours, donc à aujourd'hui : un autre jour ne propose que ce qui a une date.
        val actions = if (state.isToday) AddAction.entries
        else listOf(AddAction.NEW_MEAL, AddAction.IMPORT_FROM_AI, AddAction.CREATE_FOOD)
        AddSheet(
            actions = actions,
            onDismiss = { showAddSheet = false },
            onChoose = { action ->
                showAddSheet = false
                when (action) {
                    AddAction.IMPORT_FROM_AI -> {
                        onImportFromAi()
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                    AddAction.SCAN -> scanning = true
                    AddAction.SEARCH -> searching = true
                    AddAction.QUICK_ENTRY -> quickEntry = true
                    AddAction.HISTORY -> history = true
                    AddAction.SAVED_FOODS -> pickingSaved = true
                    AddAction.CREATE_FOOD -> onCreateFood()
                    AddAction.RECIPES -> pickingRecipe = true
                    AddAction.NEW_MEAL -> onAddMeal(state.date)
                }
            },
        )
    }
    if (scanning) {
        ScanDialog(
            onFoodChosen = { food ->
                viewModel.addFood(food)
                scanning = false
            },
            onDismiss = { scanning = false },
        )
    }
    if (searching) {
        FoodSearchDialog(
            onFoodChosen = { food ->
                viewModel.addFood(food)
                searching = false
            },
            onDismiss = { searching = false },
        )
    }
    if (quickEntry) {
        CaloriesOnlyDialog(
            onConfirm = { name, kcal ->
                val error = viewModel.addCaloriesOnly(name, kcal)
                if (error == null) quickEntry = false
                error
            },
            onDismiss = { quickEntry = false },
        )
    }
    if (history) {
        FoodHistoryDialog(
            onFoodChosen = { food ->
                viewModel.addFood(food)
                history = false
            },
            onDismiss = { history = false },
        )
    }
    if (pickingSaved) {
        SavedFoodPickerDialog(
            foods = savedFoods.orEmpty(),
            onPicked = { food ->
                viewModel.addFood(food)
                pickingSaved = false
            },
            onManage = {
                pickingSaved = false
                onOpenSavedFoods()
            },
            onDismiss = { pickingSaved = false },
        )
    }
    if (pickingRecipe) {
        RecipePickerDialog(
            recipes = recipes.orEmpty(),
            onPicked = { food ->
                viewModel.addFood(food)
                pickingRecipe = false
            },
            onManage = {
                pickingRecipe = false
                onOpenRecipes()
            },
            onDismiss = { pickingRecipe = false },
        )
    }
    sharing?.let { meal ->
        ShareMealDialog(
            onShare = { portion ->
                viewModel.shareMeal(meal, portion)
                sharing = null
            },
            onDismiss = { sharing = null },
        )
    }
}

/** Écrit le repas à partager en fichier d'import et ouvre le partage Android (l'autre l'ouvre dans son app Healthy). */
internal suspend fun shareMealFile(context: Context, json: String) {
    val file = withContext(Dispatchers.IO) {
        File(sharedCacheDir(context), "repas-healthy-${LocalDate.now()}.json").apply { writeText(json) }
    }
    shareFile(context, file, "text/plain", "Partager ce repas")
}

/** « au déjeuner », « à la collation » : où est allé l'aliment ajouté. */
internal fun addedToText(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "au petit-déjeuner"
    MealType.LUNCH -> "au déjeuner"
    MealType.DINNER -> "au dîner"
    MealType.SNACK -> "à la collation"
}

@Composable
private fun MealCard(
    meal: Meal,
    savedKeys: Set<String>,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleSaved: (Food) -> Unit,
    onShare: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // Le type de repas est déjà sur la pastille au-dessus : sans titre, la carte donne l'heure.
                    text = meal.title ?: meal.eatenAt.format(timeFormat),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(formatKcal(meal.totalKcal), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Share, contentDescription = "Partager ce repas", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                        SavedFoodBookmark(saved = savedFoodKeyOf(food.name) in savedKeys, onClick = { onToggleSaved(food) })
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

/** Marque-page d'un aliment : plein s'il est dans « Mes aliments », un appui l'y ajoute ou l'en retire. */
@Composable
internal fun SavedFoodBookmark(saved: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            contentDescription = if (saved) "Retirer de mes aliments" else "Ajouter à mes aliments",
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun savedFoodKeyOf(name: String): String = savedKeyOf(name)

/** Boissons caloriques en un geste (soda et jus comptent dans l'eau, bière et vin non). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrinkButtons(onAdd: (Drink) -> Unit) {
    val context = LocalContext.current
    // Dans une carte : posé directement sur la photo, le texte devenait illisible (retouches du 23/11/2026).
    Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    // Sur une pastille de la couleur des cartes : le titre est lisible sur la photo comme sur le fond uni.
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(24.dp))
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(type.color(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(type.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(type.label, style = MaterialTheme.typography.titleLarge)
    }
}
