package io.github.shiaho777.logsleuth.app.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Owns the coach-mark tour: whether it is showing, plus a live map of
 * target-key → on-screen bounds (root coordinates). Screens register
 * targets via [Modifier.tourTarget]; the overlay reads them to place
 * the spotlight hole and the tooltip.
 */
class TourController {
    var active by mutableStateOf(false)
        private set

    /** Auto-trigger guard: the flag-only auto-start fires at most once. */
    var hasRun by mutableStateOf(false)
        private set

    /** Key → bounds in root coordinates, updated on every layout pass. */
    val targets = mutableStateMapOf<String, Rect>()

    /**
     * Key → side-effect hook registered by screens (open the search bar,
     * toggle pause, show the scope dialog…). The tour fires these so a
     * step can actually drive the UI it is explaining.
     */
    val actions = mutableMapOf<String, () -> Unit>()

    fun fire(key: String) = actions[key]?.invoke()

    /** Fire every action whose key starts with [prefix] (teardown hooks). */
    fun fireAll(prefix: String) {
        actions.filterKeys { it.startsWith(prefix) }
            .values.toList()
            .forEach { it() }
    }

    fun start() {
        targets.clear()
        hasRun = true
        active = true
    }

    fun stop() {
        active = false
    }
}

val LocalTourController = compositionLocalOf { TourController() }

/**
 * Marks this element as a tour highlight target. The controller is read
 * through the composition local, so targets work on any screen, in any
 * slot (top bar actions, bottom nav, FABs), with no plumbing.
 */
@Composable
fun Modifier.tourTarget(key: String): Modifier {
    val controller = LocalTourController.current
    DisposableEffect(key) {
        onDispose { controller.targets.remove(key) }
    }
    return onGloballyPositioned { controller.targets[key] = it.boundsInRoot() }
}
