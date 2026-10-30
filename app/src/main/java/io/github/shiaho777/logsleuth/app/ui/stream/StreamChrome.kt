package io.github.shiaho777.logsleuth.app.ui.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.ui.guide.tourTarget
import io.github.shiaho777.logsleuth.app.ui.theme.LevelColors

/**
 * Presentational chrome around the live list: top bars, status banners, the
 * jump buttons, the selection bar and the saved-session sheet.
 *
 * Kept apart from `StreamScreen` so the screen file stays about state and
 * gestures, and these pure "take data + lambdas, draw it" pieces can be
 * reasoned about (and previewed) on their own.
 */

/** Slim banner shown while a recording is active. */
@Composable
internal fun RecordingBanner(
    lineCount: Long,
    paused: Boolean,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        color = if (paused) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        contentColor = if (paused) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (paused) {
                Icon(
                    Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(
                    if (paused) R.string.recording_banner_paused else R.string.recording_banner,
                    lineCount,
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPause) {
                Icon(
                    if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(stringResource(if (paused) R.string.resume else R.string.pause))
            }
            TextButton(onClick = onStop) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.recording_banner_stop))
            }
        }
    }
}

/** Small pill showing the engine state at the top-right of the list. */
@Composable
internal fun EngineStatusChip(
    state: LogcatEngine.State,
    adbHeld: Boolean,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when {
        adbHeld -> stringResource(R.string.engine_adb_waiting) to LevelColors.W
        state == LogcatEngine.State.RUNNING -> stringResource(R.string.engine_streaming) to LevelColors.I
        state == LogcatEngine.State.STARTING -> stringResource(R.string.engine_connecting) to LevelColors.W
        state == LogcatEngine.State.STOPPED -> stringResource(R.string.engine_stopped) to LevelColors.V
        else -> stringResource(R.string.engine_error) to LevelColors.E
    }
    val dotAlpha by if (state == LogcatEngine.State.STARTING) {
        rememberInfiniteTransition(label = "engine").animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "engineDot",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.size(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamTopBar(
    paused: Boolean,
    bubbleEnabled: Boolean,
    onPauseToggle: () -> Unit,
    onSearch: () -> Unit,
    onBubbleToggle: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onRestart: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        actions = {
            IconButton(onClick = onPauseToggle, modifier = Modifier.tourTarget("pause")) {
                Icon(
                    if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = stringResource(if (paused) R.string.resume else R.string.pause),
                    tint = if (paused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSearch, modifier = Modifier.tourTarget("search")) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_hint),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onBubbleToggle, modifier = Modifier.tourTarget("bubble")) {
                Icon(
                    Icons.Default.BubbleChart,
                    contentDescription = stringResource(R.string.bubble_controls),
                    tint = if (bubbleEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(Modifier.tourTarget("scopeActions")) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSave) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = stringResource(R.string.save_to_session),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Overflow: the engine reconnects on its own, so a manual restart
            // is only for a stream that looks wedged. It keeps the resume
            // stamp, which means the ring buffer is not dumped again.
            var more by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { more = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = more,
                    onDismissRequest = { more = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.restart_logging)) },
                        onClick = {
                            more = false
                            onRestart()
                        },
                    )
                }
            }
        },
    )
}

/**
 * Search mode's top bar.
 *
 * The query, the hit counter and the prev/next steppers live inside one
 * filled pill; only the case toggle and the close button sit outside it. Four
 * 48dp buttons sharing the row with a stock [TextField] used to squeeze the
 * query down to a couple of characters and left the field with a hard outline
 * that read as a form control, not as search.
 */
@Composable
internal fun SearchTopBar(
    query: String,
    hits: Int,
    hitIndex: Int,
    caseSensitive: Boolean,
    onQueryChange: (String) -> Unit,
    onCaseSensitiveChange: (Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val counter = when {
        query.isEmpty() -> ""
        hits <= 0 -> "0/0"
        else -> "${hitIndex + 1}/$hits"
    }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (hits > 0) onNext() }),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .focusRequester(focusRequester),
                        decorationBox = { field ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        // Clear of the caret, which sits at x=0
                                        // while the field is empty and focused.
                                        modifier = Modifier.padding(start = 3.dp),
                                    )
                                }
                                field()
                            }
                        },
                    )
                    if (counter.isNotEmpty()) {
                        Text(
                            text = counter,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Fixed slot: the pills must not resize per hit.
                            modifier = Modifier.padding(start = 2.dp, end = 4.dp),
                        )
                    }
                    if (query.isNotEmpty()) {
                        PillIconButton(
                            onClick = { onQueryChange("") },
                            contentDescription = stringResource(R.string.search_clear),
                            icon = Icons.Default.Close,
                        )
                    }
                    PillIconButton(
                        onClick = onPrev,
                        enabled = hits > 0,
                        contentDescription = stringResource(R.string.search_prev),
                        icon = Icons.Default.KeyboardArrowUp,
                    )
                    PillIconButton(
                        onClick = onNext,
                        enabled = hits > 0,
                        contentDescription = stringResource(R.string.search_next),
                        icon = Icons.Default.KeyboardArrowDown,
                    )
                }
            }
            IconButton(onClick = { onCaseSensitiveChange(!caseSensitive) }) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = stringResource(R.string.search_case_sensitive),
                    tint = if (caseSensitive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        }
    }
}

/**
 * Icon button sized to the pill it sits in. [IconButton] enforces a 48dp
 * minimum touch target, which no longer fits once three of them share a 48dp
 * tall bar with the query.
 */
@Composable
private fun PillIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    enabled: Boolean = true,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}

@Composable
internal fun JumpFabs(
    listState: LazyListState,
    entries: List<UiLogEntry>,
    followTail: Boolean,
    selecting: Boolean,
    onJumpTop: () -> Unit,
    onJumpBottom: () -> Unit,
) {
    val canScrollBack by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        }
    }
    val hasEntries = entries.isNotEmpty()
    Column(
        // Clear of the progress rail hugging the right edge, and of the
        // line-count caption that sits at its foot.
        modifier = Modifier.tourTarget("fab").padding(end = 26.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = canScrollBack && hasEntries && !selecting,
            enter = scaleIn(),
            exit = scaleOut(),
        ) {
            FloatingActionButton(
                onClick = onJumpTop,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.scroll_top),
                )
            }
        }
        AnimatedVisibility(
            visible = !followTail && hasEntries && !selecting,
            enter = scaleIn(),
            exit = scaleOut(),
        ) {
            FloatingActionButton(
                onClick = onJumpBottom,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.scroll_bottom),
                )
            }
        }
    }
}

/** Floating action bar shown while a range selection is active. */
@Composable
internal fun SelectionBar(
    visible: Boolean,
    count: Int,
    onSelectAll: () -> Unit,
    onExtendedCopy: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 18.dp, end = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.selected_count, count),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = onSelectAll) {
                    Icon(
                        Icons.Default.SelectAll,
                        contentDescription = stringResource(R.string.select_all),
                    )
                }
                IconButton(onClick = onExtendedCopy) {
                    Icon(
                        Icons.Default.TextFields,
                        contentDescription = stringResource(R.string.extended_copy),
                    )
                }
                TextButton(onClick = onSave) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = stringResource(R.string.selection_save),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.selection_save))
                }
                TextButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.copy))
                }
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }
        }
    }
}

/** Slim banner shown while the stream is paused. */
@Composable
internal fun PausedBanner(incoming: Int, onResume: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Pause,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.paused_banner, incoming),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onResume) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.paused_resume))
            }
        }
    }
}

/** Bottom sheet shown right after a recording stops — one-tap share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingSavedSheet(
    session: SessionEntity,
    backfill: Long,
    isSnapshot: Boolean = false,
    onShare: (SessionExporter.Format) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    if (isSnapshot) R.string.snapshot_saved else R.string.recording_saved,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                if (backfill > 0) {
                    stringResource(
                        R.string.recording_saved_lines_context,
                        session.lineCount,
                        backfill,
                    )
                } else {
                    stringResource(R.string.recording_saved_lines, session.lineCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onShare(SessionExporter.Format.ZIP) },
                    modifier = Modifier.weight(1f),
                ) { Text("ZIP") }
                OutlinedButton(
                    onClick = { onShare(SessionExporter.Format.TXT) },
                    modifier = Modifier.weight(1f),
                ) { Text("TXT") }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.later))
            }
        }
    }
}
