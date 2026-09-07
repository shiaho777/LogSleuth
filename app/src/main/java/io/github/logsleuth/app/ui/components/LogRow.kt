package io.github.logsleuth.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.logsleuth.app.R
import io.github.logsleuth.app.core.logcat.LogLevel
import io.github.logsleuth.app.core.logcat.LogcatEntry
import io.github.logsleuth.app.ui.theme.LevelColors

fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.V -> LevelColors.V
    LogLevel.D -> LevelColors.D
    LogLevel.I -> LevelColors.I
    LogLevel.W -> LevelColors.W
    LogLevel.E -> LevelColors.E
    LogLevel.F -> LevelColors.F
}

@Composable
fun LogRow(
    entry: LogcatEntry,
    highlight: String? = null,
    isCurrentHit: Boolean = false,
) {
    var showDetail by remember { mutableStateOf(false) }

    val bg = when {
        isCurrentHit -> MaterialTheme.colorScheme.tertiaryContainer
        entry.level.priority >= LogLevel.E.priority -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable { showDetail = true }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LevelBadge(entry.level)
        Text(
            text = highlighted(entry.displayTime, highlight),
            style = LogTextStyle,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 6.dp),
        )
        Text(
            text = highlighted("${entry.pid}-${entry.tid}", highlight),
            style = LogTextStyle,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 6.dp),
        )
        Column(Modifier.padding(start = 6.dp)) {
            if (entry.tag.isNotEmpty()) {
                Text(
                    text = highlighted(entry.tag, highlight),
                    style = LogTextStyle,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = highlighted(entry.message, highlight),
                style = LogTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (showDetail) {
        AlertDialog(
            onDismissRequest = { showDetail = false },
            confirmButton = {
                TextButton(onClick = { showDetail = false }) { Text(stringResource(R.string.close)) }
            },
            text = {
                SelectionContainer {
                    Text(
                        text = "${entry.displayTime}  ${entry.pid}-${entry.tid}  ${entry.level.letter}/${entry.tag}\n\n${entry.message}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            },
        )
    }
}

private val LogTextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
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

private fun highlighted(text: String, query: String?): AnnotatedString {
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
            withStyle(SpanStyle(background = Color(0x66FFD54F))) {
                append(text.substring(idx, idx + q.length))
            }
            start = idx + q.length
        }
    }
}
