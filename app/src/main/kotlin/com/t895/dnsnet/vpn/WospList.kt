/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * Derived from DNS66:
 * Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */

package com.t895.dnsnet.vpn

import android.util.Log
import java.util.LinkedList

/**
 * Queue of WaitingOnSocketPacket, bound on time and space.
 */
class WospList {
    companion object {
        private const val TAG = "WospList"
    }

    private val list: LinkedList<WaitingOnSocketPacket> = LinkedList<WaitingOnSocketPacket>()

    fun add(wosp: WaitingOnSocketPacket) {
        if (list.size > AdVpnThread.DNS_MAXIMUM_WAITING) {
            Log.d(TAG, "Dropping socket due to space constraints: ${list.element().socket}")
            list.element().socket.close()
            list.remove()
        }

        while (!list.isEmpty() && list.element().ageSeconds() > AdVpnThread.DNS_TIMEOUT_SEC) {
            Log.d(TAG, "Timeout on socket " + list.element().socket)
            list.element().socket.close()
            list.remove()
        }

        list.add(wosp)
    }

    operator fun iterator(): MutableIterator<WaitingOnSocketPacket> = list.iterator()

    fun size(): Int = list.size
}
