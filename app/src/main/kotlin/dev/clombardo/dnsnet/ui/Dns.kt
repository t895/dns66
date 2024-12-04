/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.clombardo.dnsnet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.clombardo.dnsnet.DnsServer
import dev.clombardo.dnsnet.R
import dev.clombardo.dnsnet.ui.theme.DnsNetTheme

@Composable
fun DnsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    enabled: Boolean,
    servers: List<DnsServer> = emptyList(),
    customDnsServers: Boolean,
    onCustomDnsServersClick: () -> Unit,
    onItemClick: (DnsServer) -> Unit,
    onItemCheckClicked: (DnsServer) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        state = listState,
    ) {
        item {
            ListSettingsContainer {
                SwitchListItem(
                    title = stringResource(R.string.custom_dns),
                    details = stringResource(R.string.dns_description),
                    checked = customDnsServers,
                    enabled = enabled,
                    onCheckedChange = { onCustomDnsServersClick() },
                )
            }
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
        }

        items(servers) {
            SplitCheckboxListItem(
                modifier = Modifier.animateItem(),
                title = it.title,
                details = it.location,
                checked = it.enabled,
                bodyEnabled = enabled,
                checkboxEnabled = enabled,
                onBodyClick = { onItemClick(it) },
                onCheckedChange = { _ -> onItemCheckClicked(it) },
            )
        }
    }
}

@Preview
@Composable
private fun DnsScreenPreview() {
    DnsNetTheme {
        val item = DnsServer()
        item.title = "Title"
        item.location = "213.73.91.35"
        DnsScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            enabled = true,
            servers = listOf(item, item, item),
            onItemClick = {},
            customDnsServers = false,
            onCustomDnsServersClick = {},
            onItemCheckClicked = {},
        )
    }
}

@Composable
fun EditDns(
    modifier: Modifier = Modifier,
    titleText: String,
    titleTextError: Boolean,
    onTitleTextChanged: (String) -> Unit,
    locationText: String,
    locationTextError: Boolean,
    onLocationTextChanged: (String) -> Unit,
    enabled: Boolean,
    onEnable: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(text = stringResource(id = R.string.title))
            },
            value = titleText,
            onValueChange = onTitleTextChanged,
            isError = titleTextError,
            supportingText = {
                if (titleTextError) {
                    Text(text = stringResource(R.string.input_blank_error))
                }
            },
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(text = stringResource(id = R.string.location_dns))
            },
            value = locationText,
            onValueChange = onLocationTextChanged,
            isError = locationTextError,
            supportingText = {
                if (locationTextError) {
                    Text(text = stringResource(R.string.input_blank_error))
                }
            },
        )
        SwitchListItem(
            title = stringResource(id = R.string.state_dns_enabled),
            checked = enabled,
            onCheckedChange = { onEnable() },
        )
    }
}

@Preview
@Composable
private fun EditDnsPreview() {
    DnsNetTheme {
        EditDns(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            titleText = "Title",
            titleTextError = false,
            onTitleTextChanged = {},
            locationText = "Location",
            locationTextError = false,
            onLocationTextChanged = {},
            enabled = true,
            onEnable = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDnsScreen(
    modifier: Modifier = Modifier,
    server: DnsServer,
    onNavigateUp: () -> Unit,
    onSave: (DnsServer) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var titleInput by rememberSaveable { mutableStateOf(server.title) }
    var titleInputError by rememberSaveable { mutableStateOf(false) }
    var locationInput by rememberSaveable { mutableStateOf(server.location) }
    var locationInputError by rememberSaveable { mutableStateOf(false) }
    var enabledInput by rememberSaveable { mutableStateOf(server.enabled) }

    if (titleInput.isNotBlank()) {
        titleInputError = false
    }
    if (locationInput.isNotBlank()) {
        locationInputError = false
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    InsetScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                windowInsets = topAppBarInsets,
                title = {
                    Text(text = stringResource(R.string.activity_edit_dns_server))
                },
                navigationIcon = {
                    BasicTooltipIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_up),
                        onClick = onNavigateUp,
                    )
                },
                actions = {
                    if (onDelete != null) {
                        BasicTooltipIconButton(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            onClick = onDelete,
                        )
                    }

                    BasicTooltipIconButton(
                        icon = Icons.Default.Save,
                        contentDescription = stringResource(R.string.save),
                        onClick = {
                            titleInputError = titleInput.isBlank()
                            locationInputError = locationInput.isBlank()
                            if (titleInputError || locationInputError) {
                                return@BasicTooltipIconButton
                            }

                            onSave(
                                DnsServer(
                                    titleInput,
                                    locationInput,
                                    enabledInput
                                )
                            )
                        },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        EditDns(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp),
            titleText = titleInput,
            titleTextError = titleInputError,
            onTitleTextChanged = { titleInput = it },
            locationText = locationInput,
            locationTextError = locationInputError,
            onLocationTextChanged = { locationInput = it },
            enabled = enabledInput,
            onEnable = { enabledInput = !enabledInput },
        )
    }
}

@Preview
@Composable
private fun EditDnsScreenPreview() {
    DnsNetTheme {
        EditDnsScreen(
            server = DnsServer("Title", "Location", true),
            onNavigateUp = {},
            onSave = {},
            onDelete = {},
        )
    }
}
