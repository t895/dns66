package org.jak_linux.dns66.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

val HomeEnterTransition = scaleIn(
    initialScale = 0.75f,
    animationSpec = tween(durationMillis = 400, easing = EmphasizedDecelerateEasing)
) + fadeIn(animationSpec = tween(400))
val HomeExitTransition = fadeOut(animationSpec = tween(50))

private const val sizeFraction = 12
private val positiveOffset = { fullSize: IntSize ->
    IntOffset(fullSize.width / sizeFraction, 0)
}
private val negativeOffset = { fullSize: IntSize ->
    IntOffset(-fullSize.width / sizeFraction, 0)
}
private val TopLevelFadeEnterSpec = tween<Float>(400)
private val TopLevelFadeExitSpec = tween<Float>(100)

val TopLevelEnter = slideIn(
    initialOffset = positiveOffset,
) + fadeIn(animationSpec = TopLevelFadeEnterSpec)
val TopLevelPopEnter = slideIn(
    initialOffset = negativeOffset,
) + fadeIn(animationSpec = TopLevelFadeEnterSpec)
val TopLevelExit = slideOut(
    targetOffset = negativeOffset,
) + fadeOut(animationSpec = TopLevelFadeExitSpec)
val TopLevelPopExit = slideOut(
    targetOffset = positiveOffset,
) + fadeOut(animationSpec = TopLevelFadeExitSpec)
