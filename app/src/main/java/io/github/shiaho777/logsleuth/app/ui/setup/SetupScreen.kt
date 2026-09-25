package io.github.shiaho777.logsleuth.app.ui.setup

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.BuildConfig
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuStatus
import io.github.shiaho777.logsleuth.app.data.prefs.AppLocales
import io.github.shiaho777.logsleuth.app.ui.components.LanguagePicker

@Composable
fun SetupScreen(
    onDone: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    // Auto-forward only during first-run setup: when the wizard is re-opened
    // from Settings while a grant already exists, the user wants to *see* the
    // status/next-step cards, not get bounced straight to the stream.
    if (state.granted && settings?.setupCompleted != true) {
        // Access became available while on this screen: proceed.
        LaunchedEffect(Unit) {
            viewModel.markSetupCompleted()
            onDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.setup_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Language first: every string below this point localizes live.
        Card(modifier = Modifier.fillMaxWidth()) {
            LanguagePicker(
                current = settings?.language ?: AppLocales.SYSTEM,
                onSelect = { tag ->
                    viewModel.setLanguage(tag)
                    AppLocales.applyFromUi(context, tag)
                },
            )
        }

        // --- Shizuku path ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.setup_shizuku_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    StatusText(
                        label = when (state.shizukuStatus) {
                            ShizukuStatus.NOT_INSTALLED -> stringResource(R.string.setup_shizuku_not_installed)
                            ShizukuStatus.NOT_RUNNING -> stringResource(R.string.setup_shizuku_not_running)
                            ShizukuStatus.PERMISSION_REQUIRED -> stringResource(R.string.setup_shizuku_needs_permission)
                            ShizukuStatus.READY -> stringResource(R.string.setup_shizuku_ready)
                        },
                        ready = state.shizukuStatus == ShizukuStatus.READY,
                    )
                }
                Text(
                    stringResource(R.string.setup_shizuku_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (state.shizukuStatus) {
                    ShizukuStatus.NOT_INSTALLED -> Button(onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://shizuku.rikka.app/download/"),
                            ),
                        )
                    }) { Text(stringResource(R.string.setup_get_shizuku)) }

                    ShizukuStatus.NOT_RUNNING -> Button(onClick = {
                        runCatching {
                            context.startActivity(
                                context.packageManager.getLaunchIntentForPackage(
                                    "moe.shizuku.privileged.api",
                                ),
                            )
                        }
                    }) { Text(stringResource(R.string.setup_open_shizuku)) }

                    ShizukuStatus.PERMISSION_REQUIRED -> Button(onClick = {
                        viewModel.requestShizukuPermission()
                    }) { Text(stringResource(R.string.setup_grant_shizuku)) }

                    ShizukuStatus.READY -> {}
                }
            }
        }

        // --- ADB path ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.setup_adb_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.readLogsGranted) {
                        StatusText(
                            label = stringResource(R.string.setup_adb_granted),
                            ready = true,
                        )
                    }
                }
                Text(
                    stringResource(R.string.setup_adb_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.READ_LOGS",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.copyAdbCommand() }) {
                        Text(stringResource(R.string.setup_copy_command))
                    }
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text(stringResource(R.string.setup_recheck))
                    }
                }
            }
        }

        TextButton(onClick = {
            viewModel.markSetupCompleted()
            onDone()
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.setup_skip))
        }
    }
}

/** Quiet inline status: accent + check when ready, muted text otherwise. */
@Composable
private fun StatusText(label: String, ready: Boolean) {
    val color = if (ready) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ready) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
