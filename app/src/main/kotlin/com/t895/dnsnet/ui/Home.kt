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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.t895.dnsnet.Host
import com.t895.dnsnet.R
import com.t895.dnsnet.config
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
    Home("home"),
    BlockLog("blocklog");
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalComposeUiApi::class)
@Composable
fun App(
    vm: HomeViewModel = viewModel(),
    onRefresh: () -> Unit,
    onLoadDefaults: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onShareLogcat: () -> Unit,
    onTryToggleService: () -> Unit,
    onStartWithoutHostsCheck: () -> Unit,
    onUpdateRefreshWork: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
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
            BasicDialog(
                modifier = Modifier
                    .semantics { testTagsAsResourceId = true }
                    .testTag("notificationPermissionDialog"),
                title = stringResource(R.string.notification_permission),
                text = stringResource(R.string.notification_permission_description),
                primaryButton = DialogButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        notificationPermissionState.launchPermissionRequest()
                        vm.onDismissNotificationPermission()
                    },
                ),
                secondaryButton = DialogButton(
                    modifier = Modifier.testTag("notificationPermissionDialog:cancel"),
                    text = stringResource(android.R.string.cancel),
                    onClick = { vm.onNotificationPermissionDenied() },
                ),
                onDismissRequest = {},
            )
        }
    }

    val showUpdateIncompleteDialog by vm.showUpdateIncompleteDialog.collectAsState()
    if (showUpdateIncompleteDialog) {
        val messageText = StringBuilder(stringResource(R.string.update_incomplete_description))
        val errorText = remember {
            if (vm.errors != null) {
                messageText.append("\n")
            }
            vm.errors?.forEach {
                messageText.append("$it\n")
            }
            messageText.toString()
        }
        BasicDialog(
            title = stringResource(R.string.update_incomplete),
            text = errorText,
            primaryButton = DialogButton(
                text = stringResource(android.R.string.ok),
                onClick = { vm.onDismissUpdateIncomplete() },
            ),
            onDismissRequest = { vm.onDismissUpdateIncomplete() },
        )
    }

    val showHostsFilesNotFoundDialog by vm.showHostsFilesNotFoundDialog.collectAsState()
    if (showHostsFilesNotFoundDialog) {
        BasicDialog(
            title = stringResource(R.string.missing_hosts_files_title),
            text = stringResource(R.string.missing_hosts_files_message),
            primaryButton = DialogButton(
                text = stringResource(R.string.button_yes),
                onClick = {
                    onStartWithoutHostsCheck()
                    vm.onDismissHostsFilesNotFound()
                },
            ),
            secondaryButton = DialogButton(
                text = stringResource(R.string.button_no),
                onClick = { vm.onDismissHostsFilesNotFound() },
            ),
            onDismissRequest = { vm.onDismissHostsFilesNotFound() },
        )
    }

    val showFilePermissionDeniedDialog by vm.showFilePermissionDeniedDialog.collectAsState()
    if (showFilePermissionDeniedDialog) {
        BasicDialog(
            title = stringResource(R.string.permission_denied),
            text = stringResource(R.string.persistable_uri_permission_failed),
            primaryButton = DialogButton(
                text = stringResource(android.R.string.ok),
                onClick = { vm.onDismissFilePermissionDenied() },
            ),
            onDismissRequest = { vm.onDismissFilePermissionDenied() },
        )
    }

    val showVpnConfigurationFailureDialog by vm.showVpnConfigurationFailureDialog.collectAsState()
    if (showVpnConfigurationFailureDialog) {
        BasicDialog(
            title = stringResource(R.string.could_not_start_vpn),
            text = stringResource(R.string.could_not_start_vpn_description),
            primaryButton = DialogButton(
                text = stringResource(android.R.string.ok),
                onClick = { vm.onDismissVpnConfigurationFailure() },
            ),
            onDismissRequest = { vm.onDismissVpnConfigurationFailure() },
        )
    }

    val showDisablePrivateDnsDialog by vm.showDisablePrivateDnsDialog.collectAsState()
    if (showDisablePrivateDnsDialog) {
        BasicDialog(
            title = stringResource(R.string.private_dns_warning),
            text = stringResource(R.string.private_dns_warning_description),
            primaryButton = DialogButton(
                text = stringResource(R.string.open_settings),
                onClick = onOpenNetworkSettings,
            ),
            secondaryButton = DialogButton(
                text = stringResource(R.string.close),
                onClick = { vm.onDismissPrivateDnsEnabledWarning() },
            ),
            tertiaryButton = DialogButton(
                text = stringResource(R.string.try_again),
                onClick = {
                    vm.onDismissPrivateDnsEnabledWarning()
                    onTryToggleService()
                },
            ),
            onDismissRequest = {},
        )
    }

    val showResetSettingsWarningDialog by vm.showResetSettingsWarningDialog.collectAsState()
    if (showResetSettingsWarningDialog) {
        BasicDialog(
            title = stringResource(R.string.warning),
            text = stringResource(R.string.reset_settings_warning_description),
            primaryButton = DialogButton(
                text = stringResource(R.string.reset),
                onClick = {
                    onLoadDefaults()
                    vm.onDismissResetSettingsDialog()
                },
            ),
            secondaryButton = DialogButton(
                text = stringResource(R.string.button_cancel),
                onClick = { vm.onDismissResetSettingsDialog() },
            ),
            onDismissRequest = { vm.onDismissResetSettingsDialog() },
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
                onImport = onImport,
                onExport = onExport,
                onShareLogcat = onShareLogcat,
                onTryToggleService = onTryToggleService,
                onUpdateRefreshWork = onUpdateRefreshWork,
            )
        }
        composable<Host> { backstackEntry ->
            val host = backstackEntry.toRoute<Host>()

            val showDeleteHostWarningDialog by vm.showDeleteHostWarningDialog.collectAsState()
            if (showDeleteHostWarningDialog) {
                BasicDialog(
                    title = stringResource(R.string.warning),
                    text = stringResource(
                        R.string.permanently_delete_warning_description,
                        host.title,
                    ),
                    primaryButton = DialogButton(
                        text = stringResource(R.string.action_delete),
                        onClick = {
                            vm.removeHost(host)
                            vm.onDismissDeleteHostWarning()
                            navController.popBackStack()
                        },
                    ),
                    secondaryButton = DialogButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { vm.onDismissDeleteHostWarning() },
                    ),
                    onDismissRequest = { vm.onDismissDeleteHostWarning() },
                )
            }

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
                    { vm.onDeleteHostWarning() }
                },
                onUriPermissionAcquireFailed = { vm.onFilePermissionDenied() },
            )
        }
        composable<DnsServer> { backstackEntry ->
            val server = backstackEntry.toRoute<DnsServer>()

            val showDeleteDnsServerWarningDialog by
            vm.showDeleteDnsServerWarningDialog.collectAsState()
            if (showDeleteDnsServerWarningDialog) {
                BasicDialog(
                    title = stringResource(R.string.warning),
                    text = stringResource(
                        R.string.permanently_delete_warning_description,
                        server.title
                    ),
                    primaryButton = DialogButton(
                        text = stringResource(R.string.action_delete),
                        onClick = {
                            vm.removeDnsServer(server)
                            vm.onDismissDeleteDnsServerWarning()
                            navController.popBackStack()
                        },
                    ),
                    secondaryButton = DialogButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { vm.onDismissDeleteDnsServerWarning() },
                    ),
                    onDismissRequest = { vm.onDismissDeleteDnsServerWarning() },
                )
            }

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
                    { vm.onDeleteDnsServerWarning() }
                },
            )
        }
        composable(TopLevelDestination.About.route) {
            AboutScreen { navController.popBackStack() }
        }
        composable(TopLevelDestination.BlockLog.route) {
            val loggedConnections by vm.connectionsLogState.collectAsState()
            BlockLogScreen(
                onNavigateUp = { navController.popBackStack() },
                loggedConnections = loggedConnections.map {
                    LoggedConnectionState(
                        it.key,
                        it.value.allowed,
                        it.value.attempts,
                        it.value.lastAttemptTime,
                    )
                },
            )
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
        onStartWithoutHostsCheck = {},
        onUpdateRefreshWork = {},
        onOpenNetworkSettings = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: HomeViewModel,
    topLevelNavController: NavHostController,
    onRefresh: () -> Unit,
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

    val displayCutout = WindowInsets.displayCutout
    val localDensity = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val startCutoutInset =
        (displayCutout.getLeft(localDensity, layoutDirection) / localDensity.density).dp
    val endCutoutInset =
        (displayCutout.getRight(localDensity, layoutDirection) / localDensity.density).dp
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            HomeDestination.entries.forEach {
                item(
                    modifier = Modifier.padding(start = startCutoutInset),
                    selected = it.route == selectedRoute,
                    onClick = { setDestination(it) },
                    icon = {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = stringResource(it.labelResId),
                        )
                    },
                    label = {
                        // For whatever reason, UIAutomator cannot find tags on navigation
                        // items unless one is set in this label scope.
                        Text(
                            modifier = Modifier.testTag("homeNavigation:${it.route}"),
                            text = stringResource(it.labelResId),
                        )
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
            contentWindowInsets = navigationSuiteScaffoldContentInsets,
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    windowInsets = navigationSuiteScaffoldTopAppBarInsets,
                    title = {
                        Text(text = stringResource(R.string.app_name))
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }

                        Box {
                            var expanded by rememberSaveable { mutableStateOf(false) }
                            IconButton(
                                onClick = { expanded = true },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options),
                                )
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

                                item(
                                    stringResource(R.string.load_defaults),
                                    canEditSettings,
                                ) { vm.onResetSettingsWarning() }
                                item(
                                    stringResource(R.string.action_import),
                                    canEditSettings,
                                    onImport
                                )
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
                        modifier = Modifier.padding(end = endCutoutInset),
                        onClick = {
                            if (selectedRoute == HomeDestination.Hosts.route) {
                                topLevelNavController.navigate(Host())
                            } else if (selectedRoute == HomeDestination.DNS.route) {
                                topLevelNavController.navigate(DnsServer())
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add),
                        )
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End,
        ) { contentPadding ->
            // List state must be hoisted outside of the NavHost or it will be lost on recomposition
            val startListState = rememberScrollState()
            val hostsListState = rememberLazyListState()
            val appListState = rememberLazyListState()
            val dnsListState = rememberLazyListState()
            NavHost(
                navController = navController,
                startDestination = HomeDestination.Start.route,
                enterTransition = { HomeEnterTransition },
                exitTransition = { HomeExitTransition },
                popEnterTransition = { HomeEnterTransition },
                popExitTransition = { HomeExitTransition },
            ) {
                composable(HomeDestination.Start.route) {
                    var resumeOnStartup by remember { mutableStateOf(config.autoStart) }
                    var watchConnection by remember { mutableStateOf(config.watchDog) }
                    var ipv6Support by remember { mutableStateOf(config.ipV6Support) }
                    var blockLog by remember { mutableStateOf(config.blockLogging) }

                    val showWatchdogWarningDialog by vm.showWatchdogWarningDialog.collectAsState()
                    val dismiss = {
                        config.watchDog = !config.watchDog
                        watchConnection = config.watchDog
                        config.save()
                        vm.onDismissWatchdogWarning()
                    }
                    if (showWatchdogWarningDialog) {
                        BasicDialog(
                            title = stringResource(R.string.unstable_feature),
                            text = stringResource(R.string.unstable_watchdog_message),
                            primaryButton = DialogButton(
                                text = stringResource(R.string.button_continue),
                                onClick = { vm.onDismissWatchdogWarning() },
                            ),
                            secondaryButton = DialogButton(
                                text = stringResource(R.string.button_cancel),
                                onClick = dismiss,
                            ),
                            onDismissRequest = dismiss,
                        )
                    }

                    val showDisableBlockLogWarningDialog by vm.showDisableBlockLogWarningDialog.collectAsState()
                    if (showDisableBlockLogWarningDialog) {
                        BasicDialog(
                            title = stringResource(R.string.warning),
                            text = stringResource(R.string.disable_block_log_warning_description),
                            primaryButton = DialogButton(
                                text = stringResource(R.string.disable),
                                onClick = {
                                    AdVpnService.logger.clear()
                                    config.blockLogging = false
                                    blockLog = config.blockLogging
                                    config.save()
                                    vm.onDismissDisableBlockLogWarning()
                                },
                            ),
                            secondaryButton = DialogButton(
                                text = stringResource(R.string.close),
                                onClick = { vm.onDismissDisableBlockLogWarning() },
                            ),
                            onDismissRequest = { vm.onDismissDisableBlockLogWarning() },
                        )
                    }

                    StartScreen(
                        contentPadding = contentPadding + PaddingValues(ListPadding) +
                                PaddingValues(bottom = VpnFabSize + FabPadding),
                        listState = startListState,
                        enabled = canEditSettings,
                        resumeOnStartup = resumeOnStartup,
                        onResumeOnStartupClick = {
                            config.autoStart = !config.autoStart
                            resumeOnStartup = config.autoStart
                            config.save()
                        },
                        watchConnection = watchConnection,
                        onWatchConnectionClick = {
                            config.watchDog = !config.watchDog
                            watchConnection = config.watchDog
                            config.save()

                            if (watchConnection) {
                                vm.onEnableWatchdog()
                            }
                        },
                        ipv6Support = ipv6Support,
                        onIpv6SupportClick = {
                            config.ipV6Support = !config.ipV6Support
                            ipv6Support = config.ipV6Support
                            config.save()
                        },
                        blockLog = blockLog,
                        onToggleBlockLog = {
                            if (blockLog) {
                                vm.onDisableBlockLogWarning()
                            } else {
                                config.blockLogging = !config.blockLogging
                                blockLog = config.blockLogging
                                config.save()
                            }
                        },
                        onOpenBlockLog = {
                            topLevelNavController.navigate(TopLevelDestination.BlockLog.route)
                        },
                        status = status,
                        onChangeVpnStatusClick = onTryToggleService,
                    )
                }
                composable(HomeDestination.Hosts.route) {
                    var filterHosts by remember { mutableStateOf(config.hosts.enabled) }
                    var refreshDaily by remember { mutableStateOf(config.hosts.automaticRefresh) }
                    val hosts by vm.hosts.collectAsState()
                    HostsScreen(
                        contentPadding = contentPadding + PaddingValues(ListPadding) +
                                PaddingValues(bottom = DefaultFabSize + FabPadding),
                        listState = hostsListState,
                        enabled = canEditSettings,
                        filterHosts = filterHosts,
                        onFilterHostsClick = {
                            config.hosts.enabled = !config.hosts.enabled
                            filterHosts = config.hosts.enabled
                            config.save()
                        },
                        refreshDaily = refreshDaily,
                        onRefreshDailyClick = {
                            config.hosts.automaticRefresh = !config.hosts.automaticRefresh
                            refreshDaily = config.hosts.automaticRefresh
                            config.save()
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
                    var showSystemApps by remember { mutableStateOf(config.appList.showSystemApps) }
                    var allowlistDefault by remember { mutableStateOf(config.appList.defaultMode) }
                    AppsScreen(
                        contentPadding = contentPadding + PaddingValues(ListPadding),
                        listState = appListState,
                        enabled = canEditSettings,
                        isRefreshing = isRefreshing,
                        onRefresh = { vm.populateAppList() },
                        showSystemApps = showSystemApps,
                        onShowSystemAppsClick = {
                            config.appList.showSystemApps = !config.appList.showSystemApps
                            showSystemApps = config.appList.showSystemApps
                            config.save()
                            vm.populateAppList()
                        },
                        bypassSelection = allowlistDefault,
                        onBypassSelection = { selection ->
                            config.appList.defaultMode = selection
                            allowlistDefault = selection
                            config.save()
                            vm.populateAppList()
                        },
                        apps = apps,
                        onAppClick = { app ->
                            vm.onToggleApp(app)
                        },
                    )
                }
                composable(HomeDestination.DNS.route) {
                    var customDnsServers by remember { mutableStateOf(config.dnsServers.enabled) }
                    val servers by vm.dnsServers.collectAsState()
                    DnsScreen(
                        contentPadding = contentPadding + PaddingValues(ListPadding) +
                                PaddingValues(bottom = DefaultFabSize + FabPadding),
                        listState = dnsListState,
                        enabled = canEditSettings,
                        servers = servers,
                        customDnsServers = customDnsServers,
                        onCustomDnsServersClick = {
                            config.dnsServers.enabled = !config.dnsServers.enabled
                            customDnsServers = config.dnsServers.enabled
                            config.save()
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
