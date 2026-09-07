package io.github.logsleuth.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.logsleuth.app.R
import io.github.logsleuth.app.core.filter.LogFilter
import io.github.logsleuth.app.core.logcat.LogLevel
import io.github.logsleuth.app.ui.stream.AppChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    filter: LogFilter,
    regexInvalid: Boolean,
    apps: List<AppChoice>,
    presets: List<LogFilter>,
    onFilterChange: (LogFilter) -> Unit,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (LogFilter) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            // Always-visible quick row: level, app, expand, save.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LevelDropdown(
                    current = filter.minLevel,
                    onSelect = { onFilterChange(filter.copy(minLevel = it)) },
                )
                AssistChip(
                    onClick = { showAppPicker = true },
                    leadingIcon = { Icon(Icons.Default.Apps, null) },
                    label = {
                        Text(
                            filter.packageName
                                ?: stringResource(R.string.app_filter_all),
                            maxLines = 1,
                        )
                    },
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = preset.id != 0L && preset.id == filter.id,
                            onClick = { onApplyPreset(preset) },
                            label = { Text(preset.name, maxLines = 1) },
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterTextField(
                        value = filter.query,
                        onValueChange = { onFilterChange(filter.copy(query = it)) },
                        label = stringResource(R.string.filter_query),
                        isError = regexInvalid,
                    )
                    FilterTextField(
                        value = filter.tagQuery,
                        onValueChange = { onFilterChange(filter.copy(tagQuery = it)) },
                        label = stringResource(R.string.filter_tag),
                    )
                    FilterTextField(
                        value = filter.excludeQuery,
                        onValueChange = { onFilterChange(filter.copy(excludeQuery = it)) },
                        label = stringResource(R.string.filter_exclude),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = filter.useRegex,
                            onCheckedChange = { onFilterChange(filter.copy(useRegex = it)) },
                        )
                        Text(
                            stringResource(R.string.filter_regex),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Text(stringResource(R.string.filter_save_preset))
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onSavePreset(name)
                showSaveDialog = false
            },
        )
    }
    if (showAppPicker) {
        AppPickerDialog(
            apps = apps,
            current = filter.packageName,
            onSelect = { pkg ->
                onFilterChange(filter.copy(packageName = pkg))
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelDropdown(current: LogLevel, onSelect: (LogLevel) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        FilterChip(
            selected = current != LogLevel.V,
            onClick = { open = true },
            label = { Text("≥ ${current.letter}") },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LogLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text("${level.letter} — ${levelName(level)}") },
                    onClick = {
                        onSelect(level)
                        open = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun levelName(level: LogLevel): String = when (level) {
    LogLevel.V -> stringResource(R.string.level_verbose)
    LogLevel.D -> stringResource(R.string.level_debug)
    LogLevel.I -> stringResource(R.string.level_info)
    LogLevel.W -> stringResource(R.string.level_warn)
    LogLevel.E -> stringResource(R.string.level_error)
    LogLevel.F -> stringResource(R.string.level_fatal)
}

@Composable
private fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_save_preset)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.filter_preset_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun AppPickerDialog(
    apps: List<AppChoice>,
    current: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val shown = remember(apps, search) {
        if (search.isBlank()) apps
        else apps.filter {
            it.label.contains(search, true) || it.packageName.contains(search, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_filter_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        TextButton(onClick = { onSelect(null) }) {
                            Text(
                                stringResource(R.string.app_filter_all),
                                color = if (current == null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                    items(shown.size) { i ->
                        val app = shown[i]
                        TextButton(onClick = { onSelect(app.packageName) }) {
                            Text(
                                "${app.label}  ·  ${app.packageName}",
                                color = if (current == app.packageName) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
