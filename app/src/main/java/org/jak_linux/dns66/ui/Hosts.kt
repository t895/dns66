package org.jak_linux.dns66.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.R

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
        Text(text = text)
    }
}

@Composable
fun HostsScreen(
    modifier: Modifier = Modifier,
    hosts: List<Configuration.Item>,
    onItemClick: (Configuration.Item) -> Unit,
    onItemStateChanged: () -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        item {
            ListSettingsContainer(modifier = Modifier.padding(16.dp)) {
                SwitchListItem(
                    title = stringResource(id = R.string.enable_hosts),
                    onCheckedChange = {},
                    onClick = {},
                )

                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = stringResource(id = R.string.legend_host_intro))
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
                    onCheckedChange = {},
                    onClick = {},
                )
            }
        }

        items(hosts) {
            val iconResource = when (it.state) {
                Configuration.Item.STATE_DENY -> R.drawable.ic_state_deny
                Configuration.Item.STATE_ALLOW -> R.drawable.ic_state_allow
                else -> R.drawable.ic_state_ignore
            }

            IconListItem(
                icon = painterResource(id = iconResource),
                onClick = {
                    onItemClick(it)
                },
                onIconClick = onItemStateChanged,
            )
        }
    }
}

@Preview
@Composable
private fun HostsScreenPreview(modifier: Modifier = Modifier) {
    HostsScreen(
        hosts = emptyList(),
        onItemClick = {},
        onItemStateChanged = {},
    )
}
