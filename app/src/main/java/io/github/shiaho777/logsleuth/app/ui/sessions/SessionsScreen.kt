package io.github.shiaho777.logsleuth.app.ui.sessions

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.BorderStroke
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.ui.components.SpringAlertDialog
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.core.importer.LogImporter
import io.github.shiaho777.logsleuth.app.data.db.EXAMPLE_SESSION_KIND
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.ui.components.SearchInputBar
import io.github.shiaho777.logsleuth.app.ui.navigation.LocalSharedTransitionScope
import io.github.shiaho777.logsleuth.app.ui.guide.tourTarget
import kotlinx.coroutines.launch
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentTopBar
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthGraphic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenSession: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsState()
    val recording by viewModel.recording.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()
    val deleteFailed by viewModel.deleteFailed.collectAsState()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(SessionCategory.ALL) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun closeSearch() {
        keyboard?.hide()
        searchOpen = false
        searchQuery = ""
    }
    BackHandler(enabled = searchOpen, onBack = ::closeSearch)
    val categories = remember(sessions, category) {
        val available = sessions.mapTo(mutableSetOf()) { sessionCategory(it.session) }
        if (category != SessionCategory.ALL) available.add(category)
        SessionCategory.entries.filter { it == SessionCategory.ALL || it in available }
    }
    val term = searchQuery.trim()
    val restricted = term.isNotEmpty() || category != SessionCategory.ALL
    val shown = remember(sessions, pendingDelete?.id, term, category) {
        sessions.filter { row ->
            val session = row.session
            session.id != pendingDelete?.id &&
                (category == SessionCategory.ALL || sessionCategory(session) == category) &&
                (term.isEmpty() || session.name.contains(term, ignoreCase = true) ||
                    session.filterPackage?.contains(term, ignoreCase = true) == true)
        }
    }
    var shareTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var renameTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val importFailed = stringResource(R.string.import_failed)
    val deletedMessage = stringResource(R.string.session_deleted)
    val deleteFailedMessage = stringResource(R.string.session_delete_failed)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(pendingDelete) {
        if (pendingDelete == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(deletedMessage, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
    }

    LaunchedEffect(deleteFailed) {
        if (deleteFailed > 0) snackbar.showSnackbar(deleteFailedMessage)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.import(uri) { result ->
                scope.launch {
                    result.onSuccess { onOpenSession(it) }
                        .onFailure { snackbar.showSnackbar(importFailed) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (searchOpen) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    SearchInputBar(
                        query = searchQuery,
                        label = stringResource(R.string.sessions_search),
                        onQueryChange = { searchQuery = it },
                        onClose = ::closeSearch,
                        modifier = Modifier.statusBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                    )
                }
            } else {
                InstrumentTopBar(
                    title = { Text(stringResource(R.string.sessions_title)) },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.sessions_search))
                        }
                        IconButton(onClick = { importLauncher.launch(LogImporter.documentTypes()) }) {
                            Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.import_logs))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .tourTarget("sessionsContent"),
        ) {
            if (categories.size > 2 || category != SessionCategory.ALL) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories.size, key = { categories[it].name }) { index ->
                        val choice = categories[index]
                        FilterChip(
                            selected = category == choice,
                            onClick = { category = choice },
                            label = { Text(stringResource(choice.label)) },
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                EmptyState(
                    text = stringResource(if (restricted) R.string.sessions_no_matches else R.string.sessions_empty),
                    graphic = if (restricted) R.drawable.sleuth_search else R.drawable.sleuth_archive,
                    action = if (restricted) {
                        {
                            TextButton(onClick = {
                                searchQuery = ""
                                category = SessionCategory.ALL
                            }) { Text(stringResource(R.string.clear)) }
                        }
                    } else {
                        {
                            Button(onClick = { importLauncher.launch(LogImporter.documentTypes()) }) {
                                Text(stringResource(R.string.welcome_import))
                            }
                        }
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown.size, key = { shown[it].session.id }) { i ->
                        val row = shown[i]
                        val session = row.session
                        val isLive = recording.isRecording && recording.sessionId == session.id
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { it == SwipeToDismissBoxValue.EndToStart && !isLive },
                        )
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            LaunchedEffect(session.id) {
                                viewModel.requestDelete(session)
                                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                            }
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = !isLive,
                            backgroundContent = {
                                Box(
                                    Modifier.fillMaxSize().padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier.animateItem(),
                        ) {
                            SessionCard(
                                session = session,
                                sizeBytes = row.sizeBytes,
                                liveLines = if (isLive) recording.lineCount else null,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onOpen = {
                                    keyboard?.hide()
                                    onOpenSession(session.id)
                                },
                                onShare = { shareTarget = session },
                                onRename = {
                                    renameTarget = session
                                    renameText = session.name
                                },
                                onDelete = { viewModel.requestDelete(session) },
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { session ->
        SpringAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.session_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.session_rename_hint)) },
                )
            },
            confirmButton = { close ->
                TextButton(onClick = {
                    val name = renameText
                    close {
                        viewModel.rename(session, name)
                        renameTarget = null
                    }
                }) { Text(stringResource(R.string.session_rename)) }
            },
            dismissButton = { close ->
                TextButton(onClick = { close { renameTarget = null } }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    val shareFailedMsg = stringResource(R.string.share_failed)
    val saveFailedMsg = stringResource(R.string.save_failed)
    val onShareResult: (Result<Unit>) -> Unit = { result ->
        result.onFailure { scope.launch { snackbar.showSnackbar(shareFailedMsg) } }
    }
    var exportSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    val saveZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val sessionId = exportSessionId ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            viewModel.saveToUri(sessionId, SessionExporter.Format.ZIP, uri) { result ->
                result.onFailure { scope.launch { snackbar.showSnackbar(saveFailedMsg) } }
            }
        }
        exportSessionId = null
    }

    shareTarget?.let { session ->
        SpringAlertDialog(
            onDismissRequest = { shareTarget = null },
            title = { Text(stringResource(R.string.share_logs)) },
            text = { Text(stringResource(R.string.export_format_prompt)) },
            confirmButton = { close ->
                Column {
                    TextButton(onClick = {
                        close {
                            shareTarget = null
                            viewModel.share(session, SessionExporter.Format.ZIP, onShareResult)
                        }
                    }) { Text("ZIP") }
                    TextButton(onClick = {
                        close {
                            exportSessionId = session.id
                            shareTarget = null
                            saveZipLauncher.launch("${session.name}.zip")
                        }
                    }) { Text(stringResource(R.string.save_to)) }
                }
            },
            dismissButton = { close ->
                TextButton(onClick = {
                    close {
                        shareTarget = null
                        viewModel.share(session, SessionExporter.Format.TXT, onShareResult)
                    }
                }) { Text("TXT") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(
    session: SessionEntity,
    sizeBytes: Long,
    liveLines: Long?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sharedScope = LocalSharedTransitionScope.current
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val category = sessionCategory(session)
    val date = remember(context, session.startedAt) {
        DateUtils.formatDateTime(
            context,
            session.startedAt,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
    val size = remember(context, sizeBytes) { Formatter.formatShortFileSize(context, sizeBytes) }
    val duration = session.endedAt?.let { end ->
        ((end - session.startedAt) / 1_000).coerceAtLeast(0).takeIf { it > 0 }
    }.takeIf { category == SessionCategory.CAPTURED && liveLines == null }
    val more = stringResource(R.string.more)
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().then(
            if (sharedScope != null) {
                with(sharedScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState("session-${session.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            } else {
                Modifier
            },
        ),
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (liveLines != null) colors.errorContainer else colors.surfaceContainerHigh,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (liveLines != null) {
                            Icon(Icons.Default.FiberManualRecord, null, Modifier.size(16.dp), tint = colors.onErrorContainer)
                        } else {
                            SleuthGraphic(
                                if (category == SessionCategory.EXAMPLE) R.drawable.sleuth_report else R.drawable.sleuth_archive,
                                Modifier.size(28.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(session.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(if (liveLines != null) R.string.session_recording_live else category.label),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (liveLines != null) colors.error else colors.primary,
                        )
                        Text(date, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = more }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_logs)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.session_rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        if (liveLines == null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SessionMetric(Icons.Default.Notes, stringResource(R.string.lines_count, liveLines ?: session.lineCount))
                if (liveLines == null || sizeBytes > 0L) SessionMetric(Icons.Default.Storage, size)
                if (duration != null) {
                    val label = if (duration >= 60L) stringResource(R.string.duration_min_sec, duration / 60L, duration % 60L)
                    else stringResource(R.string.duration_sec, duration)
                    SessionMetric(Icons.Default.Timer, label)
                }
            }
        }
    }
}

@Composable
private fun SessionMetric(icon: ImageVector, value: String) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        Text(value, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

private enum class SessionCategory(val label: Int) {
    ALL(R.string.sessions_category_all),
    CAPTURED(R.string.sessions_category_captured),
    IMPORTED(R.string.sessions_category_imported),
    EXAMPLE(R.string.sessions_category_example),
}

private fun sessionCategory(session: SessionEntity): SessionCategory = when {
    session.accessKind == EXAMPLE_SESSION_KIND -> SessionCategory.EXAMPLE
    session.imported -> SessionCategory.IMPORTED
    else -> SessionCategory.CAPTURED
}
