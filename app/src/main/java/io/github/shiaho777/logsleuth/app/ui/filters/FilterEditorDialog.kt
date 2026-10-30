package io.github.shiaho777.logsleuth.app.ui.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.ui.components.AppPickerDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterEditorDialog(
    initial: LogFilter?,
    apps: List<AppChoice>,
    onDismiss: () -> Unit,
    onSave: (LogFilter) -> Unit,
) {
    var draft by remember {
        mutableStateOf(initial ?: LogFilter.DEFAULT.copy(name = ""))
    }
    var showAppPicker by remember { mutableStateOf(false) }
    val selectedLevels = draft.effectiveLevels

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null || initial.id == 0L) {
                        R.string.filter_add
                    } else {
                        R.string.filter_edit
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.filter_preset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.filter_levels),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LogLevel.entries.forEach { level ->
                        FilterChip(
                            selected = level in selectedLevels,
                            onClick = { draft = draft.withToggledLevel(level) },
                            label = { Text(level.letter.toString()) },
                        )
                    }
                }
                AssistChip(
                    onClick = { showAppPicker = true },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = stringResource(R.string.app_filter_title),
                        )
                    },
                    label = {
                        Text(
                            draft.packageName
                                ?: stringResource(R.string.app_filter_all),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                OutlinedTextField(
                    value = draft.query,
                    onValueChange = { draft = draft.copy(query = it) },
                    label = { Text(stringResource(R.string.filter_query)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.tagQuery,
                    onValueChange = { draft = draft.copy(tagQuery = it) },
                    label = { Text(stringResource(R.string.filter_tag)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.excludeQuery,
                    onValueChange = { draft = draft.copy(excludeQuery = it) },
                    label = { Text(stringResource(R.string.filter_exclude)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.pid,
                    onValueChange = { draft = draft.copy(pid = it) },
                    label = { Text(stringResource(R.string.filter_pid)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.tid,
                    onValueChange = { draft = draft.copy(tid = it) },
                    label = { Text(stringResource(R.string.filter_tid)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.uid,
                    onValueChange = { draft = draft.copy(uid = it) },
                    label = { Text(stringResource(R.string.filter_uid)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = draft.including,
                        onCheckedChange = { draft = draft.copy(including = it) },
                    )
                    Text(
                        stringResource(
                            if (draft.including) R.string.filter_including
                            else R.string.filter_excluding,
                        ),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { draft = draft.copy(enabled = it) },
                    )
                    Text(
                        stringResource(R.string.filter_enabled),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = draft.tagExact,
                        onCheckedChange = { draft = draft.copy(tagExact = it) },
                    )
                    Text(
                        stringResource(R.string.filter_tag_exact),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = draft.useRegex,
                        onCheckedChange = { draft = draft.copy(useRegex = it) },
                    )
                    Text(
                        stringResource(R.string.filter_regex),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    stringResource(R.string.filter_empty_fields),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.name.isNotBlank(),
                onClick = {
                    onSave(
                        draft.copy(
                            name = draft.name.trim(),
                            query = draft.query.trim(),
                            tagQuery = draft.tagQuery.trim(),
                            excludeQuery = draft.excludeQuery.trim(),
                            pid = draft.pid.trim(),
                            tid = draft.tid.trim(),
                            uid = draft.uid.trim(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showAppPicker) {
        AppPickerDialog(
            apps = apps,
            current = draft.packageName,
            onSelect = { pkg ->
                draft = draft.copy(packageName = pkg)
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false },
        )
    }
}
