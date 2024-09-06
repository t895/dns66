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

class DNSFragment : Fragment(), FloatingActionButtonFragment {
    // private var adapter: ItemRecyclerViewAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_dns, container, false)

        val recyclerView = rootView.findViewById<View>(R.id.dns_entries) as RecyclerView

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        recyclerView.setHasFixedSize(true)

        recyclerView.setLayoutManager(LinearLayoutManager(requireContext()))

//        adapter = ItemRecyclerViewAdapter(requireContext(), MainActivity.config.dnsServers.items, 2)
//        recyclerView.adapter = adapter

//        val itemTouchHelper = ItemTouchHelper(ItemTouchHelperCallback(adapter!!))
//        itemTouchHelper.attachToRecyclerView(recyclerView)

//        rootView.findViewById<Switch>(R.id.dns_enabled).apply {
//            isChecked = MainActivity.config.dnsServers.enabled
//            setOnCheckedChangeListener { _, isChecked ->
//                MainActivity.config.dnsServers.enabled = isChecked
//                FileHelper.writeSettings(MainActivity.config)
//            }
//        }
        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "dns")
        return rootView
    }

    override fun setupFloatingActionButton(fab: FloatingActionButton) {
        fab.setOnClickListener {
            val main = requireActivity() as MainActivity
//            main.editItem(2, null, object : ItemChangedListener {
//                override fun onItemChanged(item: Configuration.Item?) {
//                    item ?: return
//                    MainActivity.config.dnsServers.items.add(item)
//                    adapter?.notifyItemInserted((adapter?.itemCount ?: 0) - 1)
//                    FileHelper.writeSettings(requireContext(), MainActivity.config)
//                }
//            })
        }
    }
}
