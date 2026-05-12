package com.mixer.xr18.lib.data.osc

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.util.Log

/**
 * OSC client using raw Java sockets.
 * Uses port 0 (ephemeral) - OS picks any available port.
 * This completely avoids port binding conflicts.
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
                // Port 0 = ephemeral - OS picks any available port
                socket = DatagramSocket(0)
                socket?.reuseAddress = true
                socket?.soTimeout = 1000
                
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
                        val msg = OSCMessage(pkt.data, len)
                        msgCount++
                        val msgStr = "addr=${msg.address} from ${pkt.address.hostAddress}"
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
            socket?.send(dp)
            val msgStr = "addr=$address to $mixerIp:$mixerPort"
            Log.d(TAG, "SEND $msgStr")
            onMessage?.invoke("SEND", msgStr)
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
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
