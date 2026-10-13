package io.github.shiaho777.logsleuth.app.ui.crashes

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.crashes.CrashGroup
import io.github.shiaho777.logsleuth.app.core.crashes.CrashSort
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.ui.components.LocalAppIconCache
import io.github.shiaho777.logsleuth.app.ui.guide.tourTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashesScreen(
    onBack: () -> Unit,
    onOpenPackage: (String?) -> Unit,
    viewModel: CrashesViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsState()
    val browser by viewModel.browserState.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var sortOpen by remember { mutableStateOf(false) }

    val deletedMessage = stringResource(R.string.crash_deleted_all)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(pendingDelete) {
        if (!pendingDelete) return@LaunchedEffect
        val result = snackbar.showSnackbar(deletedMessage, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crashes_title)) },
                actions = {
                    IconButton(onClick = viewModel::toggleReversed) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = stringResource(R.string.crash_sort_reverse),
                        )
                    }
                    Box {
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.crash_sort),
                            )
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SortItem(CrashSort.NEWEST, R.string.crash_sort_newest, browser.sort) {
                                viewModel.setSort(CrashSort.NEWEST)
                                sortOpen = false
                            }
                            SortItem(CrashSort.COUNT, R.string.crash_sort_count, browser.sort) {
                                viewModel.setSort(CrashSort.COUNT)
                                sortOpen = false
                            }
                            SortItem(CrashSort.NAME, R.string.crash_sort_name, browser.sort) {
                                viewModel.setSort(CrashSort.NAME)
                                sortOpen = false
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).tourTarget("crashesContent")) {
            OutlinedTextField(
                value = browser.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.crash_search)) },
            )
            if (groups.isEmpty()) {
                EmptyState(text = stringResource(R.string.crashes_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(groups, key = { it.packageName ?: "" }) { group ->
                        CrashGroupCard(
                            group = group,
                            onOpen = { onOpenPackage(group.packageName) },
                            onDelete = { viewModel.requestDeletePackage(group.packageName) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortItem(
    sort: CrashSort,
    labelRes: Int,
    selected: CrashSort,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                stringResource(labelRes),
                color = if (selected == sort) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun CrashGroupCard(
    group: CrashGroup,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cache = LocalAppIconCache.current
    val icon = cache?.iconForPackage(group.packageName)
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.label.ifBlank { stringResource(R.string.unknown_app) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!group.packageName.isNullOrBlank() && group.packageName != group.label) {
                    Text(
                        group.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        group.latestTime,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                stringResource(R.string.crash_group_count, group.count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CrashCard(
    crash: CrashEventEntity,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onShare: ((CrashEventEntity) -> Unit)? = null,
    onIgnore: (() -> Unit)? = null,
    onOpenChannel: (() -> Unit)? = null,
    onReadContext: (suspend (CrashEventEntity) -> String?)? = null,
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember(crash.id) { mutableStateOf(initiallyExpanded) }
    var contextText by remember(crash.id) { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val isAnr = crash.type == "ANR"
    val typeLabel = when (crash.type) {
        "NATIVE" -> stringResource(R.string.crash_type_native)
        "ANR" -> stringResource(R.string.crash_type_anr)
        else -> stringResource(R.string.crash_type_java)
    }
    val title = crash.appName?.takeIf { it.isNotBlank() }
        ?: crash.packageName
        ?: stringResource(R.string.unknown_app)
    val version = crashVersion(crash)

    LaunchedEffect(expanded, crash.id) {
        if (expanded && contextText == null && onReadContext != null) {
            contextText = onReadContext(crash)
        }
    }

    Card(
        onClick = { expanded = true },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (isAnr) Icons.Default.HourglassTop else Icons.Default.BugReport,
                    contentDescription = crash.type,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    crash.firstLine,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    typeLabel + " · " + DateUtils.getRelativeTimeSpanString(
                        crash.time,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    crash.packageName?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium)
                    }
                    version?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SelectionContainer {
                        Text(crash.snippet, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    if (!contextText.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.crash_context_heading),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        SelectionContainer {
                            Text(
                                contextText.orEmpty(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (onIgnore != null) {
                        TextButton(onClick = {
                            onIgnore()
                            expanded = false
                        }) {
                            Text(stringResource(R.string.crash_ignore))
                        }
                    }
                    if (onOpenChannel != null) {
                        TextButton(onClick = onOpenChannel) {
                            Text(stringResource(R.string.crash_open_channel))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onShare != null) {
                            TextButton(onClick = {
                                onShare(crash)
                                expanded = false
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.crash_share))
                            }
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(crash.snippet))
                            expanded = false
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.copy))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun crashVersion(crash: CrashEventEntity): String? {
    val name = crash.versionName?.takeIf { it.isNotBlank() }
    val code = crash.versionCode
    return when {
        name != null && code != null -> stringResource(R.string.crash_version, name, code)
        name != null -> stringResource(R.string.crash_version_name, name)
        code != null -> code.toString()
        else -> null
    }
}
