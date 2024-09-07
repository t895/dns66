/* Copyright (C) 2016 - 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db

import android.util.Log
import org.jak_linux.dns66.FileHelper
import org.jak_linux.dns66.Host
import org.jak_linux.dns66.HostState
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.Reader
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Represents hosts that are blocked.
 * <p>
 * This is a very basic set of hosts. But it supports lock-free
 * readers with writers active at the same time, only the writers
 * having to take a lock.
 */
class RuleDatabase private constructor() {
    companion object {
        private const val TAG = "RuleDatabase"
        val instance = RuleDatabase()

        fun parseLine(line: String): String? {
            var endOfLine = line.indexOf('#')

            if (endOfLine == -1) {
                endOfLine = line.length
            }

            // Trim spaces
            while (endOfLine > 0 && Character.isWhitespace(line[endOfLine - 1])) {
                endOfLine--
            }

            // The line is empty
            if (endOfLine <= 0) {
                return null
            }

            // Find beginning of host field
            var startOfHost = 0

            if (line.regionMatches(0, "127.0.0.1", 0, 9) &&
                (endOfLine <= 9 || Character.isWhitespace(line[9]))
            ) {
                startOfHost += 10
            } else if (line.regionMatches(0, "::1", 0, 3) &&
                (endOfLine <= 3 || Character.isWhitespace(line[3]))
            ) {
                startOfHost += 4
            } else if (line.regionMatches(0, "0.0.0.0", 0, 7) &&
                (endOfLine <= 7 || Character.isWhitespace(line[7]))
            ) {
                startOfHost += 8
            }

            // Trim of space at the beginning of the host
            while (startOfHost < endOfLine && Character.isWhitespace(line[startOfHost])) {
                startOfHost++
            }

            // Reject lines containing a space
            for (i in startOfHost until endOfLine) {
                if (Character.isWhitespace(line[i])) {
                    return null
                }
            }

            if (startOfHost >= endOfLine) {
                return null
            }

            return line.substring(startOfHost, endOfLine).lowercase(Locale.ENGLISH)
        }
    }

    private val blockedHosts = AtomicReference(HashSet<String>())
    private var nextBlockedHosts: HashSet<String>? = null

    /**
     * Returns the instance of the rule database.
     */
    fun getInstance(): RuleDatabase = instance

    /**
     * Checks if a host is blocked.
     *
     * @param host A hostname
     * @return true if the host is blocked, false otherwise.
     */
    fun isBlocked(host: String): Boolean = blockedHosts.get().contains(host)

    /**
     * Check if any hosts are blocked
     *
     * @return true if any hosts are blocked, false otherwise.
     */
    fun isEmpty(): Boolean = blockedHosts.get().isEmpty()

    /**
     * Load the hosts according to the configuration
     *
     * @param context A context used for opening files.
     * @throws InterruptedException Thrown if the thread was interrupted, so we don't waste time
     *                              reading more host files than needed.
     */
    @Synchronized
    @Throws(InterruptedException::class)
    fun initialize() {
        val config = FileHelper.loadCurrentSettings()

        nextBlockedHosts = HashSet(blockedHosts.get().size)

        Log.i(TAG, "Loading block list")

        if (!config.hosts.enabled) {
            Log.d(TAG, "loadBlockedHosts: Not loading, disabled.")
        } else {
            for (item in config.hosts.items) {
                if (Thread.interrupted()) {
                    throw InterruptedException("Interrupted")
                }
                loadItem(item)
            }
        }

        blockedHosts.set(nextBlockedHosts)
        Runtime.getRuntime().gc()
    }

    /**
     * Loads an item. An item can be backed by a file or contain a value in the location field.
     *
     * @param context Context to open files
     * @param item    The item to load.
     * @throws InterruptedException If the thread was interrupted.
     */
    @Throws(InterruptedException::class)
    private fun loadItem(item: Host) {
        if (item.state == HostState.IGNORE) {
            return
        }

        val reader = try {
            FileHelper.openItemFile(item)
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "loadItem: File not found: ${item.location}")
            return
        }

        if (reader == null) {
            addHost(item, item.location)
        } else {
            loadReader(item, reader)
        }
    }

    /**
     * Add a single host for an item.
     *
     * @param item The item the host belongs to
     * @param host The host
     */
    private fun addHost(item: Host, host: String) {
        // Single address to block
        if (item.state == HostState.ALLOW) {
            nextBlockedHosts?.remove(host) ?: Log.d(
                TAG,
                "addHost: nextBlockedHosts was null when attempting to remove host!"
            )
        } else if (item.state == HostState.DENY) {
            nextBlockedHosts?.add(host) ?: Log.d(
                TAG,
                "addHost: nextBlockedHosts was null when attempting to add host!"
            )
        }
    }

    /**
     * Load a single file
     *
     * @param item   The configuration item referencing the file
     * @param reader A reader to read lines from
     * @throws InterruptedException If thread was interrupted
     */
    @Throws(InterruptedException::class)
    fun loadReader(item: Host, reader: Reader): Boolean {
        var count = 0
        try {
            Log.d(TAG, "loadBlockedHosts: Reading: ${item.location}")
            BufferedReader(reader).use {
                var line = it.readLine()
                while (line != null) {
                    if (Thread.interrupted()) {
                        throw InterruptedException("Interrupted")
                    }

                    val host = parseLine(line)
                    if (host != null) {
                        count++
                        addHost(item, host)
                    }

                    line = it.readLine()
                }
            }

            Log.d(TAG, "loadBlockedHosts: Loaded $count hosts from ${item.location}")
            return true
        } catch (e: IOException) {
            Log.e(
                TAG,
                "loadBlockedHosts: Error while reading ${item.location} after $count items",
                e
            )
            return false
        } finally {
            FileHelper.closeOrWarn(reader, TAG, "loadBlockedHosts: Error closing ${item.location}")
        }
    }
}
