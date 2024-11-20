/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet.viewmodel

import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.t895.dnsnet.BuildConfig
import com.t895.dnsnet.DnsNetApplication.Companion.applicationContext
import com.t895.dnsnet.DnsServer
import com.t895.dnsnet.Host
import com.t895.dnsnet.HostException
import com.t895.dnsnet.HostFile
import com.t895.dnsnet.HostState
import com.t895.dnsnet.Preferences
import com.t895.dnsnet.config
import com.t895.dnsnet.db.RuleDatabaseUpdateWorker
import com.t895.dnsnet.logw
import com.t895.dnsnet.ui.App
import com.t895.dnsnet.vpn.AdVpnService
import com.t895.dnsnet.vpn.LoggedConnection
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections

class HomeViewModel : ViewModel() {
    private val _showUpdateIncompleteDialog = MutableStateFlow(false)
    val showUpdateIncompleteDialog = _showUpdateIncompleteDialog.asStateFlow()

    var errors: List<String>? = null

    private var refreshingLock by atomic(false)

    private val _appListRefreshing = MutableStateFlow(false)
    val appListRefreshing = _appListRefreshing.asStateFlow()

    private val _appList = MutableStateFlow<List<App>>(emptyList())
    val appList = _appList.asStateFlow()

    private val _hosts = MutableStateFlow(config.hosts.getAllHosts())
    val hosts = _hosts.asStateFlow()

    private val _dnsServers = MutableStateFlow(config.dnsServers.items.toList())
    val dnsServers = _dnsServers.asStateFlow()

    private val _showHostsFilesNotFoundDialog = MutableStateFlow(false)
    val showHostsFilesNotFoundDialog = _showHostsFilesNotFoundDialog.asStateFlow()

    private val _showWatchdogWarningDialog = MutableStateFlow(false)
    val showWatchdogWarningDialog = _showWatchdogWarningDialog.asStateFlow()

    private val _showFilePermissionDeniedDialog = MutableStateFlow(false)
    val showFilePermissionDeniedDialog = _showFilePermissionDeniedDialog.asStateFlow()

    private val _showNotificationPermissionDialog = MutableStateFlow(false)
    val showNotificationPermissionDialog = _showNotificationPermissionDialog.asStateFlow()

    private val _showVpnConfigurationFailureDialog = MutableStateFlow(false)
    val showVpnConfigurationFailureDialog = _showVpnConfigurationFailureDialog.asStateFlow()

    private val _showDisablePrivateDnsDialog = MutableStateFlow(false)
    val showDisablePrivateDnsDialog = _showDisablePrivateDnsDialog.asStateFlow()

    private val _connectionsLogState =
        MutableStateFlow<Map<String, LoggedConnection>>(emptyMap())
    val connectionsLogState = _connectionsLogState.asStateFlow()

    private val _showDisableBlockLogWarningDialog = MutableStateFlow(false)
    val showDisableBlockLogWarningDialog = _showDisableBlockLogWarningDialog.asStateFlow()

    private val _showResetSettingsWarningDialog = MutableStateFlow(false)
    val showResetSettingsWarningDialog = _showResetSettingsWarningDialog.asStateFlow()

    private val _showDeleteDnsServerWarningDialog = MutableStateFlow(false)
    val showDeleteDnsServerWarningDialog = _showDeleteDnsServerWarningDialog.asStateFlow()

    private val _showDeleteHostWarningDialog = MutableStateFlow(false)
    val showDeleteHostWarningDialog = _showDeleteHostWarningDialog.asStateFlow()

    init {
        _connectionsLogState.value = AdVpnService.logger.connections
        AdVpnService.logger.setOnConnectionListener {
            _connectionsLogState.value = HashMap(it)
        }
        populateAppList()
    }

    override fun onCleared() {
        super.onCleared()
        AdVpnService.logger.setOnConnectionListener(null)
    }

    fun onCheckForUpdateErrors() {
        val workerErrors = RuleDatabaseUpdateWorker.lastErrors
        if (!workerErrors.isNullOrEmpty()) {
            _showUpdateIncompleteDialog.value = true
            errors = workerErrors
            RuleDatabaseUpdateWorker.lastErrors = null
        }
    }

    fun onDismissUpdateIncomplete() {
        errors = null
        _showUpdateIncompleteDialog.value = false
    }

    fun populateAppList() {
        if (refreshingLock) {
            return
        }
        refreshingLock = true
        _appListRefreshing.value = true

        val pm = applicationContext.packageManager
        viewModelScope.launch(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(0)

            Collections.sort(apps, ApplicationInfo.DisplayNameComparator(pm))

            val entries = ArrayList<App>()
            val notOnVpn = HashSet<String>()
            config.appList.resolve(pm, HashSet(), notOnVpn)
            apps.forEach {
                if (it.packageName != BuildConfig.APPLICATION_ID &&
                    (config.appList.showSystemApps ||
                            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0)
                ) {
                    entries.add(
                        App(
                            info = it,
                            label = it.loadLabel(pm).toString(),
                            enabled = notOnVpn.contains(it.packageName),
                        )
                    )
                }
            }

            _appList.value = entries
            _appListRefreshing.value = false
            refreshingLock = false
        }
    }

    fun onHostsFilesNotFound() {
        _showHostsFilesNotFoundDialog.value = true
    }

    fun onDismissHostsFilesNotFound() {
        _showHostsFilesNotFoundDialog.value = false
    }

    fun onEnableWatchdog() {
        _showWatchdogWarningDialog.value = true
    }

    fun onDismissWatchdogWarning() {
        _showWatchdogWarningDialog.value = false
    }

    private fun updateHostsList() {
        _hosts.value = config.hosts.getAllHosts()
        config.save()
    }

    private fun addHostFile(host: HostFile) {
        config.hosts.items.add(host)
        updateHostsList()
    }

    private fun addHostException(host: HostException) {
        config.hosts.exceptions.add(host)
        updateHostsList()
    }

    fun addHost(host: Host) {
        when (host) {
            is HostFile -> addHostFile(host)
            is HostException -> addHostException(host)
        }
    }

    private fun removeHostFile(host: HostFile) {
        if (!config.hosts.items.contains(host)) {
            logw("Tried to remove host that does not exist in config! - $host")
            return
        }
        config.hosts.items.remove(host)
        updateHostsList()
    }

    private fun removeHostException(host: HostException) {
        if (!config.hosts.exceptions.contains(host)) {
            logw("Tried to remove host that does not exist in config! - $host")
            return
        }
        config.hosts.exceptions.remove(host)
        updateHostsList()
    }

    fun removeHost(host: Host) {
        when (host) {
            is HostFile -> removeHostFile(host)
            is HostException -> removeHostException(host)
        }
    }

    private fun replaceHostFile(oldHost: HostFile, newHost: HostFile) {
        if (!config.hosts.items.contains(oldHost)) {
            logw("Tried to replace host that does not exist in config! - $oldHost")
            return
        }
        val oldIndex = config.hosts.items.indexOf(oldHost)
        config.hosts.items.removeAt(oldIndex)
        config.hosts.items.add(oldIndex, newHost)
        updateHostsList()
    }

    private fun replaceHostException(oldHost: HostException, newHost: HostException) {
        if (!config.hosts.exceptions.contains(oldHost)) {
            logw("Tried to replace host that does not exist in config! - $oldHost")
            return
        }
        val oldIndex = config.hosts.exceptions.indexOf(oldHost)
        config.hosts.exceptions.removeAt(oldIndex)
        config.hosts.exceptions.add(oldIndex, newHost)
        updateHostsList()
    }

    fun replaceHost(oldHost: Host, newHost: Host) {
        if (oldHost is HostFile && newHost is HostFile) {
            replaceHostFile(oldHost, newHost)
        } else if (oldHost is HostException && newHost is HostException) {
            replaceHostException(oldHost, newHost)
        }
    }

    private fun cycleHostFile(host: HostFile) {
        val newHost = host.copy()
        newHost.state = when (newHost.state) {
            HostState.IGNORE -> HostState.DENY
            HostState.DENY -> HostState.ALLOW
            HostState.ALLOW -> HostState.IGNORE
        }
        replaceHostFile(host, newHost)
    }

    private fun cycleHostException(host: HostException) {
        val newHost = host.copy()
        newHost.state = when (newHost.state) {
            HostState.IGNORE -> HostState.DENY
            HostState.DENY -> HostState.ALLOW
            HostState.ALLOW -> HostState.IGNORE
        }
        replaceHostException(host, newHost)
    }

    fun cycleHost(host: Host) {
        when (host) {
            is HostFile -> cycleHostFile(host)
            is HostException -> cycleHostException(host)
        }
    }

    fun removeBlockLogEntry(hostname: String) {
        AdVpnService.logger.connections.remove(hostname)
        _connectionsLogState.value = HashMap(AdVpnService.logger.connections)
    }

    fun addDnsServer(server: DnsServer) {
        config.dnsServers.items.add(server)
        _dnsServers.value = config.dnsServers.items.toList()
        config.save()
    }

    fun removeDnsServer(server: DnsServer) {
        if (!config.dnsServers.items.contains(server)) {
            logw("Tried to remove DnsServer that does not exist in config! - $server")
            return
        }
        config.dnsServers.items.remove(server)
        _dnsServers.value = config.dnsServers.items.toList()
        config.save()
    }

    fun replaceDnsServer(
        oldServer: DnsServer,
        newDnsServer: DnsServer
    ) {
        if (!config.dnsServers.items.contains(oldServer)) {
            logw("Tried to replace host that does not exist in config! - $oldServer")
            return
        }
        val oldIndex = config.dnsServers.items.indexOf(oldServer)
        config.dnsServers.items.removeAt(oldIndex)
        config.dnsServers.items.add(oldIndex, newDnsServer)
        _dnsServers.value = config.dnsServers.items.toList()
        config.save()
    }

    fun toggleDnsServer(server: DnsServer) {
        val newServer = server.copy()
        newServer.enabled = !newServer.enabled
        replaceDnsServer(server, newServer)
    }

    fun onReloadSettings() {
        populateAppList()
        _hosts.value = config.hosts.items
        _dnsServers.value = config.dnsServers.items

        if (!config.blockLogging) {
            AdVpnService.logger.clear()
        }
    }

    fun onToggleApp(app: App) {
        val newApp = app.copy()
        newApp.enabled = !newApp.enabled
        if (!_appList.value.contains(app)) {
            logw("Tried to toggle app that does not exist in list! - $app")
            return
        }
        val oldIndex = _appList.value.indexOf(app)
        val newAppList = _appList.value.toMutableList()
        newAppList.removeAt(oldIndex)
        newAppList.add(oldIndex, newApp)
        _appList.value = newAppList.toList()

        // No change
        if (newApp.enabled && config.appList.onVpn.contains(newApp.info.packageName)) {
            return
        }
        if (!newApp.enabled && config.appList.onVpn.contains(newApp.info.packageName)) {
            return
        }

        if (newApp.enabled) {
            config.appList.notOnVpn.add(newApp.info.packageName)
            config.appList.onVpn.remove(newApp.info.packageName)
        } else {
            config.appList.notOnVpn.remove(newApp.info.packageName)
            config.appList.onVpn.add(newApp.info.packageName)
        }
        config.save()
    }

    fun onFilePermissionDenied() {
        _showFilePermissionDeniedDialog.value = true
    }

    fun onDismissFilePermissionDenied() {
        _showFilePermissionDeniedDialog.value = false
    }

    fun onNotificationPermissionNotGranted() {
        if (!Preferences.NotificationPermissionDenied) {
            _showNotificationPermissionDialog.value = true
        }
    }

    fun onNotificationPermissionDenied() {
        onDismissNotificationPermission()
        Preferences.NotificationPermissionDenied = true
    }

    fun onDismissNotificationPermission() {
        _showNotificationPermissionDialog.value = false
    }

    fun onVpnConfigurationFailure() {
        _showVpnConfigurationFailureDialog.value = true
    }

    fun onDismissVpnConfigurationFailure() {
        _showVpnConfigurationFailureDialog.value = false
    }

    fun onPrivateDnsEnabledWarning() {
        _showDisablePrivateDnsDialog.value = true
    }

    fun onDismissPrivateDnsEnabledWarning() {
        _showDisablePrivateDnsDialog.value = false
    }

    fun onDisableBlockLogWarning() {
        _showDisableBlockLogWarningDialog.value = true
    }

    fun onDismissDisableBlockLogWarning() {
        _showDisableBlockLogWarningDialog.value = false
    }

    fun onResetSettingsWarning() {
        _showResetSettingsWarningDialog.value = true
    }

    fun onDismissResetSettingsDialog() {
        _showResetSettingsWarningDialog.value = false
    }

    fun onDeleteDnsServerWarning() {
        _showDeleteDnsServerWarningDialog.value = true
    }

    fun onDismissDeleteDnsServerWarning() {
        _showDeleteDnsServerWarningDialog.value = false
    }

    fun onDeleteHostWarning() {
        _showDeleteHostWarningDialog.value = true
    }

    fun onDismissDeleteHostWarning() {
        _showDeleteHostWarningDialog.value = false
    }
}
