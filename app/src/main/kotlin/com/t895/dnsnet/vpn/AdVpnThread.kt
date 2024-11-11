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

package com.t895.dnsnet.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import com.t895.dnsnet.Configuration
import com.t895.dnsnet.FileHelper
import com.t895.dnsnet.MainActivity
import org.pcap4j.packet.IpPacket
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.util.Arrays
import java.util.LinkedList
import java.util.Queue

class AdVpnThread(
    private val vpnService: VpnService,
    private val notify: (VpnStatus) -> Unit,
    log: ((name: String, allowed: Boolean) -> Unit)?,
) : Runnable, EventLoop {
    companion object {
        private const val TAG = "AdVpnThread"
        private const val MIN_RETRY_TIME = 5
        private const val MAX_RETRY_TIME = 2 * 60

        /* If we had a successful connection for that long, reset retry timeout */
        private const val RETRY_RESET_SEC: Long = 60

        /* Maximum number of responses we want to wait for */
        const val DNS_MAXIMUM_WAITING = 1024
        const val DNS_TIMEOUT_SEC: Long = 10

        @Throws(VpnNetworkException::class)
        private fun getDnsServers(context: Context): List<InetAddress> {
            val known = HashSet<InetAddress>()
            val out = ArrayList<InetAddress>()

            with(context.getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager) {
                // Seriously, Android? Seriously?
                val activeInfo: NetworkInfo =
                    activeNetworkInfo ?: throw VpnNetworkException("No DNS Server")

                for (nw in allNetworks) {
                    val ni: NetworkInfo? = getNetworkInfo(nw)
                    if (ni == null || !ni.isConnected || ni.type != activeInfo.type ||
                        ni.subtype != activeInfo.subtype
                    ) {
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
    val upstreamDnsServers = ArrayList<InetAddress>()

    /* Data to be written to the device */
    private val deviceWrites: Queue<ByteArray> = LinkedList()

    // HashMap that keeps an upper limit of packets
    private val dnsIn: WospList = WospList()

    // The object where we actually handle packets.
    private val dnsPacketProxy = DnsPacketProxy(this, log)

    // Watch dog that checks our connection is alive.
    private val vpnWatchDog = VpnWatchdog()

    private var thread: Thread? = null
    private var blockFd: FileDescriptor? = null
    private var interruptFd: FileDescriptor? = null

    fun startThread() {
        Log.i(TAG, "Starting Vpn Thread")
        thread = Thread(this, "AdVpnThread")
        thread?.start()
        Log.i(TAG, "Vpn Thread started")
    }

    fun stopThread() {
        Log.i(TAG, "Stopping Vpn Thread")
        if (thread != null) {
            thread?.interrupt()
        }

        interruptFd =
            FileHelper.closeOrWarn(interruptFd, TAG, "stopThread: Could not close interruptFd")
        try {
            if (thread != null) {
                thread?.join(2000)
            }
        } catch (e: InterruptedException) {
            Log.w(TAG, "stopThread: Interrupted while joining thread", e)
        }
        if (thread != null && thread!!.isAlive) {
            Log.w(TAG, "stopThread: Could not kill VPN thread, it is still alive")
        } else {
            thread = null
            Log.i(TAG, "Vpn Thread stopped")
        }
    }

    @Synchronized
    override fun run() {
        Log.i(TAG, "Starting")

        // Load the block list
        try {
            dnsPacketProxy.initialize(upstreamDnsServers)
            vpnWatchDog.initialize(FileHelper.loadCurrentSettings().watchDog)
        } catch (e: InterruptedException) {
            return
        }

        notify(VpnStatus.STARTING)

        var retryTimeout = MIN_RETRY_TIME
        // Try connecting the vpn continuously
        while (true) {
            var connectTimeMillis: Long = 0
            try {
                connectTimeMillis = System.currentTimeMillis()
                // If the function returns, that means it was interrupted
                runVpn()

                Log.i(TAG, "Told to stop")
                notify(VpnStatus.STOPPING)
                break
            } catch (e: InterruptedException) {
                break
            } catch (e: VpnLostConnectionException) {
                // We want to filter out VpnNetworkException from out crash analytics as these
                // are exceptions that we expect to happen from network errors
                Log.w(TAG, "Network exception in vpn thread, ignoring and reconnecting", e)
                // If an exception was thrown, show to the user and try again
                notify(VpnStatus.RECONNECTING)
            } catch (e: VpnNetworkException) {
                // Same thing here, but show that there was an error
                Log.w(TAG, "Network exception in vpn thread, ignoring and reconnecting", e)
                notify(VpnStatus.RECONNECTING_NETWORK_ERROR)
            } catch (e: Exception) {
                Log.e(TAG, "Network exception in vpn thread, reconnecting", e)
                //ExceptionHandler.saveException(e, Thread.currentThread(), null);
                notify(VpnStatus.RECONNECTING_NETWORK_ERROR)
            }

            if (System.currentTimeMillis() - connectTimeMillis >= RETRY_RESET_SEC * 1000) {
                Log.i(TAG, "Resetting timeout")
                retryTimeout = MIN_RETRY_TIME
            }

            // ...wait and try again
            Log.i(TAG, "Retrying to connect in ${retryTimeout}seconds...")
            try {
                Thread.sleep(retryTimeout.toLong() * 1000)
            } catch (e: InterruptedException) {
                break
            }

            if (retryTimeout < MAX_RETRY_TIME) {
                retryTimeout *= 2
            }
        }

        notify(VpnStatus.STOPPED)
        Log.i(TAG, "Exiting")
    }

    @Throws(
        InterruptedException::class,
        ErrnoException::class,
        IOException::class,
        VpnNetworkException::class
    )
    private fun runVpn() {
        // Allocate the buffer for a single packet.
        val packet = ByteArray(32767)

        // A pipe we can interrupt the poll() call with by closing the interruptFd end
        val pipes = Os.pipe()
        interruptFd = pipes[0]
        blockFd = pipes[1]

        // Authenticate and configure the virtual network interface.
        try {
            configure().use { pfd ->
                // Read and write views of the tun device
                val inputStream = FileInputStream(pfd!!.fileDescriptor)
                val outFd = FileOutputStream(pfd.fileDescriptor)

                // Now we are connected. Set the flag and show the message.
                notify(VpnStatus.RUNNING)

                while (doOne(inputStream, outFd, packet)) {
                    // We keep forwarding packets till something goes wrong.
                }
            }
        } finally {
            blockFd = FileHelper.closeOrWarn(blockFd, TAG, "runVpn: Could not close blockFd")
        }
    }

    @Throws(
        IOException::class,
        ErrnoException::class,
        InterruptedException::class,
        VpnNetworkException::class
    )
    private fun doOne(
        inputStream: FileInputStream,
        outFd: FileOutputStream,
        packet: ByteArray
    ): Boolean {
        val deviceFd = StructPollfd()
        deviceFd.fd = inputStream.getFD()
        deviceFd.events = OsConstants.POLLIN.toShort()
        val blockFd = StructPollfd()
        blockFd.fd = this.blockFd
        blockFd.events = (OsConstants.POLLHUP or OsConstants.POLLERR).toShort()

        if (!deviceWrites.isEmpty()) {
            deviceFd.events = (deviceFd.events.toInt() or OsConstants.POLLOUT).toShort()
        }

        val polls = arrayOfNulls<StructPollfd>(2 + dnsIn.size())
        polls[0] = deviceFd
        polls[1] = blockFd
        run {
            var i = -1
            for (wosp in dnsIn) {
                i++
                polls[2 + i] = StructPollfd()
                val pollFd = polls[2 + i]
                pollFd!!.fd = ParcelFileDescriptor.fromDatagramSocket(wosp.socket).fileDescriptor
                pollFd.events = OsConstants.POLLIN.toShort()
            }
        }

        Log.d(TAG, "doOne: Polling ${polls.size} file descriptors")
        val result = FileHelper.poll(polls, vpnWatchDog.pollTimeout)
        if (result == 0) {
            vpnWatchDog.handleTimeout()
            return true
        }

        if (blockFd.revents.toInt() != 0) {
            Log.i(TAG, "Told to stop VPN")
            return false
        }

        // Need to do this before reading from the device, otherwise a new insertion there could
        // invalidate one of the sockets we want to read from either due to size or time out
        // constraints
        run {
            var i = -1
            val iter = dnsIn.iterator()
            while (iter.hasNext()) {
                i++
                val wosp = iter.next()
                if (polls[i + 2]!!.revents.toInt() and OsConstants.POLLIN != 0) {
                    Log.d(TAG, "Read from DNS socket" + wosp.socket)
                    iter.remove()
                    handleRawDnsResponse(wosp.packet, wosp.socket)
                    wosp.socket.close()
                }
            }
        }

        if ((deviceFd.revents.toInt() and OsConstants.POLLOUT) != 0) {
            Log.d(TAG, "Write to device")
            writeToDevice(outFd)
        }

        if (deviceFd.revents.toInt() and OsConstants.POLLIN != 0) {
            Log.d(TAG, "Read from device")
            readPacketFromDevice(inputStream, packet)
        }

        return true
    }

    @Throws(VpnNetworkException::class)
    private fun writeToDevice(outFd: FileOutputStream) =
        try {
            outFd.write(deviceWrites.poll())
        } catch (e: IOException) {
            throw VpnNetworkException("Outgoing VPN output stream closed")
        }

    @Throws(VpnNetworkException::class, SocketException::class)
    private fun readPacketFromDevice(inputStream: FileInputStream, packet: ByteArray) {
        // Read the outgoing packet from the input stream.
        val length: Int = try {
            inputStream.read(packet)
        } catch (e: IOException) {
            throw VpnNetworkException("Cannot read from device", e)
        }

        if (length == 0) {
            // TODO: Possibly change to exception
            Log.w(TAG, "Got empty packet!")
            return
        }

        val readPacket = Arrays.copyOfRange(packet, 0, length)

        vpnWatchDog.handlePacket(readPacket)
        dnsPacketProxy.handleDnsRequest(readPacket)
    }

    @Throws(VpnNetworkException::class)
    override fun forwardPacket(packet: DatagramPacket?, requestPacket: IpPacket?) {
        var dnsSocket: DatagramSocket? = null
        try {
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            dnsSocket = DatagramSocket()

            vpnService.protect(dnsSocket)

            dnsSocket.send(packet)

            if (requestPacket != null) {
                dnsIn.add(WaitingOnSocketPacket(dnsSocket, requestPacket))
            } else {
                FileHelper.closeOrWarn(
                    dnsSocket,
                    TAG,
                    "handleDnsRequest: Cannot close socket in error"
                )
            }
        } catch (e: IOException) {
            FileHelper.closeOrWarn(dnsSocket, TAG, "handleDnsRequest: Cannot close socket in error")
            if (e.cause is ErrnoException) {
                val errnoExc = e.cause as ErrnoException
                if (errnoExc.errno == OsConstants.ENETUNREACH || errnoExc.errno == OsConstants.EPERM) {
                    throw VpnLostConnectionException("Cannot send message:", e)
                }
            }
            Log.w(TAG, "handleDnsRequest: Could not send packet to upstream", e)
        }
    }

    @Throws(IOException::class)
    private fun handleRawDnsResponse(parsedPacket: IpPacket, dnsSocket: DatagramSocket) {
        val datagramData = ByteArray(1024)
        val replyPacket = DatagramPacket(datagramData, datagramData.size)
        dnsSocket.receive(replyPacket)
        dnsPacketProxy.handleDnsResponse(parsedPacket, datagramData)
    }

    override fun queueDeviceWrite(packet: IpPacket?) {
        packet ?: return
        deviceWrites.add(packet.rawData)
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
            Log.i(TAG, "newDNSServer: Ignoring DNS server $addr")
        } else if (addr is Inet4Address && format == null) {
            Log.i(TAG, "newDNSServer: Ignoring DNS server $addr")
        } else if (addr is Inet4Address) {
            upstreamDnsServers.add(addr)
            val alias = String.format(format!!, upstreamDnsServers.size + 1)
            Log.i(TAG, "configure: Adding DNS Server $addr as $alias")
            builder.addDnsServer(alias).addRoute(alias, 32)
            vpnWatchDog.setTarget(InetAddress.getByName(alias))
        } else if (addr is Inet6Address) {
            upstreamDnsServers.add(addr)
            ipv6Template!![ipv6Template.size - 1] = (upstreamDnsServers.size + 1).toByte()
            val i6addr = Inet6Address.getByAddress(ipv6Template)
            Log.i(TAG, "configure: Adding DNS Server $addr as $i6addr")
            builder.addDnsServer(i6addr)
            vpnWatchDog.setTarget(i6addr)
        }
    }

    fun configurePackages(builder: VpnService.Builder, config: Configuration) {
        val allowOnVpn: MutableSet<String> = HashSet()
        val doNotAllowOnVpn: MutableSet<String> = HashSet()

        config.appList.resolve(vpnService.packageManager, allowOnVpn, doNotAllowOnVpn)

        if (config.appList.defaultMode == com.t895.dnsnet.AllowListMode.NOT_ON_VPN) {
            for (app in allowOnVpn) {
                try {
                    Log.d(TAG, "configure: Allowing $app to use the DNS VPN")
                    builder.addAllowedApplication(app)
                } catch (e: Exception) {
                    Log.w(TAG, "configure: Cannot disallow", e)
                }
            }
        } else {
            for (app in doNotAllowOnVpn) {
                try {
                    Log.d(TAG, "configure: Disallowing $app from using the DNS VPN")
                    builder.addDisallowedApplication(app)
                } catch (e: Exception) {
                    Log.w(TAG, "configure: Cannot disallow", e)
                }
            }
        }
    }

    @Throws(VpnNetworkException::class)
    private fun configure(): ParcelFileDescriptor? {
        Log.i(TAG, "Configuring $this")

        val config = FileHelper.loadCurrentSettings()

        // Get the current DNS servers before starting the VPN
        val dnsServers = getDnsServers(vpnService)
        Log.i(TAG, "Got DNS servers = $dnsServers")

        // Configure a builder while parsing the parameters.
        val builder = vpnService.Builder()

        // Determine a prefix we can use. These are all reserved prefixes for example
        // use, so it's possible they might be blocked.
        var format: String? = null
        for (prefix in arrayOf("192.0.2", "198.51.100", "203.0.113")) {
            try {
                builder.addAddress("$prefix.1", 24)
            } catch (e: IllegalArgumentException) {
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
                Log.d(TAG, "configure: Adding IPv6 address$addr")
                builder.addAddress(addr, 120)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                ipv6Template = null
            }
        } else {
            ipv6Template = null
        }

        if (format == null) {
            Log.w(TAG, "configure: Could not find a prefix to use, directly using DNS servers")
            builder.addAddress("192.168.50.1", 24)
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
                        Log.e(TAG, "configure: Cannot add custom DNS server", e)
                    }
                }
            }
        }

        // Add all knows DNS servers
        for (addr in dnsServers) {
            try {
                newDNSServer(builder, format, ipv6Template, addr)
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "configure: Cannot add server:", e)
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
            vpnService,
            1,
            Intent(vpnService, MainActivity::class.java),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pfd = builder
            .setSession("DNSNet")
            .setConfigureIntent(pendingIntent)
            .establish()
        Log.i(TAG, "Configured")

        return pfd
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
