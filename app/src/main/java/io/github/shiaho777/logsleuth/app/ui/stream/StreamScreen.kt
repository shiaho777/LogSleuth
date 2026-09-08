package io.github.shiaho777.logsleuth.app.ui.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.ui.components.FilterBar
import io.github.shiaho777.logsleuth.app.ui.components.LogRow
import io.github.shiaho777.logsleuth.app.ui.components.NoAccessState
import io.github.shiaho777.logsleuth.app.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    onNavigate: (String) -> Unit,
    viewModel: StreamViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }

    // Follow the tail only while the user is at the bottom.
    LaunchedEffect(ui.entries.size) {
        if (isAtBottom && ui.entries.isNotEmpty() && !ui.paused) {
            listState.scrollToItem(ui.entries.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            if (ui.searching) {
                SearchTopBar(
                    query = ui.searchQuery,
                    hits = ui.searchHits.size,
                    hitIndex = ui.searchHitIndex,
                    onQueryChange = viewModel::setSearchQuery,
                    onPrev = { viewModel.nextSearchHit(-1) },
                    onNext = { viewModel.nextSearchHit(1) },
                    onClose = { viewModel.setSearching(false) },
                )
            } else {
                StreamTopBar(
                    paused = ui.paused,
                    recording = ui.recording.isRecording,
                    onPauseToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        viewModel.setPaused(!ui.paused)
                    },
                    onSearch = { viewModel.setSearching(true) },
                    onRecordToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.toggleRecording()
                    },
                    onClear = viewModel::clear,
                    onNavigate = onNavigate,
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isAtBottom && ui.entries.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(ui.entries.lastIndex) }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    BadgedBox(badge = {
                        if (ui.pausedIncoming > 0) Badge { Text("+${ui.pausedIncoming}") }
                    }) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.scroll_bottom),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // Recording status banner — appears with a slide-down animation
            AnimatedVisibility(
                visible = ui.recording.isRecording,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                RecordingBanner(
                    lineCount = ui.recording.lineCount,
                    onStop = viewModel::toggleRecording,
                )
            }

            // Paused banner — tells the user new lines are being buffered.
            AnimatedVisibility(
                visible = ui.paused,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                PausedBanner(
                    incoming = ui.pausedIncoming,
                    onResume = { viewModel.setPaused(false) },
                )
            }

            FilterBar(
                filter = ui.filter,
                regexInvalid = ui.regexInvalid,
                apps = ui.apps,
                presets = ui.presets,
                onFilterChange = viewModel::setFilter,
                onSavePreset = viewModel::savePreset,
                onApplyPreset = viewModel::applyPreset,
            )

            Box(Modifier.fillMaxSize()) {
                when {
                    ui.access?.granted != true -> NoAccessState(
                        onOpenSetup = { onNavigate(Routes.SETUP) },
                    )

                    ui.entries.isEmpty() -> EmptyState(
                        text = stringResource(R.string.empty_logs),
                    )

                    else -> LogList(
                        ui = ui,
                        listState = listState,
                        searchQuery = ui.searchQuery,
                        currentHit = ui.searchHits.getOrNull(ui.searchHitIndex),
                    )
                }

                // Engine status chip (connecting / streaming / stopped)
                EngineStatusChip(
                    state = ui.engineState,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }
    }

    // Search-hit jumping.
    LaunchedEffect(ui.searchHitIndex) {
        val target = ui.searchHits.getOrNull(ui.searchHitIndex) ?: return@LaunchedEffect
        listState.animateScrollToItem(target)
    }

    // Recording finished → offer to share immediately.
    ui.finishedSession?.let { session ->
        RecordingSavedSheet(
            session = session,
            onShare = { viewModel.shareFinishedSession(it) },
            onDismiss = viewModel::dismissFinishedSession,
        )
    }
}

/** Slim banner shown while a recording is active. */
@Composable
private fun RecordingBanner(lineCount: Long, onStop: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.recording_banner, lineCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
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
private fun EngineStatusChip(state: LogcatEngine.State, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        LogcatEngine.State.RUNNING -> stringResource(R.string.engine_streaming) to Color(0xFF30D158)
        LogcatEngine.State.STARTING -> stringResource(R.string.engine_connecting) to Color(0xFFFF9F0A)
        LogcatEngine.State.STOPPED -> stringResource(R.string.engine_stopped) to Color(0xFF8E8E93)
        LogcatEngine.State.ERROR -> stringResource(R.string.engine_error) to Color(0xFFFF453A)
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
                    .background(color),
            )
            Spacer(Modifier.size(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamTopBar(
    paused: Boolean,
    recording: Boolean,
    onPauseToggle: () -> Unit,
    onSearch: () -> Unit,
    onRecordToggle: () -> Unit,
    onClear: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        actions = {
            IconButton(onClick = onPauseToggle) {
                Icon(
                    if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = stringResource(if (paused) R.string.resume else R.string.pause),
                    tint = if (paused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_hint),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRecordToggle) {
                Icon(
                    if (recording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = stringResource(if (recording) R.string.record_stop else R.string.record_start),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.clear)) },
                    leadingIcon = { Icon(Icons.Default.Clear, null) },
                    onClick = { menuOpen = false; onClear() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sessions_title)) },
                    onClick = { menuOpen = false; onNavigate(Routes.SESSIONS) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.crashes_title)) },
                    onClick = { menuOpen = false; onNavigate(Routes.CRASHES) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.filters_title)) },
                    onClick = { menuOpen = false; onNavigate(Routes.FILTERS) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_title)) },
                    onClick = { menuOpen = false; onNavigate(Routes.SETTINGS) },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    hits: Int,
    hitIndex: Int,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        actions = {
            Text(
                if (hits == 0) "0/0" else "${hitIndex + 1}/$hits",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onPrev, enabled = hits > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
            }
            IconButton(onClick = onNext, enabled = hits > 0) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
        },
    )
}

@Composable
private fun LogList(
    ui: StreamUiState,
    listState: LazyListState,
    searchQuery: String,
    currentHit: Int?,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(
            count = ui.entries.size,
            key = { index -> ui.entries[index].seq },
        ) { index ->
            val uiEntry = ui.entries[index]
            LogRow(
                entry = uiEntry.entry,
                highlight = searchQuery.takeIf { it.isNotBlank() },
                isCurrentHit = currentHit == index,
            )
        }
    }
}


/** Slim banner shown while the stream is paused. */
@Composable
private fun PausedBanner(incoming: Int, onResume: () -> Unit) {
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
private fun RecordingSavedSheet(
    session: io.github.shiaho777.logsleuth.app.data.db.SessionEntity,
    onShare: (io.github.shiaho777.logsleuth.app.core.export.SessionExporter.Format) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
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
                stringResource(R.string.recording_saved),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.recording_saved_lines, session.lineCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Button(
                    onClick = { onShare(io.github.shiaho777.logsleuth.app.core.export.SessionExporter.Format.ZIP) },
                    modifier = Modifier.weight(1f),
                ) { Text("ZIP") }
                androidx.compose.material3.OutlinedButton(
                    onClick = { onShare(io.github.shiaho777.logsleuth.app.core.export.SessionExporter.Format.TXT) },
                    modifier = Modifier.weight(1f),
                ) { Text("TXT") }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.later))
            }
        }
    }
}
