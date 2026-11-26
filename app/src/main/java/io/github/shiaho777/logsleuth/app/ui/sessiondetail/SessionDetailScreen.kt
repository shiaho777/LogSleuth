package io.github.shiaho777.logsleuth.app.ui.sessiondetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.ui.components.EmptyFilterActions
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.ui.navigation.LocalSharedTransitionScope
import kotlinx.coroutines.launch
import io.github.shiaho777.logsleuth.app.ui.components.FilterBar
import io.github.shiaho777.logsleuth.app.ui.components.LogEntryDetail
import io.github.shiaho777.logsleuth.app.ui.components.LogRow
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.data.db.EXAMPLE_SESSION_KIND
import io.github.shiaho777.logsleuth.app.ui.stream.CaptureNotice
import io.github.shiaho777.logsleuth.app.ui.stream.SearchTopBar
import io.github.shiaho777.logsleuth.app.ui.crashes.CrashCard
import java.text.DateFormat
import java.util.Date
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpenFilters: () -> Unit = {},
    onOpenCrash: (Long) -> Unit = {},
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val sharedScope = LocalSharedTransitionScope.current
    val ui by viewModel.ui.collectAsState()
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showShare by remember { mutableStateOf(false) }
    var detailEntry by remember { mutableStateOf<LogcatEntry?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember(ui.session?.name) { mutableStateOf(ui.session?.name.orEmpty()) }

    val selectedHit = ui.searchHits.getOrNull(ui.searchHitIndex)
    var bookmarkedLine by remember { mutableStateOf<Long?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val jumpLabel = stringResource(R.string.bookmark_jump)
    val noBookmarkLog = stringResource(R.string.bookmark_unavailable)
    BackHandler(enabled = ui.searching) { viewModel.setSearching(false) }

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != 0 && viewModel.ui.value.searching) {
            keyboard?.hide()
            viewModel.setSearching(false)
        }
    }

    LaunchedEffect(selectedHit, pagerState.currentPage) {
        if (selectedHit != null && pagerState.currentPage == 0) {
            val index = ui.entries.binarySearchBy(selectedHit) { it.seq }
            if (index >= 0) listState.requestScrollToItem(index)
        }
    }

    Scaffold(
        topBar = {
            if (ui.searching) {
                SearchTopBar(
                    query = ui.searchQuery,
                    hits = ui.searchHits.size,
                    hitIndex = ui.searchHitIndex,
                    pending = ui.searchPending,
                    caseSensitive = ui.searchCaseSensitive,
                    onQueryChange = viewModel::setSearchQuery,
                    onCaseSensitiveChange = viewModel::setSearchCaseSensitive,
                    onPrev = { viewModel.nextSearchHit(-1) },
                    onNext = { viewModel.nextSearchHit(1) },
                    onClose = { viewModel.setSearching(false) },
                )
            } else {
                InstrumentTopBar(
                    title = {
                        Text(
                            ui.session?.name ?: stringResource(R.string.sessions_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            bookmarkedLine = null
                            viewModel.setSearching(true)
                            scope.launch { pagerState.scrollToPage(0) }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_hint))
                        }
                        IconButton(onClick = {
                            renameText = ui.session?.name.orEmpty()
                            showRename = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.session_rename))
                        }
                        IconButton(onClick = { showShare = true }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_logs))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .then(
                    if (sharedScope != null) {
                        with(sharedScope) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState("session-$sessionId"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (ui.session?.accessKind == EXAMPLE_SESSION_KIND) {
                CaptureNotice(stringResource(R.string.example_report_notice))
            }
            ui.remoteEndpoint?.let { endpoint ->
                CaptureNotice(
                    if (endpoint.isBlank()) stringResource(R.string.log_source_remote_unknown)
                    else stringResource(R.string.log_source_remote, endpoint),
                )
            }
            if (ui.missedEntries > 0) CaptureNotice(stringResource(R.string.recording_gap_count, ui.missedEntries))
            if (ui.sourceChanged) CaptureNotice(stringResource(R.string.recording_source_changed))
            SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                listOf(
                    stringResource(R.string.tab_logs, ui.entries.size),
                    stringResource(R.string.tab_crashes, ui.crashes.size),
                    stringResource(R.string.tab_bookmarks, ui.bookmarks.size),
                ).forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(label) },
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> Column {
                        FilterBar(
                            filter = ui.filter,
                            regexInvalid = ui.regexInvalid,
                            apps = ui.apps,
                            presets = ui.presets,
                            onFilterChange = viewModel::setFilter,
                            onSavePreset = viewModel::savePreset,
                            onApplyPreset = viewModel::applyPreset,
                            onDisableRule = { viewModel.setRuleEnabled(it, false) },
                            onManageFilters = onOpenFilters,
                        )
                        when {
                            ui.loading -> EmptyState(stringResource(R.string.empty_logs_waiting))
                            ui.missingFile -> EmptyState(stringResource(R.string.session_file_missing))
                            ui.entries.isEmpty() -> EmptyState(
                                text = stringResource(
                                    if (ui.filteredOut) R.string.empty_logs_filtered
                                    else R.string.empty_logs,
                                ),
                                action = if (ui.filteredOut && (ui.filter.hasConstraint || ui.presets.any { it.enabled })) {
                                    {
                                        EmptyFilterActions(
                                            hasCurrentFilter = ui.filter.hasConstraint,
                                            hasEnabledRules = ui.presets.any { it.enabled },
                                            onResetFilter = { viewModel.setFilter(LogFilter.DEFAULT) },
                                            onDisableRules = viewModel::disableEnabledRules,
                                        )
                                    }
                                } else null,
                            )
                            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(
                                    count = ui.entries.size,
                                    key = { index -> ui.entries[index].seq },
                                    contentType = { "log" },
                                ) { index ->
                                    LogRow(
                                        entry = ui.entries[index].entry,
                                        onOpenDetail = { detailEntry = it },
                                        snackbar = snackbar,
                                        highlight = ui.searchQuery.takeIf { it.isNotBlank() },
                                        isCurrentHit = ui.entries[index].seq == (selectedHit ?: bookmarkedLine),
                                    )
                                }
                            }
                        }
                    }

                    1 -> LazyColumn(Modifier.fillMaxSize()) {
                        items(ui.crashes.size, key = { ui.crashes[it].id }) { i ->
                            CrashCard(
                                ui.crashes[i],
                                onOpen = { onOpenCrash(ui.crashes[i].id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (ui.crashes.isEmpty()) {
                            item { EmptyState(stringResource(R.string.crashes_empty), graphic = R.drawable.sleuth_crash) }
                        }
                    }

                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(ui.bookmarks.size, key = { ui.bookmarks[it].id }) { i ->
                            val bookmark = ui.bookmarks[i]
                            ListItem(
                                headlineContent = {
                                    Text(
                                        DateFormat.getTimeInstance(DateFormat.MEDIUM)
                                            .format(Date(bookmark.time)) +
                                            if (bookmark.note.isNotEmpty()) "  ·  ${bookmark.note}" else "",
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Bookmark,
                                        contentDescription = stringResource(R.string.bookmark),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                },
                                modifier = Modifier.animateItem().clickable(onClickLabel = jumpLabel) {
                                    scope.launch {
                                        val seq = viewModel.bookmarkLine(bookmark)
                                        val entries = viewModel.ui.value.entries
                                        val index = seq?.let { entries.binarySearchBy(it) { row -> row.seq } } ?: -1
                                        if (index < 0) {
                                            snackbar.showSnackbar(noBookmarkLog)
                                        } else {
                                            viewModel.setSearching(false)
                                            bookmarkedLine = seq
                                            listState.requestScrollToItem(index)
                                            pagerState.scrollToPage(0)
                                        }
                                    }
                                },
                            )
                        }
                        if (ui.bookmarks.isEmpty()) {
                            item { EmptyState(stringResource(R.string.bookmarks_empty), graphic = R.drawable.sleuth_archive) }
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.session_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.session_rename_hint)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(renameText)
                    showRename = false
                }) { Text(stringResource(R.string.session_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    val shareFailedMsg = stringResource(R.string.share_failed)
    val saveFailedMsg = stringResource(R.string.save_failed)
    detailEntry?.let { entry ->
        LogEntryDetail(entry = entry, onDismiss = { detailEntry = null })
    }

    val onShareResult: (Result<Unit>) -> Unit = { result ->
        result.onFailure { scope.launch { snackbar.showSnackbar(shareFailedMsg) } }
    }
    val saveZipLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            viewModel.saveToUri(SessionExporter.Format.ZIP, uri) { result ->
                result.onFailure { scope.launch { snackbar.showSnackbar(saveFailedMsg) } }
            }
        }
        showShare = false
    }

    if (showShare) {
        AlertDialog(
            onDismissRequest = { showShare = false },
            title = { Text(stringResource(R.string.share_logs)) },
            text = { Text(stringResource(R.string.export_format_prompt)) },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        viewModel.share(SessionExporter.Format.ZIP, onShareResult)
                        showShare = false
                    }) { Text("ZIP") }
                    TextButton(onClick = {
                        saveZipLauncher.launch("${ui.session?.name ?: "session"}.zip")
                    }) { Text(stringResource(R.string.save_to)) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.share(SessionExporter.Format.TXT, onShareResult)
                    showShare = false
                }) { Text("TXT") }
            },
        )
    }
}
