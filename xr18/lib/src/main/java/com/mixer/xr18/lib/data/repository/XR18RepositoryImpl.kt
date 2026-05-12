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
        
        // XR18 OSC Port is 10024 (destination), but we use ephemeral port for sending
        // XR18 replies to our source port (OS-assigned ephemeral port)
        // This matches how X-air Edit works: ephemeral source port, fixed dest 10024
        client = OscClient(mixerIp = device.ipAddress, mixerPort = 10024)
        
        client?.onMessage = { type, msg -> onMessage?.invoke(type, msg) }
        
        // OscClient now uses ephemeral port (0) - no port conflict!
        // XR18 will reply to the OS-assigned source port
        client?.啟動(ioScope)
        Log.d(TAG, "OscClient started for ${device.ipAddress}:10024")

        // Collect all incoming messages
        ioScope.launch {
            client?.收到的OSC訊息?.collect { msg ->
                Log.d(TAG, "RECV: ${msg.address} args=${msg.args}")
                更新頻道狀態(msg)
            }
        }

        remoteJob = ioScope.launch {
            Log.d(TAG, "Starting query to ${device.ipAddress}:10024")
            
            // Step 1: Send /xinfo to verify connection
            client?.傳送("/xinfo")
            Log.d(TAG, "SENT: /xinfo")
            delay(500)
            
            // Step 2: Send /xremote to subscribe to periodic updates
            // XR18 will send state updates every ~250ms when subscribed
            client?.傳送("/xremote")
            Log.d(TAG, "SENT: /xremote subscription")
            delay(500)
            
            // Step 3: Query all channel main states (/ch/xx/mix = fader + on + pan)
            // This matches X-air Edit's query pattern from pcap analysis
            for (ch in 1..16) {
                val chStr = ch.toString().padStart(2, '0')
                client?.傳送("/ch/$chStr/mix")
                delay(50)  // 50ms between queries to avoid flooding
            }
            
            // Step 4: Query headamp (preamp) gain for all channels
            for (ch in 1..16) {
                val chStr = ch.toString().padStart(2, '0')
                client?.傳送("/headamp/$chStr/gain")
                delay(50)
            }
            
            Log.d(TAG, "Initial query complete, waiting for /xremote updates...")
            
            // Continue sending /xremote every 8 seconds to stay subscribed
            while (isActive) {
                delay(8000)
                client?.傳送("/xremote")
            }
        }
        
        delay(6000)
        if (_state.value.channels.isEmpty()) {
            val channels = (1..16).map { ChannelState(channelNumber = it) }
            _state.value = MixerState(device = device, channels = channels)
        }
    }

    private fun 更新頻道狀態(msg: OSCMessage) {
        val addr = msg.address
        val args = msg.args
        
        // Handle /xremote subscription responses (batch updates)
        if (addr == "/xremote" || addr.startsWith("/xremote")) {
            Log.d(TAG, "/xremote update: ${args.size} args")
            return
        }
        
        // Handle /ch/xx/mix responses (fader, on, pan as blob)
        val chMixMatch = Regex("^/ch/(\\d+)/mix$").find(addr)
        if (chMixMatch != null) {
            val ch = chMixMatch.groupValues[1].toIntOrNull() ?: return
            if (ch < 1 || ch > 16) return
            val idx = ch - 1
            if (idx >= _state.value.channels.size) return
            var cs = _state.value.channels[idx]
            if (args.isNotEmpty()) {
                val fader = (args[0] as? Number)?.toFloat() ?: 0.75f
                cs = cs.copy(fader = fader, faderDb = ChannelState.faderToDb(fader))
            }
            if (args.size > 1) {
                val on = (args[1] as? Number)?.toInt() ?: 1
                cs = cs.copy(muted = on == 0)
            }
            if (args.size > 2) {
                val pan = (args[2] as? Number)?.toFloat() ?: 0.5f
                cs = cs.copy(pan = pan)
            }
            val current = _state.value.channels.toMutableList()
            current[idx] = cs
            _state.value = _state.value.copy(channels = current)
            return
        }
        
        // Handle /ch/xx/mix/fader
        val faderMatch = Regex("^/ch/(\\d+)/mix/fader$").find(addr)
        if (faderMatch != null) {
            val ch = faderMatch.groupValues[1].toIntOrNull() ?: return
            if (ch < 1 || ch > 16 || args.isEmpty()) return
            val idx = ch - 1
            if (idx >= _state.value.channels.size) return
            val v = (args[0] as? Number)?.toFloat() ?: return
            val current = _state.value.channels.toMutableList()
            current[idx] = current[idx].copy(fader = v, faderDb = ChannelState.faderToDb(v))
            _state.value = _state.value.copy(channels = current)
            return
        }
        
        // Handle /headamp/xx/gain
        if (addr.startsWith("/headamp") && addr.contains("/gain") && args.isNotEmpty()) {
            val parts = addr.split("/")
            if (parts.size >= 3) {
                val ch = parts[2].toIntOrNull() ?: return
                if (ch < 1 || ch > 16) return
                val idx = ch - 1
                if (idx >= _state.value.channels.size) return
                val v = (args[0] as? Number)?.toFloat() ?: return
                val current = _state.value.channels.toMutableList()
                current[idx] = current[idx].copy(preampGain = v)
                _state.value = _state.value.copy(channels = current)
            }
            return
        }
    }

    private fun 停止所有連線() {
        remoteJob?.cancel()
        queryJob?.cancel()
        client?.停止()
        client = null
    }
}
