package io.github.shiaho777.logsleuth.app.ui.guide

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.ui.navigation.Routes
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * One tour beat.
 *
 * @param route navigate to this destination when the step starts, so the
 *   tour can hop across the bottom-nav pages. Null = stay put.
 * @param targetKey spotlight key registered via Modifier.tourTarget;
 *   null = no hole (centered card, or a pure live-demo beat).
 * @param enter / leave action keys fired through TourController.actions
 *   — this is how the tour itself taps buttons: open the search bar,
 *   toggle pause, pop the scope dialog, nudge the list off the tail.
 * @param showCard false for hands-free beats (the demoed UI is the show).
 * @param autoMs auto-advance after N ms without needing a tap.
 */
private data class TourStep(
    val route: String?,
    val targetKey: String?,
    val titleRes: Int,
    val descRes: Int,
    val enter: String? = null,
    val leave: String? = null,
    val showCard: Boolean = true,
    val autoMs: Long = 0,
)

private val steps = listOf(
    TourStep(Routes.STREAM, null, R.string.tour_intro_title, R.string.tour_intro_desc),
    TourStep(Routes.STREAM, "filterBar", R.string.tour_filter_title, R.string.tour_filter_desc),
    // Expands the filter bar itself so the detail fields are spotlighted.
    TourStep(
        Routes.STREAM, "filterFields", R.string.tour_fields_title, R.string.tour_fields_desc,
        enter = "filterExpand", leave = "filterCollapse",
    ),
    TourStep(Routes.STREAM, "engineStatus", R.string.tour_engine_title, R.string.tour_engine_desc),
    // Really pauses the stream — the banner slides in while reading.
    TourStep(
        Routes.STREAM, "pause", R.string.tour_pause_title, R.string.tour_pause_desc,
        enter = "pauseOn",
    ),
    // …and the next beat highlights the banner itself before resuming.
    TourStep(
        Routes.STREAM, "pausedBanner", R.string.tour_paused_title, R.string.tour_paused_desc,
        leave = "pauseOff",
    ),
    // Opens the real search bar with a typed query — hits light up live.
    TourStep(
        Routes.STREAM, "searchBar", R.string.tour_search_title, R.string.tour_search_desc,
        enter = "searchOpen", leave = "searchClose",
    ),
    TourStep(Routes.STREAM, "bubble", R.string.tour_bubble_title, R.string.tour_bubble_desc),
    TourStep(Routes.STREAM, "scopeActions", R.string.tour_scope_title, R.string.tour_scope_desc),
    // Live demo: the real scope dialog pops in per-app mode, no card.
    TourStep(
        Routes.STREAM, null, R.string.tour_scope_title, R.string.tour_scope_demo_desc,
        enter = "scopeOpenPerApp", leave = "scopeClose", showCard = false, autoMs = 4200,
    ),
    // Turns on multi-select: checkmarks and the action bar appear for real.
    TourStep(
        Routes.STREAM, "selectionBar", R.string.tour_select_title, R.string.tour_select_desc,
        enter = "selectDemo", leave = "selectOff",
    ),
    // Scrolls up so both jump-FABs physically appear, then returns to tail.
    TourStep(
        Routes.STREAM, "fab", R.string.tour_fab_title, R.string.tour_fab_desc,
        enter = "fabPeek", leave = "fabBack",
    ),
    TourStep(Routes.SESSIONS, "sessionsContent", R.string.tour_sessions_title, R.string.tour_sessions_desc),
    // Steps the report wizard forward on the user's own device.
    TourStep(
        Routes.REPORT, "reportContent", R.string.tour_report_title, R.string.tour_report_desc,
        enter = "reportDemo", leave = "reportUndemo",
    ),
    TourStep(Routes.CRASHES, "crashesContent", R.string.tour_crashes_title, R.string.tour_crashes_desc),
    TourStep(Routes.SETTINGS, "settingsContent", R.string.tour_settings_title, R.string.tour_settings_desc),
    TourStep(Routes.STREAM, null, R.string.tour_done_title, R.string.tour_done_desc),
)

/**
 * Spotlight overlay that *drives* the app: navigates between tabs and
 * fires registered UI actions, while punching a spring-animated hole in
 * the dim scrim over whichever control is being explained.
 */
@Composable
fun TourOverlay(
    controller: TourController,
    onNavigate: (String) -> Unit,
    onFinished: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]

    // One gesture must move exactly one beat. When a real Dialog sits in
    // front (the scope-demo step), dismissing it mid-tap can replay the
    // gesture to the scrim's tap handler, and a tap landing right at an
    // autoMs boundary can race the timer — both would skip a step without
    // this guard.
    val lastAdvanceNs = remember { longArrayOf(0L) }
    fun advance() {
        val now = System.nanoTime()
        if (now - lastAdvanceNs[0] < 350_000_000L) return
        lastAdvanceNs[0] = now
        if (index == steps.size - 1) onFinished() else index++
    }

    // Step lifecycle: navigate first, let the slide transition settle,
    // then fire the step's enter action. Leaving the step (index change
    // or overlay teardown) fires the leave action — pause offs, dialog
    // closes, tail re-engages.
    DisposableEffect(index) {
        onDispose { step.leave?.let(controller::fire) }
    }
    // "nextStep" lets a screen's own UI end a hands-free beat: during the
    // scope-dialog demo the real Dialog window eats the tap that dismisses
    // it, so the scrim's tap-to-advance never fires — the screen fires
    // this instead, and dismissing the demo = advancing. Absent it the
    // step still auto-advances on its timer.
    DisposableEffect(Unit) {
        controller.actions["nextStep"] = { advance() }
        onDispose { controller.actions.remove("nextStep") }
    }
    // Skip/finish at any point → every screen's "leave.*" hook runs so a
    // tour-opened search bar, dialog, pause or selection never lingers.
    DisposableEffect(Unit) {
        onDispose { controller.fireAll("leave.") }
    }
    LaunchedEffect(index) {
        controller.publishStep(index, steps.size)
        if (step.route != null) {
            onNavigate(step.route)
            delay(380)
        }
        step.enter?.let(controller::fire)
        if (step.autoMs > 0) {
            delay(step.autoMs)
            advance()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val h = constraints.maxHeight.toFloat()
        val pad = with(density) { 10.dp.toPx() }
        val corner = with(density) { 16.dp.toPx() }

        // Hole: element bounds inflated slightly; a target that has not
        // been laid out yet parks the hole off-screen (solid scrim).
        val raw = step.targetKey?.let { controller.targets[it] }
        val holeVisible = raw != null
        val hole by animateValueAsState(
            targetValue = raw?.inflate(pad) ?: Rect(-400f, -400f, -300f, -300f),
            typeConverter = Rect.VectorConverter,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "hole",
        )

        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            ),
            label = "p",
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Tap anywhere = next — the tutorial's primary gesture.
                    detectTapGestures { advance() }
                },
        ) {
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                if (holeVisible) addRoundRect(RoundRect(hole, corner, corner))
            }
            drawPath(scrim, color = Color.Black.copy(alpha = 0.72f))
            if (holeVisible) {
                drawRoundRect(
                    color = Color.White.copy(alpha = pulse),
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        if (!step.showCard) return@BoxWithConstraints

        // Card: measured once, then its top edge is spring-animated —
        // below the hole when the target sits high, above when low,
        // centered for target-free steps.
        var cardH by remember { mutableIntStateOf(0) }
        val gap = with(density) { 18.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val cardTop = when {
            cardH == 0 -> h // parked off-screen until measured
            !holeVisible -> (h - cardH) / 2f
            hole.center.y < h * 0.52f -> hole.bottom + gap
            else -> hole.top - cardH - gap
        }.coerceIn(margin, h - cardH - margin)
        val animCardTop by animateFloatAsState(
            targetValue = cardTop,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "cardTop",
        )

        TourCard(
            index = index,
            count = steps.size,
            titleRes = step.titleRes,
            descRes = step.descRes,
            isLast = index == steps.size - 1,
            onSkip = onFinished,
            onNext = ::advance,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset { IntOffset(0, animCardTop.roundToInt()) }
                .onGloballyPositioned { cardH = it.size.height }
                .graphicsLayer { alpha = if (cardH == 0) 0f else 1f }
                // Tapping the card itself also advances — otherwise the
                // biggest visible thing would be a dead touch zone.
                .pointerInput(Unit) { detectTapGestures { advance() } },
        )
    }
}

@Composable
private fun TourCard(
    index: Int,
    count: Int,
    titleRes: Int,
    descRes: Int,
    isLast: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Progress dots, animated like the pager's.
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(count) { i ->
                        val on = i <= index
                        Box(
                            Modifier
                                .size(if (i == index) 7.dp else 5.dp)
                                .graphicsLayer {
                                    alpha = if (on) 1f else 0.35f
                                }
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                ),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${index + 1} / $count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(10.dp))
            // Content crossfades per step; the card itself does not move.
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 6 })
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "tourStep",
            ) {
                Column {
                    Text(
                        stringResource(steps[it].titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(steps[it].descRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isLast) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.tour_skip))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Button(onClick = onNext) {
                    Text(
                        stringResource(
                            if (isLast) R.string.tour_done else R.string.tour_next,
                        ),
                    )
                }
            }
        }
    }
}
