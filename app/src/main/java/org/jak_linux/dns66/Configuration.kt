/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jak_linux.dns66.HostState.Companion.toHostState
import java.io.IOException
import java.io.Reader
import java.io.Writer

@Serializable
data class Configuration(
    var version: Int = 1,
    var minorVersion: Int = 0,
    var autoStart: Boolean = false,
    var hosts: Hosts = Hosts(),
    var dnsServers: DnsServers = DnsServers(),
    var allowlist: Allowlist = Allowlist(),
    var showNotification: Boolean = true,
    var nightMode: Boolean = false,
    var watchDog: Boolean = false,
    var ipV6Support: Boolean = true,
) {
    companion object {
        private const val TAG = "Configuration"

        private const val VERSION = 1

        /* Default tweak level */
        private const val MINOR_VERSION = 0

        @Throws(IOException::class)
        fun read(reader: Reader): Configuration {
            val config = reader.use {
                val data = it.readText()
                try {
                    Json.decodeFromString<Configuration>(data)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode config! - ${e.localizedMessage}")
                    Configuration()
                }
            }
            if (config.version > VERSION) {
                throw IOException("Unhandled file format version")
            }

            for (i in config.minorVersion + 1..MINOR_VERSION) {
                config.runUpdate(i)
            }

            config.updateURL(
                "http://someonewhocares.org/hosts/hosts",
                "https://someonewhocares.org/hosts/hosts",
                HostState.IGNORE
            )

            return config
        }
    }

    fun runUpdate(level: Int) {
        when (level) {
            1 -> {
                /* Switch someonewhocares to https */
                updateURL(
                    "http://someonewhocares.org/hosts/hosts",
                    "https://someonewhocares.org/hosts/hosts",
                    HostState.IGNORE
                )

                /* Switch to StevenBlack's host file */
                addURL(
                    0, "StevenBlack's hosts file (includes all others)",
                    "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
                    HostState.DENY
                )
                updateURL("https://someonewhocares.org/hosts/hosts", null, HostState.IGNORE)
                updateURL("https://adaway.org/hosts.txt", null, HostState.IGNORE)
                updateURL(
                    "https://www.malwaredomainlist.com/hostslist/hosts.txt",
                    null,
                    HostState.IGNORE
                )
                updateURL(
                    "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=1&mimetype=plaintext",
                    null,
                    HostState.IGNORE
                )

                /* Remove broken host */
                removeURL("http://winhelp2002.mvps.org/hosts.txt")

                /* Update digitalcourage dns and add cloudflare */
                updateDNS("85.214.20.141", "46.182.19.48")
                addDNS("CloudFlare DNS (1)", "1.1.1.1", false)
                addDNS("CloudFlare DNS (2)", "1.0.0.1", false)
            }

            2 -> removeURL("https://hosts-file.net/ad_servers.txt")
            3 -> disableURL("https://blokada.org/blocklists/ddgtrackerradar/standard/hosts.txt")
        }
        minorVersion = level
    }

    fun updateURL(oldURL: String, newURL: String?, newState: HostState) =
        hosts.items.forEach {
            if (it.location == oldURL) {
                if (newURL != null) {
                    it.location = newURL
                }

                it.state = newState
            }
        }

    fun updateDNS(oldIP: String, newIP: String) =
        dnsServers.items.forEach {
            if (it.location == oldIP) {
                it.location = newIP
            }
        }

    fun addDNS(title: String, location: String, isEnabled: Boolean) =
        dnsServers.items.add(
            DnsServer(
                title = title,
                location = location,
                enabled = isEnabled,
            )
        )

    fun addURL(index: Int, title: String, location: String, state: HostState) =
        hosts.items.add(
            index = index,
            element = Host(
                title = title,
                location = location,
                state = state,
            ),
        )

    fun removeURL(oldURL: String) =
        hosts.items.removeAll { it.location == oldURL }

    fun disableURL(oldURL: String) {
        Log.d(TAG, String.format("disableURL: Disabling %s", oldURL))
        hosts.items.forEach {
            if (it.location == oldURL) {
                it.state = HostState.IGNORE
            }
        }
    }

    @Throws(IOException::class)
    fun write(writer: Writer?) = writer?.write(Json.encodeToString(this))
}

@Serializable
data class Allowlist(
    var showSystemApps: Boolean = false,
    var defaultMode: AllowListMode = AllowListMode.ON_VPN,
    var itemsNotOnVpn: MutableList<String> = mutableListOf(),
    var itemsOnVpn: MutableList<String> = mutableListOf(),
) {
    /**
     * Categorizes all packages in the system into "on vpn" or
     * "not on vpn".
     *
     * @param pm       A {@link PackageManager}
     * @param onVpn    names of packages to use the VPN
     * @param notOnVpn Names of packages not to use the VPN
     */
    fun resolve(pm: PackageManager, onVpn: MutableSet<String>, notOnVpn: MutableSet<String>) {
        val webBrowserPackageNames: MutableSet<String> = HashSet()
        val resolveInfoList = pm.queryIntentActivities(newBrowserIntent(), 0)
        for (resolveInfo in resolveInfoList) {
            webBrowserPackageNames.add(resolveInfo.activityInfo.packageName)
        }

        webBrowserPackageNames.apply {
            add("com.google.android.webview")
            add("com.android.htmlviewer")
            add("com.google.android.backuptransport")
            add("com.google.android.gms")
            add("com.google.android.gsf")
        }

        for (applicationInfo in pm.getInstalledApplications(0)) {
            // We need to always keep ourselves using the VPN, otherwise our
            // watchdog does not work.
            if (applicationInfo.packageName == BuildConfig.APPLICATION_ID) {
                onVpn.add(applicationInfo.packageName)
            } else if (itemsOnVpn.contains(applicationInfo.packageName)) {
                onVpn.add(applicationInfo.packageName)
            } else if (itemsNotOnVpn.contains(applicationInfo.packageName)) {
                notOnVpn.add(applicationInfo.packageName)
            } else if (defaultMode == AllowListMode.ON_VPN) {
                onVpn.add(applicationInfo.packageName)
            } else if (defaultMode == AllowListMode.NOT_ON_VPN) {
                notOnVpn.add(applicationInfo.packageName)
            } else if (defaultMode == AllowListMode.AUTO) {
                if (webBrowserPackageNames.contains(applicationInfo.packageName)) {
                    onVpn.add(applicationInfo.packageName)
                } else if (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                    notOnVpn.add(applicationInfo.packageName)
                } else {
                    onVpn.add(applicationInfo.packageName)
                }
            }
        }
    }

    /**
     * Returns an intent for opening a website, used for finding
     * web browsers. Extracted method for mocking.
     */
    fun newBrowserIntent(): Intent =
        Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://isabrowser.dns66.jak-linux.org/"))
}

enum class AllowListMode {
    /**
     * System apps (excluding browsers) do not use the VPN.
     */
    AUTO,

    /**
     * All apps use the VPN.
     */
    ON_VPN,

    /**
     * No apps use the VPN.
     */
    NOT_ON_VPN;

    companion object {
        fun Int.toAllowListMode(): AllowListMode =
            AllowListMode.entries.firstOrNull { it.ordinal == this } ?: ON_VPN
    }
}

@Parcelize
@Serializable
data class DnsServer(
    var title: String = "",
    var location: String = "",
    var enabled: Boolean = false,
) : Parcelable

@Parcelize
@Serializable
data class Host(
    var title: String = "",
    var location: String = "",
    var state: HostState = HostState.IGNORE,
) : Parcelable {
    fun isDownloadable(): Boolean =
        location.startsWith("https://") || location.startsWith("http://")

    companion object : Parceler<Host> {
        override fun Host.write(parcel: Parcel, flags: Int) {
            parcel.apply {
                writeString(title)
                writeString(location)
                writeInt(state.ordinal)
            }
        }

        override fun create(parcel: Parcel): Host =
            Host(
                parcel.readString() ?: "",
                parcel.readString() ?: "",
                parcel.readInt().toHostState(),
            )
    }
}

@Serializable
data class Hosts(
    var enabled: Boolean = false,
    var automaticRefresh: Boolean = false,
    var items: MutableList<Host> = mutableListOf(),
)

@Serializable
data class DnsServers(
    var enabled: Boolean = false,
    var items: MutableList<DnsServer> = mutableListOf(),
)

// DO NOT change the order of these states. They correspond to UI functionality.
enum class HostState {
    IGNORE, DENY, ALLOW;

    companion object {
        fun Int.toHostState(): HostState = entries.firstOrNull { it.ordinal == this } ?: IGNORE
    }
}
