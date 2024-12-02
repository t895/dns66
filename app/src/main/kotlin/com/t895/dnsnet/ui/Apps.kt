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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.t895.dnsnet.AllowListMode
import com.t895.dnsnet.AllowListMode.Companion.toAllowListMode
import com.t895.dnsnet.R
import com.t895.dnsnet.ui.theme.DnsNetTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    showSystemApps: Boolean,
    onShowSystemAppsClick: () -> Unit,
    bypassSelection: AllowListMode,
    onBypassSelection: (AllowListMode) -> Unit,
    apps: List<App> = emptyList(),
    onAppClick: (App) -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                threshold = PullToRefreshDefaults.PositionalThreshold + contentPadding.calculateTopPadding(),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.testTag("apps:list"),
            contentPadding = contentPadding,
            state = listState,
        ) {
            item {
                ListSettingsContainer(
                    title = stringResource(R.string.allowlist_description),
                ) {
                    SwitchListItem(
                        modifier = Modifier.testTag("apps:showSystemApps"),
                        title = stringResource(R.string.switch_show_system_apps),
                        checked = showSystemApps,
                        onCheckedChange = { onShowSystemAppsClick() },
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
                                checked = thisMode == bypassSelection,
                                title = option,
                                onCheckedChange = { onBypassSelection(thisMode) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(vertical = 4.dp))
            }

            items(apps) {
                SwitchListItem(
                    modifier = Modifier.testTag("apps:listItem"),
                    title = it.label,
                    details = it.info.packageName,
                    checked = it.enabled,
                    enabled = enabled,
                    onCheckedChange = { _ -> onAppClick(it) },
                    startContent = {
                        Image(
                            modifier = Modifier.fillMaxSize(),
                            painter = rememberAsyncImagePainter(it),
                            contentDescription = it.label,
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
            bypassSelection = AllowListMode.ON_VPN,
            onBypassSelection = {},
        )
    }
}
