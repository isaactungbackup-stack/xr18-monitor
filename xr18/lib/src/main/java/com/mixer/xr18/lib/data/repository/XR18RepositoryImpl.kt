package com.mixer.xr18.lib.data.repository

import com.mixer.xr18.lib.data.osc.OscClient
import com.mixer.xr18.lib.data.osc.OSCMessage
import com.mixer.xr18.lib.domain.model.ChannelState
import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.model.MixerState
import com.mixer.xr18.lib.domain.repository.MixerRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.util.Log

class XR18RepositoryImpl(
    private val scope: CoroutineScope
) : MixerRepository {

    private val TAG = "XR18Repo"
    private val _state = MutableStateFlow(MixerState())
    override fun mixerStateFlow(): StateFlow<MixerState> = _state.asStateFlow()

    private var client: OscClient? = null
    private var remoteJob: Job? = null
    private var queryJob: Job? = null
    
    // Callback to report messages back to UI
    var onMessage: ((String, String) -> Unit)? = null

    override suspend fun discoverDevices(timeoutMs: Long): List<MixerDevice> =
        withContext(Dispatchers.IO) {
            val socket = DatagramSocket().apply { soTimeout = timeoutMs.toInt(); broadcast = true }
            try {
                val triggerData = ByteArray(1)
                val trigger = DatagramPacket(
                    triggerData, 1,
                    InetAddress.getByAddress(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())),
                    10024
                )
                socket.send(trigger)

                val buffer = ByteArray(2048)
                val results = mutableListOf<MixerDevice>()
                val deadline = System.currentTimeMillis() + timeoutMs

                while (System.currentTimeMillis() < deadline) {
                    val pkt = DatagramPacket(buffer, buffer.size)
                    socket.receive(pkt)
                    
                    val addrEnd = findNullTerminator(buffer, 0, pkt.length)
                    val addr = String(buffer, 0, addrEnd, Charsets.UTF_8)
                    if (addr != "/xinfo") continue

                    val typeTagOffset = (addrEnd + 4) and 0x7FFFFFFFC.toInt()
                    if (typeTagOffset >= pkt.length || buffer[typeTagOffset] != 0x2C.toByte()) continue

                    val strings = parseOSCStrings(buffer, typeTagOffset + 1, pkt.length)
                    if (strings.size < 3) continue

                    results.add(MixerDevice(
                        ipAddress = pkt.address.hostAddress ?: continue,
                        name = strings[0],
                        model = strings[1],
                        firmwareVersion = strings[2]
                    ))
                }
                results
            } finally {
                socket.close()
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

    override suspend fun queryChannelStates(device: MixerDevice) {
        停止所有連線()
        
        val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        client = OscClient(mixerIp = device.ipAddress, mixerPort = 10024, localPort = 10025)
        
        // Wire up the message callback
        client?.onMessage = { type, msg -> onMessage?.invoke(type, msg) }
        
        client?.啟動(ioScope)
        Log.d(TAG, "OscClient started for ${device.ipAddress}")

        // Collect all incoming messages
        ioScope.launch {
            client?.收到的OSC訊息?.collect { msg ->
                Log.d(TAG, "RECV: ${msg.address} args=${msg.args}")
                更新頻道狀態(msg)
            }
        }

        remoteJob = ioScope.launch {
            Log.d(TAG, "Starting query to ${device.ipAddress}")
            
            // Send /xremote to trigger bulk data
            client?.傳送("/xremote")
            Log.d(TAG, "SENT: /xremote")
            
            // Wait for XR18 to send bulk data
            delay(3000)
            
            // Now send individual channel queries
            for (ch in 1..16) {
                val chStr = ch.toString().padStart(2, '0')
                client?.傳送("/ch/$chStr/mix/fader")
                Log.d(TAG, "SENT: /ch/$chStr/mix/fader")
                delay(50)
                client?.傳送("/ch/$chStr/mix/on")
                Log.d(TAG, "SENT: /ch/$chStr/mix/on")
                delay(50)
            }
            
            Log.d(TAG, "Query complete, waiting for responses...")
            
            // Keep /xremote subscription alive
            while (isActive) {
                delay(8000)
                client?.傳送("/xremote")
            }
        }
        
        // Initialize state after a delay
        delay(6000)
        if (_state.value.channels.isEmpty()) {
            val channels = (1..16).map { ChannelState(channelNumber = it) }
            _state.value = MixerState(device = device, channels = channels)
        }
    }

    private fun 更新頻道狀態(msg: OSCMessage) {
        val addr = msg.address
        
        val chMatch = Regex("""/ch/(\d+)/""").find(addr) ?: return
        val ch = chMatch.groupValues[1].toIntOrNull() ?: return
        if (ch < 1 || ch > 16) return

        val current = _state.value.channels.toMutableList()
        val idx = ch - 1
        if (idx >= current.size) return
        val cs = current[idx]

        when {
            addr.endsWith("/mix/fader") && msg.args.isNotEmpty() -> {
                val v = (msg.args[0] as? Number)?.toFloat() ?: return
                current[idx] = cs.copy(fader = v, faderDb = ChannelState.faderToDb(v))
            }
            addr.endsWith("/mix/on") && msg.args.isNotEmpty() -> {
                val v = (msg.args[0] as? Number)?.toInt() ?: return
                current[idx] = cs.copy(muted = v == 0)
            }
            addr.endsWith("/mix/pan") && msg.args.isNotEmpty() -> {
                val v = (msg.args[0] as? Number)?.toFloat() ?: return
                current[idx] = cs.copy(pan = v)
            }
            addr.startsWith("/headamp") && addr.contains("/gain") && msg.args.isNotEmpty() -> {
                val v = (msg.args[0] as? Number)?.toFloat() ?: return
                current[idx] = cs.copy(preampGain = v)
            }
        }

        _state.value = _state.value.copy(channels = current)
    }

    private fun 停止所有連線() {
        remoteJob?.cancel()
        queryJob?.cancel()
        client?.停止()
        client = null
    }
}
