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
import org.jak_linux.dns66.MainActivity
import org.jak_linux.dns66.R
import org.jak_linux.dns66.vpn.AdVpnService
import org.jak_linux.dns66.vpn.AdVpnService.Companion.status
import org.jak_linux.dns66.vpn.Command
import org.jak_linux.dns66.vpn.VpnStatus

class StartFragment : Fragment() {
    companion object {
        private const val TAG = "StartFragment"

        fun updateStatus(rootView: View, status: VpnStatus) {
            val context = rootView.context
            val stateText = rootView.findViewById<TextView>(R.id.state_textview)
            val stateImage = rootView.findViewById<ImageView>(R.id.state_image)
            val startButton = rootView.findViewById<Button>(R.id.start_button)

            stateImage ?: return
            stateText ?: return

            stateImage.imageAlpha = 255
            stateImage.setImageTintList(
                ContextCompat.getColorStateList(context, R.color.colorStateImage)
            )
            when (status) {
                VpnStatus.WAITING_FOR_NETWORK,
                VpnStatus.RECONNECTING,
                VpnStatus.STARTING,
                VpnStatus.STOPPING -> {
                    stateImage.setImageDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_settings_black_24dp)
                    )
                    startButton.setText(R.string.action_stop)
                }

                VpnStatus.STOPPED -> {
                    stateImage.imageAlpha = 32
                    stateImage.setImageTintList(null)
                    stateImage.setImageDrawable(
                        AppCompatResources.getDrawable(context, R.mipmap.app_icon_large)
                    )
                    startButton.setText(R.string.action_start)
                }

                VpnStatus.RUNNING -> {
                    stateImage.setImageDrawable(
                        AppCompatResources.getDrawable(
                            context,
                            R.drawable.ic_verified_user_black_24dp
                        )
                    )
                    startButton.setText(R.string.action_stop)
                }

                VpnStatus.RECONNECTING_NETWORK_ERROR -> {
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

        updateStatus(rootView, status)

//        switchOnBoot.setChecked(MainActivity.config.autoStart)
//        switchOnBoot.setOnCheckedChangeListener { _, isChecked ->
//            MainActivity.config.autoStart = isChecked
//            FileHelper.writeSettings(MainActivity.config)
//        }

        val watchDog = rootView.findViewById<Switch>(R.id.watchdog)
//        watchDog.setChecked(MainActivity.config.watchDog)
//        watchDog.setOnCheckedChangeListener { _, isChecked ->
//            MainActivity.config.watchDog = isChecked
//            FileHelper.writeSettings(MainActivity.config)
//
//            if (isChecked) {
//                AlertDialog.Builder(requireActivity())
//                    .setIcon(R.drawable.ic_warning)
//                    .setTitle(R.string.unstable_feature)
//                    .setMessage(R.string.unstable_watchdog_message)
//                    .setNegativeButton(R.string.button_cancel) { _, _ ->
//                        watchDog.isChecked = false
//                        MainActivity.config.watchDog = false
//                        FileHelper.writeSettings(MainActivity.config)
//                    }
//                    .setPositiveButton(R.string.button_continue, null)
//                    .show()
//            }
//        }

        val ipV6Support = rootView.findViewById<Switch>(R.id.ipv6_support)
//        ipV6Support.isChecked = MainActivity.config.ipV6Support
//        ipV6Support.setOnCheckedChangeListener { _, isChecked ->
//            MainActivity.config.ipV6Support = isChecked
//            FileHelper.writeSettings(MainActivity.config)
//        }

        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "start")

        return rootView
    }
}
