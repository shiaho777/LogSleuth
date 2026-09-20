package io.github.shiaho777.logsleuth.app.ui.sessions

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.ui.navigation.LocalSharedTransitionScope
import java.io.File
import kotlinx.coroutines.launch

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
    val shown = sessions.filter { it.id != pendingDelete?.id }
    var shareTarget by remember { mutableStateOf<SessionEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val importFailed = stringResource(R.string.import_failed)
    val deletedMessage = stringResource(R.string.session_deleted)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(pendingDelete) {
        if (pendingDelete == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(deletedMessage, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
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
            TopAppBar(
                title = { Text(stringResource(R.string.sessions_title)) },
                actions = {
                    IconButton(onClick = {
                        importLauncher.launch(
                            arrayOf("application/zip", "text/plain", "application/octet-stream"),
                        )
                    }) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = stringResource(R.string.import_logs),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (shown.isEmpty()) {
            EmptyState(
                text = stringResource(R.string.sessions_empty),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown.size, key = { shown[it].id }) { i ->
                    val session = shown[i]
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { it == SwipeToDismissBoxValue.EndToStart },
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
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        modifier = Modifier.animateItem(),
                    ) {
                        SessionCard(
                            session = session,
                            liveLines = recording
                                .takeIf { it.isRecording && it.sessionId == session.id }
                                ?.lineCount,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onOpen = { onOpenSession(session.id) },
                            onShare = { shareTarget = session },
                            onDelete = { viewModel.requestDelete(session) },
                        )
                    }
                }
            }
        }
    }

    shareTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { shareTarget = null },
            title = { Text(stringResource(R.string.share_logs)) },
            text = { Text(stringResource(R.string.export_format_prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.share(session, SessionExporter.Format.ZIP)
                    shareTarget = null
                }) { Text("ZIP") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.share(session, SessionExporter.Format.TXT)
                    shareTarget = null
                }) { Text("TXT") }
            },
        )
    }
}

@Composable
private fun SessionCard(
    session: SessionEntity,
    liveLines: Long?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sharedScope = LocalSharedTransitionScope.current
    Card(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .then(
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    liveLines != null -> MaterialTheme.colorScheme.errorContainer
                    session.imported -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (liveLines != null) Icons.Default.FiberManualRecord else Icons.Default.History,
                    contentDescription = null,
                    tint = when {
                        liveLines != null -> MaterialTheme.colorScheme.onErrorContainer
                        session.imported -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    session.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    sessionMeta(session, liveLines),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.share_logs),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun sessionMeta(session: SessionEntity, liveLines: Long?): String {
    val sizeKb = runCatching { File(session.filePath).length() / 1024 }.getOrDefault(0L)
    val relative = DateUtils.getRelativeTimeSpanString(
        session.startedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    // In-progress session: the DB row still says 0 — show the live count.
    if (liveLines != null) {
        return "$relative · ${stringResource(R.string.session_recording_live)}" +
            " · ${stringResource(R.string.lines_count, liveLines)} · ${sizeKb}KB"
    }
    val duration = session.endedAt?.let { end ->
        val secs = ((end - session.startedAt) / 1000).coerceAtLeast(0)
        if (secs >= 60) " · ${secs / 60}min ${secs % 60}s" else " · ${secs}s"
    } ?: ""
    return "$relative · ${stringResource(R.string.lines_count, session.lineCount)}" +
        " · ${sizeKb}KB$duration"
}
