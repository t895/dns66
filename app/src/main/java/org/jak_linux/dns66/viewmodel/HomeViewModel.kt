package org.jak_linux.dns66.viewmodel

import android.content.pm.ApplicationInfo
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
import org.jak_linux.dns66.FileHelper
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

    private val _vpnStatus = MutableStateFlow(VpnStatus.STOPPED)
    val vpnStatus = _vpnStatus.asStateFlow()

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
}
