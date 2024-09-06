/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.FileHelper
import org.jak_linux.dns66.MainActivity
import org.jak_linux.dns66.R
import org.jak_linux.dns66.db.RuleDatabaseUpdateJobService

class HostsFragment : Fragment(), FloatingActionButtonFragment {
//    private var adapter: ItemRecyclerViewAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_hosts, container, false)

        val recyclerView = rootView.findViewById<View>(R.id.host_entries) as RecyclerView

        recyclerView.setHasFixedSize(true)

        recyclerView.setLayoutManager(LinearLayoutManager(requireContext()))

//        adapter = ItemRecyclerViewAdapter(requireContext(), MainActivity.config.hosts.items, 3)
//        recyclerView.adapter = adapter

//        val itemTouchHelper = ItemTouchHelper(ItemTouchHelperCallback(adapter!!))
//        itemTouchHelper.attachToRecyclerView(recyclerView)

//        val hostEnabled = rootView.findViewById<View>(R.id.host_enabled) as Switch
//        hostEnabled.isChecked = MainActivity.config.hosts.enabled
//        hostEnabled.setOnCheckedChangeListener { _, isChecked ->
//            MainActivity.config.hosts.enabled = isChecked
//            FileHelper.writeSettings(MainActivity.config)
//        }
//
//        val automaticRefresh = rootView.findViewById<View>(R.id.automatic_refresh) as Switch
//        automaticRefresh.isChecked = MainActivity.config.hosts.automaticRefresh
//        automaticRefresh.setOnCheckedChangeListener { _, isChecked ->
//            MainActivity.config.hosts.automaticRefresh = isChecked
//            FileHelper.writeSettings(MainActivity.config)
//            RuleDatabaseUpdateJobService.scheduleOrCancel(requireContext(), MainActivity.config)
//        }

        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "hosts")

        return rootView
    }

    override fun setupFloatingActionButton(fab: FloatingActionButton) {
        fab.setOnClickListener {
            val main = requireActivity() as MainActivity
//            main.editItem(3, null, object : ItemChangedListener {
//                override fun onItemChanged(item: Configuration.Item?) {
//                    item ?: return
//                    MainActivity.config.hosts.items.add(item)
//                    adapter?.notifyItemInserted((adapter?.itemCount ?: 0) - 1)
//                    FileHelper.writeSettings(requireContext(), MainActivity.config)
//                }
//            })
        }
    }
}
