/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.window.core.layout.WindowWidthSizeClass
import com.t895.dnsnet.R
import com.t895.dnsnet.ui.theme.DnsNetTheme
import com.t895.dnsnet.ui.theme.FabPadding
import com.t895.dnsnet.vpn.VpnStatus

@Composable
fun StartScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: ScrollState = rememberScrollState(),
    enabled: Boolean,
    resumeOnStartup: Boolean,
    onResumeOnStartupClick: () -> Unit,
    watchConnection: Boolean,
    onWatchConnectionClick: () -> Unit,
    ipv6Support: Boolean,
    onIpv6SupportClick: () -> Unit,
    status: VpnStatus,
    onChangeVpnStatusClick: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .padding(start = contentPadding.calculateStartPadding(layoutDirection))
                .padding(end = contentPadding.calculateEndPadding(layoutDirection))
                .verticalScroll(listState),
        ) {
            Spacer(Modifier.padding(top = contentPadding.calculateTopPadding()))
            ListSettingsContainer {
                SwitchListItem(
                    title = stringResource(id = R.string.switch_onboot),
                    details = stringResource(id = R.string.switch_onboot_description),
                    checked = resumeOnStartup,
                    onCheckedChange = { onResumeOnStartupClick() },
                )
                SwitchListItem(
                    title = stringResource(id = R.string.watchdog),
                    details = stringResource(id = R.string.watchdog_description),
                    checked = watchConnection,
                    enabled = enabled,
                    onCheckedChange = { onWatchConnectionClick() },
                )
                SwitchListItem(
                    title = stringResource(id = R.string.ipv6_support),
                    details = stringResource(id = R.string.ipv6_support_description),
                    checked = ipv6Support,
                    enabled = enabled,
                    onCheckedChange = { onIpv6SupportClick() },
                )
            }
            Spacer(Modifier.padding(bottom = contentPadding.calculateBottomPadding()))
        }

        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT) {
                Alignment.BottomCenter
            } else {
                Alignment.BottomEnd
            },
        ) {
            VpnFab(
                modifier = Modifier
                    .then(
                        if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT) {
                            Modifier
                        } else {
                            Modifier.displayCutoutPadding()
                        }
                    )
                    .padding(FabPadding),
                status = status,
                onClick = onChangeVpnStatusClick,
            )
        }
    }
}

@Preview
@Composable
private fun StartScreenPreview() {
    DnsNetTheme {
        StartScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            enabled = true,
            resumeOnStartup = false,
            onResumeOnStartupClick = {},
            watchConnection = false,
            onWatchConnectionClick = {},
            ipv6Support = false,
            onIpv6SupportClick = {},
            status = VpnStatus.STOPPED,
            onChangeVpnStatusClick = {},
        )
    }
}
