package io.github.shiaho777.logsleuth.app.mcp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.ui.theme.InstrumentTopBar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
internal class McpViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val status: McpStatus,
    private val identity: McpIdentity,
) : ViewModel() {
    val state = status.state
    val pairing = MutableStateFlow<String?>(null)
    private var expiry: Job? = null

    init {
        if (!state.value.active && !state.value.starting) status.paired(identity.isPaired())
        viewModelScope.launch {
            state.collect { if (!it.active) pairing.value = null }
        }
    }

    fun start() {
        try {
            ContextCompat.startForegroundService(context, intent(McpService.ACTION_START))
        } catch (_: IllegalStateException) {
            status.set(McpState(failed = true, paired = identity.isPaired()))
        } catch (_: SecurityException) {
            status.set(McpState(failed = true, paired = identity.isPaired()))
        }
    }

    fun stop() {
        pairing.value = null
        context.startService(intent(McpService.ACTION_STOP))
    }

    fun generate() {
        if (!state.value.active) return
        val code = identity.createPairing()
        pairing.value = code
        expiry?.cancel()
        expiry = viewModelScope.launch {
            delay(120_000)
            if (pairing.value == code) pairing.value = null
        }
    }

    fun revoke() {
        expiry?.cancel()
        pairing.value = null
        if (state.value.active) {
            context.startService(intent(McpService.ACTION_REVOKE))
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                identity.revoke()
                status.paired(false)
            }
        }
    }

    private fun intent(action: String) = Intent(context, McpService::class.java).setAction(action)
}

@Composable
internal fun McpScreen(onBack: () -> Unit, viewModel: McpViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val pairing by viewModel.pairing.collectAsState()
    val context = LocalContext.current
    val title = stringResource(R.string.mcp_title)
    Scaffold(topBar = {
        InstrumentTopBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.report_back))
                }
            },
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.mcp_description))
            Text(
                stringResource(when {
                    state.failed -> R.string.mcp_failed
                    state.starting -> R.string.mcp_starting
                    state.active -> R.string.mcp_running
                    else -> R.string.mcp_off
                }),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.active) {
                Text(stringResource(if (state.paired) R.string.mcp_paired else R.string.mcp_unpaired))
                if (state.activeCalls > 0) Text(stringResource(R.string.mcp_active_calls, state.activeCalls))
                Button(onClick = viewModel::generate) { Text(stringResource(R.string.mcp_pair)) }
                pairing?.let { code ->
                    Text(stringResource(R.string.mcp_pair_help))
                    SelectionContainer { Text(code, fontFamily = FontFamily.Monospace) }
                    TextButton(onClick = {
                        val clip = ClipData.newPlainText(title, code)
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    }) { Text(stringResource(R.string.mcp_copy)) }
                }
                TextButton(onClick = viewModel::stop) { Text(stringResource(R.string.mcp_stop)) }
            } else {
                Button(onClick = viewModel::start, enabled = !state.starting) {
                    Text(stringResource(R.string.mcp_start))
                }
            }
            if (state.paired) TextButton(onClick = viewModel::revoke) {
                Text(stringResource(R.string.mcp_revoke))
            }
        }
    }
}
