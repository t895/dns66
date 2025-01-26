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

/**
 * Currently there is no supported way to parse the current Wifi/LTE/etc
 * network. Here we just use the deprecated NetworkInfo API and suppress
 * the warning until a better solution comes along.
 */
@file:Suppress("DEPRECATION")

package dev.clombardo.dnsnet.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.OsConstants
import dev.clombardo.dnsnet.Configuration
import dev.clombardo.dnsnet.DnsNetApplication.Companion.applicationContext
import dev.clombardo.dnsnet.MainActivity
import dev.clombardo.dnsnet.R
import dev.clombardo.dnsnet.config
import dev.clombardo.dnsnet.logd
import dev.clombardo.dnsnet.loge
import dev.clombardo.dnsnet.logi
import dev.clombardo.dnsnet.logw
import uniffi.net.BlockLoggerCallback
import uniffi.net.nativeClose
import uniffi.net.nativePipe
import uniffi.net.runVpnNative
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

class AdVpnThread(
    private val adVpnService: AdVpnService,
    private val notify: (VpnStatus) -> Unit,
    private val blockLoggerCallback: BlockLoggerCallback,
) : Runnable {
    companion object {
        private const val MIN_RETRY_TIME = 5
        private const val MAX_RETRY_TIME = 2 * 60
        private const val RETRY_MULTIPLIER = 2

        /* If we had a successful connection for that long, reset retry timeout */
        private const val RETRY_RESET_SEC: Long = 60

        private const val PREFIX_LENGTH = 24

        @Throws(VpnNetworkException::class)
        private fun getDnsServers(context: Context): List<InetAddress> {
            val known = HashSet<InetAddress>()
            val out = ArrayList<InetAddress>()

            with(context.getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager) {
                // Seriously, Android? Seriously?
                val activeInfo: NetworkInfo =
                    activeNetworkInfo ?: throw VpnNetworkException("No DNS Server")

                for (nw in allNetworks) {
                    val ni: NetworkInfo = getNetworkInfo(nw) ?: continue
                    if (!ni.isConnected) {
                        continue
                    }
                    if (ni.type != activeInfo.type || ni.subtype != activeInfo.subtype) {
                        continue
                    }

                    val servers = getLinkProperties(nw)?.dnsServers ?: continue
                    for (address in servers) {
                        if (known.add(address)) {
                            out.add(address)
                        }
                    }
                }
            }

            return out
        }
    }

    /* Upstream DNS servers, indexed by our IP */
    private val upstreamDnsServers = ArrayList<InetAddress>()

    private var watchdogTarget: InetAddress? = null

    private var thread: Thread? = null
    private var blockFd: Int? = null
    private var interruptFd: Int? = null

    fun startThread() {
        logi("Starting Vpn Thread")
        thread = Thread(this, "AdVpnThread")
        thread?.start()
        logi("Vpn Thread started")
    }

    fun stopThread() {
        logi("Stopping Vpn Thread")
        if (thread != null) {
            thread?.interrupt()
        }

        if (interruptFd != null) {
            interruptFd = nativeClose(interruptFd!!)
        }
        try {
            if (thread != null) {
                thread?.join(2000)
            }
        } catch (e: InterruptedException) {
            logw("stopThread: Interrupted while joining thread", e)
        }
        if (thread != null && thread!!.isAlive) {
            logw("stopThread: Could not kill VPN thread, it is still alive")
        } else {
            thread = null
            logi("Vpn Thread stopped")
        }
    }

    @Synchronized
    override fun run() {
        logi("Starting")

        notify(VpnStatus.STARTING)

        var retryTimeout = MIN_RETRY_TIME
        // Try connecting the vpn continuously
        while (true) {
            val connectTimeMillis: Long = System.currentTimeMillis()

            // If the function returns, that means it was interrupted
            runVpn()

            if (System.currentTimeMillis() - connectTimeMillis >= RETRY_RESET_SEC * 1000) {
                logi("Resetting timeout")
                retryTimeout = MIN_RETRY_TIME
            }

            // ...wait and try again
            logi("Retrying to connect in ${retryTimeout}seconds...")
            try {
                Thread.sleep(retryTimeout.toLong() * 1000)
            } catch (e: InterruptedException) {
                break
            }

            if (retryTimeout < MAX_RETRY_TIME) {
                retryTimeout *= RETRY_MULTIPLIER
            }
        }

        notify(VpnStatus.STOPPED)
        logi("Exiting")
    }

    private fun runVpn() {
        // A pipe we can interrupt the poll() call with by closing the interruptFd end
        val pipes = nativePipe()!!
        interruptFd = pipes[0]
        blockFd = pipes[1]

        // Authenticate and configure the virtual network interface.
        notify(VpnStatus.RUNNING)
        val vpnFd = configure().detachFd()
        runVpnNative(
            adVpnCallback = adVpnService,
            blockLoggerCallback = blockLoggerCallback,
            hostItems = config.hosts.items.map { it.toNative() },
            hostExceptions = config.hosts.exceptions.map { it.toNative() },
            upstreamDnsServers = upstreamDnsServers.map { it.address },
            watchdogTargetAddress = watchdogTarget?.hostAddress ?: "",
            vpnFd = vpnFd,
            blockFd = blockFd!!,
            watchdogEnabled = config.watchDog
        )
        blockFd = nativeClose(blockFd!!)
    }

    @Throws(UnknownHostException::class)
    fun newDNSServer(
        builder: VpnService.Builder,
        format: String?,
        ipv6Template: ByteArray?,
        addr: InetAddress
    ) {
        // Optimally we'd allow either one, but the forwarder checks if upstream size is empty, so
        // we really need to acquire both an ipv6 and an ipv4 subnet.
        if (addr is Inet6Address && ipv6Template == null) {
            logi("newDNSServer: Ignoring DNS server $addr")
        } else if (addr is Inet4Address && format == null) {
            logi("newDNSServer: Ignoring DNS server $addr")
        } else if (addr is Inet4Address) {
            upstreamDnsServers.add(addr)
            val alias = String.format(format!!, upstreamDnsServers.size + 1)
            logi("configure: Adding DNS Server $addr as $alias")
            builder.addDnsServer(alias).addRoute(alias, 32)
            watchdogTarget = InetAddress.getByName(alias)
        } else if (addr is Inet6Address) {
            upstreamDnsServers.add(addr)
            ipv6Template!![ipv6Template.size - 1] = (upstreamDnsServers.size + 1).toByte()
            val i6addr = Inet6Address.getByAddress(ipv6Template)
            logi("configure: Adding DNS Server $addr as $i6addr")
            builder.addDnsServer(i6addr)
            watchdogTarget = i6addr
        }
    }

    fun configurePackages(builder: VpnService.Builder, config: Configuration) {
        val allowOnVpn: MutableSet<String> = HashSet()
        val doNotAllowOnVpn: MutableSet<String> = HashSet()

        config.appList.resolve(adVpnService.packageManager, allowOnVpn, doNotAllowOnVpn)

        if (config.appList.defaultMode == dev.clombardo.dnsnet.AllowListMode.NOT_ON_VPN) {
            for (app in allowOnVpn) {
                try {
                    logd("configure: Allowing $app to use the DNS VPN")
                    builder.addAllowedApplication(app)
                } catch (e: Exception) {
                    logw("configure: Cannot disallow", e)
                }
            }
        } else {
            for (app in doNotAllowOnVpn) {
                try {
                    logd("configure: Disallowing $app from using the DNS VPN")
                    builder.addDisallowedApplication(app)
                } catch (e: Exception) {
                    logw("configure: Cannot disallow", e)
                }
            }
        }
    }

    @Throws(VpnNetworkException::class)
    private fun configure(): ParcelFileDescriptor {
        logi("Configuring $this")

        // Get the current DNS servers before starting the VPN
        val dnsServers = getDnsServers(adVpnService)
        logi("Got DNS servers = $dnsServers")

        // Configure a builder while parsing the parameters.
        val builder = adVpnService.Builder()

        // Determine a prefix we can use. These are all reserved prefixes for example
        // use, so it's possible they might be blocked.
        var format: String? = null
        for (prefix in arrayOf("192.0.2", "198.51.100", "203.0.113")) {
            try {
                builder.addAddress("$prefix.1", PREFIX_LENGTH)
            } catch (e: IllegalArgumentException) {
                logd("configure: Unable to use this prefix: $prefix", e)
                continue
            }

            format = "$prefix.%d"
            break
        }

        // For fancy reasons, this is the 2001:db8::/120 subnet of the /32 subnet reserved for
        // documentation purposes. We should do this differently. Anyone have a free /120 subnet
        // for us to use?
        var ipv6Template: ByteArray? =
            byteArrayOf(32, 1, 13, (184 and 0xFF).toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

        if (hasIpV6Servers(config, dnsServers)) {
            try {
                val addr = Inet6Address.getByAddress(ipv6Template)
                logd("configure: Adding IPv6 address$addr")
                builder.addAddress(addr, 120)
            } catch (e: Exception) {
                logd("configure: Failed to add ipv6 template", e)
                ipv6Template = null
            }
        } else {
            ipv6Template = null
        }

        if (format == null) {
            logw("configure: Could not find a prefix to use, directly using DNS servers")
            builder.addAddress("192.168.50.1", PREFIX_LENGTH)
        }

        // Add configured DNS servers
        upstreamDnsServers.clear()
        if (config.dnsServers.enabled) {
            for (item in config.dnsServers.items) {
                if (item.enabled) {
                    try {
                        newDNSServer(
                            builder,
                            format,
                            ipv6Template,
                            InetAddress.getByName(item.location)
                        )
                    } catch (e: Exception) {
                        loge("configure: Cannot add custom DNS server", e)
                    }
                }
            }
        }

        // Add all known DNS servers
        for (addr in dnsServers) {
            try {
                newDNSServer(builder, format, ipv6Template, addr)
            } catch (e: Exception) {
                loge("configure: Cannot add server:", e)
            }
        }

        builder.setBlocking(true)

        // Allow applications to bypass the VPN
        builder.allowBypass()

        // Explictly allow both families, so we do not block
        // traffic for ones without DNS servers (issue 129).
        builder.allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)

        // Set the VPN to unmetered
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        configurePackages(builder, config)

        // Create a new interface using the builder and save the parameters.
        val pendingIntent = PendingIntent.getActivity(
            adVpnService,
            1,
            Intent(adVpnService, MainActivity::class.java),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pfd = builder
            .setSession(applicationContext.getString(R.string.app_name))
            .setConfigureIntent(pendingIntent)
            .establish()
        logi("Configured")

        return pfd!!
    }

    fun hasIpV6Servers(config: Configuration, dnsServers: List<InetAddress>): Boolean {
        if (!config.ipV6Support) {
            return false
        }

        if (config.dnsServers.enabled) {
            for (item in config.dnsServers.items) {
                if (item.enabled && item.location.contains(":")) {
                    return true
                }
            }
        }

        for (inetAddress in dnsServers) {
            if (inetAddress is Inet6Address) {
                return true
            }
        }

        return false
    }
}
