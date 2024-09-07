package org.jak_linux.dns66.viewmodel

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jak_linux.dns66.BuildConfig
import org.jak_linux.dns66.main.AppItem
import java.util.Collections
import kotlinx.atomicfu.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.Dns66Application.Companion.applicationContext
import org.jak_linux.dns66.DnsServer
import org.jak_linux.dns66.FileHelper
import org.jak_linux.dns66.Host
import org.jak_linux.dns66.HostState
import org.jak_linux.dns66.vpn.AdVpnService
import org.jak_linux.dns66.vpn.VpnStatus

class HomeViewModel : ViewModel() {
    private val _showUpdateIncompleteDialog = MutableStateFlow(false)
    val showUpdateIncompleteDialog = _showUpdateIncompleteDialog.asStateFlow()

    var errors: List<String>? = null

    private var refreshingLock by atomic(false)

    private val _appListRefreshing = MutableStateFlow(false)
    val appListRefreshing = _appListRefreshing.asStateFlow()

    private val _appList = MutableStateFlow<List<AppItem>>(emptyList())
    val appList = _appList.asStateFlow()

    val onVpn = HashSet<String>()
    val notOnVpn = HashSet<String>()

    var config: Configuration = FileHelper.loadCurrentSettings()

    private val _hosts = MutableStateFlow(config.hosts.items.toList())
    val hosts = _hosts.asStateFlow()

    private val _dnsServers = MutableStateFlow(config.dnsServers.items.toList())
    val dnsServers = _dnsServers.asStateFlow()

    private val _vpnStatus = MutableStateFlow(VpnStatus.STOPPED)
    val vpnStatus = _vpnStatus.asStateFlow()

    private val _showHostsFilesNotFoundDialog = MutableStateFlow(false)
    val showHostsFilesNotFoundDialog = _showHostsFilesNotFoundDialog.asStateFlow()

    private val _showWatchdogWarningDialog = MutableStateFlow(false)
    val showWatchdogWarningDialog = _showWatchdogWarningDialog.asStateFlow()

    private val _showHomeTopAppBar = MutableStateFlow(true)
    val showHomeTopAppBar = _showHomeTopAppBar.asStateFlow()

    private val _showHomeNavigationBar = MutableStateFlow(true)
    val showHomeNavigationBar = _showHomeNavigationBar.asStateFlow()

    init {
        populateAppList()
        _vpnStatus.value = AdVpnService.status
    }

    fun onUpdateIncomplete(errors: List<String>) {
        _showUpdateIncompleteDialog.value = true
        this.errors = errors
    }

    fun onUpdateIncompleteDismiss() {
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

            val entries = ArrayList<AppItem>()
            apps.forEach {
                if (it.packageName != BuildConfig.APPLICATION_ID &&
                    (config.allowlist.showSystemApps ||
                            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0)
                ) {
                    entries.add(AppItem(it, it.packageName, it.loadLabel(pm).toString()))
                }
            }

            config.allowlist.resolve(pm, onVpn, notOnVpn)

            _appList.value = entries
            _appListRefreshing.value = false
            refreshingLock = false
        }
    }

    fun onUpdateVpnStatus(status: VpnStatus) {
        _vpnStatus.value = status
    }

    fun onHostsFilesNotFound() {
        _showHostsFilesNotFoundDialog.value = true
    }

    fun onHostsFilesNotFoundDismissed() {
        _showHostsFilesNotFoundDialog.value = false
    }

    fun onEnableWatchdog() {
        _showWatchdogWarningDialog.value = true
    }

    fun onDismissWatchdogWarning() {
        _showWatchdogWarningDialog.value = false
    }

    fun showHomeInterface(topAppBar: Boolean = true, navigationBar: Boolean = true) {
        _showHomeTopAppBar.value = topAppBar
        _showHomeNavigationBar.value = navigationBar
    }

    fun addHost(host: Host) {
        config.hosts.items.add(host)
        _hosts.value = config.hosts.items.toList()
        FileHelper.writeSettings(config)
    }

    fun removeHost(host: Host) {
        if (!config.hosts.items.contains(host)) {
            Log.w(TAG, "Tried to remove host that does not exist in config! - $host")
            return
        }
        config.hosts.items.remove(host)
        _hosts.value = config.hosts.items.toList()
        FileHelper.writeSettings(config)
    }

    fun replaceHost(oldHost: Host, newHost: Host) {
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

    fun cycleHost(host: Host) {
        val newHost = host.copy()
        newHost.state = when (newHost.state) {
            HostState.IGNORE -> HostState.DENY
            HostState.DENY -> HostState.ALLOW
            HostState.ALLOW -> HostState.IGNORE
        }
        replaceHost(host, newHost)
    }

    fun addDnsServer(server: DnsServer) {
        config.dnsServers.items.add(server)
        _dnsServers.value = config.dnsServers.items.toList()
        FileHelper.writeSettings(config)
    }

    fun removeDnsServer(server: DnsServer) {
        if (!config.dnsServers.items.contains(server)) {
            Log.w(TAG, "Tried to remove DnsServer that does not exist in config! - $server")
            return
        }
        config.dnsServers.items.remove(server)
        _dnsServers.value = config.dnsServers.items.toList()
        FileHelper.writeSettings(config)
    }

    fun replaceDnsServer(oldServer: DnsServer, newDnsServer: DnsServer) {
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

    fun toggleDnsServer(server: DnsServer) {
        val newServer = server.copy()
        newServer.enabled = !newServer.enabled
        replaceDnsServer(server, newServer)
    }

    fun onReloadSettings() {
        populateAppList()
        _hosts.value = config.hosts.items
        _dnsServers.value = config.dnsServers.items
    }

    companion object {
        const val TAG = "HomeViewModel"
    }
}
