package io.github.logsleuth.app.ui.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.logsleuth.app.R
import io.github.logsleuth.app.ui.components.EmptyState
import io.github.logsleuth.app.ui.components.FilterBar
import io.github.logsleuth.app.ui.components.LogRow
import io.github.logsleuth.app.ui.components.NoAccessState
import io.github.logsleuth.app.ui.navigation.Routes
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
                    onPauseToggle = { viewModel.setPaused(!ui.paused) },
                    onSearch = { viewModel.setSearching(true) },
                    onRecordToggle = viewModel::toggleRecording,
                    onClear = viewModel::clear,
                    onNavigate = onNavigate,
                )
            }
        },
        floatingActionButton = {
            androidx.compose.animation.AnimatedVisibility(visible = !isAtBottom && ui.entries.isNotEmpty()) {
                FloatingActionButton(onClick = {
                    scope.launch { listState.animateScrollToItem(ui.entries.lastIndex) }
                }) {
                    androidx.compose.material3.BadgedBox(badge = {
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
            }
        }
    }

    // Search-hit jumping.
    LaunchedEffect(ui.searchHitIndex) {
        val target = ui.searchHits.getOrNull(ui.searchHitIndex) ?: return@LaunchedEffect
        listState.animateScrollToItem(target)
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
        actions = {
            IconButton(onClick = onPauseToggle) {
                Icon(
                    if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = stringResource(if (paused) R.string.resume else R.string.pause),
                    tint = if (paused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_hint))
            }
            IconButton(onClick = onRecordToggle) {
                Icon(
                    if (recording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = stringResource(if (recording) R.string.record_stop else R.string.record_start),
                    tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
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
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
