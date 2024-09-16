/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet.viewmodel

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.t895.dnsnet.DnsNetApplication.Companion.applicationContext
import com.t895.dnsnet.FileHelper
import com.t895.dnsnet.ui.App
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

    var config: com.t895.dnsnet.Configuration = FileHelper.loadCurrentSettings()

    private val _hosts = MutableStateFlow(config.hosts.items.toList())
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

    init {
        populateAppList()
    }

    fun onUpdateIncomplete(errors: List<String>) {
        _showUpdateIncompleteDialog.value = true
        this.errors = errors
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
                if (it.packageName != com.t895.dnsnet.BuildConfig.APPLICATION_ID &&
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

    fun addHost(host: com.t895.dnsnet.Host) {
        config.hosts.items.add(host)
        _hosts.value = config.hosts.items.toList()
        FileHelper.writeSettings(config)
    }

    fun removeHost(host: com.t895.dnsnet.Host) {
        if (!config.hosts.items.contains(host)) {
            Log.w(TAG, "Tried to remove host that does not exist in config! - $host")
            return
        }
        config.hosts.items.remove(host)
        _hosts.value = config.hosts.items.toList()
        FileHelper.writeSettings(config)
    }

    fun replaceHost(oldHost: com.t895.dnsnet.Host, newHost: com.t895.dnsnet.Host) {
        if (!config.hosts.items.contains(oldHost)) {
            Log.w(TAG, "Tried to replace host that does not exist in config! - $oldHost")
            return
        }
        val oldIndex = config.hosts.items.indexOf(oldHost)
        config.hosts.items.removeAt(oldIndex)
        config.hosts.items.add(oldIndex, newHost)
        _hosts.value = config.hosts.items.toList()
        FileHelper.writeSettings(config)
    }

    fun cycleHost(host: com.t895.dnsnet.Host) {
        val newHost = host.copy()
        newHost.state = when (newHost.state) {
            com.t895.dnsnet.HostState.IGNORE -> com.t895.dnsnet.HostState.DENY
            com.t895.dnsnet.HostState.DENY -> com.t895.dnsnet.HostState.ALLOW
            com.t895.dnsnet.HostState.ALLOW -> com.t895.dnsnet.HostState.IGNORE
        }
        replaceHost(host, newHost)
    }

    fun addDnsServer(server: com.t895.dnsnet.DnsServer) {
        config.dnsServers.items.add(server)
        _dnsServers.value = config.dnsServers.items.toList()
        FileHelper.writeSettings(config)
    }

    fun removeDnsServer(server: com.t895.dnsnet.DnsServer) {
        if (!config.dnsServers.items.contains(server)) {
            Log.w(TAG, "Tried to remove DnsServer that does not exist in config! - $server")
            return
        }
        config.dnsServers.items.remove(server)
        _dnsServers.value = config.dnsServers.items.toList()
        FileHelper.writeSettings(config)
    }

    fun replaceDnsServer(oldServer: com.t895.dnsnet.DnsServer, newDnsServer: com.t895.dnsnet.DnsServer) {
        if (!config.dnsServers.items.contains(oldServer)) {
            Log.w(TAG, "Tried to replace host that does not exist in config! - $oldServer")
            return
        }
        val oldIndex = config.dnsServers.items.indexOf(oldServer)
        config.dnsServers.items.removeAt(oldIndex)
        config.dnsServers.items.add(oldIndex, newDnsServer)
        _dnsServers.value = config.dnsServers.items.toList()
        FileHelper.writeSettings(config)
    }

    fun toggleDnsServer(server: com.t895.dnsnet.DnsServer) {
        val newServer = server.copy()
        newServer.enabled = !newServer.enabled
        replaceDnsServer(server, newServer)
    }

    fun onReloadSettings() {
        populateAppList()
        _hosts.value = config.hosts.items
        _dnsServers.value = config.dnsServers.items
    }

    fun onToggleApp(app: App) {
        val newApp = app.copy()
        newApp.enabled = !newApp.enabled
        if (!_appList.value.contains(app)) {
            Log.w(TAG, "Tried to toggle app that does not exist in list! - $app")
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
        FileHelper.writeSettings(config)
    }

    fun onFilePermissionDenied() {
        _showFilePermissionDeniedDialog.value = true
    }

    fun onDismissFilePermissionDenied() {
        _showFilePermissionDeniedDialog.value = false
    }

    fun onNotificationPermissionNotGranted() {
        val denied = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(NOTIFICATION_PERMISSION_DENIED, false)
        if (!denied) {
            _showNotificationPermissionDialog.value = true
        }
    }

    fun onNotificationPermissionDenied() {
        onDismissNotificationPermission()
        PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
            .putBoolean(NOTIFICATION_PERMISSION_DENIED, true)
            .apply()
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

    companion object {
        const val TAG = "HomeViewModel"

        private const val NOTIFICATION_PERMISSION_DENIED = "NotificationPermissionDenied"
    }
}
