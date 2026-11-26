package io.github.shiaho777.logsleuth.app.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.importer.LogImporter
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentPanel
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthGraphic
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthMark

@Composable
fun WelcomeScreen(
    onOpenSession: (Long) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSessions: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    LaunchedEffect(state.openedSessionId) {
        val id = state.openedSessionId ?: return@LaunchedEffect
        viewModel.clearResult()
        onOpenSession(id)
    }

    Box(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SleuthMark(Modifier.size(30.dp))
                Text(
                    stringResource(R.string.app_name),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            SleuthGraphic(
                resource = R.drawable.sleuth_welcome,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(width = 176.dp, height = 124.dp),
            )
            Button(
                onClick = viewModel::openExample,
                enabled = !state.opening,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (state.opening) R.string.welcome_opening else R.string.welcome_example),
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(19.dp))
            }
            InstrumentPanel(Modifier.fillMaxWidth()) {
                Column {
                    WelcomeAction(
                        icon = Icons.Outlined.FolderOpen,
                        label = stringResource(R.string.welcome_import),
                        enabled = !state.opening,
                        onClick = { importLauncher.launch(LogImporter.documentTypes()) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    WelcomeAction(
                        icon = Icons.Outlined.FiberManualRecord,
                        label = stringResource(R.string.welcome_capture),
                        enabled = !state.opening,
                        onClick = onOpenSetup,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onOpenSessions, enabled = !state.opening) {
                    Text(stringResource(R.string.welcome_saved_sessions))
                }
            }
            state.error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}


@Composable
private fun WelcomeAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) colors.primary else colors.onSurfaceVariant)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
