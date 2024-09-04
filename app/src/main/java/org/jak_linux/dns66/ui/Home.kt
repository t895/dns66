package org.jak_linux.dns66.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.R

enum class Destination(
    val route: String,
    val icon: ImageVector,
    val labelResId: Int,
) {
    Start("start", Icons.Default.Home, R.string.start_tab),
    Hosts("hosts", Icons.AutoMirrored.Filled.DriveFileMove, R.string.hosts_tab),
    EditHost("hosts/edit", Icons.Default.Edit, 0),
    Apps("apps", Icons.Default.GridOn, R.string.allowlist_tab),
    DNS("dns", Icons.Default.Dns, R.string.dns_tab),
    EditDNS("dns/edit", Icons.Default.Edit, 0),
    About("about", Icons.Default.Info, 0),
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeScreen() {
    val navController = rememberNavController()
    val backstackState by navController.currentBackStackEntryAsState()
    val selectedDestination by remember {
        derivedStateOf {
            backstackState?.destination?.route ?: Destination.Start.route
        }
    }

    val setDestination = { newDestination: Destination ->
        if (selectedDestination != newDestination.route) {
            navController.navigate(newDestination.route) {
                // Pops all destinations on the backstack
                popUpTo(0) {
                    saveState = true
                    inclusive = true
                }
                launchSingleTop = true
                restoreState = true
            }
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

                    Box {
                        var expanded by rememberSaveable { mutableStateOf(false) }
                        IconButton(
                            onClick = { expanded = true },
                        ) {
                            Icon(Icons.Default.MoreVert, null)
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            val item = @Composable { text: String, onClick: () -> Unit ->
                                DropdownMenuItem(
                                    text = {
                                        Text(text = text)
                                    },
                                    onClick = {
                                        expanded = false
                                        onClick()
                                    },
                                )
                            }

                            item(stringResource(R.string.load_defaults)) {}
                            item(stringResource(R.string.action_import)) {}
                            item(stringResource(R.string.action_export)) {}
                            item(stringResource(R.string.action_about)) {
                                navController.navigate(Destination.About.route)
                            }
                            item(stringResource(R.string.action_logcat)) {}
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar {
                val item = @Composable { destination: Destination ->
                    NavigationBarItem(
                        selected = destination.route == selectedDestination,
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
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedDestination == Destination.Hosts.route ||
                        selectedDestination == Destination.DNS.route,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = {
                        if (selectedDestination == Destination.Hosts.route) {
                            navController.navigate(Destination.EditHost.route)
                        } else if (selectedDestination == Destination.DNS.route) {
                            navController.navigate(Destination.EditDNS.route)
                        }
                    },
                ) {
                    Icon(Icons.Default.Add, null)
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
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
            composable(
                route = Destination.EditHost.route,
                arguments = listOf(
                    navArgument("host") {
                        defaultValue = null
                        nullable = true
                    }
                ),
            ) { backstackEntry ->
                val argument = backstackEntry.arguments?.get("host") as Configuration.Item?
                EditFilterScreen(
                    title = argument?.title ?: "",
                    location = argument?.location ?: "",
                    action = argument?.state ?: 0,
                    onNavigateUp = { navController.navigateUp() },
                    onSave = { title, location, action ->  },
                    onDelete = null,
                    onOpenUri = {},
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
            composable(
                route = Destination.EditDNS.route,
                arguments = listOf(
                    navArgument("dns") {
                        defaultValue = null
                        nullable = true
                    }
                ),
            ) { backstackEntry ->
                val argument = backstackEntry.arguments?.get("dns") as Configuration.Item?
                EditDnsScreen(
                    title = argument?.title ?: "",
                    location = argument?.location ?: "",
                    enabled = when (argument?.state ?: 0) {
                        Configuration.Item.STATE_ALLOW -> true
                        else -> false
                    },
                    onNavigateUp = { navController.navigateUp() },
                    onSave = { title, location, enabled ->  },
                    onDelete = null,
                )
            }
            composable(Destination.About.route) {
                AboutScreen { navController.navigate(selectedDestination) }
            }
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    HomeScreen()
}
