package com.mixer.xr18.lib.data.repository

import android.util.Log
import com.illposed.osc.OSCMessage
import com.mixer.xr18.lib.data.osc.OscClient
import com.mixer.xr18.lib.data.osc.XR18MessageParser
import com.mixer.xr18.lib.domain.model.ChannelState
import com.mixer.xr18.lib.domain.model.EqBands
import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.model.MixerState
import com.mixer.xr18.lib.domain.repository.MixerRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

private const val TAG = "XR18Repository"

class XR18RepositoryImpl(
    private val scope: CoroutineScope
) : MixerRepository {

    private val _state = MutableStateFlow(MixerState())
    override fun mixerStateFlow(): StateFlow<MixerState> = _state.asStateFlow()

    private var client: OscClient? = null
    private var remoteJob: Job? = null
    private var queryJob: Job? = null

    // ─── Discovery ───────────────────────────────────────────────
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
                    val msg = OSCMessage(pkt.data)
                    val addr = msg.address ?: continue
                    if (addr != "/xinfo") continue

                    val args = msg.arguments
                    if (args.size < 3) continue

                    results.add(
                        MixerDevice(
                            ipAddress        = pkt.address.hostAddress ?: continue,
                            name             = args[0].toString(),
                            model            = args[1].toString(),
                            firmwareVersion  = args[2].toString()
                        )
                    )
                }
                results
            } finally {
                socket.close()
            }
        }

    // ─── Connect & Query ──────────────────────────────────────────
    override suspend fun queryChannelStates(device: MixerDevice) {
        // Cancel any prior session
        停止所有連線()

        _state.value = MixerState(device = device)

        client = OscClient(mixerIp = device.ipAddress).also { it.啟動(scope) }

        // Listen for incoming messages and map them into state
        scope.launch {
            client?.收到的OSC訊息?.collect { msg ->
                更新頻道狀態(msg)
            }
        }

        // Send /xremote keep-alive every 8 seconds
        remoteJob = scope.launch {
            while (isActive) {
                client?.傳送(com.mixer.xr18.lib.data.osc.XR18MessageFactory.buildXRemote())
                delay(8_000)
            }
        }

        // Query all 16 channels once
        queryJob = scope.launch {
            for (ch in 1..16) {
                XR18MessageFactory.buildChannelQuery(ch).forEach { q ->
                    client?.傳送(q)
                }
                delay(30) // small stagger to avoid flooding
            }
        }
    }

    // ─── State Update ─────────────────────────────────────────────
    private fun 更新頻道狀態(msg: OSCMessage) {
        val addr = msg.address ?: return
        val ch = XR18MessageParser.parseChannelNumber(addr) ?: return
        if (ch < 1 || ch > 16) return

        val current = _state.value.channels.toMutableList()
        val idx = ch - 1
        val cs = current[idx]

        when {
            addr.endsWith("/mix/fader") -> {
                val v = XR18MessageParser.getFloat(msg) ?: return
                current[idx] = cs.copy(
                    fader = v,
                    faderDb = ChannelState.faderToDb(v)
                )
            }
            addr.endsWith("/mix/on") -> {
                val v = XR18MessageParser.getInt(msg) ?: return
                current[idx] = cs.copy(muted = v == 0)
            }
            addr.endsWith("/mix/pan") -> {
                val v = XR18MessageParser.getFloat(msg) ?: return
                current[idx] = cs.copy(pan = v)
            }
            addr.startsWith("/headamp") -> {
                val v = XR18MessageParser.getFloat(msg) ?: return
                current[idx] = cs.copy(preampGain = v)
            }
            addr.endsWith("/eq/on") -> {
                val v = XR18MessageParser.getInt(msg) ?: return
                current[idx] = cs.copy(eqEnabled = v == 1)
            }
            addr.endsWith("/g") -> {
                val band = XR18MessageParser.parseEqBand(addr) ?: return
                val v = XR18MessageParser.getFloat(msg) ?: return
                val bands = when (band) {
                    1 -> cs.eqBands.copy(band1 = v)
                    2 -> cs.eqBands.copy(band2 = v)
                    3 -> cs.eqBands.copy(band3 = v)
                    4 -> cs.eqBands.copy(band4 = v)
                    else -> cs.eqBands
                }
                current[idx] = cs.copy(eqBands = bands)
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

    override fun equals(other: Any?) = other is XR18RepositoryImpl
    override fun hashCode() = System.identityHashCode(this)
}
