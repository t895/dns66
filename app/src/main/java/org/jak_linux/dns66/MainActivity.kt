package org.jak_linux.dns66

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService.prepare
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.jak_linux.dns66.NotificationChannels.onCreate
import org.jak_linux.dns66.db.RuleDatabaseUpdateWorker
import org.jak_linux.dns66.ui.HomeScreen
import org.jak_linux.dns66.ui.theme.Dns66Theme
import org.jak_linux.dns66.viewmodel.HomeViewModel
import org.jak_linux.dns66.vpn.AdVpnService
import org.jak_linux.dns66.vpn.Command
import org.jak_linux.dns66.vpn.VpnStatus
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"

        const val REQUEST_START_VPN = 1
    }

    private val vm: HomeViewModel by viewModels()

    private val vpnServiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getSerializable<VpnStatus>(AdVpnService.VPN_UPDATE_STATUS_EXTRA)
                ?: VpnStatus.STOPPED
            vm.onUpdateVpnStatus(status)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onCreate(this)

        setContent {
            enableEdgeToEdge()
            Dns66Theme {
                val importLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                        it ?: return@rememberLauncherForActivityResult
                        try {
                            vm.config = Configuration.read(
                                InputStreamReader(contentResolver.openInputStream(it))
                            )
                        } catch (e: Exception) {
                            Toast.makeText(
                                this,
                                "Cannot read file: ${e.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        FileHelper.writeSettings(vm.config)
                        vm.onReloadSettings()
                        recreate()
                    }

                val exportLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
                        uri ?: return@rememberLauncherForActivityResult
                        try {
                            OutputStreamWriter(contentResolver.openOutputStream(uri)).use {
                                vm.config.write(it)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                this,
                                "Cannot write file: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                HomeScreen(
                    vm = vm,
                    onRefresh = ::refresh,
                    onLoadDefaults = {
                        vm.config = FileHelper.loadDefaultSettings()
                        FileHelper.writeSettings(vm.config)
                        vm.onReloadSettings()
                        recreate()
                    },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onExport = { exportLauncher.launch("dns66.json") },
                    onShareLogcat = ::sendLogcat,
                    onTryToggleService = ::tryToggleService,
                    onStartWithoutChecks = ::startService,
                    onUpdateRefreshWork = ::updateRefreshWork,
                )
            }
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
                .putExtra(Intent.EXTRA_EMAIL, arrayOf("jak@jak-linux.org"))
                .putExtra(Intent.EXTRA_SUBJECT, "DNS66 Logcat")
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

        val errors = RuleDatabaseUpdateWorker.lastErrors
        if (!errors.isNullOrEmpty()) {
            Log.d(TAG, "onNewIntent: It's an error")
            vm.onUpdateIncomplete(errors)
        }

        super.onNewIntent(intent)
    }

    private fun refresh() {
        val workRequest = OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>()
            .build()
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult: Received result=$resultCode for request=$requestCode")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_START_VPN && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(
                applicationContext,
                R.string.could_not_configure_vpn_service,
                Toast.LENGTH_LONG
            ).show()
        }

        if (requestCode == REQUEST_START_VPN && resultCode == Activity.RESULT_OK) {
            Log.d("MainActivity", "onActivityResult: Starting service")
            createService()
        }
    }

    fun tryToggleService() {
        if (AdVpnService.status != VpnStatus.STOPPED) {
            Log.i(TAG, "Attempting to disconnect")
            val intent = Intent(this, AdVpnService::class.java)
                .putExtra("COMMAND", Command.STOP.ordinal)
            startService(intent)
        } else {
            checkHostsFilesAndStartService()
        }
    }

    private fun checkHostsFilesAndStartService() {
        if (!areHostsFilesExistent()) {
            vm.onHostsFilesNotFound()
            return
        }
        startService()
    }

    /**
     * Check if all configured hosts files exist.
     *
     * @return true if all host files exist or no host files were configured.
     */
    private fun areHostsFilesExistent(): Boolean {
        if (!vm.config.hosts.enabled) {
            return true
        }

        for (item in vm.config.hosts.items) {
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

    /**
     * Starts the AdVpnService. If the user has not allowed this
     * VPN to run before, it will show a dialog and then call
     * onActivityResult with either [Activity.RESULT_CANCELED]
     * or [Activity.RESULT_OK] for deny/allow respectively.
     *
     * This is currently the only way of requesting permission
     * to start a VPN service. There are no activity result
     * contracts that can replace this deprecated functionality.
     */
    @Suppress("DEPRECATION")
    private fun startService() {
        Log.i(TAG, "Attempting to connect")
        val intent = prepare(Dns66Application.applicationContext)
        if (intent != null) {
            startActivityForResult(intent, REQUEST_START_VPN)
        } else {
            createService()
        }
    }

    fun createService() {
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
        if (vm.config.hosts.automaticRefresh) {
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

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnServiceBroadcastReceiver)
    }

    override fun onResume() {
        super.onResume()
        val errors = RuleDatabaseUpdateWorker.lastErrors
        if (!errors.isNullOrEmpty()) {
            Log.d(TAG, "onNewIntent: It's an error")
            vm.onUpdateIncomplete(errors)
        }

        vm.onUpdateVpnStatus(AdVpnService.status)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                vpnServiceBroadcastReceiver,
                IntentFilter(AdVpnService.VPN_UPDATE_STATUS_INTENT)
            )
    }
}
