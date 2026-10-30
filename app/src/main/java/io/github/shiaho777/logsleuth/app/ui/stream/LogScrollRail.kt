package io.github.shiaho777.logsleuth.app.ui.stream

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.ui.theme.LevelColors
import java.text.NumberFormat
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Touch strip hugging the right edge; the visible track is narrower. */
private val RailWidth = 26.dp

/** Visible track width. The thumb is wider than this, so it reads as a grip. */
private val StripWidth = 6.dp

/** The thumb always overhangs the track by this much on each side. */
private val ThumbOverhang = 2.dp

/** How much wider the thumb gets (per side) while it is being dragged. */
private val ScrubGrow = 3.dp

/** Smallest draggable thumb, so a 20k-line buffer still has a grab target. */
private val MinThumb = 40.dp

/**
 * Room to the left of the strip for the readout pill. The pill cannot be
 * measured inside [RailWidth], so the container is wider and only its
 * right-hand [RailWidth] accepts touches.
 */
private val PillSpace = 168.dp

/** One fingerprint band every this much rail height. */
private val BandStep = 4.dp

/** How often the severity fingerprint is rebuilt while logs stream in. */
private const val BUCKET_REFRESH_MS = 400L

/** How long the readout pill stays after the finger lifts. */
private const val LABEL_LINGER_MS = 1_100L

/**
 * The list's whole-content rail.
 *
 * A plain scrollbar only says "you are here". This one also carries a
 * fingerprint of the entire buffer: each band is coloured by the highest
 * severity in that slice, so error clusters are visible without scrolling and
 * without searching. The translucent thumb overlays the strip, sized to the
 * viewport, and can be dragged (or tapped) to any position.
 *
 * Three readouts answer "how long is this and where am I":
 *  - the thumb size, proportional to how much of the buffer fits on screen;
 *  - a permanent `N lines` caption at the rail's foot;
 *  - a pill that rides the thumb while dragging, showing the exact row,
 *    the total, and the percentage.
 *
 * Dragging reports an index through [onScrubTo]; the caller is responsible for
 * releasing tail-follow before the list jumps, otherwise the pin would drag
 * the viewport straight back to the newest line.
 */
@Composable
internal fun LogScrollRail(
    entries: List<UiLogEntry>,
    listState: LazyListState,
    onScrubTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = entries.size
    // derivedStateOf keeps the rail from recomposing on unrelated layout
    // passes; it still fires when the viewport size or item count changes.
    val scrollable by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo.size
            visible > 0 && info.totalItemsCount > visible
        }
    }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var railHeight by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var pillVisible by remember { mutableStateOf(false) }
    // The pill is shown and hidden imperatively from the gesture. Driving it
    // from `scrubbing` through a LaunchedEffect loses a tap: down and up can
    // land in the same frame, so the effect only ever sees `false`.
    val scope = rememberCoroutineScope()
    var hidePillJob by remember { mutableStateOf<Job?>(null) }

    val minThumbPx = with(density) { MinThumb.toPx() }
    val viewport by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.size }
    }
    val thumbPx = ScrollRailMath.thumbHeight(railHeight, viewport, total, minThumbPx)

    // Recompute only when the scroll position actually changes: reading
    // layoutInfo in composition on every frame would be wasteful.
    val liveProgress by remember {
        derivedStateOf { viewportProgress(listState) }
    }
    val progress = if (scrubbing) scrubProgress else liveProgress

    val bucketCount = with(density) {
        if (railHeight <= 0f) 0 else (railHeight / BandStep.toPx()).toInt().coerceIn(1, 400)
    }
    val buckets = rememberLevelBuckets(entries, bucketCount)

    // Keep the pill readable for a moment after the finger lifts — a tap
    // that jumps the list should still show where it landed.
    fun showPill() {
        hidePillJob?.cancel()
        hidePillJob = null
        pillVisible = true
    }

    fun hidePillSoon() {
        hidePillJob?.cancel()
        hidePillJob = scope.launch {
            delay(LABEL_LINGER_MS)
            pillVisible = false
        }
    }

    // Captured so an in-flight drag is never restarted by layout changes.
    val thumbNow = rememberUpdatedState(thumbPx)
    val totalNow = rememberUpdatedState(total)

    val thumbExtra by animateDpAsState(
        targetValue = if (scrubbing) ScrubGrow else 0.dp,
        label = "railThumbGrow",
    )
    val footAlpha by animateFloatAsState(
        targetValue = if (pillVisible) 0f else 1f,
        label = "railFootAlpha",
    )

    val numbers = remember { NumberFormat.getIntegerInstance() }
    // Formatted once per count change, not once per frame of streaming.
    val totalLabel = remember(total) { numbers.format(total) }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    val thumbColor = if (scrubbing) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val outlineColor = MaterialTheme.colorScheme.surface
    val description = stringResource(R.string.scrollbar_description)

    Box(
        modifier
            .fillMaxHeight()
            .width(RailWidth + PillSpace)
            .semantics {
                // Nothing to announce when the whole buffer already fits.
                if (scrollable) {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                }
            },
    ) {
        Canvas(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(RailWidth)
                .alpha(if (scrollable) 1f else 0f)
                .onSizeChanged { railHeight = it.height.toFloat() }
                .pointerInput(scrollable) {
                    if (!scrollable) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scrubbing = true
                        showPill()
                        scrubProgress = ScrollRailMath.progressForDrag(
                            yPx = down.position.y,
                            railPx = size.height.toFloat(),
                            thumbPx = thumbNow.value,
                        )
                        // Only report when the row changes: a pixel of finger
                        // travel rarely maps to a new row, and each report
                        // parks the viewport.
                        var lastIndex = ScrollRailMath.indexForProgress(
                            scrubProgress,
                            totalNow.value,
                        )
                        onScrubTo(lastIndex)
                        var lastY = down.position.y
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.position.y != lastY) {
                                lastY = change.position.y
                                scrubProgress = ScrollRailMath.progressForDrag(
                                    yPx = lastY,
                                    railPx = size.height.toFloat(),
                                    thumbPx = thumbNow.value,
                                )
                                val next = ScrollRailMath.indexForProgress(
                                    scrubProgress,
                                    totalNow.value,
                                )
                                if (next != lastIndex) {
                                    lastIndex = next
                                    onScrubTo(next)
                                }
                            }
                            change.consume()
                        }
                        scrubbing = false
                        hidePillSoon()
                    }
                },
        ) {
            val strip = StripWidth.toPx()
            val stripX = (size.width - strip) / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(stripX, 0f),
                size = Size(strip, size.height),
                cornerRadius = CornerRadius(strip / 2f),
            )

            // Severity fingerprint: one band per slice of the buffer. The
            // highest level wins, so a single error is not lost among info.
            if (buckets.isNotEmpty()) {
                val bars = buckets.size
                val railPx = size.height.toInt()
                var top = 0
                for (i in 0 until bars) {
                    // Both edges snapped to whole pixels. Sharing the exact
                    // edge with the next band is what stops hairline gaps from
                    // showing between two translucent bands.
                    val bottom = ((i + 1).toLong() * railPx / bars).toInt()
                    if (bottom > top) {
                        val priority = buckets[i]
                        if (priority > 0) {
                            drawRect(
                                color = colorForPriority(priority).copy(alpha = 0.5f),
                                topLeft = Offset(stripX, top.toFloat()),
                                size = Size(strip, (bottom - top).toFloat()),
                            )
                        }
                        top = bottom
                    }
                }
            }

            if (scrollable && size.height > 0f) {
                val top = ScrollRailMath.thumbTop(size.height, thumbPx, progress)
                // Wider than the track, so the grip is visible at a glance
                // even where the fingerprint underneath is the same colour.
                val width = strip + 2 * ThumbOverhang.toPx() + thumbExtra.toPx()
                val left = (size.width - width) / 2f
                val border = 1.dp.toPx()
                val innerWidth = (width - border * 2).coerceAtLeast(1f)
                val innerHeight = (thumbPx - border * 2).coerceAtLeast(1f)
                // The ring is the page colour, so the thumb separates from the
                // fingerprint underneath it in both light and dark themes.
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(left, top),
                    size = Size(width, thumbPx),
                    cornerRadius = CornerRadius(width / 2f),
                )
                drawRoundRect(
                    color = thumbColor.copy(alpha = 0.92f),
                    topLeft = Offset(left + border, top + border),
                    size = Size(innerWidth, innerHeight),
                    cornerRadius = CornerRadius(innerWidth / 2f),
                )
            }
        }

        // Permanent readout: how many lines the rail is drawing a picture of.
        // It is a solid chip, not loose text — over live log lines bare glyphs
        // read as a rendering glitch. It steps aside while the pill is up so
        // two numbers never compete.
        if (scrollable && total > 0 && footAlpha > 0.01f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = RailWidth + 6.dp, bottom = 6.dp)
                    .alpha(footAlpha),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = stringResource(R.string.scrollbar_total, totalLabel),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        if (pillVisible && total > 0 && railHeight > 0f) {
            val shown = if (scrubbing) scrubProgress else progress
            val index = ScrollRailMath.indexForProgress(shown, total)
            val thumbTop = ScrollRailMath.thumbTop(railHeight, thumbPx, shown)
            val pillHeight = with(density) { 54.dp.toPx() }
            val pillY = (thumbTop - pillHeight / 2f)
                .coerceIn(0f, (railHeight - pillHeight).coerceAtLeast(0f))
            PositionPill(
                index = index,
                total = total,
                progress = shown,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -(RailWidth + 6.dp), y = with(density) { pillY.toDp() }),
            )
        }
    }
}

/** "Line 1,234 of 20,000" plus the percentage, so a jump is never blind. */
@Composable
private fun PositionPill(
    index: Int,
    total: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(
                text = stringResource(
                    R.string.scrollbar_line_of,
                    numbers.format(index + 1),
                    numbers.format(total),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

/**
 * Severity per bucket, throttled. Rebuilds when the buffer grows or its head
 * moves (ring-buffer eviction keeps the size constant while the contents
 * shift), at most every [BUCKET_REFRESH_MS].
 */
@Composable
private fun rememberLevelBuckets(entries: List<UiLogEntry>, buckets: Int): IntArray {
    var levels by remember { mutableStateOf(IntArray(0)) }
    LaunchedEffect(buckets) {
        if (buckets <= 0) return@LaunchedEffect
        var lastSize = -1
        var lastHead = Long.MIN_VALUE
        while (isActive) {
            val size = entries.size
            val head = entries.firstOrNull()?.seq ?: -1L
            if (size != lastSize || head != lastHead) {
                lastSize = size
                lastHead = head
                levels = withContext(Dispatchers.Default) {
                    ScrollRailMath.bucketLevels(entries, buckets)
                }
            }
            delay(BUCKET_REFRESH_MS)
        }
    }
    return levels
}

/** 0..1 position of the viewport inside the list. */
private fun viewportProgress(listState: LazyListState): Float {
    val info = listState.layoutInfo
    val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
    val total = info.totalItemsCount
    val viewport = info.visibleItemsInfo.size
    val travel = total - viewport
    if (travel <= 0) return 0f
    val itemHeight = first.size.coerceAtLeast(1)
    val scrolled = listState.firstVisibleItemIndex +
        listState.firstVisibleItemScrollOffset.toFloat() / itemHeight
    return (scrolled / travel).coerceIn(0f, 1f)
}

private fun colorForPriority(priority: Int): Color = when {
    // ASSERT shares the platform priority of FATAL, so it reads as fatal.
    priority >= LogLevel.F.priority -> LevelColors.F
    priority == LogLevel.E.priority -> LevelColors.E
    priority == LogLevel.W.priority -> LevelColors.W
    priority == LogLevel.I.priority -> LevelColors.I
    priority == LogLevel.D.priority -> LevelColors.D
    else -> LevelColors.V
}

/**
 * Geometry for the rail. Pure so the drag/tap mapping can be unit-tested
 * without a Compose runtime.
 */
internal object ScrollRailMath {

    /** Viewport height, proportional to how much of the list fits on screen. */
    fun thumbHeight(railPx: Float, viewport: Int, total: Int, minPx: Float): Float {
        if (railPx <= 0f || total <= 0 || viewport <= 0) return 0f
        val proportional = railPx * (viewport.toFloat() / total)
        return proportional.coerceIn(minPx.coerceAtMost(railPx), railPx)
    }

    /** Top offset of the thumb for [progress] (0 = oldest, 1 = newest). */
    fun thumbTop(railPx: Float, thumbPx: Float, progress: Float): Float {
        val travel = (railPx - thumbPx).coerceAtLeast(0f)
        return progress.coerceIn(0f, 1f) * travel
    }

    /**
     * Inverse of [thumbTop]: the progress a finger at [yPx] selects. The thumb
     * centres on the finger, which is what makes a grab feel like a grab.
     */
    fun progressForDrag(yPx: Float, railPx: Float, thumbPx: Float): Float {
        val travel = (railPx - thumbPx).coerceAtLeast(1f)
        return ((yPx - thumbPx / 2f) / travel).coerceIn(0f, 1f)
    }

    /** Nearest row index for a progress value. */
    fun indexForProgress(progress: Float, total: Int): Int {
        if (total <= 1) return 0
        return (progress.coerceIn(0f, 1f) * (total - 1)).roundToInt().coerceIn(0, total - 1)
    }

    /**
     * Highest log priority in each of [bucketCount] equal slices of [entries].
     * One pass over the list (slices partition it), so a 20k buffer costs the
     * same as a walk of 20k — cheap enough to run twice a second.
     */
    fun bucketLevels(entries: List<UiLogEntry>, bucketCount: Int): IntArray {
        if (bucketCount <= 0 || entries.isEmpty()) return IntArray(0)
        val out = IntArray(bucketCount)
        val size = entries.size
        for (b in 0 until bucketCount) {
            val start = (b.toLong() * size / bucketCount).toInt()
            val end = (((b + 1).toLong() * size / bucketCount).toInt())
                .coerceAtLeast(start + 1)
                .coerceAtMost(size)
            if (start >= size) break
            var worst = 0
            for (i in start until end) {
                val priority = entries[i].entry.level.priority
                if (priority > worst) worst = priority
            }
            out[b] = worst
        }
        return out
    }
}
