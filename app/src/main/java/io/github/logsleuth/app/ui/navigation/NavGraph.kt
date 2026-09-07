package io.github.logsleuth.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.logsleuth.app.ui.crashes.CrashesScreen
import io.github.logsleuth.app.ui.filters.FiltersScreen
import io.github.logsleuth.app.ui.sessiondetail.SessionDetailScreen
import io.github.logsleuth.app.ui.sessions.SessionsScreen
import io.github.logsleuth.app.ui.settings.SettingsScreen
import io.github.logsleuth.app.ui.setup.SetupScreen
import io.github.logsleuth.app.ui.stream.StreamScreen

object Routes {
    const val SETUP = "setup"
    const val STREAM = "stream"
    const val SESSIONS = "sessions"
    const val SESSION_DETAIL = "session/{sessionId}"
    const val CRASHES = "crashes"
    const val FILTERS = "filters"
    const val SETTINGS = "settings"

    fun sessionDetail(sessionId: Long) = "session/$sessionId"
}

@Composable
fun LogSleuthNavHost(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SETUP) {
            SetupScreen(onDone = {
                navController.navigate(Routes.STREAM) {
                    popUpTo(Routes.SETUP) { inclusive = true }
                }
            })
        }
        composable(Routes.STREAM) {
            StreamScreen(
                onNavigate = { route -> navController.navigate(route) },
            )
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
