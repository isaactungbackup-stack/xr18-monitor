package com.mixer.xr18.lib.data.osc

import android.util.Log
import com.illposed.osc.OSCMessage
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * LAN discovery via UDP broadcast.
 * Sends a broadcast to port 10024 and collects /xinfo replies.
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

    /**
     * Perform a single discovery sweep.
     * @return list of discovered XR18 devices (may be empty)
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun discover(): List<DiscoveryResult> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val results = mutableListOf<DiscoveryResult>()
        try {
            socket = DatagramSocket().apply {
                soTimeout = timeoutMs.toInt()
                broadcast = true
            }

            // Send an empty broadcast to trigger /xinfo responses
            // The XR18 sends /xinfo when any UDP packet is received on port 10024
            val triggerData = ByteBuffer.wrap(ByteArray(1)).array()
            val trigger = DatagramPacket(
                triggerData,
                triggerData.size,
                InetAddress.getByAddress(ByteArray(4) { 0xFF.toByte() }),
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
            Log.e("XR18Discovery", "Discovery error: ${e.message}")
        } finally {
            socket?.close()
            socket = null
        }
        results
    }

    private fun parseXInfoFromPacket(data: ByteArray, length: Int, sourceIp: String): DiscoveryResult? {
        try {
            // Parse OSC bundle/message from raw bytes
            val msg = OSCMessage(data)
            val addr = msg.address ?: return null
            if (addr != "/xinfo") return null

            val args = msg.arguments
            if (args.size < 3) return null

            val name     = args.getOrNull(0)?.toString() ?: return null
            val model    = args.getOrNull(1)?.toString() ?: return null
            val firmware = args.getOrNull(2)?.toString() ?: return null

            return DiscoveryResult(
                ipAddress       = sourceIp ?: return null,
                deviceName      = name,
                model           = model,
                firmwareVersion = firmware
            )
        } catch (e: Exception) {
            return null
        }
    }
}
