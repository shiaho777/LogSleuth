package io.github.shiaho777.logsleuth.app.ui.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.ui.components.SearchInputBar
import io.github.shiaho777.logsleuth.app.ui.guide.tourTarget
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentTopBar
import io.github.shiaho777.logsleuth.app.ui.theme.LocalLevelColors
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthGraphic
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthMark

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
    missedEntries: Long,
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (paused) R.string.recording_banner_paused else R.string.recording_banner,
                        lineCount,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (missedEntries > 0) {
                    Text(
                        stringResource(R.string.recording_gap_count, missedEntries),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
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

@Composable
internal fun CaptureNotice(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Small pill showing the engine state at the top-right of the list. */
@Composable
internal fun EngineStatusChip(
    state: LogcatEngine.State,
    adbHeld: Boolean,
    modifier: Modifier = Modifier,
) {
    val levels = LocalLevelColors.current
    val (label, color) = when {
        adbHeld -> stringResource(R.string.engine_adb_waiting) to levels.W
        state == LogcatEngine.State.RUNNING -> stringResource(R.string.engine_streaming) to levels.I
        state == LogcatEngine.State.STARTING -> stringResource(R.string.engine_connecting) to levels.W
        state == LogcatEngine.State.STOPPED -> stringResource(R.string.engine_stopped) to levels.V
        else -> stringResource(R.string.engine_error) to levels.E
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    .background(color),
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
    recording: Boolean,
    canRecord: Boolean,
    onStartRecording: () -> Unit,
    onPauseToggle: () -> Unit,
    onSearch: () -> Unit,
    onBubbleToggle: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onRestart: () -> Unit,
) {
    InstrumentTopBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SleuthMark(Modifier.size(36.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
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
            IconButton(onClick = onSave, modifier = Modifier.tourTarget("scopeActions")) {
                Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save_to_session))
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
                    if (!recording) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_start_recording)) },
                            enabled = canRecord,
                            leadingIcon = { Icon(Icons.Default.FiberManualRecord, contentDescription = null) },
                            onClick = {
                                more = false
                                onStartRecording()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.clear)) },
                        leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                        onClick = {
                            more = false
                            onClear()
                        },
                    )
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

@Composable
internal fun SearchTopBar(
    query: String,
    hits: Int,
    hitIndex: Int,
    pending: Boolean,
    caseSensitive: Boolean,
    onQueryChange: (String) -> Unit,
    onCaseSensitiveChange: (Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val caseLabel = stringResource(R.string.search_case_sensitive)
    val previousLabel = stringResource(R.string.search_prev)
    val nextLabel = stringResource(R.string.search_next)
    val canNavigate = !pending && hits > 0

    Surface(color = colors.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
        ) {
            SearchInputBar(query, stringResource(R.string.search_hint), onQueryChange, onClose)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconToggleButton(
                    checked = caseSensitive,
                    onCheckedChange = onCaseSensitiveChange,
                    modifier = Modifier.semantics { contentDescription = caseLabel },
                ) {
                    Icon(
                        Icons.Default.TextFields,
                        contentDescription = null,
                        tint = if (caseSensitive) colors.primary else colors.onSurfaceVariant,
                    )
                }
                Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    when {
                        query.isBlank() -> Unit
                        pending -> Text(
                            stringResource(R.string.search_pending),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        hits == 0 -> Text(
                            stringResource(R.string.search_no_hits),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        else -> {
                            val description = stringResource(R.string.search_position_accessible, hitIndex + 1, hits)
                            Text(
                                stringResource(R.string.search_position, hitIndex + 1, hits),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onPrev,
                    enabled = canNavigate,
                    modifier = Modifier.semantics { contentDescription = previousLabel },
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(
                    onClick = onNext,
                    enabled = canNavigate,
                    modifier = Modifier.semantics { contentDescription = nextLabel },
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingSavedSheet(
    session: SessionEntity,
    backfill: Long,
    isSnapshot: Boolean = false,
    onOpenSession: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SleuthGraphic(R.drawable.sleuth_report, Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (isSnapshot) R.string.snapshot_saved else R.string.recording_saved),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (backfill > 0) {
                            stringResource(R.string.recording_saved_lines_context, session.lineCount, backfill)
                        } else {
                            stringResource(R.string.recording_saved_lines, session.lineCount)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = onOpenSession, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.report_preview))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onShare(SessionExporter.Format.ZIP) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.report_share_zip)) }
                OutlinedButton(
                    onClick = { onShare(SessionExporter.Format.TXT) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.report_share_txt)) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.later))
            }
        }
    }
}
