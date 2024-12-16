/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.clombardo.dnsnet.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.clombardo.dnsnet.ui.theme.DnsNetTheme

object NavigationBar {
    val height = 80.dp
}

object NavigationBarDefaults {
    val itemSelectedIndicatorWidth = 64.dp
    val itemHeight = 64.dp
}

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.systemBars,
    content: NavigationScope.() -> Unit,
) {
    val latestContent = rememberUpdatedState(content)
    val scope by remember { derivedStateOf { NavigationScopeImpl().apply(latestContent.value) } }

    val insets = windowInsets.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = modifier
            .clickable(
                enabled = false,
                interactionSource = null,
                indication = null,
                onClick = {},
            )
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection),
                bottom = insets.calculateBottomPadding(),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        scope.itemList.forEach {
            NavigationItem(
                modifier = Modifier.weight(1f),
                layoutType = LayoutType.NavigationBar,
                item = it,
            )
        }
    }
}

@Preview
@Composable
private fun NavigationBarPreview() {
    DnsNetTheme {
        var selectedIndex by remember { mutableIntStateOf(0) }
        NavigationBar {
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
}
