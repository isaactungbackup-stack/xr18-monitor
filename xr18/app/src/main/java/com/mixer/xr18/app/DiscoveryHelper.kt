package com.mixer.xr18.app

import android.util.Log
import com.mixer.xr18.lib.data.osc.OscClient
import com.mixer.xr18.lib.data.osc.OSCMessage
import com.mixer.xr18.lib.data.repository.XR18RepositoryImpl
import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.usecase.DiscoverMixersUseCase
import com.mixer.xr18.lib.domain.usecase.QueryChannelStatesUseCase
import com.mixer.xr18.lib.domain.usecase.ObserveMixerStateUseCase
import com.mixer.xr18.lib.presentation.Xr18ViewModel
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch

object DiscoveryHelper {
    private var viewModel: Xr18ViewModel? = null
    private val TAG = "XR18Discovery"
    
    // Store messages for display
    val sentMessages = mutableListOf<String>()
    val receivedMessages = mutableListOf<String>()
    
    @JvmStatic
    fun createDevice(ip: String, name: String, model: String, fw: String): MixerDevice {
        return MixerDevice(ip, name, model, fw)
    }
    
    @JvmStatic
    fun connectToIpSync(ip: String): Boolean {
        val latch = CountDownLatch(1)
        var result = false
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 2000
                
                val pingMsg = buildOscPing()
                val addr = InetAddress.getByName(ip)
                val pkt = DatagramPacket(pingMsg, pingMsg.size, addr, 10023)
                socket.send(pkt)
                addSent("/xinfo")
                
                val buffer = ByteArray(4096)
                val response = DatagramPacket(buffer, buffer.size)
                
                try {
                    socket.receive(response)
                    result = true
                    addReceived("/xinfo response from ${ip}")
                } catch (e: java.net.SocketTimeoutException) {
                    val pkt2 = DatagramPacket(pingMsg, pingMsg.size, addr, 10024)
                    socket.send(pkt2)
                    addSent("/xinfo to 10024")
                    try {
                        socket.receive(response)
                        result = true
                        addReceived("/xinfo response from ${ip}:10024")
                    } catch (e2: java.net.SocketTimeoutException) {
                        result = false
                        addReceived("No response from ${ip}")
                    }
                }
                
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
        
        latch.await()
        return result
    }
    
    private fun addSent(msg: String) {
        Log.d(TAG, "SEND: $msg")
        sentMessages.add("[SEND] $msg")
        if (sentMessages.size > 100) sentMessages.removeAt(0)
    }
    
    private fun addReceived(msg: String) {
        Log.d(TAG, "RECV: $msg")
        receivedMessages.add("[RECV] $msg")
        if (receivedMessages.size > 100) receivedMessages.removeAt(0)
    }
    
    private fun buildOscPing(): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        val addr = "/xinfo"
        val addrBytes = addr.toByteArray()
        baos.write(addrBytes)
        baos.write(0)
        while (baos.size() % 4 != 0) baos.write(0)
        baos.write(0) // , 
        baos.write(0)
        while (baos.size() % 4 != 0) baos.write(0)
        return baos.toByteArray()
    }
    
    @JvmStatic
    fun discoverSync(): List<MixerDevice> {
        sentMessages.clear()
        receivedMessages.clear()
        
        val latch = CountDownLatch(1)
        var result: List<MixerDevice> = emptyList()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 3000
                socket.broadcast = true
                
                val triggerData = ByteArray(1) { 0 }
                val broadcastAddr = InetAddress.getByAddress(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
                val trigger = DatagramPacket(triggerData, triggerData.size, broadcastAddr, 10024)
                socket.send(trigger)
                addSent("broadcast to 10024")
                
                val buffer = ByteArray(2048)
                val deadline = System.currentTimeMillis() + 3000
                val foundDevices = mutableListOf<MixerDevice>()
                
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val pkt = DatagramPacket(buffer, buffer.size)
                        socket.receive(pkt)
                        
                        val srcIP = pkt.address.hostAddress
                        val addrEnd = findNullTerminator(buffer, 0, pkt.length)
                        val address = String(buffer, 0, addrEnd, Charsets.UTF_8)
                        
                        addReceived("from ${srcIP}: ${address}")
                        
                        if (address == "/xinfo") {
                            val typeTagOffset = (addrEnd + 4) and 0x7FFFFFFFC.toInt()
                            if (typeTagOffset < pkt.length && buffer[typeTagOffset] == 0x2C.toByte()) {
                                val strings = parseOSCStrings(buffer, typeTagOffset + 1, pkt.length)
                                if (strings.size >= 3) {
                                    val device = MixerDevice(
                                        ipAddress = srcIP ?: "",
                                        name = strings[0],
                                        model = strings[1],
                                        firmwareVersion = strings[2]
                                    )
                                    foundDevices.add(device)
                                }
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        break
                    }
                }
                
                socket.close()
                result = foundDevices
                
            } catch (e: Exception) {
                result = emptyList()
            } finally {
                latch.countDown()
            }
        }
        
        latch.await()
        return result
    }
    
    @JvmStatic
    fun queryChannels(device: MixerDevice, callback: QueryCallback) {
        sentMessages.clear()
        receivedMessages.clear()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = XR18RepositoryImpl(AppExecutors.scope)
                val queryUseCase = QueryChannelStatesUseCase(repository)
                val observeUseCase = ObserveMixerStateUseCase(repository)
                
                viewModel = Xr18ViewModel(
                    DiscoverMixersUseCase(repository),
                    queryUseCase,
                    observeUseCase
                )
                viewModel?.連線至混音器(device)
                
                // Wait for all responses to arrive
                delay(5000)
                
                val state = viewModel?.mixerState?.value
                Log.d(TAG, "queryChannels: got state with ${state?.channels?.size ?: 0} channels")
                
                if (state != null && state.channels.isNotEmpty()) {
                    val sb = StringBuilder()
                    state.channels.take(16).forEach { ch ->
                        val db = if (ch.fader > 0.5f) "+%.1f dB".format((ch.fader - 0.5f) * 40) else "%.1f dB".format((ch.fader - 0.5f) * 40)
                        sb.append("CH%02d Fader: %.0f%% (%s) Mute: %s\n".format(ch.channelNumber, ch.fader * 100, db, if (ch.muted) "ON" else "OFF"))
                    }
                    callback.onResult(sb.toString())
                } else {
                    callback.onResult(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Query failed: ${e.message}", e)
                callback.onResult(null)
            }
        }
    }
    
    @JvmStatic
    fun getDebugMessages(): String {
        val sb = StringBuilder()
        sb.append("=== SENT ===\n")
        sentMessages.takeLast(20).forEach { sb.append(it).append("\n") }
        sb.append("\n=== RECEIVED ===\n")
        receivedMessages.takeLast(20).forEach { sb.append(it).append("\n") }
        return sb.toString()
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
