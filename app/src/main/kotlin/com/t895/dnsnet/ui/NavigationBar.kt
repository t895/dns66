package com.t895.dnsnet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collection.MutableVector
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.t895.dnsnet.ui.theme.DnsNetTheme
import com.t895.dnsnet.ui.theme.EmphasizedDecelerateEasing
import com.t895.dnsnet.vpn.VpnStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class NavigationBarItem(
    val selected: Boolean,
    val icon: ImageVector,
    val text: String,
    val onClick: () -> Unit,
)

sealed interface NavigationBarScope {
    val itemList: MutableVector<NavigationBarItem>

    fun item(
        selected: Boolean,
        icon: ImageVector,
        text: String,
        onClick: () -> Unit,
    )
}

class NavigationBarScopeImpl : NavigationBarScope {
    override val itemList: MutableVector<NavigationBarItem> = mutableVectorOf()

    override fun item(selected: Boolean, icon: ImageVector, text: String, onClick: () -> Unit) {
        itemList.add(
            NavigationBarItem(
                selected = selected,
                icon = icon,
                text = text,
                onClick = onClick,
            )
        )
    }
}

@Composable
fun NavigationBarItem(
    modifier: Modifier = Modifier,
    item: NavigationBarItem,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .sizeIn(minWidth = 64.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = item.onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.padding(bottom = 12.dp))
        Box(contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(16.dp)
            val animatedWidth by animateDpAsState(
                targetValue = if (item.selected) 64.dp else 0.dp,
                animationSpec = tween(
                    durationMillis = if (item.selected) 250 else 0,
                    easing = EmphasizedDecelerateEasing,
                ),
                label = "animatedWidth",
            )

            Box(
                modifier = Modifier
                    .size(width = animatedWidth, height = 32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = shape,
                    )
            )
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 32.dp)
                    .clip(shape)
                    .indication(
                        interactionSource = interactionSource,
                        indication = ripple(),
                    )
            )
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = item.icon,
                contentDescription = null,
                tint = if (item.selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.padding(vertical = 2.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            fontWeight = if (item.selected) {
                FontWeight.Bold
            } else {
                null
            },
            color = if (item.selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.padding(bottom = 16.dp))
    }
}

@Preview
@Composable
private fun NavigationBarItemPreview() {
    DnsNetTheme {
        var selected by remember { mutableStateOf(false) }
        NavigationBarItem(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            item = NavigationBarItem(
                selected = selected,
                icon = Icons.Default.VpnKey,
                text = "Start",
                onClick = { selected = !selected },
            )
        )
    }
}

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    status: VpnStatus,
    onStatusButtonClick: () -> Unit,
    windowInsets: WindowInsets = WindowInsets.systemBars,
    content: NavigationBarScope.() -> Unit,
) {
    val latestContent = rememberUpdatedState(content)
    val scope by remember { derivedStateOf { NavigationBarScopeImpl().apply(latestContent.value) } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = status == VpnStatus.RUNNING || status == VpnStatus.STOPPED,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Button(onClick = onStatusButtonClick) {
                if (status == VpnStatus.STOPPED || status == VpnStatus.RUNNING) {
                    Icon(
                        imageVector = if (status == VpnStatus.STOPPED) {
                            Icons.Default.PlayArrow
                        } else {
                            Icons.Default.Stop
                        },
                        contentDescription = null,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = status != VpnStatus.RUNNING && status != VpnStatus.STOPPED,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Square,
            )
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = windowInsets.getBottom(LocalDensity.current).dp)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                scope.itemList.forEach {
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        item = it,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun NavigationBarPreview() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf(VpnStatus.STOPPED) }
    val coroutineScope = rememberCoroutineScope()
    NavigationBar(
        status = status,
        onStatusButtonClick = {
            coroutineScope.launch {
                if (status == VpnStatus.STOPPED) {
                    status = VpnStatus.STARTING
                    delay(1000)
                    status = VpnStatus.RUNNING
                } else {
                    status = VpnStatus.STOPPING
                    delay(1000)
                    status = VpnStatus.STOPPED
                }
            }
        },
    ) {
        item(
            selected = selectedIndex == 0,
            icon = Icons.Default.VpnKey,
            text = "Start",
            onClick = { selectedIndex = 0 },
        )
        item(
            selected = selectedIndex == 1,
            icon = Icons.Default.Dns,
            text = "DNS",
            onClick = { selectedIndex = 1 },
        )
        item(
            selected = selectedIndex == 2,
            icon = Icons.Default.Android,
            text = "Apps",
            onClick = { selectedIndex = 2 },
        )
    }
}
