package io.github.shiaho777.logsleuth.app.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

internal object SleuthMotion : MotionScheme {
    fun <T> spatial(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = visibilityThreshold)

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spatial()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(stiffness = Spring.StiffnessMedium)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(stiffness = Spring.StiffnessLow)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = fastSpatialSpec()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(stiffness = Spring.StiffnessMedium * 1.5f)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = defaultSpatialSpec()

    fun enter(offset: Int) = fadeIn(defaultEffectsSpec()) +
        slideInHorizontally(spatial(IntOffset.VisibilityThreshold)) { offset }

    fun exit(offset: Int) = fadeOut(defaultEffectsSpec()) +
        slideOutHorizontally(spatial(IntOffset.VisibilityThreshold)) { -offset }

    fun swap(offset: Int) = ContentTransform(
        targetContentEnter = enter(offset),
        initialContentExit = exit(offset),
        sizeTransform = SizeTransform { _, _ -> spatial(IntSize.VisibilityThreshold) },
    )

    fun expand(from: Alignment.Vertical = Alignment.Top) =
        expandVertically(spatial(IntSize.VisibilityThreshold), expandFrom = from) + fadeIn(defaultEffectsSpec())

    fun collapse(towards: Alignment.Vertical = Alignment.Top) =
        shrinkVertically(spatial(IntSize.VisibilityThreshold), shrinkTowards = towards) + fadeOut(defaultEffectsSpec())
}

internal fun Modifier.selectionPlate(
    count: Int,
    position: () -> Float,
    color: Color,
    radius: Dp,
): Modifier = drawBehind {
    val tabWidth = size.width / count
    val logicalIndex = position().coerceIn(0f, (count - 1).toFloat())
    val visualIndex = if (layoutDirection == LayoutDirection.Rtl) count - 1 - logicalIndex else logicalIndex
    drawRoundRect(
        color = color,
        topLeft = Offset(tabWidth * visualIndex, 0f),
        size = Size(tabWidth, size.height),
        cornerRadius = CornerRadius(radius.toPx()),
    )
}
