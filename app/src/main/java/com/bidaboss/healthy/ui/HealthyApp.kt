package com.bidaboss.healthy.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
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
import com.bidaboss.healthy.body.ui.ProfileScreen
import com.bidaboss.healthy.body.ui.WeightScreen
import com.bidaboss.healthy.importexport.ui.ImportScreen
import com.bidaboss.healthy.importexport.ui.ImportViewModel
import com.bidaboss.healthy.meals.ui.MealEditorScreen
import com.bidaboss.healthy.meals.ui.MealsScreen
import com.bidaboss.healthy.meals.ui.RecipeEditorScreen
import com.bidaboss.healthy.meals.ui.RecipesScreen
import com.bidaboss.healthy.meals.ui.ScannedProductsScreen
import com.bidaboss.healthy.settings.ui.SettingsScreen
import com.bidaboss.healthy.health.ui.WatchTrendsScreen
import com.bidaboss.healthy.today.ui.BadgesScreen
import com.bidaboss.healthy.today.ui.TodayScreen
import com.bidaboss.healthy.today.ui.WaterScreen
import com.bidaboss.healthy.today.ui.WeeklyRecapScreen
import com.bidaboss.healthy.update.ui.UpdateDialog
import com.bidaboss.healthy.update.ui.UpdateViewModel
import com.bidaboss.healthy.workout.ui.SessionRunScreen
import com.bidaboss.healthy.workout.ui.SportProfileScreen
import com.bidaboss.healthy.workout.ui.WorkoutScreen
import java.time.LocalDate

private object Routes {
    const val TODAY = "today"
    const val MEALS = "meals"
    const val WORKOUT = "workout"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val PROFILE = "profile"
    const val WEIGHT = "weight"
    const val WATER = "water"
    const val BADGES = "badges"
    const val WEEKLY_RECAP = "weekly_recap"
    const val WATCH = "watch"
    const val SPORT_PROFILE = "sport_profile"
    const val MEAL_EDITOR = "meal_editor"
    const val ARG_MEAL_ID = "mealId"
    const val ARG_DATE = "date"

    /** [mealId] nul = nouveau repas pour le jour [date]. */
    fun mealEditor(mealId: Long?, date: LocalDate) = "$MEAL_EDITOR/${mealId ?: NO_MEAL}/$date"

    const val NO_MEAL = -1L

    const val RECIPES = "recipes"
    const val SCANNED_PRODUCTS = "scanned_products"
    const val RECIPE_EDITOR = "recipe_editor"
    const val ARG_RECIPE_ID = "recipeId"

    /** [recipeId] nul = nouveau plat. */
    fun recipeEditor(recipeId: Long?) = "$RECIPE_EDITOR/${recipeId ?: NO_MEAL}"

    const val SESSION_RUN = "session_run"
    const val ARG_SESSION_ID = "sessionId"
    const val ARG_DURATION = "durationMin"

    fun sessionRun(sessionId: Long, durationMin: Int) = "$SESSION_RUN/$sessionId/$durationMin"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Aujourd'hui", Icons.Rounded.Home),
    Tab(Routes.MEALS, "Repas", Icons.Rounded.Restaurant),
    Tab(Routes.WORKOUT, "Sport", Icons.Rounded.FitnessCenter),
    Tab(Routes.SETTINGS, "Réglages", Icons.Rounded.Settings),
)

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
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route ||
                        (tab.route == Routes.SETTINGS && (currentRoute == Routes.IMPORT || currentRoute == Routes.PROFILE ||
                            currentRoute == Routes.WEIGHT || currentRoute == Routes.SPORT_PROFILE)) ||
                        (tab.route == Routes.TODAY && (currentRoute == Routes.WATER || currentRoute == Routes.BADGES ||
                            currentRoute == Routes.WEEKLY_RECAP || currentRoute == Routes.WATCH)) ||
                        (tab.route == Routes.MEALS && (currentRoute?.startsWith(Routes.MEAL_EDITOR) == true || currentRoute == Routes.SCANNED_PRODUCTS)) ||
                        (tab.route == Routes.WORKOUT && inSession)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onStartSession = { sessionId, durationMin ->
                        navController.navigate(Routes.sessionRun(sessionId, durationMin))
                    },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                    onOpenWeight = { navController.navigate(Routes.WEIGHT) { launchSingleTop = true } },
                    onOpenWater = { navController.navigate(Routes.WATER) { launchSingleTop = true } },
                    onOpenBadges = { navController.navigate(Routes.BADGES) { launchSingleTop = true } },
                    onOpenWeeklyRecap = { navController.navigate(Routes.WEEKLY_RECAP) { launchSingleTop = true } },
                    onOpenWatch = { navController.navigate(Routes.WATCH) { launchSingleTop = true } },
                )
            }
            composable(Routes.WATCH) { WatchTrendsScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.WATER) { WaterScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.BADGES) { BadgesScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.WEEKLY_RECAP) { WeeklyRecapScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.MEALS) {
                MealsScreen(
                    onAddMeal = { date -> navController.navigate(Routes.mealEditor(null, date)) },
                    onEditMeal = { mealId -> navController.navigate(Routes.mealEditor(mealId, LocalDate.now())) },
                    onImport = { navController.navigate(Routes.IMPORT) { launchSingleTop = true } },
                    onOpenRecipes = { navController.navigate(Routes.RECIPES) { launchSingleTop = true } },
                    onOpenScannedProducts = { navController.navigate(Routes.SCANNED_PRODUCTS) { launchSingleTop = true } },
                )
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
                )
            }
            composable(Routes.WORKOUT) {
                WorkoutScreen(
                    onStartSession = { sessionId, durationMin ->
                        navController.navigate(Routes.sessionRun(sessionId, durationMin))
                    },
                    onImport = { navController.navigate(Routes.IMPORT) { launchSingleTop = true } },
                    onOpenWatch = { navController.navigate(Routes.WATCH) { launchSingleTop = true } },
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
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenImport = { navController.navigate(Routes.IMPORT) { launchSingleTop = true } },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                    onOpenWeight = { navController.navigate(Routes.WEIGHT) { launchSingleTop = true } },
                    onOpenSportProfile = { navController.navigate(Routes.SPORT_PROFILE) { launchSingleTop = true } },
                    onCheckUpdates = updateViewModel::checkNow,
                )
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

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
