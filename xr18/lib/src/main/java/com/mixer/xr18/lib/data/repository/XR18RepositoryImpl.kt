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

class XR18RepositoryImpl(
    private val scope: CoroutineScope
) : MixerRepository {

    private val _state = MutableStateFlow(MixerState())
    override fun mixerStateFlow(): StateFlow<MixerState> = _state.asStateFlow()

    private var client: OscClient? = null
    private var remoteJob: Job? = null
    private var queryJob: Job? = null

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
        
        // Initialize channels
        val channels = (1..16).map { num ->
            ChannelState(channelNumber = num)
        }
        _state.value = MixerState(device = device, channels = channels)
        
        client = OscClient(mixerIp = device.ipAddress).also { it.啟動(scope) }

        // Collect messages in background
        scope.launch {
            client?.收到的OSC訊息?.collect { msg ->
                更新頻道狀態(msg)
            }
        }

        remoteJob = scope.launch {
            // FIRST: Send /xremote to start subscription
            client?.傳送("/xremote")
            delay(2000) // Wait for bulk data response
            
            // THEN: Query individual parameters for remaining data
            for (ch in 1..16) {
                val chStr = ch.toString().padStart(2, '0')
                client?.傳送("/ch/$chStr/mix/fader")
                delay(20)
                client?.傳送("/ch/$chStr/mix/on")
                delay(20)
                client?.傳送("/headamp/$ch/gain")
                delay(30)
            }
            
            // Keep sending /xremote periodically
            while (isActive) {
                client?.傳送("/xremote")
                delay(8_000)
            }
        }
    }

    private fun 更新頻道狀態(msg: OSCMessage) {
        val addr = msg.address
        
        // Handle /xremote bulk data format: /ch/xx/parameter value
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
                // XR18 returns: 0 = muted (on=true is muted? or is it inverted?)
                // Actually /mix/on=0 means muted, /mix/on=1 means not muted
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
