package io.github.shiaho777.logsleuth.app.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.ui.guide.tourTarget
import io.github.shiaho777.logsleuth.app.ui.theme.instrumentSurface

@Composable
internal fun InstrumentNavigation(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(insets)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .tourTarget("navBar")
                .instrumentSurface()
                .padding(4.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val selection = animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(160),
                    label = "tabSelection",
                )
                val foreground = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .drawBehind {
                            val progress = selection.value
                            if (progress > 0f) {
                                drawRoundRect(
                                    color = lerp(colors.primaryContainer.copy(alpha = 0f), colors.primaryContainer, progress),
                                    cornerRadius = CornerRadius(15.dp.toPx()),
                                )
                                val width = 13.dp.toPx() * progress
                                drawRoundRect(
                                    color = colors.primary.copy(alpha = progress),
                                    topLeft = Offset((size.width - width) / 2f, size.height - 3.dp.toPx()),
                                    size = Size(width, 2.dp.toPx()),
                                    cornerRadius = CornerRadius(1.dp.toPx()),
                                )
                            }
                        }
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                ) {
                    Icon(destination.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = foreground,
                    )
                }
            }
        }
    }
}
