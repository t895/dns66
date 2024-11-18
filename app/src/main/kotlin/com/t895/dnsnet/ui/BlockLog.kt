/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet.ui

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t895.dnsnet.R
import com.t895.dnsnet.ui.theme.ListPadding
import com.t895.dnsnet.ui.theme.ScrollUpIndicatorPadding
import com.t895.dnsnet.ui.theme.ScrollUpIndicatorSize
import kotlinx.parcelize.Parcelize

@Parcelize
data class LoggedConnectionState(
    val name: String,
    val allowed: Boolean,
    var attempts: Long,
    var lastAttemptTime: Long,
) : Parcelable

@OptIn(ExperimentalMaterial3Api::class)
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

    var showModifyListSheet by rememberSaveable { mutableStateOf(false) }

    var sortState by rememberSaveable { mutableStateOf(BlockLogSortState()) }
    var filterState by rememberSaveable { mutableStateOf(BlockLogFilterState()) }

    val sortedList by remember {
        derivedStateOf {
            when (sortState.selectedType) {
                BlockLogSortType.Alphabetical -> if (sortState.ascending) {
                    loggedConnections.sortedByDescending { it.name }
                } else {
                    loggedConnections.sortedBy { it.name }
                }

                BlockLogSortType.LastConnected -> if (sortState.ascending) {
                    loggedConnections.sortedByDescending { it.lastAttemptTime }
                } else {
                    loggedConnections.sortedBy { it.lastAttemptTime }
                }

                BlockLogSortType.Attempts -> if (sortState.ascending) {
                    loggedConnections.sortedByDescending { it.attempts }
                } else {
                    loggedConnections.sortedBy { it.attempts }
                }
            }
        }
    }
    val filteredList by remember {
        derivedStateOf {
            sortedList.filter {
                var result = true
                filterState.filters.forEach { (type, mode) ->
                    when (type) {
                        BlockLogFilterType.Blocked -> {
                            result = when (mode) {
                                FilterMode.Include -> !it.allowed
                                FilterMode.Exclude -> it.allowed
                            }
                        }
                    }
                }
                result
            }
        }
    }

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

            Spacer(Modifier.padding(vertical = 16.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { showModifyListSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.modify_list),
                    )
                }
            }
        }

        items(filteredList) {
            ContentSetting(
                modifier = Modifier.animateItem(),
                title = it.name,
                details = if (it.allowed) allowedString else blockedString,
                endContent = {
                    Row {
                        Text(
                            text = it.attempts.toString(),
                            color = if (it.allowed) allowedColor else blockedColor,
                        )

                    }
                },
            )
        }
    }

    var currentModifyListPage by rememberSaveable { mutableIntStateOf(0) }
    if (showModifyListSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModifyListSheet = false }
        ) {
            MaterialHorizontalTabLayout(
                initialPage = currentModifyListPage,
                onPageChange = { currentModifyListPage = it },
                pages = listOf(
                    TabLayoutContent(
                        tabContent = {
                            Text("Sort")
                        },
                        pageContent = {
                            BlockLogSortType.entries.forEach {
                                BlockLogSortItem(
                                    selected = sortState.selectedType == it,
                                    ascending = sortState.ascending,
                                    type = it,
                                    onClick = {
                                        sortState = if (sortState.selectedType == it) {
                                            BlockLogSortState(
                                                selectedType = it,
                                                ascending = !sortState.ascending,
                                            )
                                        } else {
                                            BlockLogSortState(
                                                selectedType = it,
                                                ascending = true,
                                            )
                                        }
                                        println(sortState)
                                    }
                                )
                            }
                        },
                    ),
                    TabLayoutContent(
                        tabContent = {
                            Text("Filter")
                        },
                        pageContent = {
                            BlockLogFilterType.entries.forEach {
                                BlockLogFilterItem(
                                    type = it,
                                    mode = filterState.filters[it],
                                    onClick = {
                                        val newFilters = filterState.filters.toMutableMap()
                                        val currentState = filterState.filters[it]
                                        when (currentState) {
                                            FilterMode.Include ->
                                                newFilters[it] = FilterMode.Exclude

                                            FilterMode.Exclude -> newFilters.remove(it)
                                            null -> newFilters[it] = FilterMode.Include
                                        }
                                        filterState = BlockLogFilterState(newFilters)
                                    }
                                )
                            }
                        },
                    ),
                )
            )
        }
    }
}

@Composable
fun BlockLogListItem(
    modifier: Modifier = Modifier,
    text: String,
    endContent: @Composable () -> Unit,
) {
    ContentSetting(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp),
        title = text,
    ) {
        endContent()
    }
}

enum class BlockLogSortType(@StringRes val labelRes: Int) {
    Attempts(R.string.attempts),
    LastConnected(R.string.last_connected),
    Alphabetical(R.string.alphabetical),
}

@Parcelize
data class BlockLogSortState(
    val selectedType: BlockLogSortType = BlockLogSortType.Attempts,
    val ascending: Boolean = true,
) : Parcelable

@Composable
private fun BlockLogSortItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    ascending: Boolean,
    type: BlockLogSortType,
    onClick: () -> Unit,
) {
    val text = stringResource(type.labelRes)
    BlockLogListItem(
        modifier = modifier
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        text = text,
    ) {
        if (selected) {
            val animatedRotation by animateFloatAsState(
                targetValue = if (ascending) 0f else -180f,
                label = "animatedRotation",
            )
            Icon(
                modifier = Modifier.rotate(animatedRotation),
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = text,
            )
        }
    }
}

enum class BlockLogFilterType(@StringRes val labelRes: Int) {
    Blocked(R.string.blocked),
}

enum class FilterMode {
    Include,
    Exclude,
}

@Parcelize
data class BlockLogFilterState(
    val filters: Map<BlockLogFilterType, FilterMode> = emptyMap()
) : Parcelable

@Composable
private fun BlockLogFilterItem(
    modifier: Modifier = Modifier,
    type: BlockLogFilterType,
    mode: FilterMode?,
    onClick: () -> Unit,
) {
    val state = when (mode) {
        FilterMode.Include -> ToggleableState.On
        FilterMode.Exclude -> ToggleableState.Indeterminate
        null -> ToggleableState.Off
    }
    BlockLogListItem(
        modifier = modifier
            .triStateToggleable(
                state = state,
                onClick = onClick,
            ),
        text = stringResource(type.labelRes),
    ) {
        TriStateCheckbox(
            state = state,
            onClick = onClick,
        )
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
            LoggedConnectionState("some.blocked.server", false, 100, 0),
            LoggedConnectionState("some.allowed.server", true, 100, 0),
        ),
    )
}
