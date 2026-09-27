package io.github.shiaho777.logsleuth.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.apps.AppLogGroup
import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.ui.theme.LevelColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared "pick a scope" dialog used by both the clear and save-to-session
 * actions: either everything, or a checked subset of apps.
 *
 * Layout: a mode switch (All / Per app) on top; in per-app mode the body
 * splits into the app list (left, sorted by line count) and a metrics
 * panel for the focused app (right). [onConfirm] receives null for All,
 * or the set of selected uids (null uid = the unattributed bucket).
 */
@Composable
fun LogScopeDialog(
    title: String,
    icon: ImageVector,
    confirmLabel: String,
    confirmTint: Color,
    groups: List<AppLogGroup>?,
    totalLines: Int,
    onDismiss: () -> Unit,
    onConfirm: (Set<Int?>?) -> Unit,
) {
    var perApp by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Int?>()) }
    var focusedUid by remember { mutableStateOf<Int?>(null) }
    val focused = groups?.firstOrNull { it.uid == focusedUid } ?: groups?.firstOrNull()
    val selectedCount = groups?.filter { it.uid in selected }?.sumOf { it.total } ?: 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(0.94f),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = confirmTint)
                    Spacer(Modifier.width(10.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !perApp,
                        onClick = { perApp = false },
                        label = {
                            Text(
                                stringResource(R.string.scope_all, totalLines),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                    FilterChip(
                        selected = perApp,
                        onClick = { perApp = true },
                        label = { Text(stringResource(R.string.scope_per_app)) },
                    )
                }

                if (perApp) {
                    Spacer(Modifier.height(10.dp))
                    when {
                        groups == null -> Box(
                            Modifier.fillMaxWidth().height(280.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.scope_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        groups.isEmpty() -> Box(
                            Modifier.fillMaxWidth().height(280.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.scope_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        else -> Row(Modifier.height(280.dp)) {
                            LazyColumn(Modifier.weight(1.15f)) {
                                items(groups.size, key = { groups[it].uid ?: -1 }) { i ->
                                    AppScopeRow(
                                        group = groups[i],
                                        checked = groups[i].uid in selected,
                                        focused = groups[i].uid == focused?.uid,
                                        onClick = {
                                            focusedUid = groups[i].uid
                                            selected = if (groups[i].uid in selected) {
                                                selected - groups[i].uid
                                            } else {
                                                selected + groups[i].uid
                                            }
                                        },
                                    )
                                }
                            }
                            VerticalDivider(Modifier.padding(horizontal = 8.dp))
                            focused?.let { MetricsPane(it, Modifier.weight(0.85f)) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        enabled = if (perApp) {
                            selected.isNotEmpty()
                        } else {
                            totalLines > 0
                        },
                        onClick = { onConfirm(if (perApp) selected.toSet() else null) },
                    ) {
                        Text(
                            text = if (perApp) {
                                "$confirmLabel · $selectedCount"
                            } else {
                                confirmLabel
                            },
                            color = if (perApp && selected.isEmpty() || (!perApp && totalLines == 0)) {
                                confirmTint
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppScopeRow(
    group: AppLogGroup,
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (focused) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        AppBadge(group.packageName, group.label)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                group.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.scope_lines, group.total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** App icon when the uid resolved to a package; a letter tile otherwise. */
@Composable
private fun AppBadge(packageName: String?, label: String) {
    val context = LocalContext.current
    val icon: ImageBitmap? = remember(packageName) {
        packageName?.let {
            runCatching {
                context.packageManager.getApplicationIcon(it).toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }
    if (icon != null) {
        androidx.compose.foundation.Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)),
        )
    } else {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Right-hand metrics for the focused app: totals, level mix, time span. */
@Composable
private fun MetricsPane(group: AppLogGroup, modifier: Modifier = Modifier) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Column(modifier.padding(vertical = 4.dp)) {
        Text(
            group.label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (group.packageName != null) {
            Text(
                group.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${group.total}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.scope_lines, group.total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(10.dp))
        LevelBar(group)
        Spacer(Modifier.height(8.dp))
        if (group.errors > 0) {
            Text(
                stringResource(R.string.scope_errors, group.errors),
                style = MaterialTheme.typography.labelMedium,
                color = LevelColors.E,
            )
        }
        if (group.warns > 0) {
            Text(
                stringResource(R.string.scope_warns, group.warns),
                style = MaterialTheme.typography.labelMedium,
                color = LevelColors.W,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${timeFmt.format(Date(group.firstMillis))} – ${timeFmt.format(Date(group.lastMillis))}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Proportional per-level strip — one glance tells the app's log mix. */
@Composable
private fun LevelBar(group: AppLogGroup) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        for (level in LogLevel.entries) {
            val n = group.levelCounts[level.ordinal]
            if (n > 0) {
                Box(
                    Modifier
                        .weight(n.toFloat())
                        .height(8.dp)
                        .background(levelColorOf(level)),
                )
            }
        }
    }
}

private fun levelColorOf(level: LogLevel): Color = when (level) {
    LogLevel.V -> LevelColors.V
    LogLevel.D -> LevelColors.D
    LogLevel.I -> LevelColors.I
    LogLevel.W -> LevelColors.W
    LogLevel.E -> LevelColors.E
    LogLevel.F -> LevelColors.F
    LogLevel.A -> LevelColors.A
}
