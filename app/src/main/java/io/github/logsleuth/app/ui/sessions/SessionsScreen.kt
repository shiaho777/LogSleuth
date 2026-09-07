package io.github.logsleuth.app.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.logsleuth.app.R
import io.github.logsleuth.app.core.export.SessionExporter
import io.github.logsleuth.app.data.db.SessionEntity
import io.github.logsleuth.app.ui.components.EmptyState
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onOpenSession: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsState()
    var shareTarget by remember { mutableStateOf<SessionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sessions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyState(
                text = stringResource(R.string.sessions_empty),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions.size, key = { sessions[it].id }) { i ->
                    val session = sessions[i]
                    SessionCard(
                        session = session,
                        onOpen = { onOpenSession(session.id) },
                        onShare = { shareTarget = session },
                        onDelete = { viewModel.delete(session) },
                    )
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
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(session.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    sessionSummary(session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_logs))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

private fun sessionSummary(session: SessionEntity): String {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    val start = fmt.format(Date(session.startedAt))
    val sizeKb = runCatching { java.io.File(session.filePath).length() / 1024 }.getOrDefault(0L)
    return "$start · ${session.lineCount} lines · ${sizeKb} KB"
}
