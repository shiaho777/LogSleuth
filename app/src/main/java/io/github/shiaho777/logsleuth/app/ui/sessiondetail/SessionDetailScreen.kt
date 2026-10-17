package io.github.shiaho777.logsleuth.app.ui.sessiondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.ui.navigation.LocalSharedTransitionScope
import kotlinx.coroutines.launch
import io.github.shiaho777.logsleuth.app.ui.components.FilterBar
import io.github.shiaho777.logsleuth.app.ui.components.LogRow
import io.github.shiaho777.logsleuth.app.ui.crashes.CrashCard
import java.text.DateFormat
import java.util.Date

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
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember(ui.session?.name) { mutableStateOf(ui.session?.name.orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.session?.name ?: stringResource(R.string.sessions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (ui.searching) {
                        TextField(
                            value = ui.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                        )
                        IconButton(onClick = { viewModel.setSearchCaseSensitive(!ui.searchCaseSensitive) }) {
                            Icon(
                                Icons.Default.TextFields,
                                contentDescription = stringResource(R.string.search_case_sensitive),
                            )
                        }
                        IconButton(onClick = { viewModel.setSearching(false) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    } else {
                    IconButton(onClick = { viewModel.setSearching(true) }) {
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
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
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
                                action = if (ui.filteredOut && ui.presets.any { it.enabled }) {
                                    {
                                        TextButton(onClick = viewModel::disableEnabledRules) {
                                            Text(stringResource(R.string.filter_rules_clear))
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(
                                    count = ui.entries.size,
                                    key = { index -> ui.entries[index].seq },
                                ) { index ->
                                    LogRow(
                                        entry = ui.entries[index].entry,
                                        modifier = Modifier.animateItem(),
                                        snackbar = snackbar,
                                        highlight = ui.searchQuery.takeIf { it.isNotBlank() },
                                        isCurrentHit = ui.searchHits.getOrNull(ui.searchHitIndex) == index,
                                    )
                                }
                            }
                        }
                    }

                    1 -> LazyColumn(Modifier.fillMaxSize()) {
                        items(ui.crashes.size, key = { ui.crashes[it].id }) { i ->
                            CrashCard(
                                ui.crashes[i],
                                onDelete = null,
                                onOpen = { onOpenCrash(ui.crashes[i].id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (ui.crashes.isEmpty()) {
                            item { EmptyState(stringResource(R.string.crashes_empty)) }
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
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (ui.bookmarks.isEmpty()) {
                            item { EmptyState(stringResource(R.string.bookmarks_empty)) }
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

    val saveZipLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.saveToUri(SessionExporter.Format.ZIP, uri)
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
                        viewModel.share(SessionExporter.Format.ZIP)
                        showShare = false
                    }) { Text("ZIP") }
                    TextButton(onClick = {
                        saveZipLauncher.launch("${ui.session?.name ?: "session"}.zip")
                    }) { Text(stringResource(R.string.save_to)) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.share(SessionExporter.Format.TXT)
                    showShare = false
                }) { Text("TXT") }
            },
        )
    }
}
