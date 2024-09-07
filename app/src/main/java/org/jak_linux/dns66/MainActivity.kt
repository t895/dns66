package org.jak_linux.dns66

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.jak_linux.dns66.NotificationChannels.onCreate
import org.jak_linux.dns66.db.RuleDatabaseUpdateJobService
import org.jak_linux.dns66.db.RuleDatabaseUpdateTask
import org.jak_linux.dns66.ui.HomeScreen
import org.jak_linux.dns66.ui.theme.Dns66Theme
import org.jak_linux.dns66.viewmodel.HomeViewModel
import org.jak_linux.dns66.vpn.AdVpnService
import org.jak_linux.dns66.vpn.Command
import org.jak_linux.dns66.vpn.VpnStatus
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"

        private const val REQUEST_FILE_OPEN = 1
        private const val REQUEST_FILE_STORE = 2
        private const val REQUEST_ITEM_EDIT = 3
        const val REQUEST_START_VPN = 4
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
                HomeScreen(
                    vm = vm,
                    onRefresh = ::refresh,
                    onLoadDefaults = {
                        vm.config = FileHelper.loadDefaultSettings()
                        FileHelper.writeSettings(vm.config)
                        vm.onReloadSettings()
                        recreate()
                    },
                    onImport = {
                        val intent = Intent()
                            .setType("*/*")
                            .setAction(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)

                        startActivityForResult(intent, REQUEST_FILE_OPEN)
                    },
                    onExport = {
                        val exportIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("*/*")
                            .putExtra(Intent.EXTRA_TITLE, "dns66.json")

                        startActivityForResult(exportIntent, REQUEST_FILE_STORE)
                    },
                    onShareLogcat = ::sendLogcat,
                    onTryToggleService = { AdVpnService.tryToggleService(vm, this) },
                    onCreateService = ::createService,
                )
            }
        }

        RuleDatabaseUpdateJobService.scheduleOrCancel(vm.config)
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

    override fun onNewIntent(intent: Intent?) {
        if (intent!!.getBooleanExtra("UPDATE", false)) {
            refresh()
        }

        val errors = RuleDatabaseUpdateTask.lastErrors.getAndSet(null)
        if (errors != null && errors.isNotEmpty()) {
            Log.d(TAG, "onNewIntent: It's an error")
            vm.onUpdateIncomplete(errors)
        }

        super.onNewIntent(intent)
    }

    private fun refresh() {
        val task = RuleDatabaseUpdateTask(vm.config, true)
        task.execute()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult: Received result=$resultCode for request=$requestCode")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_FILE_OPEN && resultCode == RESULT_OK) {
            val selectedfile = data?.data ?: return //The uri with the location of the file
            try {
                vm.config = Configuration.read(
                    InputStreamReader(contentResolver.openInputStream(selectedfile))
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            FileHelper.writeSettings(vm.config)
            vm.onReloadSettings()
            recreate()
        }

        if (requestCode == REQUEST_FILE_STORE && resultCode == RESULT_OK) {
            // The uri with the location of the file
            val selectedfile = data!!.data
            try {
                OutputStreamWriter(contentResolver.openOutputStream(selectedfile!!)).use {
                    vm.config.write(it)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot write file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            recreate()
        }

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

//        if (requestCode == REQUEST_ITEM_EDIT && resultCode == RESULT_OK) {
//            val item = Configuration.Item()
//            Log.d("FOOOO", "onActivityResult: item title = " + data!!.getStringExtra("ITEM_TITLE"))
//            if (data.hasExtra("DELETE")) {
//                itemChangedListener!!.onItemChanged(null)
//                return
//            }
//
//            item.apply {
//                title = data.getStringExtra("ITEM_TITLE") ?: ""
//                location = data.getStringExtra("ITEM_LOCATION") ?: ""
//                state = data.getIntExtra("ITEM_STATE", 0)
//            }
//            itemChangedListener!!.onItemChanged(item)
//        }
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

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnServiceBroadcastReceiver)
    }

    override fun onResume() {
        super.onResume()
        val errors = RuleDatabaseUpdateTask.lastErrors.getAndSet(null)
        if (errors != null && errors.isNotEmpty()) {
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
