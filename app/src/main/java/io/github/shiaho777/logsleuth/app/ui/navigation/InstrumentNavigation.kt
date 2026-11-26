package io.github.shiaho777.logsleuth.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.ui.guide.tourTarget
import io.github.shiaho777.logsleuth.app.ui.theme.instrumentSurface
import kotlin.math.abs

@Composable
internal fun InstrumentNavigation(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }
    var lastSelectedIndex by rememberSaveable { mutableStateOf(0) }
    if (selectedIndex >= 0) {
        SideEffect { lastSelectedIndex = selectedIndex }
    }
    val position = animateFloatAsState(
        targetValue = (if (selectedIndex >= 0) selectedIndex else lastSelectedIndex).toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navigationSelection",
    )
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    Box(
        Modifier.fillMaxWidth().windowInsetsPadding(insets),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = selectedIndex >= 0,
            enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
            NavigationPlate(destinations, selectedIndex, position, onSelect)
        }
    }
}

@Composable
private fun NavigationPlate(
    destinations: List<TopLevelDestination>,
    selectedIndex: Int,
    position: State<Float>,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .tourTarget("navBar")
            .instrumentSurface()
            .padding(4.dp)
            .drawBehind {
                val tabWidth = size.width / destinations.size
                val index = position.value.coerceIn(0f, destinations.lastIndex.toFloat())
                val visualIndex = if (layoutDirection == LayoutDirection.Rtl) destinations.lastIndex - index else index
                drawRoundRect(
                    color = colors.primaryContainer,
                    topLeft = Offset(tabWidth * visualIndex, 0f),
                    size = Size(tabWidth, size.height),
                    cornerRadius = CornerRadius(15.dp.toPx()),
                )
            }
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEachIndexed { index, destination ->
            NavigationTab(
                destination = destination,
                selected = index == selectedIndex,
                index = index,
                position = position,
                onSelect = { onSelect(destination) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavigationTab(
    destination: TopLevelDestination,
    selected: Boolean,
    index: Int,
    position: State<Float>,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coverage by remember(position, index) {
        derivedStateOf { (1f - abs(position.value - index)).coerceIn(0f, 1f) }
    }
    val colors = MaterialTheme.colorScheme
    val foreground = lerp(colors.onSurfaceVariant, colors.onPrimaryContainer, coverage)
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
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
