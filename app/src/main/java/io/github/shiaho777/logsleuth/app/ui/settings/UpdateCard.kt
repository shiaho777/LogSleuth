package io.github.shiaho777.logsleuth.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.update.DlStatus
import io.github.shiaho777.logsleuth.app.core.update.ReleaseInfo
import io.github.shiaho777.logsleuth.app.core.update.UpdateUiState
import java.util.Locale

/** GitHub link + in-place update checker/downloader, lives at Settings bottom. */
@Composable
fun UpdateCard(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: (ReleaseInfo) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenRepo: () -> Unit,
    onOpenRelease: (ReleaseInfo) -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row — GitHub identity, right side is the check button.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRepo),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_github),
                    contentDescription = "GitHub",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.update_card_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.update_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CheckButton(state.checking, onCheck)
            }

            // ---- check result line ----
            state.checkError?.let { err ->
                Text(
                    stringResource(R.string.update_failed, err),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!state.checking && state.checkError == null && state.latest != null) {
                if (state.updateAvailable) {
                    UpdateAvailableBody(
                        state.latest!!, state, onDownload,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.update_up_to_date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // One shared download block — covers update downloads, history-row
            // downloads, and transfers restored after a restart.
            state.dlRelease?.takeIf { state.dlStatus != DlStatus.NONE }?.let { r ->
                DownloadBlock(r, state, onPause, onResume, onCancel, onInstall)
            }

            // ---- version history disclosure ----
            if (state.releases.isNotEmpty()) {
                HorizontalDivider()
                VersionHistory(state.releases, state.dlRelease?.tag, onDownload, onOpenRelease)
            }
        }
    }
}

@Composable
private fun CheckButton(checking: Boolean, onCheck: () -> Unit) {
    if (checking) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.update_checking),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        TextButton(onClick = onCheck) { Text(stringResource(R.string.update_check)) }
    }
}

/** Update-available panel: release meta, notes, and the download trigger. */
@Composable
private fun UpdateAvailableBody(
    release: ReleaseInfo,
    state: UpdateUiState,
    onDownload: (ReleaseInfo) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.update_available, release.tag),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        release.apkName?.let {
            Text(
                "$it · ${formatBytes(release.apkSize)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (release.notes.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Text(
                release.notes.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }

        val transferring = state.dlRelease?.tag == release.tag &&
            state.dlStatus != DlStatus.NONE && state.dlStatus != DlStatus.FAILED
        if (!transferring && release.apkUrl != null) {
            Button(onClick = { onDownload(release) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.update_download))
            }
        } else {
            Text(
                stringResource(R.string.update_no_apk),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Progress bar + speed + pause/resume/cancel/install controls. */
@Composable
private fun DownloadBlock(
    release: ReleaseInfo,
    state: UpdateUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
) {
    val progress = if (state.dlTotalBytes > 0) {
        state.dlDoneBytes.toFloat() / state.dlTotalBytes
    } else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(
                R.string.update_progress,
                formatBytes(state.dlDoneBytes),
                formatBytes(state.dlTotalBytes),
                if (state.dlStatus == DlStatus.RUNNING) {
                    "${formatBytes(state.dlBps)}/s"
                } else {
                    statusLabel(state.dlStatus)
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.dlError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.dlStatus) {
                DlStatus.RUNNING -> {
                    OutlinedButton(onClick = onPause) {
                        Text(stringResource(R.string.update_pause))
                    }
                }
                DlStatus.PAUSED, DlStatus.FAILED -> {
                    OutlinedButton(onClick = onResume) {
                        Text(stringResource(R.string.update_resume))
                    }
                }
                DlStatus.DONE -> {
                    Button(onClick = onInstall) {
                        Text(stringResource(R.string.update_install, release.tag))
                    }
                }
                DlStatus.NONE -> {}
            }
            TextButton(onClick = onCancel) {
                Text(
                    stringResource(R.string.update_cancel),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun VersionHistory(
    releases: List<ReleaseInfo>,
    downloadingTag: String?,
    onDownload: (ReleaseInfo) -> Unit,
    onOpenRelease: (ReleaseInfo) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // Header + disclosure live in one Column so they count as a single child
    // of the card's spacedBy parent — otherwise the parent's 10dp gap lands
    // between header and content and snaps shut at the end of the collapse.
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.update_history),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                releases.forEach { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRelease(r) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.tag, style = MaterialTheme.typography.bodyLarge)
                            if (r.publishedAt.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    r.publishedAt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (r.apkName != null) {
                            Text(
                                "${r.apkName} · ${formatBytes(r.apkSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (r.apkUrl != null && r.tag != downloadingTag) {
                        IconButton(onClick = { onDownload(r) }) {
                            Icon(
                                Icons.Filled.Download, null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: DlStatus): String = stringResource(
    when (status) {
        DlStatus.PAUSED -> R.string.update_status_paused
        DlStatus.DONE -> R.string.update_status_done
        DlStatus.FAILED -> R.string.update_status_failed
        else -> R.string.update_status_paused
    },
)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
