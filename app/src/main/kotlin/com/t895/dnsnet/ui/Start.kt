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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    blockLog: Boolean,
    onToggleBlockLog: () -> Unit,
    onOpenBlockLog: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onShareLogcat: () -> Unit,
    onResetSettings: () -> Unit,
    onOpenAbout: () -> Unit,
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
                SplitSwitchListItem(
                    title = stringResource(id = R.string.block_log),
                    details = stringResource(id = R.string.block_log_description),
                    maxDetailLines = Int.MAX_VALUE,
                    outlineColor = MaterialTheme.colorScheme.outline,
                    checked = blockLog,
                    switchEnabled = enabled,
                    bodyEnabled = blockLog,
                    onCheckedChange = { onToggleBlockLog() },
                    onBodyClick = onOpenBlockLog,
                )
            }
            Spacer(Modifier.padding(vertical = 4.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalSettingsButton(
                    enabled = enabled,
                    title = stringResource(R.string.action_import),
                    description = stringResource(R.string.import_description),
                    icon = Icons.Default.Download,
                    onClick = onImport,
                )
                FilledTonalSettingsButton(
                    title = stringResource(R.string.action_export),
                    description = stringResource(R.string.export_description),
                    icon = Icons.Default.Upload,
                    onClick = onExport,
                )
                FilledTonalSettingsButton(
                    title = stringResource(R.string.action_logcat),
                    description = stringResource(R.string.logcat_description),
                    icon = Icons.Default.BugReport,
                    onClick = onShareLogcat,
                )
                FilledTonalSettingsButton(
                    enabled = enabled,
                    title = stringResource(R.string.load_defaults),
                    description = stringResource(R.string.load_defaults_description),
                    icon = Icons.Default.History,
                    onClick = onResetSettings,
                )
                FilledTonalSettingsButton(
                    title = stringResource(R.string.action_about),
                    description = stringResource(R.string.about_description),
                    icon = Icons.Default.Info,
                    onClick = onOpenAbout,
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
            blockLog = true,
            onToggleBlockLog = {},
            onOpenBlockLog = {},
            onImport = {},
            onExport = {},
            onShareLogcat = {},
            onResetSettings = {},
            onOpenAbout = {},
        )
    }
}
