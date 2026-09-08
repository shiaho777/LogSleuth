package io.github.shiaho777.logsleuth.app.ui.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter

/**
 * Guided 3-step bug-report flow: pick app → reproduce while recording →
 * share. Crashes in the selected app stop the recording automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onOpenSessions: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_report)) })
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            StepHeader(step = ui.step)
            when (ui.step) {
                1 -> StepPickApp(
                    ui = ui,
                    onSelect = viewModel::selectApp,
                    onNext = viewModel::nextStep,
                )

                2 -> StepReproduce(
                    ui = ui,
                    onBack = viewModel::prevStep,
                    onStart = viewModel::startRecording,
                    onStopShare = viewModel::stopRecording,
                )

                else -> StepShare(
                    ui = ui,
                    onShare = viewModel::share,
                    onKeep = onOpenSessions,
                )
            }
        }
    }
}

@Composable
private fun StepHeader(step: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            stringResource(R.string.step_counter, step),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { step / 3f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StepPickApp(
    ui: ReportUiState,
    onSelect: (AppChoice) -> Unit,
    onNext: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(ui.apps, query) {
        if (query.isBlank()) ui.apps
        else ui.apps.filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }
    }

    Text(
        stringResource(R.string.report_step1_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        stringResource(R.string.report_step1_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text(stringResource(R.string.report_search_apps)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
        items(filtered.size, key = { filtered[it].packageName }) { i ->
            val app = filtered[i]
            val selected = app.packageName == ui.selectedApp?.packageName
            Surface(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable { onSelect(app) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else Icons.Default.Apps,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.End) {
        Button(onClick = onNext, enabled = ui.selectedApp != null) {
            Text(stringResource(R.string.report_next))
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StepReproduce(
    ui: ReportUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStopShare: () -> Unit,
) {
    Text(
        stringResource(R.string.report_step2_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        stringResource(R.string.report_step2_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apps, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        ui.selectedApp?.label ?: "",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        ui.selectedApp?.packageName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (ui.crashCaught != null) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.report_crash_caught, ui.crashCaught ?: ""),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    Spacer(Modifier.weight(1f))

    if (ui.isRecording) {
        Text(
            stringResource(R.string.report_recording_active, ui.recordedLines),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp),
        )
        Button(
            onClick = onStopShare,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.report_stop_share))
        }
    } else {
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FiberManualRecord, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.report_start_recording))
        }
    }
    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Icon(Icons.Default.ChevronLeft, contentDescription = null)
        Text(stringResource(R.string.report_back))
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun StepShare(
    ui: ReportUiState,
    onShare: (SessionExporter.Format) -> Unit,
    onKeep: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.report_step3_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.report_step3_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )
        Button(onClick = { onShare(SessionExporter.Format.ZIP) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("ZIP")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onShare(SessionExporter.Format.TXT) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("TXT")
        }
        TextButton(onClick = onKeep, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.report_keep))
        }
    }
}
