package org.jak_linux.dns66.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.R
import org.jak_linux.dns66.ui.theme.Dns66Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsScreen(
    modifier: Modifier = Modifier,
    servers: List<Configuration.Item> = emptyList(),
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    customDnsServers: Boolean = false,
    onCustomDnsServersClick: () -> Unit,
    onItemClick: (Configuration.Item) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
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
                    onCheckedChange = { _ -> onItemClick(it) },
                    onClick = { onItemClick(it) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun DnsScreenPreview(modifier: Modifier = Modifier) {
    val item = Configuration.Item()
    item.title = "Title"
    item.location = "213.73.91.35"
    Dns66Theme {
        DnsScreen(
            servers = listOf(item, item, item),
            onItemClick = { _ -> },
            onCustomDnsServersClick = {},
        )
    }
}
