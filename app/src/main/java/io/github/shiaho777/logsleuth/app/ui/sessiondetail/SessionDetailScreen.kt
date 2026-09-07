package io.github.shiaho777.logsleuth.app.ui.sessiondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.ui.components.FilterBar
import io.github.shiaho777.logsleuth.app.ui.components.LogRow
import io.github.shiaho777.logsleuth.app.ui.crashes.CrashCard
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val listState = rememberLazyListState()
    var tab by remember { mutableIntStateOf(0) }
    var showShare by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.session?.name ?: stringResource(R.string.sessions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showShare = true }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_logs))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SecondaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = {
                    Text(stringResource(R.string.tab_logs, ui.entries.size))
                })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                    Text(stringResource(R.string.tab_crashes, ui.crashes.size))
                })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = {
                    Text(stringResource(R.string.tab_bookmarks, ui.bookmarks.size))
                })
            }

            when (tab) {
                0 -> Column {
                    FilterBar(
                        filter = ui.filter,
                        regexInvalid = false,
                        apps = emptyList(),
                        presets = emptyList(),
                        onFilterChange = viewModel::setFilter,
                        onSavePreset = {},
                        onApplyPreset = {},
                    )
                    when {
                        ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        ui.missingFile -> EmptyState(stringResource(R.string.session_file_missing))
                        ui.entries.isEmpty() -> EmptyState(stringResource(R.string.empty_logs))
                        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(
                                count = ui.entries.size,
                                key = { index -> ui.entries[index].seq },
                            ) { index ->
                                LogRow(entry = ui.entries[index].entry)
                            }
                        }
                    }
                }

                1 -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.crashes.size) { i -> CrashCard(ui.crashes[i], onDelete = null) }
                    if (ui.crashes.isEmpty()) {
                        item { EmptyState(stringResource(R.string.crashes_empty)) }
                    }
                }

                else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.bookmarks.size) { i ->
                        val bookmark = ui.bookmarks[i]
                        Text(
                            text = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(bookmark.time)) +
                                if (bookmark.note.isNotEmpty()) "  ·  ${bookmark.note}" else "",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    if (ui.bookmarks.isEmpty()) {
                        item { EmptyState(stringResource(R.string.bookmarks_empty)) }
                    }
                }
            }
        }
    }

    if (showShare) {
        AlertDialog(
            onDismissRequest = { showShare = false },
            title = { Text(stringResource(R.string.share_logs)) },
            text = { Text(stringResource(R.string.export_format_prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.share(SessionExporter.Format.ZIP)
                    showShare = false
                }) { Text("ZIP") }
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
