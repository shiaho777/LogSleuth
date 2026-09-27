package io.github.shiaho777.logsleuth.app.ui.guide

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import kotlin.math.abs
import kotlinx.coroutines.launch

/** One tour page: hero icon + title + description + up to 3 bullets. */
private data class GuidePage(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val accent: GuideAccent,
    val bullets: List<Pair<ImageVector, Int>> = emptyList(),
)

private enum class GuideAccent { PRIMARY, SECONDARY, TERTIARY }

private val pages = listOf(
    GuidePage(
        icon = Icons.Default.Terminal,
        titleRes = R.string.guide_welcome_title,
        descRes = R.string.guide_welcome_desc,
        accent = GuideAccent.PRIMARY,
    ),
    GuidePage(
        icon = Icons.Default.KeyboardArrowDown,
        titleRes = R.string.guide_stream_title,
        descRes = R.string.guide_stream_desc,
        accent = GuideAccent.SECONDARY,
        bullets = listOf(
            Icons.Default.KeyboardArrowDown to R.string.guide_stream_b1,
            Icons.Default.Pause to R.string.guide_stream_b2,
            Icons.Default.Search to R.string.guide_stream_b3,
        ),
    ),
    GuidePage(
        icon = Icons.Default.FilterList,
        titleRes = R.string.guide_filter_title,
        descRes = R.string.guide_filter_desc,
        accent = GuideAccent.TERTIARY,
        bullets = listOf(
            Icons.Default.FilterList to R.string.guide_filter_b1,
            Icons.Default.Apps to R.string.guide_filter_b2,
            Icons.Default.Bookmarks to R.string.guide_filter_b3,
        ),
    ),
    GuidePage(
        icon = Icons.Default.History,
        titleRes = R.string.guide_sessions_title,
        descRes = R.string.guide_sessions_desc,
        accent = GuideAccent.PRIMARY,
        bullets = listOf(
            Icons.Default.Save to R.string.guide_sessions_b1,
            Icons.Default.PlayArrow to R.string.guide_sessions_b2,
            Icons.Default.Share to R.string.guide_sessions_b3,
        ),
    ),
    GuidePage(
        icon = Icons.Default.BugReport,
        titleRes = R.string.guide_crashes_title,
        descRes = R.string.guide_crashes_desc,
        accent = GuideAccent.SECONDARY,
        bullets = listOf(
            Icons.Default.BugReport to R.string.guide_crashes_b1,
            Icons.Default.Notifications to R.string.guide_crashes_b2,
            Icons.Default.History to R.string.guide_crashes_b3,
        ),
    ),
    GuidePage(
        icon = Icons.Default.BubbleChart,
        titleRes = R.string.guide_bubble_title,
        descRes = R.string.guide_bubble_desc,
        accent = GuideAccent.TERTIARY,
        bullets = listOf(
            Icons.Default.BubbleChart to R.string.guide_bubble_b1,
            Icons.Default.OpenInNew to R.string.guide_bubble_b2,
        ),
    ),
)

/**
 * First-run feature tour: a HorizontalPager of six ordered pages.
 * All motion derives from the pager offset (parallax, staggered bullets)
 * or infinite idle loops (hero bob) — nothing starts mid-gesture, so the
 * tour tracks the finger one-to-one and settles with a soft spring.
 */
@Composable
fun GuideScreen(
    onDone: () -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.size - 1

    fun finish() {
        viewModel.markCompleted()
        onDone()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Top row: skip stays out of the thumb path, dimmed on arrival
            // so it reads as optional, not the primary action.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = ::finish,
                    enabled = !isLast,
                ) {
                    Text(
                        stringResource(R.string.guide_skip),
                        color = if (isLast) {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                // Signed offset: 0 = settled, ±1 = one full page away.
                // Parallax and fades are continuous functions of it, so
                // content tracks the drag exactly — no per-page animation
                // restart, no settle snap.
                val offset = (pagerState.currentPage - page) +
                    pagerState.currentPageOffsetFraction
                GuidePageContent(pages[page], offset)
            }

            // Bottom bar: dots centered, next/done pinned end.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                PageDots(
                    count = pages.size,
                    current = pagerState.currentPage,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Button(
                    onClick = {
                        if (isLast) {
                            finish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Text(
                        stringResource(
                            if (isLast) R.string.guide_done else R.string.guide_next,
                        ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (isLast) {
                            Icons.Default.PlayArrow
                        } else {
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidePageContent(page: GuidePage, offset: Float) {
    val focus = (1f - abs(offset)).coerceIn(0f, 1f)
    val accent = when (page.accent) {
        GuideAccent.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.primary
        GuideAccent.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.secondary
        GuideAccent.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.tertiary
    }
    // Idle float — a slow 6dp bob keeps the hero alive while the page
    // is parked. Pauses naturally off-screen via the same composable.
    val bob by rememberInfiniteTransition(label = "hero").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing)),
        label = "bob",
    )
    val bobDp = (bob - 0.5f) * 12f // -6..+6 dp over the loop

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))

        // Hero: leads the parallax (full offset), scales up on approach.
        val heroScale = 0.82f + 0.18f * focus
        Surface(
            shape = CircleShape,
            color = accent.first,
            modifier = Modifier
                .size(128.dp)
                .graphicsLayer {
                    scaleX = heroScale
                    scaleY = heroScale
                    translationY = offset * -56f + bobDp * density
                    alpha = 0.35f + 0.65f * focus
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    tint = accent.second,
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        Text(
            stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                translationY = offset * -34f
                alpha = focus
            },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(page.descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                translationY = offset * -20f
                alpha = focus
            },
        )

        if (page.bullets.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Trailing parallax: deeper bullets lag further behind,
                // so the column cascades into place during the swipe.
                page.bullets.forEachIndexed { i, (icon, labelRes) ->
                    val lag = 1f + i * 0.35f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest
                                    .copy(alpha = 0.55f),
                            )
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                            .graphicsLayer {
                                translationY = offset * -14f * lag
                                alpha = (focus * 1.6f - i * 0.18f).coerceIn(0f, 1f)
                            },
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accent.first,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = accent.second,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/** Sliding pill indicator — active dot stretches, inactive dots shrink. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(
                targetValue = if (active) 22.dp else 8.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "dotW",
            )
            val color by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                animationSpec = tween(220),
                label = "dotC",
            )
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}
