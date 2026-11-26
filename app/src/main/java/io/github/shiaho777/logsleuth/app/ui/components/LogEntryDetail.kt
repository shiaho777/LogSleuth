package io.github.shiaho777.logsleuth.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentPanel
import io.github.shiaho777.logsleuth.app.ui.theme.LocalLogTextScale
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogEntryDetail(
    entry: LogcatEntry,
    onDismiss: () -> Unit,
    onMakeFilter: ((LogcatEntry) -> Unit)? = null,
) {
    val width = LocalWindowInfo.current.containerSize.width
    val wide = with(LocalDensity.current) { width.toDp() >= 600.dp }
    var raw by remember(entry) { mutableStateOf(false) }
    val content: @Composable (CloseWithMotion) -> Unit = { close ->
        val filterAction = onMakeFilter?.let { makeFilter ->
            { selected: LogcatEntry ->
                close {
                    onDismiss()
                    makeFilter(selected)
                }
            }
        }
        EntryDetailContent(entry, raw, { raw = it }, { close(onDismiss) }, filterAction)
    }
    if (!wide) {
        SpringBottomSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) { close ->
            content(close)
        }
    } else {
        SpringDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            showAtEnd = true,
        ) { close ->
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                content(close)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryDetailContent(
    entry: LogcatEntry,
    raw: Boolean,
    onFormatChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onMakeFilter: ((LogcatEntry) -> Unit)?,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val copied = stringResource(R.string.copied)
    val close = stringResource(R.string.close)
    val cache = LocalAppIconCache.current
    val appIcon = if (entry.remoteEndpoint == null) cache?.iconFor(entry.uid) else null
    val packageName = if (entry.remoteEndpoint == null) cache?.packageFor(entry.uid) else entry.remotePackage
    val appLabel = if (entry.remoteEndpoint == null) cache?.labelFor(entry.uid) else packageName
    val text = if (raw) entry.raw else entry.message
    val travel = with(LocalDensity.current) { 24.dp.roundToPx() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1

    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LevelBadge(entry.level)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.tag.ifEmpty { stringResource(R.string.entry_detail) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!appLabel.isNullOrBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (appIcon != null) {
                                Image(appIcon, contentDescription = null, modifier = Modifier.size(16.dp).clip(MaterialTheme.shapes.extraSmall))
                            }
                            Text(
                                text = appLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = close }) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                SegmentedButton(
                    selected = !raw,
                    onClick = { onFormatChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(stringResource(R.string.entry_message)) },
                )
                SegmentedButton(
                    selected = raw,
                    onClick = { onFormatChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(stringResource(R.string.entry_raw)) },
                )
            }
            AnimatedContent(
                targetState = raw,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                transitionSpec = { SleuthMotion.swap(travel * direction * if (targetState) 1 else -1) },
                label = "logEntryFormat",
            ) { showRaw ->
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (entry.displayTime.isNotBlank()) EntryMetadata(stringResource(R.string.settings_col_time), entry.displayTime)
                        EntryMetadata(stringResource(R.string.filter_pid), entry.pid.toString())
                        EntryMetadata(stringResource(R.string.filter_tid), entry.tid.toString())
                        entry.uid?.let { EntryMetadata(stringResource(R.string.filter_uid), it.toString()) }
                        if (!packageName.isNullOrBlank() && packageName != appLabel) {
                            EntryMetadata(stringResource(R.string.settings_col_package), packageName)
                        }
                    }
                    InstrumentPanel(Modifier.fillMaxWidth()) {
                        SelectionContainer(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                if (showRaw) entry.raw else entry.message,
                                style = logMessageStyle(LocalLogTextScale.current),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copyGuarded(text, clipboard, snackbar, scope, context, copied) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(if (raw) R.string.copy_raw else R.string.copy_message),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (onMakeFilter != null) {
                    TextButton(onClick = { onMakeFilter(entry) }) {
                        Icon(Icons.Default.FilterAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.filter_from_line), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp))
    }
}

@Composable
private fun EntryMetadata(label: String, value: String) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
