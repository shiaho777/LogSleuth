package io.github.shiaho777.logsleuth.app.ui.stream

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drag-to-extend tracking for range selection on the log list.
 *
 * Selection *starts* on the rows' own `onLongClick` (platform long-press
 * detection — reliable on a perfectly still finger, and cancelled by
 * scroll-past-slop for free). This modifier handles the rest:
 *
 * - A gesture tracker on [PointerEventPass.Initial] follows the pointer for
 *   the whole gesture. While [isSelecting] is true every position change is
 *   consumed, so the LazyColumn's own scrollable stays parked — the list
 *   only moves via the edge auto-scroll.
 * - A normal coroutine (launched inside `pointerInput`, not the restricted
 *   gesture scope, so `delay` works) ticks while the finger is held near
 *   the top/bottom edge: it scrolls the list and keeps extending the
 *   selection, with speed proportional to how far past the edge zone the
 *   finger is. A dwelling finger produces no pointer events, which is why
 *   the ticker is event-independent.
 * - The selection boundary tracks the finger 1:1 — no smoothing — and the
 *   range stays locked on release for the caller to act on.
 */
internal fun Modifier.logDragSelect(
    listState: LazyListState,
    isSelecting: () -> Boolean,
    seqAt: (Int) -> Long?,
    onSelectExtend: (Long) -> Unit,
): Modifier = pointerInput(listState) {
    val edgeZonePx = 72.dp.toPx()
    val tickMs = 25L

    var pointerDown = false
    var lastY = Float.NaN

    /** Visible item index under viewport-Y, clamped to the nearest row. */
    fun indexAtY(y: Float): Int? {
        val items = listState.layoutInfo.visibleItemsInfo
        if (items.isEmpty()) return null
        val hit = items.firstOrNull {
            y >= it.offset.toFloat() && y < (it.offset + it.size).toFloat()
        }
        return (hit ?: if (y < items.first().offset) items.first() else items.last()).index
    }

    fun extendTo(y: Float) {
        val vh = listState.layoutInfo.viewportSize.height.toFloat()
        indexAtY(y.coerceIn(0f, vh - 1f))?.let(seqAt)?.let(onSelectExtend)
    }

    coroutineScope {
        // Edge auto-scroll ticker: while the finger dwells in an edge zone
        // no move events arrive, so scrolling+extending runs on a timer in
        // a normal coroutine — not the restricted gesture scope.
        launch {
            while (isActive) {
                delay(tickMs)
                if (!pointerDown || lastY.isNaN() || !isSelecting()) continue
                val vh = listState.layoutInfo.viewportSize.height.toFloat()
                val overTop = edgeZonePx - lastY
                val overBottom = lastY - (vh - edgeZonePx)
                val edgeFactor = when {
                    overTop > 0 -> -(overTop / edgeZonePx).coerceIn(0.2f, 1f)
                    overBottom > 0 -> (overBottom / edgeZonePx).coerceIn(0.2f, 1f)
                    else -> 0f
                }
                if (edgeFactor != 0f) {
                    // Non-suspend dispatch: this coroutine only scrolls
                    // during selection, when the list's own scrollable is
                    // parked by our consumed move events.
                    listState.dispatchRawDelta(edgeFactor * vh * 0.05f)
                    extendTo(lastY)
                }
            }
        }

        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            pointerDown = true
            lastY = down.position.y
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                when {
                    // Tracked pointer absent from this batch: keep waiting
                    // unless nothing is held down at all.
                    change == null -> {
                        if (event.changes.none { it.pressed }) {
                            pointerDown = false
                            break
                        }
                    }
                    !change.pressed -> {
                        pointerDown = false
                        break
                    }
                    else -> {
                        lastY = change.position.y
                        if (isSelecting()) {
                            if (change.positionChange() != Offset.Zero) change.consume()
                            extendTo(lastY)
                        }
                    }
                }
            }
        }
    }
}
