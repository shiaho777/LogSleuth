package io.github.shiaho777.logsleuth.app.ui.crashes

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.ui.components.LocalAppIconCache
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashDetailsScreen(
    onBack: () -> Unit,
    viewModel: CrashDetailsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val crash = ui.crash
    val scope = rememberCoroutineScope()
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) scope.launch { viewModel.saveZip(uri) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        crash?.appName?.takeIf { it.isNotBlank() }
                            ?: crash?.packageName
                            ?: stringResource(R.string.crash_details_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setSearching(!ui.searching) }) {
                        Icon(
                            if (ui.searching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(
                                if (ui.searching) R.string.close else R.string.crash_search,
                            ),
                        )
                    }
                    IconButton(onClick = { viewModel.setWrap(!ui.wrap) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.WrapText,
                            contentDescription = stringResource(R.string.crash_wrap),
                            tint = if (ui.wrap) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { saveLauncher.launch(viewModel.zipFileName()) }) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = stringResource(R.string.save_to),
                        )
                    }
                    IconButton(onClick = viewModel::share) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_logs),
                        )
                    }
                    IconButton(onClick = { viewModel.delete(onBack) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (crash == null) {
            EmptyState(
                text = stringResource(R.string.crashes_empty),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val icon = LocalAppIconCache.current?.iconForPackage(crash.packageName)
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = stringResource(R.string.crash_app_icon),
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                crash.appName?.takeIf { it.isNotBlank() }
                    ?: crash.packageName
                    ?: stringResource(R.string.unknown_app),
                style = MaterialTheme.typography.titleLarge,
            )
            crash.packageName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            crashVersionLine(crash)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                DateUtils.getRelativeTimeSpanString(
                    crash.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(onClick = viewModel::copyStack, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.copy))
                }
                FilledTonalButton(onClick = viewModel::openAppInfo, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.crash_open_app), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(onClick = viewModel::openChannel) {
                Text(stringResource(R.string.crash_open_channel))
            }
            crash.packageName?.let { pkg ->
                TextButton(onClick = viewModel::ignore) {
                    Text(stringResource(R.string.crash_ignore))
                }
            }
            if (ui.searching) {
                OutlinedTextField(
                    value = ui.searchQuery,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.crash_search)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.crash_search))
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = highlighted(crash.snippet, ui.searchRanges),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    softWrap = ui.wrap,
                    overflow = if (ui.wrap) TextOverflow.Visible else TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!ui.contextText.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.crash_context_heading),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = highlighted(ui.contextText.orEmpty(), ui.contextSearchRanges),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        softWrap = ui.wrap,
                        overflow = if (ui.wrap) TextOverflow.Visible else TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun highlighted(text: String, ranges: List<IntRange>) = buildAnnotatedString {
    if (ranges.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    val highlight = SpanStyle(background = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
    var cursor = 0
    for (range in ranges.sortedBy { it.first }) {
        if (range.first > cursor) append(text.substring(cursor, range.first))
        val end = (range.last + 1).coerceAtMost(text.length)
        if (range.first < end) {
            withStyle(highlight) { append(text.substring(range.first, end)) }
        }
        cursor = end
    }
    if (cursor < text.length) append(text.substring(cursor))
}

@Composable
private fun crashVersionLine(crash: io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity): String? {
    val name = crash.versionName?.takeIf { it.isNotBlank() }
    val code = crash.versionCode
    return when {
        name != null && code != null -> stringResource(R.string.crash_version, name, code)
        name != null -> stringResource(R.string.crash_version_name, name)
        code != null -> code.toString()
        else -> null
    }
}
