package com.mixer.xr18.lib.data.osc

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.util.Log

/**
 * OSC client using raw Java sockets.
 * Uses port 0 (ephemeral) to avoid binding conflicts.
 */
class OscClient(
    private val mixerIp: String,
    private val mixerPort: Int = 10023
) {
    private val TAG = "OscClient"
    private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var isRunning = false

    var onMessage: ((String, String) -> Unit)? = null
    
    private val _收到的OSC訊息 = MutableSharedFlow<OSCMessage>(extraBufferCapacity = 64)
    val 收到的OSC訊息: SharedFlow<OSCMessage> = _收到的OSC訊息.asSharedFlow()

    fun 啟動(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        receiveJob = scope.launch(Dispatchers.IO) {
            try {
                // CRITICAL FIX: Bind to port 0 (ephemeral) instead of 10024
                // XR18 replies to the SOURCE PORT of the incoming query
                // With port 0, OS assigns an available port (e.g., 60002+)
                // XR18 will reply to that ephemeral port - no conflict!
                socket = DatagramSocket(0)
                socket?.reuseAddress = true
                socket?.soTimeout = 2000
                
                val localPort = socket?.localPort
                Log.d(TAG, "Socket bound to ephemeral port $localPort")
                onMessage?.invoke("RECV", "Socket on port $localPort")
                
                val buffer = ByteArray(4096)
                var msgCount = 0
                while (isActive && isRunning) {
                    try {
                        val pkt = DatagramPacket(buffer, buffer.size)
                        socket?.receive(pkt)
                        val len = pkt.length
                        
                        // Log raw bytes with ASCII interpretation
                        val hexStr = buffer.take(len).map { String.format("%02X", it) }.joinToString(" ")
                        val asciiStr = buffer.take(len).map { 
                            if (it in 0x20..0x7E) it.toChar() else '.' 
                        }.joinToString("")
                        Log.d(TAG, "RAW[$len] hex=$hexStr")
                        Log.d(TAG, "RAW[$len] ascii=$asciiStr")
                        onMessage?.invoke("RECV", "RAW[$len]")
                        onMessage?.invoke("RECV", "  hex=$hexStr")
                        
                        val msg = OSCMessage(pkt.data, len)
                        msgCount++
                        val msgStr = "addr=${msg.address} args=${msg.args}"
                        Log.d(TAG, "RECV[$msgCount] $msgStr")
                        onMessage?.invoke("RECV", msgStr)
                        _收到的OSC訊息.emit(msg)
                    } catch (e: java.net.SocketTimeoutException) {
                        // Normal timeout - continue
                    } catch (e: Exception) {
                        Log.e(TAG, "Recv error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start socket: ${e.message}")
                onMessage?.invoke("RECV", "Socket error: ${e.message}")
            }
        }
    }

    fun 傳送(address: String, vararg args: Any) {
        try {
            val packet = buildOscPacket(address, args.toList())
            val addr = InetAddress.getByName(mixerIp)
            val dp = DatagramPacket(packet, packet.size, addr, mixerPort)
            
            // Log the packet with ASCII interpretation
            val hexSend = packet.map { String.format("%02X", it) }.joinToString(" ")
            val asciiStr = packet.map { 
                if (it in 0x20..0x7E) it.toChar() else '.' 
            }.joinToString("")
            Log.d(TAG, "SEND[$address] len=${packet.size}")
            Log.d(TAG, "  hex= $hexSend")
            Log.d(TAG, "  ascii=$asciiStr")
            onMessage?.invoke("SEND", "[$address] len=${packet.size}")
            onMessage?.invoke("SEND", "  hex= $hexSend")
            
            socket?.send(dp)
            
            Log.d(TAG, "SEND OK to $mixerIp:$mixerPort")
            onMessage?.invoke("SEND", "OK: to $mixerIp:$mixerPort")
        } catch (e: Exception) {
            Log.e(TAG, "Send FAILED: ${e.message}")
            onMessage?.invoke("SEND", "FAILED: ${e.message}")
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
        
        // Write address
        val addrBytes = address.toByteArray()
        baos.write(addrBytes)
        baos.write(0)  // null terminator
        while (baos.size() % 4 != 0) baos.write(0)  // pad to 4-byte boundary
        
        // Write type tag (starts with comma)
        baos.write(44) // ASCII ","
        for (arg in args) {
            when (arg) {
                is Int -> baos.write(105) // 'i'
                is Float -> baos.write(102) // 'f'
                is String -> baos.write(115) // 's'
            }
        }
        while (baos.size() % 4 != 0) baos.write(0)  // pad type tag to 4-byte boundary
        
        // Write arguments
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
        args = parseArguments(data, length)
    )

    companion object {
        private fun parseAddress(data: ByteArray, length: Int): String {
            var end = 0
            while (end < length && data[end] != 0.toByte()) end++
            return String(data, 0, end, Charsets.UTF_8)
        }
        
        private fun parseArguments(data: ByteArray, length: Int): List<Any> {
            val args = mutableListOf<Any>()
            
            var pos = 0
            while (pos < length && data[pos] != 0.toByte()) pos++
            pos = (pos + 4) and 0x7FFFFFFFC.toInt()
            
            if (pos >= length || data[pos] != 0x2C.toByte()) return args
            
            pos = (pos + 4) and 0x7FFFFFFFC.toInt()
            
            while (pos + 4 <= length) {
                val typeTag = data[pos].toChar()
                when (typeTag) {
                    'i' -> {
                        val v = ((data[pos+1].toInt() and 0xFF) shl 24) or
                                ((data[pos+2].toInt() and 0xFF) shl 16) or
                                ((data[pos+3].toInt() and 0xFF) shl 8) or
                                (data[pos+4].toInt() and 0xFF)
                        args.add(v)
                    }
                    'f' -> {
                        val bits = ((data[pos+1].toInt() and 0xFF) shl 24) or
                                  ((data[pos+2].toInt() and 0xFF) shl 16) or
                                  ((data[pos+3].toInt() and 0xFF) shl 8) or
                                  (data[pos+4].toInt() and 0xFF)
                        args.add(java.lang.Float.intBitsToFloat(bits))
                    }
                    'T' -> args.add(true)
                    'F' -> args.add(false)
                    else -> break
                }
                pos = (pos + 4) and 0x7FFFFFFFC.toInt()
            }
            
            return args
        }
    }
}
