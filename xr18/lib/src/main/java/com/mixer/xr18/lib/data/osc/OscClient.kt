package com.mixer.xr18.lib.data.osc

import android.util.Log
import com.illposed.osc.OSCMessage
import com.illposed.osc.OSCReceiver
import com.illposed.osc.OSCSender
import com.illposed.osc.TransportException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "XR18OscClient"
private const val LOG_TAG = "XR18OSC"

class OscClient(
    private val mixerIp: String,
    private val mixerPort: Int = 10023,
    private val localPort: Int = 10024
) {
    private var sender: OSCSender? = null
    private var receiver: DatagramSocket? = null
    private var receiveJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    private val _收到的OSC訊息 = MutableSharedFlow<OSCMessage>(extraBufferCapacity = 64)
    val 收到的OSC訊息: SharedFlow<OSCMessage> = _收到的OSC訊息.asSharedFlow()

    fun 啟動(scope: CoroutineScope) {
        if (isRunning.getAndSet(true)) return

        // OSC Sender -> XR18 port 10023
        sender = OSCSender().apply {
            this.datagramAddress = mixerIp
            this.datagramPort = mixerPort
            try {
                start()
            } catch (e: TransportException) {
                Log.e(TAG, "OSC sender failed to start: ${e.message}")
            }
        }

        // Datagram receiver for incoming OSC on local port
        receiveJob = scope.launch(Dispatchers.IO) {
            try {
                receiver = DatagramSocket(localPort).apply {
                    soTimeout = 1000
                }
                val buffer = ByteArray(4096)
                while (isActive && isRunning.get()) {
                    try {
                        val pkt = DatagramPacket(buffer, buffer.size)
                        receiver?.receive(pkt)
                        val msg = OSCMessage(buffer)
                        _收到的OSC訊息.emit(msg)
                    } catch (e: java.net.SocketTimeoutException) {
                        // Normal timeout — keep looping
                    } catch (e: Exception) {
                        Log.w(TAG, "Receive error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Receiver error: ${e.message}")
            }
        }
    }

    fun 傳送(msg: com.illposed.osc.messages.OSCRequest) {
        try {
            sender?.send(msg)
        } catch (e: TransportException) {
            Log.w(TAG, "Send error: ${e.message}")
        }
    }

    fun 停止() {
        isRunning.set(false)
        receiveJob?.cancel()
        try {
            sender?.stop()
        } catch (e: Exception) { }
        try {
            receiver?.close()
        } catch (e: Exception) { }
        sender = null
        receiver = null
    }
}
