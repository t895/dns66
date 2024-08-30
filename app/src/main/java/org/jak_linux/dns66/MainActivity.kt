package org.jak_linux.dns66

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import org.jak_linux.dns66.NotificationChannels.onCreate
import org.jak_linux.dns66.db.RuleDatabaseUpdateJobService
import org.jak_linux.dns66.db.RuleDatabaseUpdateTask
import org.jak_linux.dns66.main.FloatingActionButtonFragment
import org.jak_linux.dns66.main.MainFragmentPagerAdapter
import org.jak_linux.dns66.main.StartFragment
import org.jak_linux.dns66.vpn.AdVpnService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"

        private const val REQUEST_FILE_OPEN = 1
        private const val REQUEST_FILE_STORE = 2
        private const val REQUEST_ITEM_EDIT = 3

        private var _config: Configuration? = null
        val config get() = _config!!
    }

    private lateinit var viewPager: ViewPager
    private val vpnServiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val strId = intent.getIntExtra(
                AdVpnService.VPN_UPDATE_STATUS_EXTRA,
                R.string.notification_stopped
            )
            updateStatus(strId)
        }
    }

    private var itemChangedListener: ItemChangedListener? = null

    private lateinit var fragmentPagerAdapter: MainFragmentPagerAdapter
    private lateinit var floatingActionButton: FloatingActionButton
    private lateinit var pageChangeListener: ViewPager.SimpleOnPageChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onCreate(this)

        if (savedInstanceState == null || _config == null) {
            _config = FileHelper.loadCurrentSettings(this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else if (config.nightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        viewPager = findViewById(R.id.view_pager)

        fragmentPagerAdapter = MainFragmentPagerAdapter(this, supportFragmentManager)
        viewPager.adapter = fragmentPagerAdapter

        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        tabLayout.setupWithViewPager(viewPager)

        // Add a page change listener that sets the floating action button per tab.
        floatingActionButton = findViewById(R.id.floating_action_button)
        pageChangeListener = object : SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                val fragment = supportFragmentManager.findFragmentByTag(
                    "android:switcher:${viewPager.id}:" +
                        fragmentPagerAdapter.getItemId(position)
                )
                if (fragment is FloatingActionButtonFragment) {
                    (fragment as FloatingActionButtonFragment).setupFloatingActionButton(
                        floatingActionButton
                    )
                    floatingActionButton.show()
                } else {
                    floatingActionButton.hide()
                }
            }
        }
        viewPager.addOnPageChangeListener(pageChangeListener)

        RuleDatabaseUpdateJobService.scheduleOrCancel(this, config)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        menu.findItem(R.id.setting_show_notification).setChecked(config.showNotification)
        menu.findItem(R.id.setting_night_mode).setChecked(config.nightMode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            menu.findItem(R.id.setting_night_mode).setVisible(false)
        }

        // On Android O, require users to configure notifications via notification channels.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.findItem(R.id.setting_show_notification).setVisible(false)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_refresh -> refresh()
            R.id.action_load_defaults -> {
                _config = FileHelper.loadDefaultSettings(this)
                FileHelper.writeSettings(this, config)
                recreate()
            }

            R.id.action_import -> {
                val intent = Intent()
                    .setType("*/*")
                    .setAction(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)

                startActivityForResult(intent, REQUEST_FILE_OPEN)
            }

            R.id.action_export -> {
                val exportIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_TITLE, "dns66.json")

                startActivityForResult(exportIntent, REQUEST_FILE_STORE)
            }

            R.id.setting_night_mode -> {
                item.isChecked = !item.isChecked
                config.nightMode = item.isChecked
                FileHelper.writeSettings(this@MainActivity, config)
                recreate()
            }

            R.id.setting_show_notification -> {
                // If we are enabling notifications, we do not need to show a dialog.
                if (!item.isChecked) {
                    item.isChecked = !item.isChecked
                    config.showNotification = item.isChecked
                    FileHelper.writeSettings(this@MainActivity, config)
                    return super.onOptionsItemSelected(item)
                }

                AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.disable_notification_title)
                    .setMessage(R.string.disable_notification_message)
                    .setPositiveButton(R.string.disable_notification_ack) { _, _ ->
                        item.isChecked = !item.isChecked
                        config.showNotification = item.isChecked
                        FileHelper.writeSettings(this@MainActivity, config)
                    }
                    .setNegativeButton(R.string.disable_notification_nak) { _, _ -> }
                    .show()
            }

            R.id.action_about -> startActivity(Intent(this, InfoActivity::class.java))

            R.id.action_logcat -> sendLogcat()
        }

        return super.onOptionsItemSelected(item)
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

            val eMailIntent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_EMAIL, arrayOf("jak@jak-linux.org"))
                .putExtra(Intent.EXTRA_SUBJECT, "DNS66 Logcat")
                .putExtra(Intent.EXTRA_TEXT, logcat.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(eMailIntent)
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
            errors.add(0, getString(R.string.update_incomplete_description))
            AlertDialog.Builder(this)
                .setAdapter(newAdapter(errors), null)
                .setTitle(R.string.update_incomplete)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        super.onNewIntent(intent)
    }

    private fun newAdapter(errors: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, errors) {
            @Suppress("deprecation")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                view.findViewById<TextView>(android.R.id.text1).text =
                    Html.fromHtml(errors[position])
                return view
            }
        }

    private fun refresh() {
        val task = RuleDatabaseUpdateTask(config, true)
        task.execute()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult: Received result=$resultCode for request=$requestCode")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_FILE_OPEN && resultCode == RESULT_OK) {
            val selectedfile = data!!.data //The uri with the location of the file
            try {
                _config = Configuration.read(
                    InputStreamReader(contentResolver.openInputStream(selectedfile!!))
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            recreate()
            FileHelper.writeSettings(this, config)
        }

        if (requestCode == REQUEST_FILE_STORE && resultCode == RESULT_OK) {
            // The uri with the location of the file
            val selectedfile = data!!.data
            try {
                OutputStreamWriter(contentResolver.openOutputStream(selectedfile!!)).use {
                    config.write(it)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot write file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            recreate()
        }

        if (requestCode == REQUEST_ITEM_EDIT && resultCode == RESULT_OK) {
            val item = Configuration.Item()
            Log.d("FOOOO", "onActivityResult: item title = " + data!!.getStringExtra("ITEM_TITLE"))
            if (data.hasExtra("DELETE")) {
                itemChangedListener!!.onItemChanged(null)
                return
            }

            item.apply {
                title = data.getStringExtra("ITEM_TITLE") ?: ""
                location = data.getStringExtra("ITEM_LOCATION") ?: ""
                state = data.getIntExtra("ITEM_STATE", 0)
            }
            itemChangedListener!!.onItemChanged(item)
        }
    }

    private fun updateStatus(status: Int) {
        if (viewPager.getChildAt(0) == null) {
            return
        }

        StartFragment.updateStatus(viewPager.getChildAt(0).getRootView(), status)
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
            errors.add(0, getString(R.string.update_incomplete_description))
            AlertDialog.Builder(this)
                .setAdapter(newAdapter(errors), null)
                .setTitle(R.string.update_incomplete)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        pageChangeListener.onPageSelected(viewPager.currentItem)
        updateStatus(AdVpnService.vpnStatus)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                vpnServiceBroadcastReceiver,
                IntentFilter(AdVpnService.VPN_UPDATE_STATUS_INTENT)
            )
    }

    /**
     * Start the item editor activity
     *
     * @param item     an item to edit, may be null
     * @param listener A listener that will be called once the editor returns
     */
    fun editItem(stateChoices: Int, item: Configuration.Item?, listener: ItemChangedListener?) {
        val editIntent = Intent(this, ItemActivity::class.java)
        itemChangedListener = listener
        if (item != null) {
            editIntent.putExtra("ITEM_TITLE", item.title)
                .putExtra("ITEM_LOCATION", item.location)
                .putExtra("ITEM_STATE", item.state)
        }
        editIntent.putExtra("STATE_CHOICES", stateChoices)
        startActivityForResult(editIntent, REQUEST_ITEM_EDIT)
    }
}
