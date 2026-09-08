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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@Composable
fun SetupScreen(
    onDone: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    if (state.granted) {
        // Access became available while on this screen: proceed.
        androidx.compose.runtime.LaunchedEffect(Unit) {
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
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        // Dynamic "Next step" banner — tells the user exactly what to do right now.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.setup_next_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        state.granted -> stringResource(R.string.setup_next_done)
                        state.shizukuStatus == io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuStatus.PERMISSION_REQUIRED ->
                            stringResource(R.string.setup_next_grant_shizuku)
                        state.shizukuStatus == io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuStatus.NOT_RUNNING ->
                            stringResource(R.string.setup_next_start_shizuku)
                        state.shizukuStatus == io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuStatus.NOT_INSTALLED ->
                            stringResource(R.string.setup_next_install_shizuku)
                        else -> stringResource(R.string.setup_next_adb)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // --- Shizuku path ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.setup_shizuku_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.setup_shizuku_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusChip(
                    label = when (state.shizukuStatus) {
                        ShizukuStatus.NOT_INSTALLED -> stringResource(R.string.setup_shizuku_not_installed)
                        ShizukuStatus.NOT_RUNNING -> stringResource(R.string.setup_shizuku_not_running)
                        ShizukuStatus.PERMISSION_REQUIRED -> stringResource(R.string.setup_shizuku_needs_permission)
                        ShizukuStatus.READY -> stringResource(R.string.setup_shizuku_ready)
                    },
                    ok = state.shizukuStatus == ShizukuStatus.READY,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }

        // --- ADB path ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.setup_adb_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.setup_adb_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card {
                    Text(
                        text = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.READ_LOGS",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.copyAdbCommand() }) {
                        Text(stringResource(R.string.setup_copy_command))
                    }
                    OutlinedButton(onClick = { viewModel.refresh() }) {
                        Text(stringResource(R.string.setup_recheck))
                    }
                }
                if (state.readLogsGranted) {
                    StatusChip(label = stringResource(R.string.setup_adb_granted), ok = true)
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

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Text(text = label, color = color, style = MaterialTheme.typography.labelMedium)
}
