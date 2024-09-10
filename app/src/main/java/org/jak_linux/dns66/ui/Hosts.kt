package org.jak_linux.dns66.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jak_linux.dns66.Dns66Application.Companion.applicationContext
import org.jak_linux.dns66.Host
import org.jak_linux.dns66.HostState
import org.jak_linux.dns66.HostState.Companion.toHostState
import org.jak_linux.dns66.R
import org.jak_linux.dns66.ui.theme.DefaultFabSize
import org.jak_linux.dns66.ui.theme.Dns66Theme
import org.jak_linux.dns66.ui.theme.FabPadding
import org.jak_linux.dns66.ui.theme.ListPadding

@Composable
private fun IconText(
    modifier: Modifier = Modifier,
    icon: Painter,
    text: String,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(painter = icon, contentDescription = null)
        Spacer(modifier = Modifier.padding(horizontal = 2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun HostsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    filterHosts: Boolean,
    onFilterHostsClick: () -> Unit,
    refreshDaily: Boolean,
    onRefreshDailyClick: () -> Unit,
    hosts: List<Host>,
    onHostClick: (Host) -> Unit,
    onHostStateChanged: (Host) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .padding(ListPadding)
            .padding(bottom = DefaultFabSize + FabPadding),
        contentPadding = contentPadding,
    ) {
        item {
            ListSettingsContainer {
                SwitchListItem(
                    title = stringResource(id = R.string.enable_hosts),
                    checked = filterHosts,
                    onCheckedChange = { onFilterHostsClick() },
                    onClick = onFilterHostsClick,
                )

                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.legend_host_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconText(
                        icon = painterResource(id = R.drawable.ic_state_ignore),
                        text = stringResource(id = R.string.legend_host_ignore),
                    )
                    IconText(
                        icon = painterResource(id = R.drawable.ic_state_allow),
                        text = stringResource(id = R.string.legend_host_allow),
                    )
                    IconText(
                        icon = painterResource(id = R.drawable.ic_state_deny),
                        text = stringResource(id = R.string.legend_host_deny),
                    )
                }

                Spacer(modifier = Modifier.padding(vertical = 2.dp))

                SwitchListItem(
                    title = stringResource(id = R.string.automatic_refresh),
                    details = stringResource(id = R.string.automatic_refresh_description),
                    checked = refreshDaily,
                    onCheckedChange = { onRefreshDailyClick() },
                    onClick = onRefreshDailyClick,
                )
            }
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
        }

        items(hosts) {
            val iconResource = when (it.state) {
                HostState.DENY -> R.drawable.ic_state_deny
                HostState.ALLOW -> R.drawable.ic_state_allow
                else -> R.drawable.ic_state_ignore
            }

            IconListItem(
                modifier = Modifier.animateItem(),
                onClick = {
                    onHostClick(it)
                },
                title = it.title,
                details = it.location,
                iconContent = {
                    IconButton(onClick = { onHostStateChanged(it) }) {
                        Icon(painterResource(iconResource), null)
                    }
                },
            )
        }
    }
}

@Preview
@Composable
private fun HostsScreenPreview() {
    val items = buildList {
        val item1 = Host()
        item1.title = "StevenBlack's hosts file"
        item1.location = "https://url.to.hosts.file.com/"
        item1.state = HostState.IGNORE
        add(item1)

        val item2 = Host()
        item2.title = "StevenBlack's hosts file"
        item2.location = "https://url.to.hosts.file.com/"
        item2.state = HostState.DENY
        add(item2)

        val item3 = Host()
        item3.title = "StevenBlack's hosts file"
        item3.location = "https://url.to.hosts.file.com/"
        item3.state = HostState.ALLOW
        add(item3)
    }

    Dns66Theme {
        HostsScreen(
            filterHosts = false,
            onFilterHostsClick = {},
            refreshDaily = false,
            onRefreshDailyClick = {},
            hosts = items,
            onHostClick = {},
            onHostStateChanged = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHost(
    modifier: Modifier = Modifier,
    titleText: String,
    titleTextError: Boolean,
    onTitleTextChanged: (String) -> Unit,
    locationText: String,
    locationTextError: Boolean,
    onLocationTextChanged: (String) -> Unit,
    onOpenHostsDirectoryClick: () -> Unit,
    state: HostState,
    onStateChanged: (HostState) -> Unit,
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
                Text(text = stringResource(id = R.string.location))
            },
            value = locationText,
            onValueChange = onLocationTextChanged,
            trailingIcon = {
                IconButton(onClick = onOpenHostsDirectoryClick) {
                    Icon(imageVector = Icons.Default.AttachFile, contentDescription = null)
                }
            },
            isError = locationTextError,
            supportingText = {
                if (locationTextError) {
                    Text(text = stringResource(R.string.input_blank_error))
                }
            },
        )

        val itemStates = stringArrayResource(id = R.array.item_states)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                // The `menuAnchor` modifier must be passed to the text field to handle
                // expanding/collapsing the menu on click. A read-only text field has
                // the anchor type `PrimaryNotEditable`.
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                value = itemStates[state.ordinal],
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(text = stringResource(id = R.string.action)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                itemStates.forEachIndexed { index, s ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = s,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        onClick = {
                            expanded = false
                            onStateChanged(index.toHostState())
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun EditHostPreview() {
    Dns66Theme {
        var state by remember { mutableStateOf(HostState.IGNORE) }
        var titleText by remember { mutableStateOf("") }
        var locationText by remember { mutableStateOf("") }
        EditHost(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .fillMaxWidth()
                .padding(20.dp),
            titleText = titleText,
            titleTextError = false,
            onTitleTextChanged = { titleText = it },
            locationText = locationText,
            locationTextError = false,
            onLocationTextChanged = { locationText = it },
            onOpenHostsDirectoryClick = {},
            state = state,
            onStateChanged = { state = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHostScreen(
    modifier: Modifier = Modifier,
    host: Host,
    onNavigateUp: () -> Unit,
    onSave: (Host) -> Unit,
    onDelete: (() -> Unit)? = null,
    onUriPermissionAcquireFailed: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var titleInput by rememberSaveable { mutableStateOf(host.title) }
    var titleInputError by rememberSaveable { mutableStateOf(false) }
    var locationInput by rememberSaveable { mutableStateOf(host.location) }
    var locationInputError by rememberSaveable { mutableStateOf(false) }
    var stateInput by rememberSaveable { mutableStateOf(host.state) }

    if (titleInput.isNotBlank()) {
        titleInputError = false
    }
    if (locationInput.isNotBlank()) {
        locationInputError = false
    }

    val locationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it ?: return@rememberLauncherForActivityResult

            try {
                applicationContext.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                onUriPermissionAcquireFailed()
                return@rememberLauncherForActivityResult
            }

            locationInput = it.toString()
        }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.activity_edit_filter))
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

                            onSave(Host(titleInput, locationInput, stateInput))
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
    ) { paddingValues ->
        EditHost(
            modifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp),
            titleText = titleInput,
            titleTextError = titleInputError,
            onTitleTextChanged = { titleInput = it },
            locationText = locationInput,
            locationTextError = locationInputError,
            onLocationTextChanged = { locationInput = it },
            onOpenHostsDirectoryClick = { locationLauncher.launch(arrayOf("*/*")) },
            state = stateInput,
            onStateChanged = { stateInput = it },
        )
    }
}

@Preview
@Composable
private fun EditHostScreenPreview() {
    Dns66Theme {
        EditHostScreen(
            host = Host(),
            onNavigateUp = {},
            onSave = {},
            onDelete = {},
            onUriPermissionAcquireFailed = {},
        )
    }
}
