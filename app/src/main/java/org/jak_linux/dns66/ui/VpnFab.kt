package org.jak_linux.dns66.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jak_linux.dns66.ui.theme.Dns66Theme
import org.jak_linux.dns66.ui.theme.VpnFabSize
import org.jak_linux.dns66.vpn.VpnStatus

@Composable
fun VpnFab(
    modifier: Modifier = Modifier,
    status: VpnStatus,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animationDurationMillis = 100
    val fadeIn = fadeIn(animationSpec = tween(durationMillis = animationDurationMillis))
    val fadeOut = fadeOut(animationSpec = tween(durationMillis = animationDurationMillis))
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 0.dp else 6.dp,
        animationSpec = tween(durationMillis = animationDurationMillis),
        label = "elevation",
    )
    val containerColor by animateColorAsState(
        targetValue = when (status) {
            VpnStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = animationDurationMillis),
        label = "containerColor",
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
            )
            .clip(shape)
            .background(containerColor)
            .size(VpnFabSize)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val contentSize = 42.dp
        val contentColor by animateColorAsState(
            targetValue = when (status) {
                VpnStatus.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onPrimary
            },
            animationSpec = tween(durationMillis = animationDurationMillis),
            label = "contentColor",
        )

        AnimatedVisibility(
            visible = status == VpnStatus.RUNNING,
            enter = fadeIn,
            exit = fadeOut,
        ) {
            Icon(
                modifier = Modifier.size(contentSize),
                imageVector = Icons.Default.Stop,
                contentDescription = null,
                tint = contentColor,
            )
        }

        AnimatedVisibility(
            visible = status == VpnStatus.STOPPED,
            enter = fadeIn,
            exit = fadeOut,
        ) {
            Icon(
                modifier = Modifier.size(contentSize),
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}

@Preview
@Composable
private fun VpnFabPreview() {
    Dns66Theme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .size(128.dp),
            contentAlignment = Alignment.Center,
        ) {
            var status by remember { mutableStateOf(VpnStatus.STOPPED) }
            VpnFab(status = status) {
                if (status == VpnStatus.RUNNING) {
                    status = VpnStatus.STOPPED
                } else if (status == VpnStatus.STARTING) {
                    status = VpnStatus.RUNNING
                } else if (status == VpnStatus.STOPPED) {
                    status = VpnStatus.STARTING
                }
            }
        }
    }
}
