package io.github.shiaho777.logsleuth.app.ui.filters

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(
    onBack: () -> Unit,
    viewModel: FiltersViewModel = hiltViewModel(),
) {
    val presets by viewModel.presets.collectAsState()
    var editing by remember { mutableStateOf<LogFilter?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val importFailed = stringResource(R.string.filter_import_failed)
    val importOk = stringResource(R.string.filter_import_ok)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = viewModel.exportText()
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val count = text?.let { viewModel.importText(it) }
        scope.launch {
            snackbar.showSnackbar(
                if (count == null) importFailed else importOk.format(count),
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.filters_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.report_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
                            .format(LocalDateTime.now())
                        exportLauncher.launch("logsleuth-filters-$stamp.txt")
                    }) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = stringResource(R.string.filter_export),
                        )
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("text/plain", "text/*")) }) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = stringResource(R.string.filter_import),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.filter_add))
            }
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
                item {
                    Text(
                        stringResource(R.string.filter_stack_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                items(presets.size, key = { presets[it].id }) { i ->
                    val preset = presets[i]
                    Card(
                        onClick = { editing = preset; showEditor = true },
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Switch(
                                checked = preset.enabled,
                                onCheckedChange = { viewModel.setEnabled(preset, it) },
                            )
                            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    presetSummary(preset),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                viewModel.setIncluding(preset, !preset.including)
                            }) {
                                Text(
                                    stringResource(
                                        if (preset.including) R.string.filter_including
                                        else R.string.filter_excluding,
                                    ),
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

@Composable
private fun presetSummary(f: LogFilter): String {
    val kind = stringResource(if (f.including) R.string.filter_including else R.string.filter_excluding)
    return buildString {
        append(kind)
        append(" · ≥ ${f.minLevel.letter}")
        if (f.query.isNotBlank()) append(" · q:\"${f.query}\"")
        if (f.tagQuery.isNotBlank()) append(" · tag:\"${f.tagQuery}\"")
        if (f.excludeQuery.isNotBlank()) append(" · -\"${f.excludeQuery}\"")
        if (f.pid.isNotBlank()) append(" · pid:${f.pid}")
        if (f.tid.isNotBlank()) append(" · tid:${f.tid}")
        if (f.uid.isNotBlank()) append(" · uid:${f.uid}")
        if (f.useRegex) append(" · regex")
        f.packageName?.let { append(" · $it") }
    }
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
    var pid by remember { mutableStateOf(initial?.pid ?: "") }
    var tid by remember { mutableStateOf(initial?.tid ?: "") }
    var uid by remember { mutableStateOf(initial?.uid ?: "") }
    var useRegex by remember { mutableStateOf(initial?.useRegex ?: false) }
    var including by remember { mutableStateOf(initial?.including ?: true) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: false) }

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
                OutlinedTextField(
                    value = pid, onValueChange = { pid = it },
                    label = { Text(stringResource(R.string.filter_pid)) }, singleLine = true,
                )
                OutlinedTextField(
                    value = tid, onValueChange = { tid = it },
                    label = { Text(stringResource(R.string.filter_tid)) }, singleLine = true,
                )
                OutlinedTextField(
                    value = uid, onValueChange = { uid = it },
                    label = { Text(stringResource(R.string.filter_uid)) }, singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = including, onCheckedChange = { including = it })
                    Text(
                        stringResource(if (including) R.string.filter_including else R.string.filter_excluding),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Text(stringResource(R.string.filter_enabled), modifier = Modifier.padding(start = 8.dp))
                }
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
                            pid = pid.trim(),
                            tid = tid.trim(),
                            uid = uid.trim(),
                            useRegex = useRegex,
                            including = including,
                            enabled = enabled,
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
