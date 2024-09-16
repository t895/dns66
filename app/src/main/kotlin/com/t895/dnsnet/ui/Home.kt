/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowWidthSizeClass
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.t895.dnsnet.DnsServer
import com.t895.dnsnet.FileHelper
import com.t895.dnsnet.Host
import com.t895.dnsnet.R
import com.t895.dnsnet.ui.theme.DefaultFabSize
import com.t895.dnsnet.ui.theme.EmphasizedAccelerateEasing
import com.t895.dnsnet.ui.theme.EmphasizedDecelerateEasing
import com.t895.dnsnet.ui.theme.FabPadding
import com.t895.dnsnet.ui.theme.HomeEnterTransition
import com.t895.dnsnet.ui.theme.HomeExitTransition
import com.t895.dnsnet.ui.theme.ListPadding
import com.t895.dnsnet.ui.theme.TopLevelEnter
import com.t895.dnsnet.ui.theme.TopLevelExit
import com.t895.dnsnet.ui.theme.TopLevelPopEnter
import com.t895.dnsnet.ui.theme.TopLevelPopExit
import com.t895.dnsnet.ui.theme.VpnFabSize
import com.t895.dnsnet.viewmodel.HomeViewModel
import com.t895.dnsnet.vpn.AdVpnService
import com.t895.dnsnet.vpn.VpnStatus

enum class HomeDestination(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelResId: Int,
) {
    Start("start", Icons.Default.VpnKey, R.string.start_tab),
    Hosts("hosts", Icons.AutoMirrored.Filled.DriveFileMove, R.string.hosts_tab),
    Apps("apps", Icons.Default.Android, R.string.allowlist_tab),
    DNS("dns", Icons.Default.Dns, R.string.dns_tab),
}

enum class TopLevelDestination(val route: String) {
    About("about"),
    Home("home");
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun App(
    vm: HomeViewModel = viewModel(),
    onRefresh: () -> Unit,
    onLoadDefaults: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onShareLogcat: () -> Unit,
    onTryToggleService: () -> Unit,
    onStartWithoutChecks: () -> Unit,
    onUpdateRefreshWork: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionState =
            rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS) {
                vm.onNotificationPermissionDenied()
            }
        LaunchedEffect(Unit) {
            if (!notificationPermissionState.status.isGranted) {
                vm.onNotificationPermissionNotGranted()
            }
        }

        val showNotificationPermissionDialog by vm.showNotificationPermissionDialog.collectAsState()
        if (showNotificationPermissionDialog) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {
                    TextButton(
                        onClick = {
                            notificationPermissionState.launchPermissionRequest()
                            vm.onDismissNotificationPermission()
                        }
                    ) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.onNotificationPermissionDenied() }) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                },
                title = { Text(text = stringResource(R.string.notification_permission)) },
                text = { Text(text = stringResource(R.string.notification_permission_description)) },
            )
        }
    }

    val showUpdateIncompleteDialog by vm.showUpdateIncompleteDialog.collectAsState()
    if (showUpdateIncompleteDialog) {
        AlertDialog(
            onDismissRequest = { vm.onDismissUpdateIncomplete() },
            confirmButton = {
                TextButton(onClick = { vm.onDismissUpdateIncomplete() }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            title = {
                Text(text = stringResource(R.string.update_incomplete))
            },
            text = {
                Column {
                    Text(text = stringResource(R.string.update_incomplete_description))
                    vm.errors?.forEach {
                        Text(text = it)
                    }
                }
            },
        )
    }

    val showHostsFilesNotFoundDialog by vm.showHostsFilesNotFoundDialog.collectAsState()
    if (showHostsFilesNotFoundDialog) {
        AlertDialog(
            onDismissRequest = { vm.onDismissHostsFilesNotFound() },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStartWithoutChecks()
                        vm.onDismissHostsFilesNotFound()
                    },
                ) {
                    Text(text = stringResource(R.string.button_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onDismissHostsFilesNotFound() }) {
                    Text(text = stringResource(R.string.button_no))
                }
            },
            title = {
                Text(text = stringResource(R.string.missing_hosts_files_title))
            },
            text = {
                Text(text = stringResource(R.string.missing_hosts_files_message))
            },
        )
    }

    val showFilePermissionDeniedDialog by vm.showFilePermissionDeniedDialog.collectAsState()
    if (showFilePermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { vm.onDismissFilePermissionDenied() },
            confirmButton = {
                TextButton(onClick = { vm.onDismissFilePermissionDenied() }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            title = {
                Text(text = stringResource(R.string.permission_denied))
            },
            text = {
                Text(text = stringResource(R.string.persistable_uri_permission_failed))
            },
        )
    }

    val showVpnConfigurationFailureDialog by vm.showVpnConfigurationFailureDialog.collectAsState()
    if (showVpnConfigurationFailureDialog) {
        AlertDialog(
            onDismissRequest = { vm.onDismissVpnConfigurationFailure() },
            confirmButton = {
                TextButton(onClick = { vm.onDismissVpnConfigurationFailure() }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            title = { Text(text = stringResource(R.string.could_not_start_vpn)) },
            text = { Text(text = stringResource(R.string.could_not_start_vpn_description)) },
        )
    }

    val navController = rememberNavController()
    NavHost(
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        navController = navController,
        startDestination = TopLevelDestination.Home.route,
        enterTransition = { TopLevelEnter },
        exitTransition = { TopLevelExit },
        popEnterTransition = { TopLevelPopEnter },
        popExitTransition = { TopLevelPopExit },
    ) {
        composable(TopLevelDestination.Home.route) {
            HomeScreen(
                vm = vm,
                topLevelNavController = navController,
                onRefresh = onRefresh,
                onLoadDefaults = onLoadDefaults,
                onImport = onImport,
                onExport = onExport,
                onShareLogcat = onShareLogcat,
                onTryToggleService = onTryToggleService,
                onUpdateRefreshWork = onUpdateRefreshWork,
            )
        }
        composable<Host> { backstackEntry ->
            val host = backstackEntry.toRoute<Host>()
            EditHostScreen(
                host = host,
                onNavigateUp = { navController.navigateUp() },
                onSave = { hostToSave ->
                    if (host.title.isEmpty()) {
                        vm.addHost(hostToSave)
                    } else {
                        vm.replaceHost(host, hostToSave)
                    }
                    navController.popBackStack()
                },
                onDelete = if (host.title.isEmpty()) {
                    null
                } else {
                    {
                        vm.removeHost(host)
                        navController.popBackStack()
                    }
                },
                onUriPermissionAcquireFailed = { vm.onFilePermissionDenied() },
            )
        }
        composable<DnsServer> { backstackEntry ->
            val server = backstackEntry.toRoute<DnsServer>()
            EditDnsScreen(
                server = server,
                onNavigateUp = { navController.navigateUp() },
                onSave = { savedServer ->
                    if (server.title.isEmpty()) {
                        vm.addDnsServer(savedServer)
                    } else {
                        vm.replaceDnsServer(server, savedServer)
                    }
                    navController.popBackStack()
                },
                onDelete = if (server.title.isEmpty()) {
                    null
                } else {
                    {
                        vm.removeDnsServer(server)
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(TopLevelDestination.About.route) {
            AboutScreen { navController.popBackStack() }
        }
    }
}

@Preview
@Composable
fun AppPreview() {
    App(
        onRefresh = {},
        onLoadDefaults = {},
        onImport = {},
        onExport = {},
        onShareLogcat = {},
        onTryToggleService = {},
        onStartWithoutChecks = {},
        onUpdateRefreshWork = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: HomeViewModel,
    topLevelNavController: NavHostController,
    onRefresh: () -> Unit,
    onLoadDefaults: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onShareLogcat: () -> Unit,
    onTryToggleService: () -> Unit,
    onUpdateRefreshWork: () -> Unit,
) {
    val navController = rememberNavController()
    val backstackState by navController.currentBackStackEntryAsState()
    var savedRoute by rememberSaveable { mutableStateOf(HomeDestination.Start.route) }
    val selectedRoute by remember {
        derivedStateOf {
            val currentState = backstackState?.destination?.route
            if (currentState != null && currentState != savedRoute) {
                savedRoute = currentState
            }
            currentState ?: savedRoute
        }
    }

    val setDestination = { newHomeDestination: HomeDestination ->
        if (selectedRoute != newHomeDestination.route) {
            navController.navigate(newHomeDestination.route) {
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

    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            HomeDestination.entries.forEach {
                item(
                    selected = it.route == selectedRoute,
                    onClick = { setDestination(it) },
                    icon = {
                        Icon(it.icon, null)
                    },
                    label = {
                        Text(text = stringResource(it.labelResId))
                    },
                )
            }
        },
        layoutType = if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT) {
            NavigationSuiteType.NavigationBar
        } else {
            NavigationSuiteType.NavigationRail
        },
    ) {
        val status by AdVpnService.status.collectAsState()
        val canEditSettings by remember {
            derivedStateOf { status == VpnStatus.STOPPED }
        }
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = stringResource(R.string.app_name))
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
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
                                val item =
                                    @Composable { text: String, enabled: Boolean, onClick: () -> Unit ->
                                        MenuItem(
                                            text = text,
                                            enabled = enabled,
                                            onClick = {
                                                expanded = false
                                                onClick()
                                            }
                                        )
                                    }

                                item(stringResource(R.string.load_defaults), canEditSettings, onLoadDefaults)
                                item(stringResource(R.string.action_import), canEditSettings, onImport)
                                item(stringResource(R.string.action_export), true, onExport)
                                item(stringResource(R.string.action_logcat), true, onShareLogcat)
                                item(stringResource(R.string.action_about), true) {
                                    topLevelNavController.navigate(TopLevelDestination.About.route)
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = (selectedRoute == HomeDestination.Hosts.route ||
                            selectedRoute == HomeDestination.DNS.route) && canEditSettings,
                    enter = scaleIn(animationSpec = tween(easing = EmphasizedDecelerateEasing)),
                    exit = scaleOut(animationSpec = tween(easing = EmphasizedAccelerateEasing)),
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (selectedRoute == HomeDestination.Hosts.route) {
                                topLevelNavController.navigate(Host())
                            } else if (selectedRoute == HomeDestination.DNS.route) {
                                topLevelNavController.navigate(DnsServer())
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
                startDestination = HomeDestination.Start.route,
                enterTransition = { HomeEnterTransition },
                exitTransition = { HomeExitTransition },
                popEnterTransition = { HomeEnterTransition },
                popExitTransition = { HomeExitTransition },
            ) {
                composable(HomeDestination.Start.route) {
                    var resumeOnStartup by remember { mutableStateOf(vm.config.autoStart) }
                    var watchConnection by remember { mutableStateOf(vm.config.watchDog) }
                    var ipv6Support by remember { mutableStateOf(vm.config.ipV6Support) }

                    val showWatchdogWarningDialog by vm.showWatchdogWarningDialog.collectAsState()
                    val dismiss = {
                        vm.config.watchDog = !vm.config.watchDog
                        watchConnection = vm.config.watchDog
                        FileHelper.writeSettings(vm.config)
                        vm.onDismissWatchdogWarning()
                    }
                    if (showWatchdogWarningDialog) {
                        AlertDialog(
                            onDismissRequest = dismiss,
                            confirmButton = {
                                TextButton(onClick = { vm.onDismissWatchdogWarning() }) {
                                    Text(text = stringResource(R.string.button_continue))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = dismiss) {
                                    Text(text = stringResource(R.string.button_cancel))
                                }
                            },
                            title = {
                                Text(text = stringResource(R.string.unstable_feature))
                            },
                            text = {
                                Text(text = stringResource(R.string.unstable_watchdog_message))
                            },
                        )
                    }

                    StartScreen(
                        contentPadding = contentPadding + PaddingValues(ListPadding) +
                                PaddingValues(bottom = VpnFabSize + FabPadding),
                        enabled = canEditSettings,
                        resumeOnStartup = resumeOnStartup,
                        onResumeOnStartupClick = {
                            vm.config.autoStart = !vm.config.autoStart
                            resumeOnStartup = vm.config.autoStart
                            FileHelper.writeSettings(vm.config)
                        },
                        watchConnection = watchConnection,
                        onWatchConnectionClick = {
                            vm.config.watchDog = !vm.config.watchDog
                            watchConnection = vm.config.watchDog
                            FileHelper.writeSettings(vm.config)

                            if (watchConnection) {
                                vm.onEnableWatchdog()
                            }
                        },
                        ipv6Support = ipv6Support,
                        onIpv6SupportClick = {
                            vm.config.ipV6Support = !vm.config.ipV6Support
                            ipv6Support = vm.config.ipV6Support
                            FileHelper.writeSettings(vm.config)
                        },
                        status = status,
                        onChangeVpnStatusClick = onTryToggleService,
                    )
                }
                composable(HomeDestination.Hosts.route) {
                    var filterHosts by remember { mutableStateOf(vm.config.hosts.enabled) }
                    var refreshDaily by remember { mutableStateOf(vm.config.hosts.automaticRefresh) }
                    val hosts by vm.hosts.collectAsState()
                    HostsScreen(
                        contentPadding = contentPadding + PaddingValues(ListPadding) +
                                PaddingValues(bottom = DefaultFabSize + FabPadding),
                        enabled = canEditSettings,
                        filterHosts = filterHosts,
                        onFilterHostsClick = {
                            vm.config.hosts.enabled = !vm.config.hosts.enabled
                            filterHosts = vm.config.hosts.enabled
                            FileHelper.writeSettings(vm.config)
                        },
                        refreshDaily = refreshDaily,
                        onRefreshDailyClick = {
                            vm.config.hosts.automaticRefresh = !vm.config.hosts.automaticRefresh
                            refreshDaily = vm.config.hosts.automaticRefresh
                            FileHelper.writeSettings(vm.config)
                            onUpdateRefreshWork()
                        },
                        hosts = hosts,
                        onHostClick = { host ->
                            topLevelNavController.navigate(host)
                        },
                        onHostStateChanged = { host ->
                            vm.cycleHost(host)
                        },
                    )
                }

                composable(HomeDestination.Apps.route) {
                    val apps by vm.appList.collectAsState()
                    val isRefreshing by vm.appListRefreshing.collectAsState()
                    var showSystemApps by remember { mutableStateOf(vm.config.appList.showSystemApps) }
                    var allowlistDefault by remember { mutableStateOf(vm.config.appList.defaultMode) }
                    AppsScreen(
                        contentPadding = contentPadding + PaddingValues(ListPadding),
                        enabled = canEditSettings,
                        isRefreshing = isRefreshing,
                        onRefresh = { vm.populateAppList() },
                        showSystemApps = showSystemApps,
                        onShowSystemAppsClick = {
                            vm.config.appList.showSystemApps = !vm.config.appList.showSystemApps
                            showSystemApps = vm.config.appList.showSystemApps
                            FileHelper.writeSettings(vm.config)
                            vm.populateAppList()
                        },
                        bypassSelection = allowlistDefault,
                        onBypassSelection = { selection ->
                            vm.config.appList.defaultMode = selection
                            allowlistDefault = selection
                            FileHelper.writeSettings(vm.config)
                            vm.populateAppList()
                        },
                        apps = apps,
                        onAppClick = { app ->
                            vm.onToggleApp(app)
                        },
                    )
                }
                composable(HomeDestination.DNS.route) {
                    var customDnsServers by remember { mutableStateOf(vm.config.dnsServers.enabled) }
                    val servers by vm.dnsServers.collectAsState()
                    DnsScreen(
                        contentPadding = contentPadding + PaddingValues(ListPadding) +
                                PaddingValues(bottom = DefaultFabSize + FabPadding),
                        enabled = canEditSettings,
                        servers = servers,
                        customDnsServers = customDnsServers,
                        onCustomDnsServersClick = {
                            vm.config.dnsServers.enabled = !vm.config.dnsServers.enabled
                            customDnsServers = vm.config.dnsServers.enabled
                            FileHelper.writeSettings(vm.config)
                        },
                        onItemClick = { item ->
                            topLevelNavController.navigate(item)
                        },
                        onItemCheckClicked = { item ->
                            vm.toggleDnsServer(item)
                        },
                    )
                }
            }
        }
    }
}
