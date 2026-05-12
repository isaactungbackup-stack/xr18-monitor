package com.mixer.xr18.lib.data.osc

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * LAN discovery via UDP broadcast.
 */
class Xr18DiscoveryClient(
    private val broadcastPort: Int = 10024,
    private val timeoutMs: Long = 2000
) {
    private var socket: DatagramSocket? = null

    data class DiscoveryResult(
        val ipAddress: String,
        val deviceName: String,
        val model: String,
        val firmwareVersion: String
    )

    suspend fun discover(): List<DiscoveryResult> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val results = mutableListOf<DiscoveryResult>()
        try {
            socket = DatagramSocket().apply {
                soTimeout = timeoutMs.toInt()
                broadcast = true
            }

            val triggerData = ByteArray(1) { 0 }
            val trigger = DatagramPacket(
                triggerData, triggerData.size,
                InetAddress.getByAddress(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())),
                broadcastPort
            )
            socket?.send(trigger)

            val buffer = ByteArray(2048)
            val deadline = System.currentTimeMillis() + timeoutMs

            while (System.currentTimeMillis() < deadline) {
                try {
                    val pkt = DatagramPacket(buffer, buffer.size)
                    socket?.receive(pkt)
                    val msg = parseXInfoFromPacket(pkt.data, pkt.length, pkt.address.hostAddress)
                    if (msg != null) results.add(msg)
                } catch (e: java.net.SocketTimeoutException) {
                    break
                }
            }
        } catch (e: Exception) {
            // Discovery failed
        } finally {
            socket?.close()
            socket = null
        }
        results
    }

    private fun parseXInfoFromPacket(data: ByteArray, length: Int, sourceIp: String?): DiscoveryResult? {
        try {
            if (sourceIp == null) return null
            val addrEnd = findNullTerminator(data, 0, length)
            val address = String(data, 0, addrEnd, Charsets.UTF_8)
            if (address != "/xinfo") return null

            val typeTagOffset = (addrEnd + 4) and 0x7FFFFFFFC.toInt()
            if (typeTagOffset >= length || data[typeTagOffset] != 0x2C.toByte()) return null

            val strings = parseOSCStrings(data, typeTagOffset + 1, length)
            if (strings.size < 3) return null

            return DiscoveryResult(sourceIp, strings[0], strings[1], strings[2])
        } catch (e: Exception) {
            return null
        }
    }

    private fun findNullTerminator(data: ByteArray, start: Int, end: Int): Int {
        var i = start
        while (i < end && data[i] != 0.toByte()) i++
        return i.coerceAtMost(end)
    }

    private fun parseOSCStrings(data: ByteArray, start: Int, length: Int): List<String> {
        val strings = mutableListOf<String>()
        var pos = start
        while (pos < length && data[pos] != 0.toByte()) {
            val end = findNullTerminator(data, pos, length)
            if (end <= pos) break
            strings.add(String(data, pos, end - pos, Charsets.UTF_8))
            pos = (end + 4) and 0x7FFFFFFFC.toInt()
        }
        return strings
    }
}
