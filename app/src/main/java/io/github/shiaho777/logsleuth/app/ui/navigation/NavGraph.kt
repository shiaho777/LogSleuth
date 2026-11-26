package io.github.shiaho777.logsleuth.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.ui.automation.AutomationScreen
import io.github.shiaho777.logsleuth.app.ui.crashes.AppCrashesScreen
import io.github.shiaho777.logsleuth.app.ui.crashes.CrashDetailsScreen
import io.github.shiaho777.logsleuth.app.ui.crashes.CrashesScreen
import io.github.shiaho777.logsleuth.app.ui.filters.FiltersScreen
import io.github.shiaho777.logsleuth.app.ui.guide.LocalTourController
import io.github.shiaho777.logsleuth.app.ui.guide.TourController
import io.github.shiaho777.logsleuth.app.ui.guide.TourOverlay
import io.github.shiaho777.logsleuth.app.ui.report.ReportScreen
import io.github.shiaho777.logsleuth.app.ui.sessiondetail.SessionDetailScreen
import io.github.shiaho777.logsleuth.app.ui.sessions.SessionsScreen
import io.github.shiaho777.logsleuth.app.ui.settings.SettingsScreen
import io.github.shiaho777.logsleuth.app.ui.setup.SetupScreen
import io.github.shiaho777.logsleuth.app.ui.setup.WelcomeScreen
import io.github.shiaho777.logsleuth.app.ui.stream.StreamScreen
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthMotion

object Routes {
    const val WELCOME = "welcome"
    const val SETUP = "setup"
    const val STREAM = "stream"
    const val SESSIONS = "sessions"
    const val SESSION_DETAIL = "session/{sessionId}"
    const val REPORT = "report"
    const val CRASHES = "crashes"
    const val APP_CRASHES = "crashes/pkg/{packageName}?event={eventId}"
    const val CRASH_DETAIL = "crash/{eventId}"
    const val FILTERS = "filters"
    const val AUTOMATION = "automation"
    const val SETTINGS = "settings"

    /** Route token for a crash whose package logcat never named. */
    const val UNKNOWN_PACKAGE = "_unknown"

    fun sessionDetail(sessionId: Long) = "session/$sessionId"

    fun appCrashes(packageName: String?, eventId: Long = -1L): String {
        val key = Uri.encode(packageName?.takeIf { it.isNotBlank() } ?: UNKNOWN_PACKAGE)
        return "crashes/pkg/$key?event=$eventId"
    }

    fun crashDetail(eventId: Long) = "crash/$eventId"

    fun packageFromKey(key: String?): String? {
        val decoded = Uri.decode(key ?: return null)
        return decoded.takeIf { it.isNotBlank() && it != UNKNOWN_PACKAGE }
    }
}

internal data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.STREAM, R.string.nav_stream, Icons.AutoMirrored.Filled.Article),
    TopLevelDestination(Routes.SESSIONS, R.string.nav_sessions, Icons.Filled.History),
    TopLevelDestination(Routes.REPORT, R.string.nav_report, Icons.Filled.Flag),
    TopLevelDestination(Routes.CRASHES, R.string.nav_crashes, Icons.Filled.BugReport),
    TopLevelDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

private val topLevelOrder = topLevelDestinations.mapIndexed { index, destination ->
    destination.route to index
}.toMap()

/** Shared-element scope from the [SharedTransitionLayout] wrapping the NavHost. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

@Composable
fun LogSleuthNavHost(
    navController: NavHostController,
    startDestination: String,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val layoutSign = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val tabTravel = with(LocalDensity.current) { 48.dp.roundToPx() } * layoutSign
    val pageTravel = with(LocalDensity.current) { 32.dp.roundToPx() } * layoutSign

    // Coach-mark tour: one controller for the whole graph. Screens mark
    // their highlight elements with Modifier.tourTarget; the overlay sits
    // above everything (nav bar included) inside this Box.
    val tour = remember { TourController() }

    CompositionLocalProvider(LocalTourController provides tour) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
        // Exclude the status-bar inset: screens' TopAppBar consumes it
        // itself — applying it here too produced a double-height top gap.
        // Keep horizontal (cutouts) and bottom (gesture bar on screens
        // without the nav bar).
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        bottomBar = {
            InstrumentNavigation(topLevelDestinations, currentRoute ?: startDestination) { destination ->
                navController.openTab(destination.route)
            }
        },
    ) { padding ->
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    // Consume what the Scaffold already inset, so a screen's
                    // own imePadding() only adds the part the keyboard still
                    // covers — otherwise the content lifts by the nav bar
                    // height twice and leaves a blank band above the keyboard.
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding),
                    enterTransition = { SleuthMotion.enter(pageOffset(tabTravel, pageTravel, returning = false)) },
                    exitTransition = { SleuthMotion.exit(pageOffset(tabTravel, pageTravel, returning = false)) },
                    popEnterTransition = { SleuthMotion.enter(pageOffset(tabTravel, pageTravel, returning = true)) },
                    popExitTransition = { SleuthMotion.exit(pageOffset(tabTravel, pageTravel, returning = true)) },
                ) {
                    composable(Routes.WELCOME) {
                        WelcomeScreen(
                            onOpenSession = { id ->
                                navController.navigate(Routes.SESSIONS)
                                navController.navigate(Routes.sessionDetail(id))
                            },
                            onOpenSetup = { navController.navigate(Routes.SETUP) },
                            onOpenSessions = { navController.navigate(Routes.SESSIONS) },
                        )
                    }
                    composable(Routes.SETUP) {
                        SetupScreen(
                            onBack = { navController.popBackStack() },
                            onDone = {
                                if (navController.previousBackStackEntry?.destination?.route == Routes.REPORT) {
                                    navController.popBackStack()
                                } else {
                                    navController.navigate(Routes.STREAM) {
                                        popUpTo(Routes.SETUP) { inclusive = true }
                                    }
                                }
                            },
                        )
                    }
                    composable(Routes.STREAM) {
                        StreamScreen(onNavigate = { route -> navController.navigate(route) })
                    }
                    composable(Routes.SESSIONS) {
                        SessionsScreen(
                            animatedVisibilityScope = this,
                            onOpenSession = { id -> navController.navigate(Routes.sessionDetail(id)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        Routes.SESSION_DETAIL,
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
                    ) { entry ->
                        SessionDetailScreen(
                            sessionId = entry.arguments?.getLong("sessionId") ?: -1L,
                            animatedVisibilityScope = this,
                            onBack = { navController.popBackStack() },
                            onOpenFilters = { navController.navigate(Routes.FILTERS) },
                            onOpenCrash = { id -> navController.navigate(Routes.crashDetail(id)) },
                        )
                    }
                    composable(Routes.REPORT) {
                        ReportScreen(
                            onBack = { navController.popBackStack() },
                            onOpenSessions = { navController.openTab(Routes.SESSIONS) },
                            onOpenSetup = { navController.navigate(Routes.SETUP) },
                            onOpenStream = { navController.openTab(Routes.STREAM) },
                            onPreviewSession = { id -> navController.navigate(Routes.sessionDetail(id)) },
                        )
                    }
                    composable(Routes.CRASHES) {
                        CrashesScreen(
                            onBack = { navController.popBackStack() },
                            onOpenPackage = { pkg ->
                                navController.navigate(Routes.appCrashes(pkg))
                            },
                        )
                    }
                    composable(
                        Routes.APP_CRASHES,
                        arguments = listOf(
                            navArgument("packageName") { type = NavType.StringType },
                            navArgument("eventId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                        ),
                    ) {
                        AppCrashesScreen(
                            onBack = { navController.popBackStack() },
                            onOpenCrash = { id -> navController.navigate(Routes.crashDetail(id)) },
                        )
                    }
                    composable(
                        Routes.CRASH_DETAIL,
                        arguments = listOf(navArgument("eventId") { type = NavType.LongType }),
                    ) {
                        CrashDetailsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.FILTERS) {
                        FiltersScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.AUTOMATION) {
                        AutomationScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenSetup = { navController.navigate(Routes.SETUP) },
                            onOpenAutomation = { navController.navigate(Routes.AUTOMATION) },
                            onOpenWelcome = { navController.navigate(Routes.WELCOME) },
                            onOpenGuide = {
                                navController.navigate(Routes.STREAM) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                }
                                tour.start()
                            },
                        )
                    }
                }
            }
        }
    }

            // Coach-mark overlay — above the whole Scaffold, nav bar
            // included, so targets anywhere can be highlighted.
            if (tour.active) {
                TourOverlay(
                    controller = tour,
                    onNavigate = navController::openTab,
                    onFinished = tour::stop,
                )
            }
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageOffset(
    tabTravel: Int,
    pageTravel: Int,
    returning: Boolean,
): Int {
    val from = initialState.destination.route?.let(topLevelOrder::get)
    val to = targetState.destination.route?.let(topLevelOrder::get)
    return when {
        from != null && to != null && from != to -> if (to > from) tabTravel else -tabTravel
        returning -> -pageTravel
        else -> pageTravel
    }
}

private fun NavHostController.openTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
