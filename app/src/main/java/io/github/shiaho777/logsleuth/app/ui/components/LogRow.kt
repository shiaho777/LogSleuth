package io.github.shiaho777.logsleuth.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.logcat.DateTimePatterns
import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.core.logcat.UidNames
import io.github.shiaho777.logsleuth.app.core.logcat.VisibleColumns
import io.github.shiaho777.logsleuth.app.core.logcat.abbreviateTag
import io.github.shiaho777.logsleuth.app.core.logcat.formatLogTime
import io.github.shiaho777.logsleuth.app.core.text.ClipboardGuard
import io.github.shiaho777.logsleuth.app.core.text.matchRanges
import io.github.shiaho777.logsleuth.app.ui.theme.LocalLevelColors
import io.github.shiaho777.logsleuth.app.ui.theme.LocalLogTextScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Which columns a log row draws, and how the timestamp is written. */
data class LogRowPrefs(
    val showTime: Boolean = true,
    val showDate: Boolean = false,
    val showUid: Boolean = false,
    val showPid: Boolean = true,
    val showTid: Boolean = true,
    val showTag: Boolean = true,
    val showPackage: Boolean = true,
    /** "time" | "datetime" | "epoch" */
    val timeFormat: String = "time",
    val datePattern: String = DateTimePatterns.DATE_PRESET,
    val timePattern: String = DateTimePatterns.TIME_PRESET,
    /** Draw the whole message instead of clamping the row to 8 lines. */
    val logsExpanded: Boolean = false,
    /** Search highlight matches case. Off keeps the previous ignore-case hit. */
    val searchCaseSensitive: Boolean = false,
) {
    fun visibleColumns(): VisibleColumns = VisibleColumns(
        showDate = showDate,
        showTime = showTime,
        showUid = showUid,
        showPid = showPid,
        showTid = showTid,
        showPackage = showPackage,
        showTag = showTag,
        datePattern = datePattern,
        timePattern = timePattern,
        timeFormat = timeFormat,
    )
}

val LocalLogRowPrefs = compositionLocalOf { LogRowPrefs() }

/** Level color for the active theme (see [LocalLevelColors]). */
@Composable
fun levelColor(level: LogLevel): Color {
    val palette = LocalLevelColors.current
    return when (level) {
        LogLevel.V -> palette.V
        LogLevel.D -> palette.D
        LogLevel.I -> palette.I
        LogLevel.W -> palette.W
        LogLevel.E -> palette.E
        LogLevel.F -> palette.F
        LogLevel.A -> palette.A
    }
}

/** Black or white, whichever reads better on [background] — badge letters
 *  must stay legible on the bright dark-theme greens and oranges too. */
fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.4f) Color.Black else Color.White

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
    onMakeFilter: ((LogcatEntry) -> Unit)? = null,
) {
    // Wide windows keep the detail beside the list instead of covering it.
    // Container size (not screen size) so split-screen and foldables count.
    val density = LocalDensity.current
    val containerWidth = LocalWindowInfo.current.containerSize.width
    val wideScreen = with(density) { containerWidth.toDp() } >= 600.dp
    var showDetail by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied)

    val scale = LocalLogTextScale.current
    val prefs = LocalLogRowPrefs.current
    // Formatting a stamp per recomposition was measurable on hot streams;
    // the entry is immutable, so only a prefs change can change the text.
    val dateText = remember(entry, prefs.datePattern) {
        DateTimePatterns.format(
            entry.timestampMillis,
            prefs.datePattern,
            DateTimePatterns.DATE_PRESET,
        )
    }
    val timeText = remember(entry, prefs.timeFormat, prefs.timePattern) {
        formatLogTime(entry, prefs.timeFormat, prefs.timePattern)
    }
    val levelTint = levelColor(entry.level)
    val isError = entry.level.priority >= LogLevel.E.priority

    fun openDetail() {
        showDetail = true
    }

    fun copyRaw() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        copyGuarded(entry.raw, clipboard, snackbar, scope, context, copiedMessage)
    }

    val detailLabel = stringResource(R.string.entry_detail)
    val copyLabel = stringResource(R.string.copy_raw)
    val selectLabel = stringResource(R.string.a11y_extend_selection)
    val selecting = onClick != null

    val highlightStyle = highlightStyle(isError)
    val ignoreCase = !prefs.searchCaseSensitive
    // The list shows the abbreviated tag; the detail keeps the full one.
    // Highlight ranges are computed on the same string that is drawn.
    val displayTag = remember(entry.tag) { abbreviateTag(entry.tag) }
    val tagText = remember(displayTag, highlight, highlightStyle, ignoreCase) {
        highlighted(displayTag, highlight, highlightStyle, ignoreCase)
    }
    val messageText = remember(entry.message, highlight, highlightStyle, ignoreCase) {
        highlighted(entry.message, highlight, highlightStyle, ignoreCase)
    }

    val rowBg = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isCurrentHit -> MaterialTheme.colorScheme.tertiaryContainer
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(rowBg)
            .combinedClickable(
                onClick = { if (onClick != null) onClick() else openDetail() },
                onLongClick = {
                    if (onLongClick != null) onLongClick() else copyRaw()
                },
            )
            .semantics {
                // TalkBack cannot long-press; expose the same operations as
                // custom actions and name the tap for what it actually does.
                if (selecting) {
                    onClick(label = selectLabel) { onClick!!.invoke(); true }
                } else {
                    onClick(label = detailLabel) { openDetail(); true }
                    customActions = listOf(
                        CustomAccessibilityAction(copyLabel) { copyRaw(); true },
                    )
                }
            }
            .padding(vertical = 2.dp),
    ) {
        // Level color strip — the fastest scanning cue in a dense log list.
        // Thin, and muted below Warn so verbose/debug noise recedes while
        // warnings and errors keep full saturation.
        Box(
            modifier = Modifier
                .padding(start = 6.dp, end = 6.dp)
                .width(2.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(
                    levelTint.copy(
                        alpha = if (entry.level.priority >= LogLevel.W.priority) 1f else 0.55f,
                    ),
                )
                .align(Alignment.CenterVertically),
        )

        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val cache = LocalAppIconCache.current
                val icon = cache?.iconFor(entry.uid)
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(13.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
                if (prefs.showPackage) {
                    cache?.labelFor(entry.uid)?.let { label ->
                        Text(
                            text = label,
                            style = logMetaStyle(scale),
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 96.dp)
                                .padding(end = 4.dp),
                        )
                    }
                }
                if (prefs.showDate) {
                    Text(
                        text = dateText,
                        style = logMetaStyle(scale),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                if (prefs.showTime) {
                    Text(
                        text = timeText,
                        style = logMetaStyle(scale),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
                    )
                }
                if (prefs.showUid) {
                    val uidLabel = entry.uid?.let { UidNames.wellKnownName(it) ?: it.toString() } ?: "-"
                    Text(
                        text = uidLabel,
                        style = logMetaStyle(scale),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = MetaGap),
                    )
                }
                if (prefs.showPid || prefs.showTid) {
                    val ids = when {
                        prefs.showPid && prefs.showTid -> "${entry.pid}-${entry.tid}"
                        prefs.showPid -> entry.pid.toString()
                        else -> entry.tid.toString()
                    }
                    Text(
                        text = ids,
                        style = logMetaStyle(scale),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = MetaGap),
                    )
                }
                if (prefs.showTag && entry.tag.isNotEmpty()) {
                    Text(
                        text = tagText,
                        style = logMetaStyle(scale),
                        color = levelTint.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                // Decorative affordance only — the row's click action is
                // already labelled, so the icon must not read as a node.
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(13.dp)
                        .semantics { invisibleToUser() },
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
                maxLines = if (prefs.logsExpanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (showDetail) {
        val dismiss = { showDetail = false }
        if (wideScreen) {
            EntryDetailPane(
                entry = entry,
                snackbar = snackbar,
                onMakeFilter = onMakeFilter,
                onDismiss = dismiss,
            )
        } else {
            ModalBottomSheet(onDismissRequest = dismiss) {
                EntryDetailContent(
                    entry = entry,
                    snackbar = snackbar,
                    onMakeFilter = onMakeFilter,
                    onDismiss = dismiss,
                    modifier = Modifier.padding(bottom = 32.dp),
                )
            }
        }
    }
}

/** Fixed gap between meta columns — padding, not space characters, so the
 *  columns line up regardless of the font's space width. */
private val MetaGap = 6.dp

private fun notifyCopied(
    snackbar: SnackbarHostState?,
    scope: CoroutineScope,
    context: android.content.Context,
    message: String,
) {
    if (snackbar != null) scope.launch { snackbar.showSnackbar(message) }
    else Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/**
 * Clipboard writes are Binder transactions (~1 MiB process-wide). A glued
 * fatal block can exceed that; copy the head and say it was truncated
 * instead of crashing with TransactionTooLargeException.
 */
private fun copyGuarded(
    text: String,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    snackbar: SnackbarHostState?,
    scope: CoroutineScope,
    context: android.content.Context,
    copiedMessage: String,
) {
    if (ClipboardGuard.fits(text)) {
        clipboard.setText(AnnotatedString(text))
        notifyCopied(snackbar, scope, context, copiedMessage)
    } else {
        clipboard.setText(AnnotatedString(text.take(ClipboardGuard.MAX_CLIPBOARD_CHARS)))
        notifyCopied(
            snackbar,
            scope,
            context,
            context.getString(R.string.copy_truncated),
        )
    }
}

/**
 * Wide-screen detail: a docked pane on the right instead of a sheet, so the
 * stream stays visible beside it. A dialog window gives the pane the same
 * outside-tap/back dismissal the sheet has on phones.
 */
@Composable
private fun EntryDetailPane(
    entry: LogcatEntry,
    snackbar: SnackbarHostState?,
    onMakeFilter: ((LogcatEntry) -> Unit)?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            // Scrim: tapping outside the pane closes it, like the sheet.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(onClick = onDismiss),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.55f)
                    .widthIn(min = 360.dp, max = 560.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 1.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.entry_detail),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    }
                    HorizontalDivider()
                    EntryDetailContent(
                        entry = entry,
                        snackbar = snackbar,
                        onMakeFilter = onMakeFilter,
                        onDismiss = onDismiss,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = 12.dp, bottom = 16.dp),
                        scrollMessage = true,
                    )
                }
            }
        }
    }
}

/** The detail body shared by the phone sheet and the wide-screen pane. */
@Composable
private fun EntryDetailContent(
    entry: LogcatEntry,
    snackbar: SnackbarHostState?,
    onMakeFilter: ((LogcatEntry) -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrollMessage: Boolean = false,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied)

    Column(modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(entry.level)
            LocalAppIconCache.current?.iconFor(entry.uid)?.let { icon ->
                Spacer(Modifier.width(8.dp))
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
            LocalAppIconCache.current?.labelFor(entry.uid)?.let { label ->
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = entry.tag.ifEmpty { stringResource(R.string.entry_detail) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        val detailPackage = LocalAppIconCache.current?.packageFor(entry.uid)
        Text(
            text = "${entry.displayTime}  ·  pid ${entry.pid}  ·  tid ${entry.tid}" +
                (entry.uid?.let { "  ·  uid $it" } ?: "") +
                (detailPackage?.let { "  ·  $it" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        SelectionContainer(Modifier.weight(1f, fill = false)) {
            Text(
                text = entry.message,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = if (scrollMessage) {
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                copyGuarded(entry.message, clipboard, snackbar, scope, context, copiedMessage)
                onDismiss()
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
                copyGuarded(entry.raw, clipboard, snackbar, scope, context, copiedMessage)
                onDismiss()
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
        if (onMakeFilter != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                onMakeFilter(entry)
                onDismiss()
            }) {
                Icon(
                    Icons.Default.FilterAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.filter_from_line))
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
    fontSize = 12.sp * scale,
    lineHeight = 16.sp * scale,
)

@Composable
fun LevelBadge(level: LogLevel) {
    val tint = levelColor(level)
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.letter.toString(),
            color = onColorFor(tint),
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

private fun highlighted(
    text: String,
    query: String?,
    style: SpanStyle,
    ignoreCase: Boolean,
): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)
    val ranges = matchRanges(text, query, ignoreCase)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges) {
            if (range.first > cursor) append(text.substring(cursor, range.first))
            withStyle(style) { append(text.substring(range.first, range.last + 1)) }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
