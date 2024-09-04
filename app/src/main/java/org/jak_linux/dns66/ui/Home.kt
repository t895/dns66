package org.jak_linux.dns66.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.jak_linux.dns66.R

enum class Destination(
    val route: String,
    val icon: ImageVector,
    val labelResId: Int,
) {
    Start("start", Icons.Default.Home, R.string.start_tab),
    Hosts("hosts", Icons.AutoMirrored.Filled.DriveFileMove, R.string.hosts_tab),
    Apps("apps", Icons.Default.GridOn, R.string.allowlist_tab),
    DNS("dns", Icons.Default.Dns, R.string.dns_tab),
    About("about", Icons.Default.Info, 0),
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeScreen() {
    val navController = rememberNavController()
    var selectedDestination by rememberSaveable { mutableStateOf(Destination.Start) }
    val setDestination = { newDestination: Destination ->
        if (selectedDestination != newDestination) {
            navController.navigate(newDestination.route)
            selectedDestination = newDestination
        }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                title = {
                    Text(text = stringResource(R.string.app_name))
                },
                actions = {
                    IconButton(
                        onClick = {},
                    ) {
                        Icon(Icons.Default.Refresh, null)
                    }
                    IconButton(
                        onClick = {},
                    ) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar {
                val item = @Composable { destination: Destination ->
                    NavigationBarItem(
                        selected = destination == selectedDestination,
                        onClick = { setDestination(destination) },
                        icon = {
                            Icon(destination.icon, null)
                        },
                        label = {
                            Text(text = stringResource(destination.labelResId))
                        },
                    )
                }
                item(Destination.Start)
                item(Destination.Hosts)
                item(Destination.Apps)
                item(Destination.DNS)
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Start.route,
        ) {
            composable(Destination.Start.route) {
                StartScreen(
                    modifier = Modifier.padding(contentPadding),
                )
            }
            composable(Destination.Hosts.route) {
                HostsScreen(
                    modifier = Modifier.padding(contentPadding),
                    hosts = emptyList(),
                    onItemClick = {},
                    onItemStateChanged = {},
                )
            }
            composable(Destination.Apps.route) {
                AppsScreen(
                    modifier = Modifier.padding(contentPadding),
                    showSystemApps = false,
                    onShowSystemAppsClick = {},
                    bypassSelection = 0,
                    onBypassSelection = {},
                    apps = emptyList(),
                    onAppClick = {},
                )
            }
            composable(Destination.DNS.route) {
                DnsScreen(
                    modifier = Modifier.padding(contentPadding),
                    servers = emptyList(),
                    isRefreshing = false,
                    onRefresh = {},
                    customDnsServers = false,
                    onCustomDnsServersClick = {},
                    onItemClick = {},
                )
            }
            composable(Destination.About.route) {
                AboutScreen { navController.navigate(selectedDestination.route) }
            }
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview(modifier: Modifier = Modifier) {
    HomeScreen()
}
