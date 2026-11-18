package io.github.shiaho777.logsleuth.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.remoteadb.RemoteAdbManager

/**
 * Configuration for reading a second device's logs over wireless ADB:
 * host:port, a connection probe (the remote screen may ask the user to
 * allow debugging), and the live status of the link.
 */
@Composable
internal fun RemoteAdbCard(
    host: String,
    port: Int,
    onSave: (String, Int) -> Unit,
    viewModel: SettingsViewModel,
) {
    val remote by viewModel.remoteState.collectAsState()
    var hostDraft by remember(host) { mutableStateOf(host) }
    var portDraft by remember(port) { mutableStateOf(port.toString()) }

    Card {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.remote_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.remote_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = hostDraft,
                    onValueChange = { hostDraft = it },
                    label = { Text(stringResource(R.string.remote_host)) },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = portDraft,
                    onValueChange = { text -> portDraft = text.filter { it.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.remote_port)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val p = portDraft.toIntOrNull() ?: 5555
                    onSave(hostDraft.trim(), p)
                }) { Text(stringResource(R.string.save)) }
                OutlinedButton(
                    onClick = viewModel::testRemoteConnection,
                    enabled = remote.status != RemoteAdbManager.Status.CONNECTING,
                ) {
                    Text(
                        stringResource(
                            if (remote.status == RemoteAdbManager.Status.CONNECTING) {
                                R.string.remote_connecting
                            } else {
                                R.string.remote_test
                            },
                        ),
                    )
                }
                if (remote.status == RemoteAdbManager.Status.READY) {
                    OutlinedButton(onClick = viewModel::disconnectRemote) {
                        Text(stringResource(R.string.remote_disconnect))
                    }
                }
            }
            val statusText = when (remote.status) {
                RemoteAdbManager.Status.IDLE -> stringResource(R.string.remote_status_idle)
                RemoteAdbManager.Status.CONNECTING -> stringResource(R.string.remote_status_connecting)
                RemoteAdbManager.Status.READY -> stringResource(R.string.remote_status_ready)
                RemoteAdbManager.Status.FAILED -> stringResource(
                    R.string.remote_status_failed,
                    remote.detail ?: "",
                )
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = when (remote.status) {
                    RemoteAdbManager.Status.READY -> MaterialTheme.colorScheme.primary
                    RemoteAdbManager.Status.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (remote.status == RemoteAdbManager.Status.CONNECTING ||
                remote.status == RemoteAdbManager.Status.FAILED
            ) {
                Text(
                    stringResource(R.string.remote_allow_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}