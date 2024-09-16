package org.jak_linux.dns66.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jak_linux.dns66.DnsServer
import org.jak_linux.dns66.R
import org.jak_linux.dns66.ui.theme.Dns66Theme

@Composable
fun DnsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
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
    ) {
        item {
            ListSettingsContainer {
                SwitchListItem(
                    title = stringResource(R.string.custom_dns),
                    details = stringResource(R.string.dns_description),
                    checked = customDnsServers,
                    enabled = enabled,
                    sharedInteractionSource = remember { MutableInteractionSource() },
                    onCheckedChange = { onCustomDnsServersClick() },
                    onClick = onCustomDnsServersClick,
                )
            }
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
        }

        items(servers) {
            CheckboxListItem(
                modifier = Modifier.animateItem(),
                title = it.title,
                details = it.location,
                checked = it.enabled,
                enabled = enabled,
                onCheckedChange = { _ -> onItemCheckClicked(it) },
                onClick = { onItemClick(it) },
            )
        }
    }
}

@Preview
@Composable
private fun DnsScreenPreview() {
    Dns66Theme {
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
            sharedInteractionSource = remember { MutableInteractionSource() },
            onCheckedChange = { onEnable() },
            onClick = onEnable,
        )
    }
}

@Preview
@Composable
private fun EditDnsPreview() {
    Dns66Theme {
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.activity_edit_dns_server))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            titleInputError = titleInput.isBlank()
                            locationInputError = locationInput.isBlank()
                            if (titleInputError || locationInputError) {
                                return@IconButton
                            }

                            onSave(DnsServer(titleInput, locationInput, enabledInput))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        EditDns(
            modifier = Modifier
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
fun EditDnsScreenPreview(modifier: Modifier = Modifier) {
    Dns66Theme {
        EditDnsScreen(
            server = DnsServer("Title", "Location", true),
            onNavigateUp = {},
            onSave = {},
            onDelete = {},
        )
    }
}
