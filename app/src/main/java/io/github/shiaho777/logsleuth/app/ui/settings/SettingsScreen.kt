package io.github.shiaho777.logsleuth.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.BuildConfig
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.logcat.AccessKind
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import io.github.shiaho777.logsleuth.app.ui.components.LanguagePicker
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val access by viewModel.access.collectAsState()
    val context = LocalContext.current
    val s = settings ?: return

    // ADB grants have no event stream — refresh when the screen opens.
    LaunchedEffect(Unit) { viewModel.refreshAccess() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_access),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val accessText = when (access.kind) {
                        AccessKind.SHIZUKU -> stringResource(R.string.settings_access_shizuku)
                        AccessKind.READ_LOGS -> stringResource(R.string.settings_access_adb)
                        AccessKind.NONE -> stringResource(R.string.no_access_title)
                    }
                    Text(
                        accessText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (access.granted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    TextButton(onClick = onOpenSetup) {
                        Text(stringResource(R.string.settings_access_open))
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_buffer_size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    // Drag locally; persist once on release — writing the
                    // DataStore on every pointer move stalls the stream.
                    var sliderValue by remember(s.bufferSize) {
                        mutableStateOf(s.bufferSize.toFloat())
                    }
                    Text(
                        stringResource(
                            R.string.settings_buffer_size_value,
                            sliderValue.roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = (it / 1000f).roundToInt() * 1000f
                        },
                        onValueChangeFinished = {
                            viewModel.setBufferSize(sliderValue.roundToInt())
                        },
                        valueRange = 1_000f..200_000f,
                    )
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_recording_max),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    // Same drag-then-persist pattern as the buffer slider.
                    var limitValue by remember(s.recordingMaxMb) {
                        mutableStateOf(s.recordingMaxMb.toFloat())
                    }
                    Text(
                        stringResource(
                            R.string.settings_recording_max_value,
                            limitValue.roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = limitValue,
                        onValueChange = {
                            limitValue = (it / 8f).roundToInt() * 8f
                        },
                        onValueChangeFinished = {
                            viewModel.setRecordingMaxMb(limitValue.roundToInt())
                        },
                        valueRange = 8f..512f,
                    )
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_recording_hours),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    // 0 means "no time limit"; 1..24 h otherwise.
                    var hoursValue by remember(s.recordingMaxHours) {
                        mutableStateOf(s.recordingMaxHours.toFloat())
                    }
                    Text(
                        if (hoursValue.roundToInt() == 0) {
                            stringResource(R.string.settings_recording_hours_unlimited)
                        } else {
                            stringResource(
                                R.string.settings_recording_hours_value,
                                hoursValue.roundToInt(),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = hoursValue,
                        onValueChange = { hoursValue = it.roundToInt().toFloat() },
                        onValueChangeFinished = {
                            viewModel.setRecordingMaxHours(hoursValue.roundToInt())
                        },
                        valueRange = 0f..24f,
                    )
                }
            }

            Card {
                Column {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_crash_notifications),
                        checked = s.crashNotifications,
                        onCheckedChange = viewModel::setCrashNotifications,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_bubble),
                        subtitle = stringResource(R.string.settings_bubble_desc),
                        checked = s.bubbleEnabled && viewModel.canDrawOverlays(),
                        onCheckedChange = { wanted ->
                            if (!viewModel.setBubbleEnabled(wanted)) {
                                context.startActivity(viewModel.overlaySettingsIntent())
                            }
                        },
                    )
                }
            }

            Card {
                LanguagePicker(
                    current = s.language,
                    onSelect = { tag ->
                        viewModel.setLanguage(tag)
                        // The repo's DataStore write is async; applyFromUi
                        // commits the override synchronously and recreates
                        // the activity on <33.
                        AppLocales.applyFromUi(context, tag)
                    },
                )
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(
                            "system" to R.string.theme_system,
                            "light" to R.string.theme_light,
                            "dark" to R.string.theme_dark,
                        ).forEachIndexed { index, (value, labelRes) ->
                            SegmentedButton(
                                selected = s.theme == value,
                                onClick = { viewModel.setTheme(value) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                            ) { Text(stringResource(labelRes)) }
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_log_text_size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(
                            0 to R.string.text_size_compact,
                            1 to R.string.text_size_default,
                            2 to R.string.text_size_comfortable,
                        ).forEachIndexed { index, (value, labelRes) ->
                            SegmentedButton(
                                selected = s.logTextScale == value,
                                onClick = { viewModel.setLogTextScale(value) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                            ) { Text(stringResource(labelRes)) }
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
