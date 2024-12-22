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

package dev.clombardo.dnsnet.vpn

import dev.clombardo.dnsnet.db.RuleDatabase
import dev.clombardo.dnsnet.logi
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.SOARecord
import org.xbill.DNS.Section
import org.xbill.DNS.TextParseException
import uniffi.net.GenericIpPacket
import uniffi.net.buildResponsePacket
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress

/**
 * Creates and parses packets, and sends packets to a remote socket or the device using
 * {@link AdVpnThread}.
 */
class DnsPacketProxy(
    private val eventLoop: AdVpnThread,
    private val log: (name: String, allowed: Boolean) -> Unit,
) {
    companion object {
        // Choose a value that is smaller than the time needed to unblock a host.
        private const val NEGATIVE_CACHE_TTL_SECONDS = 5L
        private var NEGATIVE_CACHE_SOA_RECORD: SOARecord = try {
            // Let's use a guaranteed invalid hostname here, clients are not supposed to use
            // our fake values, the whole thing just exists for negative caching.
            val name = Name("dnsnet.dnsnet.invalid.")
            SOARecord(
                name,
                DClass.IN,
                NEGATIVE_CACHE_TTL_SECONDS,
                name,
                name,
                0,
                0,
                0,
                0,
                NEGATIVE_CACHE_TTL_SECONDS
            )
        } catch (e: TextParseException) {
            throw RuntimeException(e)
        }
    }

    private val ruleDatabase = RuleDatabase()
    private var upstreamDnsServers = ArrayList<InetAddress>()

    /**
     * Initializes the rules database and the list of upstream servers.
     *
     * @param upstreamDnsServers The upstream DNS servers to use; or an empty list if no
     *                           rewriting of ip addresses takes place
     * @throws InterruptedException If the database initialization was interrupted
     */
    @Throws(InterruptedException::class)
    fun initialize(upstreamDnsServers: ArrayList<InetAddress>) {
        ruleDatabase.initialize()
        this.upstreamDnsServers = upstreamDnsServers
    }

    /**
     * Handles a responsePayload from an upstream DNS server
     *
     * @param requestPacket   The original request packet
     * @param responsePayload The payload of the response
     */
    fun handleDnsResponse(requestPacket: ByteArray, responsePayload: ByteArray) =
        eventLoop.queueDeviceWrite(buildResponsePacket(requestPacket, responsePayload))

    /**
     * Handles a DNS request, by either blocking it or forwarding it to the remote location.
     *
     * @param packetData The packet data to read
     * @throws VpnNetworkException If some network error occurred
     */
    @Throws(VpnNetworkException::class)
    fun handleDnsRequest(packetData: ByteArray) {
        val packet = GenericIpPacket(packetData)

        // TODO: We currently assume that DNS requests will be UDP only. This is not true.
        val udpPacketData = packet.getUdpPacket() ?: return
        val udpPacket = uniffi.net.UdpPacket(udpPacketData)

        val destinationAddress = packet.getDestinationAddress() ?: return
        val realDestinationAddress = translateDestinationAddress(destinationAddress) ?: return
        val inetRealDestinationAddress = InetAddress.getByAddress(realDestinationAddress)

        val destinationPort = udpPacket.getDestinationPort().toInt()

        if (!udpPacket.hasPayload()) {
            // logi("handleDnsRequest: Sending UDP packet without payload: $parsedUdp")

            // Let's be nice to Firefox. Firefox uses an empty UDP packet to
            // the gateway to reduce the RTT. For further details, please see
            // https://bugzilla.mozilla.org/show_bug.cgi?id=888268
            try {
                val outPacket = DatagramPacket(
                    ByteArray(0),
                    0,
                    0,
                    inetRealDestinationAddress,
                    destinationPort,
                )
                eventLoop.forwardPacket(outPacket, null)
            } catch (e: Exception) {
                logi("handleDnsRequest: Could not send empty UDP packet", e)
            }
            return
        }

        val udpPayload = udpPacket.getPayload()!!
        val dnsMsg = try {
            Message(udpPayload)
        } catch (e: IOException) {
            logi("handleDnsRequest: Discarding non-DNS or invalid packet", e)
            return
        }

        if (dnsMsg.question == null) {
            logi("handleDnsRequest: Discarding DNS packet with no query $dnsMsg")
            return
        }

        val dnsQueryName = dnsMsg.question.name.toString(true).lowercase()
        if (!ruleDatabase.isBlocked(dnsQueryName)) {
            logi("handleDnsRequest: DNS Name $dnsQueryName Allowed, sending to $realDestinationAddress")
            log(dnsQueryName, true)
            val outPacket = DatagramPacket(
                udpPayload,
                0,
                udpPayload.size,
                inetRealDestinationAddress,
                destinationPort,
            )
            eventLoop.forwardPacket(outPacket, packetData)
        } else {
            logi("handleDnsRequest: DNS Name $dnsQueryName Blocked!")
            log(dnsQueryName, false)
            dnsMsg.header.setFlag(Flags.QR.toInt())
            dnsMsg.header.rcode = Rcode.NOERROR
            dnsMsg.addRecord(NEGATIVE_CACHE_SOA_RECORD, Section.AUTHORITY)
            handleDnsResponse(packetData, dnsMsg.toWire())
        }
    }

    /**
     * Translates the destination address in the packet to the real one. In
     * case address translation is not used, this just returns the original one.
     *
     * @param destinationAddress Destination address in that packet that we want to translate.
     * @return The translated address or null on failure.
     */
    private fun translateDestinationAddress(destinationAddress: ByteArray): ByteArray? {
        val realDestinationAddress: ByteArray
        if (upstreamDnsServers.isNotEmpty()) {
            val index = destinationAddress[destinationAddress.size - 1] - 2

            try {
                realDestinationAddress = upstreamDnsServers[index].address
            } catch (e: Exception) {
                // TODO: Create byte array to address string func
//                loge(
//                    """
//                        handleDnsRequest: Cannot handle packets to:
//                        ${parsedPacket.header.dstAddr.hostAddress}
//                        Not a valid address for this network
//                    """.trimIndent(),
//                    e,
//                )
                return null
            }
//            logd {
//                String.format(
//                    Locale.ENGLISH,
//                    "handleDnsRequest: Incoming packet to %s AKA %d AKA %s",
//                    parsedPacket.header.dstAddr.hostAddress,
//                    index,
//                    destAddr
//                )
//            }
        } else {
            realDestinationAddress = destinationAddress
//            logd {
//                "handleDnsRequest: Incoming packet to ${parsedPacket.header.dstAddr.hostAddress} - is upstream"
//            }
        }
        return realDestinationAddress
    }
}
