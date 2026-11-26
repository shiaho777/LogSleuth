package io.github.shiaho777.logsleuth.app.ui.automation

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.automation.AutomationRule
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onBack: () -> Unit,
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val sourceApps by viewModel.sourceApps.collectAsState()
    var editing by remember { mutableStateOf<AutomationRule?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            InstrumentTopBar(
                title = { Text(stringResource(R.string.automation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.report_back),
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
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.automation_add))
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.automation_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rules.isEmpty()) {
                EmptyState(text = stringResource(R.string.automation_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rules.size, key = { rules[it].id }) { i ->
                        val rule = rules[i]
                        Card(
                            onClick = {
                                editing = rule
                                showEditor = true
                            },
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        rule.name.ifBlank { stringResource(R.string.automation_unnamed) },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        buildString {
                                            append("→ ")
                                            append(rule.targetPackage)
                                            if (rule.query.isNotBlank()) {
                                                append("  ·  ")
                                                append(rule.query)
                                            }
                                            rule.packageName?.let {
                                                append("  ·  ")
                                                append(it)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { viewModel.setEnabled(rule, it) },
                                )
                                IconButton(onClick = { viewModel.delete(rule) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        AutomationRuleEditor(
            initial = editing,
            sourceApps = sourceApps,
            targetApps = apps,
            onSave = { rule ->
                viewModel.save(rule)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}