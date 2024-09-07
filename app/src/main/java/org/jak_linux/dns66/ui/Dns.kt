package org.jak_linux.dns66.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jak_linux.dns66.DnsItem
import org.jak_linux.dns66.R
import org.jak_linux.dns66.ui.theme.Dns66Theme

@Composable
fun DnsScreen(
    modifier: Modifier = Modifier,
    servers: List<DnsItem> = emptyList(),
    customDnsServers: Boolean,
    onCustomDnsServersClick: () -> Unit,
    onItemClick: (DnsItem) -> Unit,
) {
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
    ) {
        item {
            ListSettingsContainer {
                SwitchListItem(
                    title = stringResource(R.string.custom_dns),
                    details = stringResource(R.string.dns_description),
                    checked = customDnsServers,
                    onCheckedChange = { onCustomDnsServersClick() },
                    onClick = onCustomDnsServersClick,
                )
            }
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
        }

        items(servers) {
            CheckboxListItem(
                title = it.title,
                details = it.location,
                checked = it.enabled,
                onCheckedChange = { _ -> onItemClick(it) },
                onClick = { onItemClick(it) },
            )
        }
    }
}

@Preview
@Composable
private fun DnsScreenPreview() {
    val item = DnsItem()
    item.title = "Title"
    item.location = "213.73.91.35"
    Dns66Theme {
        DnsScreen(
            servers = listOf(item, item, item),
            onItemClick = { _ -> },
            customDnsServers = false,
            onCustomDnsServersClick = {},
        )
    }
}

@Composable
fun EditDns(
    modifier: Modifier = Modifier,
    titleText: String,
    onTitleTextChanged: (String) -> Unit,
    locationText: String,
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
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(text = stringResource(id = R.string.location_dns))
            },
            value = locationText,
            onValueChange = onLocationTextChanged,
        )
        SwitchListItem(
            title = stringResource(id = R.string.state_dns_enabled),
            checked = enabled,
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
            onTitleTextChanged = {},
            locationText = "Location",
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
    title: String,
    location: String,
    enabled: Boolean,
    onNavigateUp: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var titleInput by rememberSaveable { mutableStateOf(title) }
    var locationInput by rememberSaveable { mutableStateOf(location) }
    var enabledInput by rememberSaveable { mutableStateOf(enabled) }

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

                    IconButton(onClick = { onSave(titleInput, locationInput, enabledInput) }) {
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
            onTitleTextChanged = { titleInput = it },
            locationText = locationInput,
            onLocationTextChanged = { locationInput = it },
            enabled = enabledInput,
            onEnable = { enabledInput = !enabledInput },
        )
    }
}

@Preview
@Composable
fun EditDnsScreenPreview(modifier: Modifier = Modifier) {
    EditDnsScreen(
        title = "Title",
        location = "Location",
        enabled = true,
        onNavigateUp = {},
        onSave = { _, _, _ -> },
        onDelete = {},
    )
}
