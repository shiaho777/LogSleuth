package io.github.shiaho777.logsleuth.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.ui.theme.LevelColors
import io.github.shiaho777.logsleuth.app.ui.theme.LocalLogTextScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.V -> LevelColors.V
    LogLevel.D -> LevelColors.D
    LogLevel.I -> LevelColors.I
    LogLevel.W -> LevelColors.W
    LogLevel.E -> LevelColors.E
    LogLevel.F -> LevelColors.F
    LogLevel.A -> LevelColors.A
}

@Composable
fun LogRow(
    entry: LogcatEntry,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    isCurrentHit: Boolean = false,
    isSelected: Boolean = false,
    snackbar: SnackbarHostState? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    var showDetail by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied)

    val scale = LocalLogTextScale.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val timeText = remember(entry.timestampMillis) {
        timeFormat.format(Date(entry.timestampMillis))
    }
    val levelTint = levelColor(entry.level)
    val isError = entry.level.priority >= LogLevel.E.priority

    val highlightStyle = highlightStyle(isError)
    val tagText = remember(entry.tag, highlight, highlightStyle) {
        highlighted(entry.tag, highlight, highlightStyle)
    }
    val messageText = remember(entry.message, highlight, highlightStyle) {
        highlighted(entry.message, highlight, highlightStyle)
    }

    val rowBg = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isCurrentHit -> MaterialTheme.colorScheme.tertiaryContainer
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(rowBg)
            .combinedClickable(
                onClick = { if (onClick != null) onClick() else showDetail = true },
                onLongClick = {
                    if (onLongClick != null) {
                        onLongClick()
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboard.setText(AnnotatedString(entry.raw))
                        notifyCopied(snackbar, scope, context, copiedMessage)
                    }
                },
            )
            .padding(vertical = 2.dp),
    ) {
        // Level color strip — the fastest scanning cue in a dense log list.
        Box(
            modifier = Modifier
                .padding(start = 5.dp, end = 7.dp)
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(levelTint)
                .align(Alignment.CenterVertically),
        )

        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeText,
                    style = logMetaStyle(scale),
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = "  ${entry.pid}-${entry.tid}  ",
                    style = logMetaStyle(scale),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                )
                if (entry.tag.isNotEmpty()) {
                    Text(
                        text = tagText,
                        style = logMetaStyle(scale),
                        color = levelTint.copy(alpha = 0.95f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(R.string.entry_detail),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(13.dp),
                )
            }
            Text(
                text = messageText,
                style = logMessageStyle(scale),
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                // Multi-line entries (e.g. glued fatal blocks) are capped in
                // the list; the detail sheet always shows the full message.
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (showDetail) {
        EntryDetailSheet(entry = entry, snackbar = snackbar, onDismiss = { showDetail = false })
    }
}

private fun notifyCopied(
    snackbar: SnackbarHostState?,
    scope: CoroutineScope,
    context: android.content.Context,
    message: String,
) {
    if (snackbar != null) scope.launch { snackbar.showSnackbar(message) }
    else Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDetailSheet(
    entry: LogcatEntry,
    snackbar: SnackbarHostState?,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(entry.level)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.tag.ifEmpty { stringResource(R.string.entry_detail) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${entry.displayTime}  ·  pid ${entry.pid}  ·  tid ${entry.tid}" +
                    (entry.uid?.let { "  ·  uid $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            SelectionContainer {
                Text(
                    text = entry.message,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(entry.message))
                    onDismiss()
                    notifyCopied(snackbar, scope, context, copiedMessage)
                }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.copy_message))
                }
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(entry.raw))
                    onDismiss()
                    notifyCopied(snackbar, scope, context, copiedMessage)
                }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.copy_raw))
                }
            }
        }
    }
}

@Composable
private fun logMetaStyle(scale: Float) = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.5.sp * scale,
    lineHeight = 13.sp * scale,
)

@Composable
private fun logMessageStyle(scale: Float) = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.5.sp * scale,
    lineHeight = 15.sp * scale,
)

@Composable
fun LevelBadge(level: LogLevel) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(levelColor(level)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.letter.toString(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Search-hit highlight derived from the theme's tertiary container pair, so
 *  it stays legible on both the plain surface and error-tinted rows, in light
 *  and dark. */
@Composable
private fun highlightStyle(isError: Boolean) = SpanStyle(
    background = MaterialTheme.colorScheme.tertiary.copy(
        alpha = if (isError) 0.5f else 0.35f,
    ),
)

private fun highlighted(text: String, query: String?, style: SpanStyle): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var start = 0
        val lower = text.lowercase()
        val q = query.lowercase()
        while (true) {
            val idx = lower.indexOf(q, start)
            if (idx < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(style) {
                append(text.substring(idx, idx + q.length))
            }
            start = idx + q.length
        }
    }
}
