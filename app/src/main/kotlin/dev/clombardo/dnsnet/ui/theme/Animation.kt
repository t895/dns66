/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.clombardo.dnsnet.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

val HomeEnterTransition = scaleIn(
    initialScale = 0.75f,
    animationSpec = tween(durationMillis = 400, easing = EmphasizedDecelerateEasing)
) + fadeIn(animationSpec = tween(400))
val HomeExitTransition = fadeOut(animationSpec = tween(50))

private const val SIZE_FRACTION = 12
private val PositiveOffset = { fullSize: IntSize ->
    IntOffset(fullSize.width / SIZE_FRACTION, 0)
}
private val NegativeOffset = { fullSize: IntSize ->
    IntOffset(-fullSize.width / SIZE_FRACTION, 0)
}
private val TopLevelFadeEnterSpec = tween<Float>(400)
private val TopLevelFadeExitSpec = tween<Float>(100)

val TopLevelEnter = slideIn(
    initialOffset = PositiveOffset,
) + fadeIn(animationSpec = TopLevelFadeEnterSpec)
val TopLevelPopEnter = slideIn(
    initialOffset = NegativeOffset,
) + fadeIn(animationSpec = TopLevelFadeEnterSpec)
val TopLevelExit = slideOut(
    targetOffset = NegativeOffset,
) + fadeOut(animationSpec = TopLevelFadeExitSpec)
val TopLevelPopExit = slideOut(
    targetOffset = PositiveOffset,
) + fadeOut(animationSpec = TopLevelFadeExitSpec)

val ShowScrollUpIndicator = slideInVertically { it }
val HideScrollUpIndicator = slideOutVertically { it }

val ShowRefreshHostFilesSpinner = fadeIn() + expandHorizontally(
    animationSpec = tween(250),
    expandFrom = Alignment.Start,
    clip = false,
)
val HideRefreshHostFilesSpinner = fadeOut() + shrinkHorizontally(
    animationSpec = tween(250),
    shrinkTowards = Alignment.Start,
    clip = false,
)
