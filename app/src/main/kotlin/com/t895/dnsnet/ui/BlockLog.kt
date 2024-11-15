/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t895.dnsnet.R
import com.t895.dnsnet.ui.theme.ListPadding
import com.t895.dnsnet.ui.theme.ScrollUpIndicatorPadding
import com.t895.dnsnet.ui.theme.ScrollUpIndicatorSize
import com.t895.dnsnet.vpn.LoggedConnectionState

@Composable
fun BlockLog(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    contentPadding: PaddingValues,
    loggedConnections: List<LoggedConnectionState>,
) {
    val allowedString = stringResource(R.string.allowed)
    val blockedString = stringResource(R.string.blocked)
    val allowedColor = MaterialTheme.colorScheme.onSurface
    val blockedColor = MaterialTheme.colorScheme.error
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding + PaddingValues(ListPadding) +
                PaddingValues(bottom = ScrollUpIndicatorPadding + ScrollUpIndicatorSize),
    ) {
        item {
            val blockedConnections = loggedConnections.count { !it.allowed }
            val blockedConnectionsPercent =
                blockedConnections.toFloat() / loggedConnections.size.toFloat()
            val blockedRatioAnimated by animateFloatAsState(
                targetValue = blockedConnectionsPercent,
                label = "blockedRatioAnimated",
            )
            val size = 256.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(size),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size),
                    progress = {
                        if (blockedRatioAnimated.isNaN()) 0f else blockedRatioAnimated
                    },
                    strokeWidth = 14.dp,
                )

                val blockedConnectionsString = stringResource(
                    id = R.string.blocked_connections_percent,
                    formatArgs = arrayOf((blockedConnectionsPercent * 100).toInt()),
                )
                Text(
                    modifier = Modifier
                        .width(size * 0.65f)
                        .heightIn(min = 0.dp, max = size * 0.65f),
                    text = blockedConnectionsString,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.padding(vertical = 8.dp))
        }

        items(loggedConnections) {
            ContentSetting(
                modifier = Modifier.animateItem(),
                title = it.name,
                details = if (it.allowed) allowedString else blockedString,
                endContent = {
                    Text(
                        text = it.attempts.toString(),
                        color = if (it.allowed) allowedColor else blockedColor,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockLogScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    loggedConnections: List<LoggedConnectionState>,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = stringResource(id = R.string.block_log))
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_up),
                            )
                        }
                    },
                    windowInsets = topAppBarInsets,
                    scrollBehavior = scrollBehavior,
                )
            },
            contentWindowInsets = scaffoldContentInsets,
        ) { contentPadding ->
            BlockLog(
                contentPadding = contentPadding,
                listState = listState,
                loggedConnections = loggedConnections,
            )
        }

        val isAtTop by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex != 0
            }
        }
        ScrollUpIndicator(
            visible = isAtTop,
            onClick = { listState.animateScrollToItem(0) },
        )
    }
}

@Preview
@Composable
fun BlockLogScreenPreview() {
    BlockLogScreen(
        modifier = Modifier,
        onNavigateUp = {},
        loggedConnections = listOf(
            LoggedConnectionState("some.blocked.server", false, 100),
            LoggedConnectionState("some.allowed.server", true, 100),
        ),
    )
}
