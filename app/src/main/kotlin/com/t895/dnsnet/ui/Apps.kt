/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.t895.dnsnet.AllowListMode.Companion.toAllowListMode
import com.t895.dnsnet.R
import com.t895.dnsnet.ui.theme.DnsNetTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    showSystemApps: Boolean,
    onShowSystemAppsClick: () -> Unit,
    bypassSelection: com.t895.dnsnet.AllowListMode,
    onBypassSelection: (com.t895.dnsnet.AllowListMode) -> Unit,
    apps: List<App> = emptyList(),
    onAppClick: (App) -> Unit,
) {
    val pm = LocalContext.current.packageManager
    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        LazyColumn(contentPadding = contentPadding) {
            item {
                ListSettingsContainer(
                    title = stringResource(R.string.allowlist_description),
                ) {
                    SwitchListItem(
                        title = stringResource(R.string.switch_show_system_apps),
                        checked = showSystemApps,
                        enabled = enabled,
                        sharedInteractionSource = remember { MutableInteractionSource() },
                        onCheckedChange = { onShowSystemAppsClick() },
                        onClick = onShowSystemAppsClick,
                    )

                    var expanded by rememberSaveable { mutableStateOf(false) }
                    val bypassOptions = stringArrayResource(R.array.allowlist_defaults)
                    ExpandableOptionsItem(
                        expanded = expanded,
                        enabled = enabled,
                        title = stringResource(R.string.allowlist_defaults_title),
                        details = bypassOptions[bypassSelection.ordinal],
                        sharedInteractionSource = remember { MutableInteractionSource() },
                        onExpandClick = { expanded = !expanded },
                    ) {
                        bypassOptions.forEachIndexed { i, option ->
                            val thisMode = i.toAllowListMode()
                            RadioListItem(
                                selected = thisMode == bypassSelection,
                                title = option,
                                onClick = { onBypassSelection(thisMode) },
                                onButtonClick = { onBypassSelection(thisMode) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(vertical = 4.dp))
            }

            items(apps) {
                IconSwitchListItem(
                    title = it.label,
                    details = it.info.packageName,
                    checked = it.enabled,
                    enabled = enabled,
                    sharedInteractionSource = remember { MutableInteractionSource() },
                    onCheckedChange = { _ -> onAppClick(it) },
                    onClick = { onAppClick(it) },
                    iconContent = {
                        it.loadIcon(pm)
                        Image(
                            modifier = Modifier.fillMaxSize(),
                            painter = rememberDrawablePainter(it.getIcon()),
                            contentDescription = null,
                        )
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppsScreenPreview() {
    DnsNetTheme {
        AppsScreen(
            isRefreshing = false,
            enabled = true,
            onRefresh = {},
            apps = listOf(App(ApplicationInfo(), "Label", true)),
            onAppClick = {},
            showSystemApps = false,
            onShowSystemAppsClick = {},
            bypassSelection = com.t895.dnsnet.AllowListMode.ON_VPN,
            onBypassSelection = {},
        )
    }
}
