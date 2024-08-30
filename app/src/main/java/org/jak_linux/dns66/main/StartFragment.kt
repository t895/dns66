/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.FileHelper
import org.jak_linux.dns66.MainActivity
import org.jak_linux.dns66.R
import org.jak_linux.dns66.vpn.AdVpnService
import org.jak_linux.dns66.vpn.AdVpnService.Companion.vpnStatus
import org.jak_linux.dns66.vpn.AdVpnService.Companion.vpnStatusToTextId
import org.jak_linux.dns66.vpn.Command
import java.io.IOException

class StartFragment : Fragment() {
    companion object {
        private const val TAG = "StartFragment"

        const val REQUEST_START_VPN = 1

        fun updateStatus(rootView: View, status: Int) {
            val context = rootView.context
            val stateText = rootView.findViewById<TextView>(R.id.state_textview)
            val stateImage = rootView.findViewById<ImageView>(R.id.state_image)
            val startButton = rootView.findViewById<Button>(R.id.start_button)

            stateImage ?: return
            stateText ?: return

            stateText.text = rootView.context.getString(vpnStatusToTextId(status))
            stateImage.setContentDescription(rootView.context.getString(vpnStatusToTextId(status)))
            stateImage.imageAlpha = 255
            stateImage.setImageTintList(
                ContextCompat.getColorStateList(context, R.color.colorStateImage)
            )
            when (status) {
                AdVpnService.VPN_STATUS_RECONNECTING, AdVpnService.VPN_STATUS_STARTING, AdVpnService.VPN_STATUS_STOPPING -> {
                    stateImage.setImageDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_settings_black_24dp)
                    )
                    startButton.setText(R.string.action_stop)
                }

                AdVpnService.VPN_STATUS_STOPPED -> {
                    stateImage.imageAlpha = 32
                    stateImage.setImageTintList(null)
                    stateImage.setImageDrawable(
                        AppCompatResources.getDrawable(context, R.mipmap.app_icon_large)
                    )
                    startButton.setText(R.string.action_start)
                }

                AdVpnService.VPN_STATUS_RUNNING -> {
                    stateImage.setImageDrawable(
                        AppCompatResources.getDrawable(
                            context,
                            R.drawable.ic_verified_user_black_24dp
                        )
                    )
                    startButton.setText(R.string.action_stop)
                }

                AdVpnService.VPN_STATUS_RECONNECTING_NETWORK_ERROR -> {
                    stateImage.setImageDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_error_black_24dp)
                    )
                    startButton.setText(R.string.action_stop)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView: View = inflater.inflate(R.layout.fragment_start, container, false)
        val switchOnBoot = rootView.findViewById<Switch>(R.id.switch_onboot)

        val view = rootView.findViewById<View>(R.id.state_image) as ImageView

        view.setOnLongClickListener {
            startStopService()
            return@setOnLongClickListener true
        }

        val startButton = rootView.findViewById<View>(R.id.start_button) as Button
        startButton.setOnClickListener {
            startStopService()
        }

        updateStatus(rootView, vpnStatus)

        switchOnBoot.setChecked(MainActivity.config.autoStart)
        switchOnBoot.setOnCheckedChangeListener { _, isChecked ->
            MainActivity.config.autoStart = isChecked
            FileHelper.writeSettings(requireContext(), MainActivity.config)
        }

        val watchDog = rootView.findViewById<Switch>(R.id.watchdog)
        watchDog.setChecked(MainActivity.config.watchDog)
        watchDog.setOnCheckedChangeListener { _, isChecked ->
            MainActivity.config.watchDog = isChecked
            FileHelper.writeSettings(requireContext(), MainActivity.config)

            if (isChecked) {
                AlertDialog.Builder(requireActivity())
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.unstable_feature)
                    .setMessage(R.string.unstable_watchdog_message)
                    .setNegativeButton(R.string.button_cancel) { _, _ ->
                        watchDog.isChecked = false
                        MainActivity.config.watchDog = false
                        FileHelper.writeSettings(requireContext(), MainActivity.config)
                    }
                    .setPositiveButton(R.string.button_continue, null)
                    .show()
            }
        }

        val ipV6Support = rootView.findViewById<Switch>(R.id.ipv6_support)
        ipV6Support.isChecked = MainActivity.config.ipV6Support
        ipV6Support.setOnCheckedChangeListener { _, isChecked ->
            MainActivity.config.ipV6Support = isChecked
            FileHelper.writeSettings(requireContext(), MainActivity.config)
        }

        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "start")

        return rootView
    }

    private fun startStopService(): Boolean {
        if (vpnStatus != AdVpnService.VPN_STATUS_STOPPED) {
            Log.i(TAG, "Attempting to disconnect")
            val intent = Intent(activity, AdVpnService::class.java)
                .putExtra("COMMAND", Command.STOP.ordinal)
            requireActivity().startService(intent)
        } else {
            checkHostsFilesAndStartService()
        }
        return true
    }

    private fun checkHostsFilesAndStartService() {
        if (!areHostsFilesExistant()) {
            AlertDialog.Builder(activity)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.missing_hosts_files_title)
                .setMessage(R.string.missing_hosts_files_message)
                .setNegativeButton(R.string.button_no, null)
                .setPositiveButton(R.string.button_yes) { _, _ ->
                    startService()
                }
                .show()
            return
        }
        startService()
    }

    @Suppress("DEPRECATION")
    private fun startService() {
        Log.i(TAG, "Attempting to connect")
        val intent = VpnService.prepare(context)
        if (intent != null) {
            startActivityForResult(intent, REQUEST_START_VPN)
        } else {
            onActivityResult(REQUEST_START_VPN, Activity.RESULT_OK, null)
        }
    }

    /**
     * Check if all configured hosts files exist.
     *
     * @return true if all host files exist or no host files were configured.
     */
    fun areHostsFilesExistant(): Boolean {
        if (!MainActivity.config.hosts.enabled) {
            return true
        }

        for (item in MainActivity.config.hosts.items) {
            if (item.state != Configuration.Item.STATE_IGNORE) {
                try {
                    val reader = FileHelper.openItemFile(requireContext(), item) ?: continue
                    reader.close()
                } catch (e: IOException) {
                    return false
                }
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(
            TAG,
            "onActivityResult: Received result=$resultCode for request=$requestCode"
        )
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_START_VPN && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(
                requireContext(),
                R.string.could_not_configure_vpn_service,
                Toast.LENGTH_LONG
            ).show()
        }

        if (requestCode == REQUEST_START_VPN && resultCode == Activity.RESULT_OK) {
            Log.d("MainActivity", "onActivityResult: Starting service")
            val intent = Intent(requireContext(), AdVpnService::class.java)
                .putExtra("COMMAND", Command.START.ordinal)
                .putExtra(
                    "NOTIFICATION_INTENT",
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                    )
                )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent)
            } else {
                requireContext().startService(intent)
            }
        }
    }
}
