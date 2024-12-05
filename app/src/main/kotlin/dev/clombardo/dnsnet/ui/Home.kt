/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.clombardo.dnsnet.ui

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowWidthSizeClass
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dev.clombardo.dnsnet.DnsServer
import dev.clombardo.dnsnet.Host
import dev.clombardo.dnsnet.HostException
import dev.clombardo.dnsnet.HostFile
import dev.clombardo.dnsnet.HostState
import dev.clombardo.dnsnet.R
import dev.clombardo.dnsnet.config
import dev.clombardo.dnsnet.db.RuleDatabaseUpdateWorker
import dev.clombardo.dnsnet.ui.HomeDestinationIcon.Companion.toHomeDestinationIcon
import dev.clombardo.dnsnet.ui.theme.DefaultFabSize
import dev.clombardo.dnsnet.ui.theme.EmphasizedAccelerateEasing
import dev.clombardo.dnsnet.ui.theme.EmphasizedDecelerateEasing
import dev.clombardo.dnsnet.ui.theme.FabPadding
import dev.clombardo.dnsnet.ui.theme.HomeEnterTransition
import dev.clombardo.dnsnet.ui.theme.HomeExitTransition
import dev.clombardo.dnsnet.ui.theme.ListPadding
import dev.clombardo.dnsnet.ui.theme.TopLevelEnter
import dev.clombardo.dnsnet.ui.theme.TopLevelExit
import dev.clombardo.dnsnet.ui.theme.TopLevelPopEnter
import dev.clombardo.dnsnet.ui.theme.TopLevelPopExit
import dev.clombardo.dnsnet.ui.theme.VpnFabSize
import dev.clombardo.dnsnet.viewmodel.HomeViewModel
import dev.clombardo.dnsnet.vpn.AdVpnService
import dev.clombardo.dnsnet.vpn.VpnStatus
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
enum class HomeDestinationIcon(val icon: ImageVector) {
    Start(Icons.Default.VpnKey),
    Hosts(Icons.AutoMirrored.Filled.DriveFileMove),
    Apps(Icons.Default.Android),
    DNS(Icons.Default.Dns);

    companion object {
        fun Int.toHomeDestinationIcon(): HomeDestinationIcon =
            HomeDestinationIcon.entries.firstOrNull { it.ordinal == this } ?: Start
    }
}

@Parcelize
@Serializable
open class HomeDestination(
    val iconEnum: HomeDestinationIcon,
    @StringRes val labelResId: Int,
) : Parcelable {
    companion object : Parceler<HomeDestination> {
        override fun HomeDestination.write(parcel: Parcel, flags: Int) {
            parcel.apply {
                writeInt(iconEnum.ordinal)
                writeInt(labelResId)
            }
        }

        override fun create(parcel: Parcel): HomeDestination =
            HomeDestination(
                parcel.readInt().toHomeDestinationIcon(),
                parcel.readInt(),
            )
    }
}

object HomeDestinations {
    val entries = listOf(Start, Hosts, Apps, DNS)

    @Serializable
    data object Start : HomeDestination(HomeDestinationIcon.Start, R.string.start_tab)

    @Serializable
    data object Hosts : HomeDestination(HomeDestinationIcon.Hosts, R.string.hosts_tab)

    @Serializable
    data object Apps : HomeDestination(HomeDestinationIcon.Apps, R.string.allowlist_tab)

    @Serializable
    data object DNS : HomeDestination(HomeDestinationIcon.DNS, R.string.dns_tab)
}

@Serializable
open class TopLevelDestination {
    @Serializable
    data object About : TopLevelDestination()

    @Serializable
    data object Home : TopLevelDestination()

    @Serializable
    data object BlockLog : TopLevelDestination()

    @Serializable
    data object Credits : TopLevelDestination()
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalComposeUiApi::class)
@Composable
fun App(
    vm: HomeViewModel = viewModel(),
    onRefreshHosts: () -> Unit,
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
            title = stringResource(R.string.private_dns_error),
            text = stringResource(R.string.private_dns_error_description),
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
    val status by AdVpnService.status.collectAsState()
    val canEditSettings by remember {
        derivedStateOf { status == VpnStatus.STOPPED }
    }
    NavHost(
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        navController = navController,
        startDestination = TopLevelDestination.Home,
        enterTransition = { TopLevelEnter },
        exitTransition = { TopLevelExit },
        popEnterTransition = { TopLevelPopEnter },
        popExitTransition = { TopLevelPopExit },
    ) {
        composable<TopLevelDestination.Home> {
            vm.showStatusBarShade()
            HomeScreen(
                vm = vm,
                topLevelNavController = navController,
                status = status,
                canEditSettings = canEditSettings,
                onRefreshHosts = onRefreshHosts,
                onImport = onImport,
                onExport = onExport,
                onShareLogcat = onShareLogcat,
                onTryToggleService = onTryToggleService,
                onUpdateRefreshWork = onUpdateRefreshWork,
            )
        }
        composable<HostFile> { backstackEntry ->
            vm.hideStatusBarShade()
            val host = backstackEntry.toRoute<HostFile>()
            EditHostDestination(
                host = host,
                vm = vm,
                navController = navController,
            )
        }
        composable<HostException> { backstackEntry ->
            vm.hideStatusBarShade()
            val host = backstackEntry.toRoute<HostException>()
            EditHostDestination(
                host = host,
                vm = vm,
                navController = navController,
            )
        }
        composable<DnsServer> { backstackEntry ->
            vm.hideStatusBarShade()
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
        composable<TopLevelDestination.About> {
            vm.hideStatusBarShade()
            AboutScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenCredits = { navController.navigate(TopLevelDestination.Credits) },
            )
        }
        composable<TopLevelDestination.BlockLog> {
            vm.hideStatusBarShade()
            BlockLogScreen(
                canEditSettings = canEditSettings,
                onNavigateUp = { navController.popBackStack() },
                loggedConnections = vm.connectionsLog,
                onCreateException = {
                    navController.navigate(
                        HostException(
                            title = "",
                            data = it.hostname,
                            state = if (it.allowed) {
                                HostState.DENY
                            } else {
                                HostState.ALLOW
                            },
                        )
                    )
                },
            )
        }
        composable<TopLevelDestination.Credits> {
            vm.hideStatusBarShade()
            CreditsScreen { navController.popBackStack() }
        }
    }
}

@Composable
fun EditHostDestination(
    host: Host,
    vm: HomeViewModel,
    navController: NavController,
) {
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
                if (host is HostException) {
                    vm.removeBlockLogEntry(host.data)
                }
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
        onUriPermissionAcquireFailed = if (host is HostFile) {
            { vm.onFilePermissionDenied() }
        } else {
            null
        },
    )
}

@Preview
@Composable
fun AppPreview() {
    App(
        onRefreshHosts = {},
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

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: HomeViewModel,
    topLevelNavController: NavHostController,
    status: VpnStatus,
    canEditSettings: Boolean,
    onRefreshHosts: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onShareLogcat: () -> Unit,
    onTryToggleService: () -> Unit,
    onUpdateRefreshWork: () -> Unit,
) {
    val navController = rememberNavController()
    var currentDestination: HomeDestination by rememberSaveable {
        mutableStateOf(HomeDestinations.Start)
    }

    val setDestination = { newHomeDestination: HomeDestination ->
        if (currentDestination != newHomeDestination) {
            currentDestination = newHomeDestination
            navController.navigate(newHomeDestination) {
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

    val displayCutout = WindowInsets.displayCutout
    val localDensity = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val startCutoutInset =
        (displayCutout.getLeft(localDensity, layoutDirection) / localDensity.density).dp
    val endCutoutInset =
        (displayCutout.getRight(localDensity, layoutDirection) / localDensity.density).dp
    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            HomeDestinations.entries.forEach {
                item(
                    modifier = Modifier.padding(start = startCutoutInset),
                    selected = it == currentDestination,
                    onClick = { setDestination(it) },
                    icon = {
                        Icon(
                            imageVector = it.iconEnum.icon,
                            contentDescription = stringResource(it.labelResId),
                        )
                    },
                    label = {
                        // For whatever reason, UIAutomator cannot find tags on navigation
                        // items unless one is set in this label scope.
                        Text(
                            modifier = Modifier.testTag("homeNavigation:$it"),
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
        Scaffold(
            contentWindowInsets = navigationSuiteScaffoldContentInsets,
            floatingActionButton = {
                val endSystemBarsPadding = (
                    WindowInsets.systemBars.getRight(
                        localDensity,
                        layoutDirection,
                    ) / localDensity.density
                    ).dp
                AnimatedVisibility(
                    modifier = Modifier
                        .padding(end = endCutoutInset)
                        .padding(end = endSystemBarsPadding),
                    visible = (
                        currentDestination == HomeDestinations.Hosts ||
                            currentDestination == HomeDestinations.DNS
                        ) && canEditSettings,
                    enter = scaleIn(animationSpec = tween(easing = EmphasizedDecelerateEasing)),
                    exit = scaleOut(animationSpec = tween(easing = EmphasizedAccelerateEasing)),
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (currentDestination == HomeDestinations.Hosts) {
                                topLevelNavController.navigate(HostFile())
                            } else if (currentDestination == HomeDestinations.DNS) {
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
                startDestination = HomeDestinations.Start,
                enterTransition = { HomeEnterTransition },
                exitTransition = { HomeExitTransition },
                popEnterTransition = { HomeEnterTransition },
                popExitTransition = { HomeExitTransition },
            ) {
                composable<HomeDestinations.Start> {
                    vm.showStatusBarShade()
                    var resumeOnStartup by remember { mutableStateOf(config.autoStart) }
                    var watchConnection by remember { mutableStateOf(config.watchDog) }
                    var ipv6Support by remember { mutableStateOf(config.ipV6Support) }
                    var blockLog by remember { mutableStateOf(config.blockLogging) }

                    val showDisableBlockLogWarningDialog by vm.showDisableBlockLogWarningDialog.collectAsState()
                    if (showDisableBlockLogWarningDialog) {
                        BasicDialog(
                            title = stringResource(R.string.warning),
                            text = stringResource(R.string.disable_block_log_warning_description),
                            primaryButton = DialogButton(
                                text = stringResource(R.string.disable),
                                onClick = {
                                    vm.onClearBlockLog()
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
                            topLevelNavController.navigate(TopLevelDestination.BlockLog)
                        },
                        onImport = onImport,
                        onExport = onExport,
                        onShareLogcat = onShareLogcat,
                        onResetSettings = { vm.onResetSettingsWarning() },
                        onOpenAbout = { topLevelNavController.navigate(TopLevelDestination.About) },
                        status = status,
                        onChangeVpnStatusClick = onTryToggleService,
                    )
                }
                composable<HomeDestinations.Hosts> {
                    vm.showStatusBarShade()
                    var refreshDaily by remember { mutableStateOf(config.hosts.automaticRefresh) }
                    val isRefreshingHosts by RuleDatabaseUpdateWorker.isRefreshing.collectAsState()
                    HostsScreen(
                        contentPadding = contentPadding + PaddingValues(ListPadding) +
                            PaddingValues(bottom = DefaultFabSize + FabPadding),
                        listState = hostsListState,
                        enabled = canEditSettings,
                        refreshDaily = refreshDaily,
                        onRefreshDailyClick = {
                            config.hosts.automaticRefresh = !config.hosts.automaticRefresh
                            refreshDaily = config.hosts.automaticRefresh
                            config.save()
                            onUpdateRefreshWork()
                        },
                        hosts = vm.hosts,
                        onHostClick = { host ->
                            topLevelNavController.navigate(host)
                        },
                        onHostStateChanged = { host ->
                            vm.cycleHost(host)
                        },
                        isRefreshingHosts = isRefreshingHosts,
                        onRefreshHosts = onRefreshHosts,
                    )
                }

                composable<HomeDestinations.Apps> {
                    vm.showStatusBarShade()
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
                        apps = vm.appList,
                        onAppClick = { app ->
                            vm.onToggleApp(app)
                        },
                    )
                }
                composable<HomeDestinations.DNS> {
                    vm.showStatusBarShade()
                    var customDnsServers by remember { mutableStateOf(config.dnsServers.enabled) }
                    DnsScreen(
                        contentPadding = contentPadding + PaddingValues(ListPadding) +
                            PaddingValues(bottom = DefaultFabSize + FabPadding),
                        listState = dnsListState,
                        enabled = canEditSettings,
                        servers = vm.dnsServers,
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
