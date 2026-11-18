package io.github.shiaho777.logsleuth.app.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.automation.AutomationRule
import io.github.shiaho777.logsleuth.app.core.filter.CompiledFilter
import io.github.shiaho777.logsleuth.app.ui.components.AppPickerDialog

@Composable
internal fun AutomationRuleEditor(
    initial: AutomationRule?,
    apps: List<AppChoice>,
    onSave: (AutomationRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial ?: AutomationRule()) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }

    val regexBad = draft.useRegex && CompiledFilter.isInvalidRegex(draft.query)
    val canSave = draft.name.isNotBlank() && draft.targetPackage.isNotBlank() && !regexBad

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.automation_add else R.string.automation_edit,
                ),
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.automation_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.query,
                    onValueChange = { draft = draft.copy(query = it) },
                    label = { Text(stringResource(R.string.filter_query)) },
                    singleLine = true,
                    isError = regexBad,
                    supportingText = if (regexBad) {
                        { Text(stringResource(R.string.filter_regex_invalid)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.filter_regex), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = draft.useRegex,
                        onCheckedChange = { draft = draft.copy(useRegex = it) },
                    )
                }
                OutlinedTextField(
                    value = draft.tagQuery,
                    onValueChange = { draft = draft.copy(tagQuery = it) },
                    label = { Text(stringResource(R.string.filter_tag)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { showSourcePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        draft.packageName?.let { pkg ->
                            apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                        } ?: stringResource(R.string.automation_source_any),
                    )
                }
                OutlinedButton(
                    onClick = { showTargetPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (draft.targetPackage.isBlank()) {
                            stringResource(R.string.automation_target_pick)
                        } else {
                            apps.firstOrNull { it.packageName == draft.targetPackage }?.label
                                ?: draft.targetPackage
                        },
                    )
                }
                Text(
                    stringResource(R.string.automation_target_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft.action,
                    onValueChange = { draft = draft.copy(action = it) },
                    label = { Text(stringResource(R.string.automation_action)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = if (draft.cooldownSeconds == 0) "" else draft.cooldownSeconds.toString(),
                    onValueChange = { text ->
                        val seconds = text.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0
                        draft = draft.copy(cooldownSeconds = seconds)
                    },
                    label = { Text(stringResource(R.string.automation_cooldown)) },
                    supportingText = { Text(stringResource(R.string.automation_cooldown_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(draft) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showSourcePicker) {
        AppPickerDialog(
            apps = apps,
            current = draft.packageName,
            onSelect = { pkg ->
                draft = draft.copy(packageName = pkg)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false },
        )
    }
    if (showTargetPicker) {
        AppPickerDialog(
            apps = apps,
            current = draft.targetPackage.takeIf { it.isNotBlank() },
            onSelect = { pkg ->
                // "All apps" makes no sense for a broadcast target.
                if (pkg != null) draft = draft.copy(targetPackage = pkg)
                showTargetPicker = false
            },
            onDismiss = { showTargetPicker = false },
        )
    }
}