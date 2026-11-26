package io.github.shiaho777.logsleuth.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.BuildConfig
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.logcat.AccessKind
import io.github.shiaho777.logsleuth.app.core.logcat.AccessPolicy
import io.github.shiaho777.logsleuth.app.core.root.RootStatus
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import io.github.shiaho777.logsleuth.app.ui.components.LanguagePicker
import io.github.shiaho777.logsleuth.app.ui.guide.tourTarget
import kotlin.math.roundToInt
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentPanel
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentTopBar
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthMark
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthGraphic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenWelcome: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenMcp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val access by viewModel.access.collectAsState()
    val rootStatus by viewModel.rootStatus.collectAsState()
    val context = LocalContext.current
    val s = settings ?: return
    val notificationManager = remember(context) { NotificationManagerCompat.from(context) }
    var notificationsAllowed by remember(notificationManager) {
        mutableStateOf(notificationManager.areNotificationsEnabled())
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = notificationManager.areNotificationsEnabled()
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = notificationManager.areNotificationsEnabled()
    }
    val openNotificationSettings = {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val crashNotifications = s.crashNotifications && notificationsAllowed

    // ADB grants have no event stream — refresh when the screen opens.
    LaunchedEffect(Unit) { viewModel.refreshAccess() }

    Scaffold(
        topBar = {
            InstrumentTopBar(title = { Text(stringResource(R.string.settings_title)) })
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .tourTarget("settingsContent")
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionHeader(stringResource(R.string.settings_section_access))

            InstrumentPanel(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SleuthGraphic(R.drawable.sleuth_access, Modifier.size(56.dp))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_access),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val accessText = when (access.kind) {
                            AccessKind.SHIZUKU -> stringResource(R.string.settings_access_shizuku)
                            AccessKind.ROOT -> stringResource(R.string.settings_access_root)
                            AccessKind.READ_LOGS -> stringResource(R.string.settings_access_adb)
                            AccessKind.REMOTE_ADB -> stringResource(R.string.settings_access_remote)
                            AccessKind.NONE -> stringResource(R.string.no_access_title)
                        }
                        Text(
                            accessText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (access.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onOpenSetup) {
                            Text(stringResource(R.string.settings_access_open))
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_access_mode),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Text(
                        stringResource(R.string.settings_access_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    listOf(
                        AccessPolicy.AUTO to R.string.access_mode_auto,
                        AccessPolicy.SHIZUKU to R.string.access_mode_shizuku,
                        AccessPolicy.ROOT to R.string.access_mode_root,
                        AccessPolicy.ADB to R.string.access_mode_adb,
                        AccessPolicy.REMOTE to R.string.access_mode_remote,
                    ).forEach { (value, labelRes) ->
                        SettingsChoiceRow(
                            title = stringResource(labelRes),
                            selected = s.accessPreference == value,
                            onClick = { viewModel.setAccessPreference(value) },
                        )
                    }
                    val rootDetail = when {
                        !s.rootEnabled -> stringResource(R.string.settings_root_desc)
                        rootStatus == RootStatus.READY -> stringResource(R.string.setup_root_ready)
                        rootStatus == RootStatus.CHECKING -> stringResource(R.string.setup_root_checking)
                        rootStatus == RootStatus.DENIED -> stringResource(R.string.setup_root_denied)
                        else -> stringResource(R.string.settings_root_desc)
                    }
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_root),
                        subtitle = rootDetail,
                        checked = s.rootEnabled,
                        onCheckedChange = viewModel::setRootEnabled,
                    )
                }
            }

            RemoteAdbCard(
                host = s.remoteAdbHost,
                port = s.remoteAdbPort,
                onSave = viewModel::setRemoteAdb,
                viewModel = viewModel,
            )

            SettingsSectionHeader(stringResource(R.string.settings_section_stream))

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
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_live_only),
                    subtitle = stringResource(R.string.settings_live_only_desc),
                    checked = s.liveOnlyOnStart,
                    onCheckedChange = viewModel::setLiveOnlyOnStart,
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_recording))

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_recording_limits),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    // Drag locally; persist once on release.
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

            SettingsSectionHeader(stringResource(R.string.settings_section_crashes))

            Card {
                Column {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_crash_notifications),
                        subtitle = if (notificationsAllowed) null else {
                            stringResource(R.string.settings_notifications_permission_desc)
                        },
                        checked = crashNotifications,
                        onCheckedChange = { enabled ->
                            viewModel.setCrashNotifications(enabled)
                            if (enabled && !notificationsAllowed) {
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    openNotificationSettings()
                                }
                            }
                        },
                    )
                    if (!notificationsAllowed) {
                        TextButton(onClick = openNotificationSettings, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(stringResource(R.string.settings_system_notifications))
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.settings_crash_save),
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            stringResource(R.string.settings_crash_notify),
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    CrashPolicyRow(
                        label = stringResource(R.string.settings_crash_java),
                        save = s.collectJava,
                        notify = s.notifyJava,
                        notifyEnabled = crashNotifications,
                        onSave = viewModel::setCollectJava,
                        onNotify = viewModel::setNotifyJava,
                    )
                    CrashPolicyRow(
                        label = stringResource(R.string.settings_crash_native),
                        save = s.collectNative,
                        notify = s.notifyNative,
                        notifyEnabled = crashNotifications,
                        onSave = viewModel::setCollectNative,
                        onNotify = viewModel::setNotifyNative,
                    )
                    CrashPolicyRow(
                        label = stringResource(R.string.settings_crash_anr),
                        save = s.collectAnr,
                        notify = s.notifyAnr,
                        notifyEnabled = crashNotifications,
                        onSave = viewModel::setCollectAnr,
                        onNotify = viewModel::setNotifyAnr,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_separate_channels),
                        subtitle = stringResource(R.string.settings_separate_channels_desc),
                        checked = s.separateCrashChannels,
                        onCheckedChange = viewModel::setSeparateCrashChannels,
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

            SettingsSectionHeader(stringResource(R.string.mcp_title))
            TextButton(onClick = onOpenMcp) {
                Text(stringResource(R.string.mcp_open))
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_automation))

            Card {
                Column {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_automation),
                        subtitle = stringResource(R.string.settings_automation_desc),
                        checked = s.automationEnabled,
                        onCheckedChange = viewModel::setAutomationEnabled,
                    )
                    TextButton(
                        onClick = onOpenAutomation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(stringResource(R.string.settings_automation_manage))
                    }
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
                Column(Modifier.padding(top = 16.dp)) {
                    Text(
                        stringResource(R.string.settings_columns),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Text(
                        stringResource(R.string.settings_time_format),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        listOf(
                            "time" to R.string.time_format_clock,
                            "datetime" to R.string.time_format_datetime,
                            "epoch" to R.string.time_format_epoch,
                        ).forEachIndexed { index, (value, labelRes) ->
                            SegmentedButton(
                                selected = s.logTimeFormat == value,
                                onClick = { viewModel.setLogTimeFormat(value) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                            ) { Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_col_date),
                        checked = s.showLogDate,
                        onCheckedChange = viewModel::setShowLogDate,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_col_time),
                        checked = s.showLogTime,
                        onCheckedChange = viewModel::setShowLogTime,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_col_uid),
                        checked = s.showLogUid,
                        onCheckedChange = viewModel::setShowLogUid,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_col_pid),
                        checked = s.showLogPid,
                        onCheckedChange = viewModel::setShowLogPid,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_col_tid),
                        checked = s.showLogTid,
                        onCheckedChange = viewModel::setShowLogTid,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_col_tag),
                        checked = s.showLogTag,
                        onCheckedChange = viewModel::setShowLogTag,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_col_package),
                        checked = s.showLogPackage,
                        onCheckedChange = viewModel::setShowLogPackage,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_logs_expanded),
                        checked = s.logsExpanded,
                        onCheckedChange = viewModel::setLogsExpanded,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_search_case),
                        checked = s.searchCaseSensitive,
                        onCheckedChange = viewModel::setSearchCaseSensitive,
                    )
                    OutlinedTextField(
                        value = s.datePattern,
                        onValueChange = viewModel::setDatePattern,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_date_pattern)) },
                    )
                    OutlinedTextField(
                        value = s.timePattern,
                        onValueChange = viewModel::setTimePattern,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_time_pattern)) },
                    )
                }
            }

            Card {
                Column {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_watch_boot),
                        subtitle = stringResource(R.string.settings_watch_boot_desc),
                        checked = s.watchOnBoot,
                        onCheckedChange = viewModel::setWatchOnBoot,
                    )
                    // The switch is the user's intent; this is the system's
                    // answer. Without it the toggle claims a watch that the
                    // system has already refused (see #118).
                    if (s.watchOnBoot && s.watchBlockedAt > 0L) {
                        Text(
                            text = stringResource(R.string.settings_watch_blocked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 8.dp,
                            ),
                        )
                    }
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_open_crashes_startup),
                        checked = s.openCrashesOnStartup,
                        onCheckedChange = viewModel::setOpenCrashesOnStartup,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_include_device_zip),
                        checked = s.includeDeviceInfoInZip,
                        onCheckedChange = viewModel::setIncludeDeviceInfoInZip,
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_include_app_zip),
                        checked = s.includeAppInfoInZip,
                        onCheckedChange = viewModel::setIncludeAppInfoInZip,
                    )
                    if (s.crashBlacklist.isNotEmpty()) {
                        Text(
                            stringResource(R.string.settings_crash_blacklist),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Text(
                            stringResource(R.string.settings_crash_blacklist_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        s.crashBlacklist.sorted().forEach { pkg ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    pkg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.unignorePackage(pkg) }) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
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
                    var scaleValue by remember(s.logTextScale) { mutableStateOf(s.logTextScale) }
                    Text(
                        "${(scaleValue * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = scaleValue,
                        onValueChange = { scaleValue = it },
                        onValueChangeFinished = { viewModel.setLogTextScale(scaleValue) },
                        valueRange = 0.75f..1.50f,
                    )
                }
            }

            // Reset sits above the community and repository cards: the last
            // things on the screen are the way out, not the way in.
            ResetSettingsCard(onReset = viewModel::resetToDefaults)

            SettingsSectionHeader(stringResource(R.string.settings_section_updates))

            InstrumentPanel(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SleuthMark(Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            InstrumentPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    TextButton(onClick = onOpenWelcome) {
                        Text(stringResource(R.string.welcome_quick_start))
                    }
                    TextButton(onClick = onOpenGuide) {
                        Text(stringResource(R.string.settings_replay_guide))
                    }
                }
            }

            CommunityCard(
                onJoinGroup = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP_INVITE_URL)),
                    )
                },
                onOpenBilibili = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(BILIBILI_SPACE_URL)),
                    )
                },
            )

            val updateState by viewModel.update.collectAsState()
            UpdateCard(
                state = updateState,
                onCheck = viewModel::checkUpdates,
                onDownload = viewModel::downloadRelease,
                onPause = viewModel::pauseDownload,
                onResume = viewModel::resumeDownload,
                onCancel = viewModel::cancelDownload,
                onInstall = viewModel::installDownloaded,
                onOpenRepo = { context.startActivity(viewModel.openRepoIntent()) },
                onOpenRelease = { context.startActivity(viewModel.openReleaseIntent(it)) },
            )
        }
    }
}

/** Group title: extra space above opens a new section, tight space below
 *  keeps the title attached to its cards. */
@Composable
private fun SettingsSectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        HorizontalDivider(
            modifier = Modifier.weight(0.45f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
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

@Composable
private fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CrashPolicyRow(
    label: String,
    save: Boolean,
    notify: Boolean,
    notifyEnabled: Boolean,
    onSave: (Boolean) -> Unit,
    onNotify: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Checkbox(
            checked = save,
            onCheckedChange = onSave,
            modifier = Modifier.width(64.dp),
        )
        Checkbox(
            checked = notify,
            onCheckedChange = onNotify,
            enabled = notifyEnabled,
            modifier = Modifier.width(64.dp),
        )
    }
}
