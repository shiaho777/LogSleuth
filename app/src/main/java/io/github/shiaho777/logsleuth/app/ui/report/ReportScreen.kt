package io.github.shiaho777.logsleuth.app.ui.report

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.ui.components.LocalAppIconCache
import io.github.shiaho777.logsleuth.app.ui.guide.LocalTourController
import io.github.shiaho777.logsleuth.app.ui.guide.tourTarget
import kotlinx.coroutines.launch
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentPanel
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentTopBar
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthGraphic

/**
 * Guided 3-step bug-report flow: pick app → reproduce while recording →
 * share. Crashes in the selected app stop the recording automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenStream: () -> Unit,
    onPreviewSession: (Long) -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val shareFailedMsg = stringResource(R.string.share_failed)
    val launchFailedMsg = stringResource(R.string.report_open_target_failed)

    // Tour hook: the overlay picks the first app and steps the wizard
    // forward so the user sees the reproduce stage, then backs out.
    val tour = LocalTourController.current
    DisposableEffect(Unit) {
        var steppedByTour = false
        tour.actions["reportDemo"] = {
            val s = viewModel.ui.value
            if (s.step == 1 && s.selectedApp == null && s.apps.isNotEmpty()) {
                viewModel.selectApp(s.apps.first())
                viewModel.nextStep()
                steppedByTour = true
            }
        }
        tour.actions["reportUndemo"] = {
            if (steppedByTour) viewModel.prevStep()
        }
        tour.actions["leave.report"] = {
            if (steppedByTour) viewModel.prevStep()
        }
        onDispose {
            listOf("reportDemo", "reportUndemo", "leave.report")
                .forEach { tour.actions.remove(it) }
        }
    }

    Scaffold(
        topBar = {
            InstrumentTopBar(title = { Text(stringResource(R.string.nav_report)) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .tourTarget("reportContent")
                .padding(horizontal = 16.dp),
        ) {
            StepHeader(step = ui.step)
            when (ui.step) {
                1 -> StepPickApp(
                    ui = ui,
                    onSelect = viewModel::selectApp,
                    onNext = viewModel::nextStep,
                    onOpenSetup = onOpenSetup,
                )

                2 -> StepReproduce(
                    ui = ui,
                    onBack = viewModel::prevStep,
                    onStart = viewModel::startRecording,
                    onStopShare = viewModel::stopRecording,
                    onOpenSetup = onOpenSetup,
                    onOpenStream = onOpenStream,
                    onOpenTarget = {
                        if (!viewModel.openTarget()) scope.launch { snackbar.showSnackbar(launchFailedMsg) }
                    },
                )

                else -> ui.finishedSession?.let { session ->
                    StepShare(
                        lineCount = session.lineCount,
                        crashApp = ui.crashCaught,
                        exporting = ui.exporting,
                        onPreview = { onPreviewSession(session.id) },
                        onShare = { format ->
                            viewModel.share(format) { result ->
                                result.onFailure { scope.launch { snackbar.showSnackbar(shareFailedMsg) } }
                            }
                        },
                        onKeep = {
                            viewModel.finishReport()
                            onOpenSessions()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHeader(step: Int) {
    val description = stringResource(R.string.step_counter, step)
    val labels = listOf(R.string.report_phase_app, R.string.report_phase_record, R.string.report_phase_report)
    val icons = listOf(Icons.Default.Apps, Icons.Default.FiberManualRecord, Icons.Outlined.Assignment)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 0f..3f, 2)
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val current = index + 1 == step
            val complete = index + 1 < step
            val tint = if (current || complete) colors.primary else colors.onSurfaceVariant
            Column(
                modifier = Modifier.weight(1f)
                    .background(if (current) colors.primaryContainer else colors.surface, MaterialTheme.shapes.medium)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    if (complete) Icons.Default.CheckCircle else icons[index],
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
                Text(stringResource(label), color = tint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StepPickApp(
    ui: ReportUiState,
    onSelect: (AppChoice) -> Unit,
    onNext: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(ui.apps, query) {
        val term = query.trim()
        if (term.isEmpty()) ui.apps
        else ui.apps.filter { it.label.contains(term, true) || it.packageName.contains(term, true) }
    }

    Text(
        stringResource(R.string.report_step1_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text(stringResource(R.string.report_search_apps)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    LazyColumn(Modifier.weight(1f).padding(top = 8.dp).selectableGroup()) {
        if (!ui.hasAccess) {
            item {
                ReportNotice(
                    text = stringResource(R.string.report_access_required),
                    action = stringResource(R.string.no_access_action),
                    onAction = onOpenSetup,
                )
            }
        }
        if (ui.appsLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        } else if (filtered.isEmpty()) {
            item {
                Text(
                    stringResource(if (query.isBlank()) R.string.report_no_apps else R.string.report_no_matches),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(filtered.size, key = { filtered[it].packageName }) { i ->
            val app = filtered[i]
            val selected = app.packageName == ui.selectedApp?.packageName
            ListItem(
                headlineContent = {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    ReportAppIcon(app, local = ui.sourceEndpoint == null)
                },
                trailingContent = if (selected) {
                    { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else null,
                colors = ListItemDefaults.colors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(app) })
                    .animateItem(),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.End) {
        Button(onClick = onNext, enabled = ui.selectedApp != null && !ui.appsLoading) {
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
    onOpenSetup: () -> Unit,
    onOpenStream: () -> Unit,
    onOpenTarget: () -> Unit,
) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.report_step2_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))

        InstrumentPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ui.selectedApp?.let { ReportAppIcon(it, local = ui.sourceEndpoint == null) }
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

        ui.crashCaught?.let { app ->
            ReportCrashLabel(app, Modifier.padding(vertical = 10.dp))
        }
    }

    if (ui.failed) {
        Text(
            stringResource(if (ui.isRecording) R.string.report_stop_failed else R.string.report_recording_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
    when {
        ui.isStarting || ui.isFinishing -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (ui.isStarting) R.string.report_starting else R.string.report_finishing))
            }
        }

        ui.isRecording -> {
            Text(
                stringResource(R.string.report_recording_active, ui.recordedLines),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )
            if (ui.sourceEndpoint == null) {
                OutlinedButton(onClick = onOpenTarget, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.report_open_target))
                }
            } else {
                Text(
                    stringResource(R.string.report_reproduce_on_device),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(onClick = onStopShare, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.report_stop_share))
            }
        }

        ui.otherRecording -> ReportNotice(
            text = stringResource(R.string.report_other_recording),
            action = stringResource(R.string.report_open_recording),
            onAction = onOpenStream,
        )

        !ui.hasAccess -> ReportNotice(
            text = stringResource(R.string.report_access_required),
            action = stringResource(R.string.no_access_action),
            onAction = onOpenSetup,
        )

        else -> Button(onClick = onStart, enabled = !ui.appsLoading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FiberManualRecord, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.report_start_recording))
        }
    }
    TextButton(
        onClick = onBack,
        enabled = !ui.isStarting && !ui.isRecording && !ui.isFinishing,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Default.ChevronLeft, contentDescription = null)
        Text(stringResource(R.string.report_back))
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun StepShare(
    lineCount: Long,
    crashApp: String?,
    exporting: Boolean,
    onPreview: () -> Unit,
    onShare: (SessionExporter.Format) -> Unit,
    onKeep: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SleuthGraphic(R.drawable.sleuth_report, Modifier.size(56.dp))
            Column(
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.report_captured_lines, lineCount),
                    style = MaterialTheme.typography.titleMedium,
                )
                crashApp?.let { ReportCrashLabel(it) }
            }
        }

        Button(
            onClick = onPreview,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.report_preview))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onShare(SessionExporter.Format.ZIP) },
                enabled = !exporting,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.report_share_zip))
            }
            OutlinedButton(
                onClick = { onShare(SessionExporter.Format.TXT) },
                enabled = !exporting,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.Subject, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.report_share_txt))
            }
        }
        if (exporting) {
            Text(
                stringResource(R.string.report_exporting),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        TextButton(onClick = onKeep, enabled = !exporting, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.report_keep))
        }
    }
}


@Composable
private fun ReportCrashLabel(app: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(R.string.report_crash_caught, app),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReportNotice(text: String, action: String, onAction: () -> Unit) {
    InstrumentPanel(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun ReportAppIcon(app: AppChoice, local: Boolean) {
    val bitmap = if (local) LocalAppIconCache.current?.iconForPackage(app.packageName) else null
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(32.dp).clip(MaterialTheme.shapes.extraSmall))
    } else {
        Icon(
            Icons.Default.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp).padding(4.dp),
        )
    }
}
