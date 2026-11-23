package com.bidaboss.healthy.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bidaboss.healthy.body.ui.MeScreen
import com.bidaboss.healthy.body.ui.ProfileScreen
import com.bidaboss.healthy.body.ui.WeightScreen
import com.bidaboss.healthy.importexport.ui.ImportMealCard
import com.bidaboss.healthy.importexport.ui.ImportScreen
import com.bidaboss.healthy.importexport.ui.pasteFromClipboard
import com.bidaboss.healthy.meals.ui.SavedFoodEditorScreen
import com.bidaboss.healthy.meals.ui.SavedFoodsScreen
import androidx.compose.ui.platform.LocalContext
import com.bidaboss.healthy.importexport.ui.ImportViewModel
import com.bidaboss.healthy.meals.ui.MealEditorScreen
import com.bidaboss.healthy.meals.ui.MealsScreen
import com.bidaboss.healthy.meals.ui.RecipeEditorScreen
import com.bidaboss.healthy.meals.ui.RecipesScreen
import com.bidaboss.healthy.meals.ui.ScannedProductsScreen
import com.bidaboss.healthy.settings.ui.SettingsDetailScreen
import com.bidaboss.healthy.settings.ui.SettingsScreen
import com.bidaboss.healthy.settings.ui.SettingsSection
import com.bidaboss.healthy.health.ui.HealthScreen
import com.bidaboss.healthy.health.ui.HealthSection
import com.bidaboss.healthy.today.ui.BadgesScreen
import com.bidaboss.healthy.today.ui.TodayScreen
import com.bidaboss.healthy.today.ui.WaterScreen
import com.bidaboss.healthy.today.ui.WeeklyRecapScreen
import com.bidaboss.healthy.update.ui.UpdateBanner
import com.bidaboss.healthy.update.ui.UpdateDialog
import com.bidaboss.healthy.update.ui.UpdateViewModel
import com.bidaboss.healthy.workout.ui.AddActivityScreen
import com.bidaboss.healthy.workout.ui.RouteEditorScreen
import com.bidaboss.healthy.workout.ui.RoutesScreen
import com.bidaboss.healthy.workout.ui.SessionRunScreen
import com.bidaboss.healthy.workout.ui.SportProfileScreen
import com.bidaboss.healthy.workout.ui.WorkoutScreen
import java.time.LocalDate

/*
 * Les 5 onglets de la refonte (23/11/2026) : Aujourd'hui, Repas, Sport, Santé, Moi. Chaque fonction a une seule
 * maison ; ses écrans secondaires s'ouvrent depuis son onglet. Réglages n'est plus un onglet : il s'ouvre par
 * l'icône à curseurs de Moi.
 *
 * Fichier partagé entre les lots de la refonte : chaque lot n'écrit que dans son emplacement (repéré par
 * « LOT n »), à trois endroits : ses routes (Routes), son rattachement à un onglet (tabRouteOf) et ses écrans
 * (NavHost). Voir docs/decisions.md.
 */
internal object Routes {
    // ── Onglets ──
    const val TODAY = "today"
    const val MEALS = "meals"
    const val WORKOUT = "workout"
    const val HEALTH = "health"
    const val ME = "me"

    // ── LOT 0 : Moi, Réglages et import ──
    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings_section"
    const val ARG_SECTION = "section"

    fun settingsSection(section: SettingsSection) = "$SETTINGS_SECTION/${section.route}"

    const val IMPORT = "import"
    const val PROFILE = "profile"
    const val WEIGHT = "weight"
    const val BADGES = "badges"
    const val SPORT_PROFILE = "sport_profile"

    // ── LOT 1 : Aujourd'hui ──
    const val WATER = "water"
    const val WEEKLY_RECAP = "weekly_recap"

    // ── LOT 2 : Repas ──
    const val MEAL_EDITOR = "meal_editor"
    const val ARG_MEAL_ID = "mealId"
    const val ARG_DATE = "date"

    /** [mealId] nul = nouveau repas pour le jour [date]. */
    fun mealEditor(mealId: Long?, date: LocalDate) = "$MEAL_EDITOR/${mealId ?: NO_MEAL}/$date"

    const val NO_MEAL = -1L

    const val RECIPES = "recipes"
    const val SAVED_FOODS = "saved_foods"
    const val SAVED_FOOD_EDITOR = "saved_food_editor"
    const val ARG_FOOD_ID = "foodId"

    /** [foodId] nul = nouvel aliment. */
    fun savedFoodEditor(foodId: Long?) = "$SAVED_FOOD_EDITOR/${foodId ?: NO_MEAL}"
    const val SCANNED_PRODUCTS = "scanned_products"
    const val RECIPE_EDITOR = "recipe_editor"
    const val ARG_RECIPE_ID = "recipeId"

    /** [recipeId] nul = nouveau plat. */
    fun recipeEditor(recipeId: Long?) = "$RECIPE_EDITOR/${recipeId ?: NO_MEAL}"

    // ── LOT 3 : Sport et trajets ──
    const val SESSION_RUN = "session_run"
    const val ARG_SESSION_ID = "sessionId"
    const val ARG_DURATION = "durationMin"

    fun sessionRun(sessionId: Long, durationMin: Int) = "$SESSION_RUN/$sessionId/$durationMin"

    const val ADD_ACTIVITY = "add_activity"
    const val ROUTES = "routes"
    const val ROUTE_EDITOR = "route_editor"
    const val ARG_ROUTE_ID = "routeId"

    /** [routeId] nul = nouveau trajet. */
    fun routeEditor(routeId: Long?) = "$ROUTE_EDITOR/${routeId ?: NO_MEAL}"

    // ── LOT 4 : Santé et Moi ──
    const val WATCH = "watch"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Accueil", Icons.Rounded.Home),
    Tab(Routes.MEALS, "Repas", Icons.Rounded.Restaurant),
    Tab(Routes.WORKOUT, "Sport", Icons.Rounded.FitnessCenter),
    Tab(Routes.HEALTH, "Santé", Icons.Rounded.MonitorHeart),
    Tab(Routes.ME, "Moi", Icons.Rounded.Person),
)

/** L'onglet (sa route) auquel appartient un écran : il reste allumé dans la barre pendant qu'on y est. */
private fun tabRouteOf(route: String?): String? = when {
    route == null -> null
    // LOT 1 : Aujourd'hui
    route == Routes.TODAY || route == Routes.WATER || route == Routes.WEEKLY_RECAP -> Routes.TODAY
    // LOT 2 : Repas
    route == Routes.MEALS || route == Routes.SCANNED_PRODUCTS || route == Routes.RECIPES || route == Routes.SAVED_FOODS ||
        route.startsWith(Routes.SAVED_FOOD_EDITOR) ||
        route.startsWith(Routes.MEAL_EDITOR) || route.startsWith(Routes.RECIPE_EDITOR) -> Routes.MEALS
    // LOT 3 : Sport et trajets
    route == Routes.WORKOUT || route.startsWith(Routes.SESSION_RUN) || route == Routes.ADD_ACTIVITY ||
        route == Routes.ROUTES || route.startsWith(Routes.ROUTE_EDITOR) -> Routes.WORKOUT
    // LOT 4 : Santé et Moi
    route == Routes.HEALTH || route == Routes.WATCH -> Routes.HEALTH
    // LOT 0 : Moi, Réglages et import
    route == Routes.ME || route == Routes.SETTINGS || route.startsWith(Routes.SETTINGS_SECTION) ||
        route == Routes.IMPORT || route == Routes.PROFILE || route == Routes.WEIGHT || route == Routes.BADGES ||
        route == Routes.SPORT_PROFILE -> Routes.ME
    else -> null
}

/**
 * @param importRequests augmente à chaque fichier ou texte reçu d'une autre app :
 * l'écran Importer s'ouvre alors automatiquement sur l'aperçu.
 */
@Composable
fun HealthyApp(
    importViewModel: ImportViewModel,
    importRequests: Int,
    onPickFile: () -> Unit,
) {
    // « J'ai une sauvegarde » sur l'accueil : ouvre l'écran Importer comme un fichier reçu.
    var restoreRequests by rememberSaveable { mutableIntStateOf(0) }
    OnboardingGate(onRestoreBackup = { restoreRequests++ }) {
        MainNavigation(importViewModel, importRequests + restoreRequests, onPickFile)
    }
}

@Composable
private fun MainNavigation(
    importViewModel: ImportViewModel,
    importRequests: Int,
    onPickFile: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(importRequests) {
        if (importRequests > 0) navController.navigate(Routes.IMPORT) { launchSingleTop = true }
    }

    // Pendant une séance guidée, la barre d'onglets disparaît : écran plein.
    val inSession = currentRoute?.startsWith(Routes.SESSION_RUN) == true

    // Mise à jour depuis le dossier Drive : vérifiée à chaque retour dans l'app (pas seulement au
    // premier lancement : Android garde souvent l'app ouverte en arrière-plan), jamais pendant une séance.
    val updateViewModel: UpdateViewModel = viewModel(
        factory = healthyViewModelFactory { UpdateViewModel(it.updateRepository, it.updateStore) },
    )
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        updateViewModel.checkAutomatically()
        onStopOrDispose { }
    }
    if (!inSession) UpdateDialog(updateState, updateViewModel)

    Scaffold(
        bottomBar = {
            if (!inSession && currentRoute != Routes.WEEKLY_RECAP) NavigationBar {
                val currentTab = tabRouteOf(currentRoute)
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab.route,
                        onClick = { navController.navigateToTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
        val bannerVersion by updateViewModel.banner.collectAsStateWithLifecycle()
        if (!inSession) bannerVersion?.let { UpdateBanner(it, onClick = updateViewModel::checkNow) }
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.weight(1f),
            // Transitions douces entre onglets et écrans : fondu et petit glissement (retouches du 23/11/2026).
            enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 40 } },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 40 } },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onStartSession = { sessionId, durationMin ->
                        navController.navigate(Routes.sessionRun(sessionId, durationMin))
                    },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                    onOpenWater = { navController.navigate(Routes.WATER) { launchSingleTop = true } },
                    onOpenBadges = { navController.navigate(Routes.BADGES) { launchSingleTop = true } },
                    onOpenWeeklyRecap = { navController.navigate(Routes.WEEKLY_RECAP) { launchSingleTop = true } },
                    onOpenWatch = { navController.navigate(Routes.WATCH) { launchSingleTop = true } },
                )
            }
            // ── LOT 4 : Santé et Moi ──
            composable(Routes.HEALTH) {
                HealthScreen(
                    onOpenWater = { navController.navigate(Routes.WATER) { launchSingleTop = true } },
                    onOpenRecap = { navController.navigate(Routes.WEEKLY_RECAP) { launchSingleTop = true } },
                )
            }
            // « Ma montre » d'Aujourd'hui et de Sport ouvre Santé sur la partie Montre.
            composable(Routes.WATCH) {
                HealthScreen(
                    onOpenWater = { navController.navigate(Routes.WATER) { launchSingleTop = true } },
                    onOpenRecap = { navController.navigate(Routes.WEEKLY_RECAP) { launchSingleTop = true } },
                    initialSection = HealthSection.WATCH,
                )
            }
            // ── LOT 1 : Aujourd'hui ──
            composable(Routes.WATER) { WaterScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.BADGES) { BadgesScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.WEEKLY_RECAP) { WeeklyRecapScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.MEALS) {
                val context = LocalContext.current
                MealsScreen(
                    onAddMeal = { date -> navController.navigate(Routes.mealEditor(null, date)) },
                    onEditMeal = { mealId -> navController.navigate(Routes.mealEditor(mealId, LocalDate.now())) },
                    onOpenRecipes = { navController.navigate(Routes.RECIPES) { launchSingleTop = true } },
                    onOpenSavedFoods = { navController.navigate(Routes.SAVED_FOODS) { launchSingleTop = true } },
                    onCreateFood = { navController.navigate(Routes.savedFoodEditor(null)) },
                    onImportFromAi = { pasteFromClipboard(context, importViewModel) },
                    importCard = { idleExtra ->
                        ImportMealCard(
                            viewModel = importViewModel,
                            onPickFile = onPickFile,
                            onOpenImport = { navController.navigate(Routes.IMPORT) { launchSingleTop = true } },
                            idleExtra = idleExtra,
                        )
                    },
                )
            }
            composable(Routes.SAVED_FOODS) {
                SavedFoodsScreen(
                    onOpenFood = { id -> navController.navigate(Routes.savedFoodEditor(id)) },
                    onOpenScannedProducts = { navController.navigate(Routes.SCANNED_PRODUCTS) { launchSingleTop = true } },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(
                route = "${Routes.SAVED_FOOD_EDITOR}/{${Routes.ARG_FOOD_ID}}",
                arguments = listOf(navArgument(Routes.ARG_FOOD_ID) { type = NavType.LongType }),
            ) { entry ->
                val foodId = entry.arguments?.getLong(Routes.ARG_FOOD_ID) ?: Routes.NO_MEAL
                SavedFoodEditorScreen(foodId = foodId.takeIf { it != Routes.NO_MEAL }, onClose = { navController.popBackStack() })
            }
            composable(Routes.SCANNED_PRODUCTS) { ScannedProductsScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.RECIPES) {
                RecipesScreen(
                    onOpenRecipe = { id -> navController.navigate(Routes.recipeEditor(id)) },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(
                route = "${Routes.RECIPE_EDITOR}/{${Routes.ARG_RECIPE_ID}}",
                arguments = listOf(navArgument(Routes.ARG_RECIPE_ID) { type = NavType.LongType }),
            ) { entry ->
                val recipeId = entry.arguments?.getLong(Routes.ARG_RECIPE_ID) ?: Routes.NO_MEAL
                RecipeEditorScreen(
                    recipeId = recipeId.takeIf { it != Routes.NO_MEAL },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(
                route = "${Routes.MEAL_EDITOR}/{${Routes.ARG_MEAL_ID}}/{${Routes.ARG_DATE}}",
                arguments = listOf(
                    navArgument(Routes.ARG_MEAL_ID) { type = NavType.LongType },
                    navArgument(Routes.ARG_DATE) { type = NavType.StringType },
                ),
            ) { entry ->
                val mealId = entry.arguments?.getLong(Routes.ARG_MEAL_ID) ?: Routes.NO_MEAL
                val date = entry.arguments?.getString(Routes.ARG_DATE)
                MealEditorScreen(
                    mealId = mealId.takeIf { it != Routes.NO_MEAL },
                    initialDate = date?.let(LocalDate::parse) ?: LocalDate.now(),
                    onClose = { navController.popBackStack() },
                    onOpenRecipes = { navController.navigate(Routes.RECIPES) { launchSingleTop = true } },
                    onOpenSavedFoods = { navController.navigate(Routes.SAVED_FOODS) { launchSingleTop = true } },
                )
            }
            composable(Routes.WORKOUT) {
                WorkoutScreen(
                    onStartSession = { sessionId, durationMin ->
                        navController.navigate(Routes.sessionRun(sessionId, durationMin))
                    },
                    onImport = { navController.navigate(Routes.IMPORT) { launchSingleTop = true } },
                    onOpenWatch = { navController.navigate(Routes.WATCH) { launchSingleTop = true } },
                    onAddActivity = { navController.navigate(Routes.ADD_ACTIVITY) { launchSingleTop = true } },
                    onOpenRoutes = { navController.navigate(Routes.ROUTES) { launchSingleTop = true } },
                )
            }
            composable(Routes.ADD_ACTIVITY) {
                AddActivityScreen(
                    onClose = { navController.popBackStack() },
                    onOpenRoutes = { navController.navigate(Routes.ROUTES) { launchSingleTop = true } },
                    onEditRoute = { navController.navigate(Routes.routeEditor(it)) { launchSingleTop = true } },
                )
            }
            composable(Routes.ROUTES) {
                RoutesScreen(onEdit = { navController.navigate(Routes.routeEditor(it)) { launchSingleTop = true } })
            }
            composable(
                route = "${Routes.ROUTE_EDITOR}/{${Routes.ARG_ROUTE_ID}}",
                arguments = listOf(navArgument(Routes.ARG_ROUTE_ID) { type = NavType.LongType }),
            ) { entry ->
                RouteEditorScreen(
                    routeId = entry.arguments?.getLong(Routes.ARG_ROUTE_ID)?.takeIf { it != Routes.NO_MEAL },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(
                route = "${Routes.SESSION_RUN}/{${Routes.ARG_SESSION_ID}}/{${Routes.ARG_DURATION}}",
                arguments = listOf(
                    navArgument(Routes.ARG_SESSION_ID) { type = NavType.LongType },
                    navArgument(Routes.ARG_DURATION) { type = NavType.IntType },
                ),
            ) { entry ->
                SessionRunScreen(
                    sessionId = entry.arguments?.getLong(Routes.ARG_SESSION_ID) ?: -1L,
                    durationMin = entry.arguments?.getInt(Routes.ARG_DURATION) ?: 0,
                    onClose = { navController.popBackStack() },
                )
            }
            // ── LOT 0 : Moi, Réglages et import ──
            composable(Routes.ME) {
                MeScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                    onOpenWeight = { navController.navigate(Routes.WEIGHT) { launchSingleTop = true } },
                    onOpenBadges = { navController.navigate(Routes.BADGES) { launchSingleTop = true } },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenSection = { navController.navigate(Routes.settingsSection(it)) { launchSingleTop = true } },
                    onOpenImport = { navController.navigate(Routes.IMPORT) { launchSingleTop = true } },
                    onOpenSportProfile = { navController.navigate(Routes.SPORT_PROFILE) { launchSingleTop = true } },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(
                route = "${Routes.SETTINGS_SECTION}/{${Routes.ARG_SECTION}}",
                arguments = listOf(navArgument(Routes.ARG_SECTION) { type = NavType.StringType }),
            ) { entry ->
                val section = SettingsSection.ofRoute(entry.arguments?.getString(Routes.ARG_SECTION))
                if (section == null) {
                    // Section inconnue (ne devrait pas arriver) : retour à Réglages.
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    SettingsDetailScreen(
                        section = section,
                        onClose = { navController.popBackStack() },
                        onOpenSportProfile = { navController.navigate(Routes.SPORT_PROFILE) { launchSingleTop = true } },
                        onCheckUpdates = updateViewModel::checkNow,
                    )
                }
            }
            composable(Routes.PROFILE) { ProfileScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.WEIGHT) { WeightScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.SPORT_PROFILE) { SportProfileScreen(onDone = { navController.popBackStack() }) }
            composable(Routes.IMPORT) {
                ImportScreen(
                    viewModel = importViewModel,
                    onPickFile = onPickFile,
                    // Retour là d'où l'on vient (Repas, Sport ou Réglages) ; sinon Aujourd'hui.
                    onDone = { if (!navController.popBackStack()) navController.navigateToTab(Routes.TODAY) },
                )
            }
        }
        }
    }
}

internal fun NavHostController.navigateToTab(route: String) {
    // Accueil est le départ du graphe : on y revient en dépilant. Avec popUpTo, l'appui ne faisait rien quand on
    // venait d'un écran ouvert depuis Accueil (Ma montre, Eau...), qui lui-même allume un autre onglet.
    if (route == graph.findStartDestination().route) {
        popBackStack(route, inclusive = false)
        if (currentDestination?.route != route) navigate(route) { launchSingleTop = true }
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
