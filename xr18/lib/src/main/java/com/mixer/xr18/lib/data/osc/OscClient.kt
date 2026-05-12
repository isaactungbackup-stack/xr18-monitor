package com.mixer.xr18.lib.data.osc

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Native OSC client using raw Java sockets.
 * No external dependencies.
 */
class OscClient(
    private val mixerIp: String,
    private val mixerPort: Int = 10023,
    private val localPort: Int = 10024
) {
    private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var isRunning = false

    private val _收到的OSC訊息 = MutableSharedFlow<OSCMessage>(extraBufferCapacity = 64)
    val 收到的OSC訊息: SharedFlow<OSCMessage> = _收到的OSC訊息.asSharedFlow()

    fun 啟動(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        receiveJob = scope.launch(Dispatchers.IO) {
            try {
                socket = DatagramSocket(localPort).apply {
                    soTimeout = 1000
                }
                val buffer = ByteArray(4096)
                while (isActive && isRunning) {
                    try {
                        val pkt = DatagramPacket(buffer, buffer.size)
                        socket?.receive(pkt)
                        val msg = OSCMessage(pkt.data, pkt.length)
                        _收到的OSC訊息.emit(msg)
                    } catch (e: java.net.SocketTimeoutException) {
                        // Normal timeout - continue
                    } catch (e: Exception) {
                        // Continue
                    }
                }
            } catch (e: Exception) {
                // Failed to start
            }
        }
    }

    fun 傳送(address: String, vararg args: Any) {
        try {
            val packet = buildOscPacket(address, args.toList())
            val addr = InetAddress.getByName(mixerIp)
            val dp = DatagramPacket(packet, packet.size, addr, mixerPort)
            socket?.send(dp)
        } catch (e: Exception) {
            // Send failed
        }
    }

    fun 停止() {
        isRunning = false
        receiveJob?.cancel()
        try {
            socket?.close()
        } catch (e: Exception) { }
        socket = null
    }

    private fun buildOscPacket(address: String, args: List<Any>): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        val addrBytes = address.toByteArray()
        baos.write(addrBytes)
        baos.write(0)
        while (baos.size() % 4 != 0) baos.write(0)
        
        baos.write(0)
        baos.write(44)
        baos.write(0); baos.write(0)
        
        for (arg in args) {
            when (arg) {
                is Int -> {
                    var v = arg
                    baos.write((v ushr 24) and 0xFF)
                    baos.write((v ushr 16) and 0xFF)
                    baos.write((v ushr 8) and 0xFF)
                    baos.write(v and 0xFF)
                }
                is Float -> {
                    val bits = java.lang.Float.floatToIntBits(arg)
                    baos.write((bits ushr 24) and 0xFF)
                    baos.write((bits ushr 16) and 0xFF)
                    baos.write((bits ushr 8) and 0xFF)
                    baos.write(bits and 0xFF)
                }
                is String -> {
                    val strBytes = arg.toString().toByteArray()
                    baos.write(strBytes)
                    baos.write(0)
                    while (baos.size() % 4 != 0) baos.write(0)
                }
            }
        }
        return baos.toByteArray()
    }
}

data class OSCMessage(
    val address: String,
    val args: List<Any> = emptyList()
) {
    constructor(data: ByteArray, length: Int) : this(
        address = parseAddress(data, length),
        args = emptyList()
    )

    companion object {
        private fun parseAddress(data: ByteArray, length: Int): String {
            var end = 0
            while (end < length && data[end] != 0.toByte()) end++
            return String(data, 0, end, Charsets.UTF_8)
        }
    }
}
