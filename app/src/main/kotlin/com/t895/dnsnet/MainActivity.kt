/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * Derived from DNS66:
 * Copyright (C) 2016 - 2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.t895.dnsnet

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService.prepare
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.t895.dnsnet.db.RuleDatabaseUpdateWorker
import com.t895.dnsnet.ui.App
import com.t895.dnsnet.ui.theme.DnsNetTheme
import com.t895.dnsnet.viewmodel.HomeViewModel
import com.t895.dnsnet.vpn.AdVpnService
import com.t895.dnsnet.vpn.Command
import com.t895.dnsnet.vpn.VpnStatus
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val vm: HomeViewModel by viewModels()

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        NotificationChannels.onCreate(this)

        setContent {
            enableEdgeToEdge()
            DnsNetTheme {
                val importLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                        it ?: return@rememberLauncherForActivityResult
                        try {
                            config = Configuration.load(contentResolver.openInputStream(it)!!)
                        } catch (e: Exception) {
                            logd("Cannot read file", e)
                            Toast.makeText(
                                this,
                                "Cannot read file: ${e.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        config.save()
                        vm.onReloadSettings()
                        recreate()
                    }

                val exportLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
                        uri ?: return@rememberLauncherForActivityResult
                        try {
                            OutputStreamWriter(contentResolver.openOutputStream(uri)).use {
                                config.save()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                this,
                                "Cannot write file: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                val vpnLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        if (it.resultCode == Activity.RESULT_CANCELED) {
                            vm.onVpnConfigurationFailure()
                        } else if (it.resultCode == Activity.RESULT_OK) {
                            logd("onActivityResult: Starting service")
                            createService()
                        }
                    }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true },
                ) {
                    App(
                        vm = vm,
                        onRefresh = ::refresh,
                        onLoadDefaults = {
                            config = Configuration()
                            config.save()
                            vm.onReloadSettings()
                            recreate()
                        },
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                        onExport = { exportLauncher.launch("dnsnet.json") },
                        onShareLogcat = ::sendLogcat,
                        onTryToggleService = { tryToggleService(true, vpnLauncher) },
                        onStartWithoutHostsCheck = { tryToggleService(false, vpnLauncher) },
                        onUpdateRefreshWork = ::updateRefreshWork,
                        onOpenNetworkSettings = ::openNetworkSettings,
                    )
                }
            }
        }

        if (!areHostsFilesExistent() && savedInstanceState == null) {
            refresh()
        }

        updateRefreshWork()
    }

    private fun sendLogcat() {
        var proc: Process? = null
        try {
            proc = Runtime.getRuntime().exec("logcat -d")
            val bis = BufferedReader(InputStreamReader(proc.inputStream))
            val logcat = StringBuilder()
            var line: String?
            while (bis.readLine().also { line = it } != null) {
                logcat.append(line)
                logcat.append('\n')
            }

            val sendIntent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_EMAIL, arrayOf("clombardo169@gmail.com"))
                .putExtra(Intent.EXTRA_SUBJECT, "DNSNet Logcat")
                .putExtra(Intent.EXTRA_TEXT, logcat.toString())
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Not supported: $e", Toast.LENGTH_LONG).show()
        } finally {
            proc?.destroy()
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (intent.getBooleanExtra("UPDATE", false)) {
            refresh()
        }

        vm.onCheckForUpdateErrors()

        super.onNewIntent(intent)
    }

    private fun refresh() {
        val workRequest = OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>()
            .build()
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun tryToggleService(
        hostsCheck: Boolean,
        launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    ) {
        if (AdVpnService.status.value != VpnStatus.STOPPED) {
            logi("Attempting to disconnect")
            val intent = Intent(this, AdVpnService::class.java)
                .putExtra("COMMAND", Command.STOP.ordinal)
            startService(intent)
        } else {
            if (isPrivateDnsEnabled()) {
                vm.onPrivateDnsEnabledWarning()
                return
            }
            if (!areHostsFilesExistent() && hostsCheck) {
                vm.onHostsFilesNotFound()
                return
            }
            startService(launcher)
        }
    }

    /**
     * Check if all configured hosts files exist.
     *
     * @return true if all host files exist or no host files were configured.
     */
    private fun areHostsFilesExistent(): Boolean {
        if (!config.hosts.enabled) {
            return true
        }

        for (item in config.hosts.items) {
            if (item.state != HostState.IGNORE) {
                try {
                    val reader = FileHelper.openItemFile(item) ?: return false
                    reader.close()
                } catch (e: IOException) {
                    return false
                }
            }
        }
        return true
    }

    private fun isPrivateDnsEnabled(): Boolean {
        // Private DNS isn't enabled by default until Android 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return false
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return false
        return linkProperties.isPrivateDnsActive || linkProperties.privateDnsServerName != null
    }

    private fun openNetworkSettings() = startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))

    /**
     * Starts the AdVpnService. If the user has not allowed this
     * VPN to run before, it will show a dialog and then call
     * onActivityResult with either [Activity.RESULT_CANCELED]
     * or [Activity.RESULT_OK] for deny/allow respectively.
     */
    private fun startService(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        logi("Attempting to connect")
        val intent = prepare(DnsNetApplication.applicationContext)
        if (intent != null) {
            launcher.launch(intent)
        } else {
            createService()
        }
    }

    private fun createService() {
        val intent = Intent(applicationContext, AdVpnService::class.java)
            .putExtra("COMMAND", Command.START.ordinal)
            .putExtra(
                "NOTIFICATION_INTENT",
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    Intent(applicationContext, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateRefreshWork() {
        val workManager = WorkManager.getInstance(this)
        if (config.hosts.automaticRefresh) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresDeviceIdle(true)
                .setRequiresStorageNotLow(true)
                .build()

            val work = PeriodicWorkRequestBuilder<RuleDatabaseUpdateWorker>(1, TimeUnit.DAYS)
                .addTag(RuleDatabaseUpdateWorker.PERIODIC_TAG)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                RuleDatabaseUpdateWorker.PERIODIC_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                work,
            )
        } else {
            workManager.cancelAllWorkByTag(RuleDatabaseUpdateWorker.PERIODIC_TAG)
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onCheckForUpdateErrors()
    }
}
