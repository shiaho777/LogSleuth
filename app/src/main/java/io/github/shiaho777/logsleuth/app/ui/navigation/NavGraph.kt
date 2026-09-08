package io.github.shiaho777.logsleuth.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.ui.crashes.CrashesScreen
import io.github.shiaho777.logsleuth.app.ui.filters.FiltersScreen
import io.github.shiaho777.logsleuth.app.ui.report.ReportScreen
import io.github.shiaho777.logsleuth.app.ui.sessiondetail.SessionDetailScreen
import io.github.shiaho777.logsleuth.app.ui.sessions.SessionsScreen
import io.github.shiaho777.logsleuth.app.ui.settings.SettingsScreen
import io.github.shiaho777.logsleuth.app.ui.setup.SetupScreen
import io.github.shiaho777.logsleuth.app.ui.stream.StreamScreen

object Routes {
    const val SETUP = "setup"
    const val STREAM = "stream"
    const val SESSIONS = "sessions"
    const val SESSION_DETAIL = "session/{sessionId}"
    const val REPORT = "report"
    const val CRASHES = "crashes"
    const val FILTERS = "filters"
    const val SETTINGS = "settings"

    fun sessionDetail(sessionId: Long) = "session/$sessionId"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.STREAM, R.string.nav_stream, Icons.Filled.Article),
    TopLevelDestination(Routes.SESSIONS, R.string.nav_sessions, Icons.Filled.History),
    TopLevelDestination(Routes.REPORT, R.string.nav_report, Icons.Filled.Flag),
    TopLevelDestination(Routes.CRASHES, R.string.nav_crashes, Icons.Filled.BugReport),
    TopLevelDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

private val topLevelRoutes = topLevelDestinations.map { it.route }.toSet()

@Composable
fun LogSleuthNavHost(
    navController: NavHostController,
    startDestination: String,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                ShortNavigationBar {
                    topLevelDestinations.forEach { dest ->
                        ShortNavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                if (currentRoute != dest.route) {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() },
        ) {
            composable(Routes.SETUP) {
                SetupScreen(onDone = {
                    navController.navigate(Routes.STREAM) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                })
            }
            composable(Routes.STREAM) {
                StreamScreen(onNavigate = { route -> navController.navigate(route) })
            }
            composable(Routes.SESSIONS) {
                SessionsScreen(
                    onOpenSession = { id -> navController.navigate(Routes.sessionDetail(id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.SESSION_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) {
                SessionDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.REPORT) {
                ReportScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSessions = { navController.navigate(Routes.SESSIONS) },
                )
            }
            composable(Routes.CRASHES) {
                CrashesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.FILTERS) {
                FiltersScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
