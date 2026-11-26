package io.github.shiaho777.logsleuth.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shiaho777.logsleuth.app.ui.theme.SleuthMotion
import io.github.shiaho777.logsleuth.app.ui.theme.instrumentSurface
import io.github.shiaho777.logsleuth.app.ui.theme.selectionPlate
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
internal fun SpringPagerTabs(
    pager: PagerState,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .instrumentSurface()
            .padding(4.dp)
            .selectionPlate(labels.size, { pager.currentPage + pager.currentPageOffsetFraction }, colors.primaryContainer, 14.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val coverage by remember(pager, index) {
                derivedStateOf {
                    val position = pager.currentPage + pager.currentPageOffsetFraction
                    (1f - abs(position - index)).coerceIn(0f, 1f)
                }
            }
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(MaterialTheme.shapes.medium)
                    .selectable(
                        selected = pager.currentPage == index,
                        role = Role.Tab,
                        onClick = {
                            if (pager.targetPage != index) {
                                scope.launch { pager.animateScrollToPage(index, animationSpec = SleuthMotion.spatial()) }
                            }
                        },
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = lerp(colors.onSurfaceVariant, colors.onPrimaryContainer, coverage),
                )
            }
        }
    }
}
