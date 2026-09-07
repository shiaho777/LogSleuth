package io.github.shiaho777.logsleuth.app.ui.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(
    onBack: () -> Unit,
    viewModel: FiltersViewModel = hiltViewModel(),
) {
    val presets by viewModel.presets.collectAsState()
    var editing by remember { mutableStateOf<LogFilter?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.filters_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) { Icon(Icons.Default.Add, contentDescription = null) }
        },
    ) { padding ->
        if (presets.isEmpty()) {
            EmptyState(
                text = stringResource(R.string.filters_empty),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(presets.size, key = { presets[it].id }) { i ->
                    val preset = presets[i]
                    Card(
                        onClick = { editing = preset; showEditor = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    presetSummary(preset),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.delete(preset) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        FilterEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { filter ->
                viewModel.save(filter)
                showEditor = false
            },
        )
    }
}

private fun presetSummary(f: LogFilter): String = buildString {
    append("≥ ${f.minLevel.letter}")
    if (f.query.isNotBlank()) append(" · q:\"${f.query}\"")
    if (f.tagQuery.isNotBlank()) append(" · tag:\"${f.tagQuery}\"")
    if (f.excludeQuery.isNotBlank()) append(" · -\"${f.excludeQuery}\"")
    if (f.useRegex) append(" · regex")
    f.packageName?.let { append(" · $it") }
}

@Composable
private fun FilterEditorDialog(
    initial: LogFilter?,
    onDismiss: () -> Unit,
    onSave: (LogFilter) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var query by remember { mutableStateOf(initial?.query ?: "") }
    var tag by remember { mutableStateOf(initial?.tagQuery ?: "") }
    var exclude by remember { mutableStateOf(initial?.excludeQuery ?: "") }
    var useRegex by remember { mutableStateOf(initial?.useRegex ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.filter_add else R.string.filter_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.filter_preset_name)) }, singleLine = true,
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text(stringResource(R.string.filter_query)) }, singleLine = true,
                )
                OutlinedTextField(
                    value = tag, onValueChange = { tag = it },
                    label = { Text(stringResource(R.string.filter_tag)) }, singleLine = true,
                )
                OutlinedTextField(
                    value = exclude, onValueChange = { exclude = it },
                    label = { Text(stringResource(R.string.filter_exclude)) }, singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useRegex, onCheckedChange = { useRegex = it })
                    Text(stringResource(R.string.filter_regex), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: LogFilter.DEFAULT).copy(
                            name = name.trim(),
                            query = query.trim(),
                            tagQuery = tag.trim(),
                            excludeQuery = exclude.trim(),
                            useRegex = useRegex,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
