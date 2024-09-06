/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.jak_linux.dns66.BuildConfig
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.FileHelper
import org.jak_linux.dns66.MainActivity
import org.jak_linux.dns66.R
import org.jak_linux.dns66.viewmodel.HomeViewModel
import java.lang.ref.WeakReference
import java.util.Collections

/**
 * Activity showing a list of apps that are allowlisted by the VPN.
 *
 * @author Braden Farmer
 */
class AllowlistFragment : Fragment() {
    companion object {
        private const val TAG = "Allowlist"
    }

    private var appListGenerator: AppListGenerator? = null

    lateinit var appList: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.activity_allowlist, container, false)

        appList = rootView.findViewById<View>(R.id.list) as RecyclerView
        appList.setHasFixedSize(true)

        appList.setLayoutManager(LinearLayoutManager(requireContext()))

        val dividerItemDecoration =
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        appList.addItemDecoration(dividerItemDecoration)

        swipeRefresh = rootView.findViewById<View>(R.id.swiperefresh) as SwipeRefreshLayout
        swipeRefresh.setOnRefreshListener {
            appListGenerator = AppListGenerator(requireContext().packageManager)
            appListGenerator?.execute()
        }
        swipeRefresh.isRefreshing = true

//        val switchShowSystemApps =
//            rootView.findViewById<Switch>(R.id.switch_show_system_apps)
//        switchShowSystemApps.isChecked = MainActivity.config.allowlist.showSystemApps
//        switchShowSystemApps.setOnCheckedChangeListener { _, isChecked ->
//            MainActivity.config.allowlist.showSystemApps = isChecked
//            FileHelper.writeSettings(MainActivity.config)
//            appListGenerator = AppListGenerator(requireContext().packageManager)
//            appListGenerator?.execute()
//        }

//        val allowlistDefaultText =
//            rootView.findViewById<View>(R.id.allowlist_default_text) as TextView
//        allowlistDefaultText.text =
//            resources.getStringArray(R.array.allowlist_defaults)[MainActivity.config.allowlist.defaultMode]
//        val onDefaultChangeClicked = object : View.OnClickListener {
//            override fun onClick(v: View?) {
//                val menu = PopupMenu(context, rootView.findViewById(R.id.change_default))
//                menu.inflate(R.menu.allowlist_popup)
//                menu.setOnMenuItemClickListener {
//                    when (it.itemId) {
//                        R.id.allowlist_default_on_vpn -> {
//                            Log.d(TAG, "onMenuItemClick: OnVpn")
//                            MainActivity.config.allowlist.defaultMode =
//                                Configuration.Allowlist.DEFAULT_MODE_ON_VPN
//                        }
//
//                        R.id.allowlist_default_not_on_vpn -> {
//                            Log.d(TAG, "onMenuItemClick: NotOnVpn")
//                            MainActivity.config.allowlist.defaultMode =
//                                Configuration.Allowlist.DEFAULT_MODE_NOT_ON_VPN
//                        }
//
//                        R.id.allowlist_default_intelligent -> {
//                            Log.d(TAG, "onMenuItemClick: Intelligent")
//                            MainActivity.config.allowlist.defaultMode =
//                                Configuration.Allowlist.DEFAULT_MODE_INTELLIGENT
//                        }
//                    }
//                    allowlistDefaultText.text =
//                        resources.getStringArray(R.array.allowlist_defaults)[MainActivity.config.allowlist.defaultMode]
//                    appListGenerator = AppListGenerator(requireContext().packageManager)
//                    appListGenerator?.execute()
//                    FileHelper.writeSettings(MainActivity.config)
//                    return@setOnMenuItemClickListener true
//                }
//
//                menu.show()
//            }
//        }

//        rootView.findViewById<View>(R.id.change_default).setOnClickListener(onDefaultChangeClicked)
//        allowlistDefaultText.setOnClickListener(onDefaultChangeClicked)

        appListGenerator = AppListGenerator(requireContext().packageManager)
        appListGenerator?.execute()

        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "allowlist")

        return rootView
    }

    inner class AppListAdapter(val pm: PackageManager, val list: ArrayList<AppItem>) :
        RecyclerView.Adapter<AppListAdapter.ViewHolder>() {
        private val onVpn: MutableSet<String> = HashSet()
        private val notOnVpn = HashSet<String>()

//        init {
//            MainActivity.config.allowlist.resolve(pm, onVpn, notOnVpn)
//        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.allowlist_row, parent, false)
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = list[position]

            if (holder.task != null) {
                holder.task!!.cancel(true)
            }

            holder.task = null
            val icon = entry.loadIcon(pm)
            if (icon != null) {
                holder.icon.setImageDrawable(icon)
                holder.icon.setVisibility(View.VISIBLE)
            } else {
                holder.icon.setVisibility(View.INVISIBLE)

                holder.task = object : AsyncTask<AppItem, Void, Drawable>() {
                    override fun doInBackground(vararg params: AppItem?): Drawable? =
                        params[0]?.loadIcon(pm)

                    override fun onPostExecute(result: Drawable?) {
                        if (!isCancelled) {
                            holder.icon.setImageDrawable(result)
                            holder.icon.setVisibility(View.VISIBLE)
                        }
                        super.onPostExecute(result)
                    }
                }

                holder.task?.execute(entry)
            }

            holder.apply {
                name.text = entry.label
                details.text = entry.packageName
                allowlistSwitch.setOnCheckedChangeListener(null);
                allowlistSwitch.setChecked(notOnVpn.contains(entry.packageName))
            }

            holder.allowlistSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
                /* No change, do nothing */
//                if (isChecked && MainActivity.config.allowlist.items.contains(entry.packageName)) {
//                    return@setOnCheckedChangeListener
//                }
//
//                if (!isChecked &&
//                    MainActivity.config.allowlist.itemsOnVpn.contains(entry.packageName)
//                ) {
//                    return@setOnCheckedChangeListener
//                }
//
//                if (isChecked) {
//                    MainActivity.config.allowlist.items.add(entry.packageName)
//                    MainActivity.config.allowlist.itemsOnVpn.remove(entry.packageName)
//                    notOnVpn.add(entry.packageName)
//                } else {
//                    MainActivity.config.allowlist.items.remove(entry.packageName)
//                    MainActivity.config.allowlist.itemsOnVpn.add(entry.packageName)
//                    notOnVpn.remove(entry.packageName)
//                }
//                FileHelper.writeSettings(MainActivity.config)
            }

            holder.itemView.setOnClickListener {
                holder.allowlistSwitch.setChecked(!holder.allowlistSwitch.isChecked)
            }
        }

        override fun getItemCount(): Int = list.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView
            val name: TextView
            val details: TextView
            val allowlistSwitch: Switch

            var task: AsyncTask<AppItem, Void, Drawable>? = null

            init {
                with(itemView) {
                    icon = findViewById(R.id.app_icon)
                    name = findViewById(R.id.name)
                    details = findViewById(R.id.details)
                    allowlistSwitch = findViewById(R.id.checkbox)
                }
            }
        }
    }

    inner class AppListGenerator(private val pm: PackageManager) :
        AsyncTask<Void, Void, AppListAdapter>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Void?): AppListAdapter {
            val apps = pm.getInstalledApplications(0)

            Collections.sort(apps, ApplicationInfo.DisplayNameComparator(pm))

            val entries = ArrayList<AppItem>()
            for (app in apps) {
//                if (app.packageName != BuildConfig.APPLICATION_ID &&
//                    (MainActivity.config.allowlist.showSystemApps ||
//                        (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0)
//                ) {
//                    entries.add(AppItem(app, app.packageName, app.loadLabel(pm).toString()))
//                }
            }

            return AppListAdapter(pm, entries)
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: AppListAdapter?) {
            appList.adapter = result
            swipeRefresh.isRefreshing = false
        }
    }
}
