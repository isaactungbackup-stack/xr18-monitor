package com.mixer.xr18.lib.data.osc

import com.illposed.osc.OSCMessage
import com.illposed.osc.messages.OSCResponse
import com.mixer.xr18.lib.domain.model.MixerDevice

/**
 * Parser for incoming OSC messages from XR18.
 */
object XR18MessageParser {

    /**
     * Parse a /xinfo response to extract device metadata.
     * Expected format: /xinfo <device-name> <model> <firmware-version>
     */
    fun parseXInfo(msg: OSCMessage): MixerDevice? {
        val addr = msg.address ?: return null
        if (addr != XR18Addresses.XINFO) return null

        val args = msg.arguments
        if (args.size < 3) return null

        val name    = args.getOrNull(0)?.toString() ?: "Unknown"
        val model   = args.getOrNull(1)?.toString() ?: "XR18"
        val firmware = args.getOrNull(2)?.toString() ?: "?"

        return MixerDevice(
            ipAddress  = "",  // Will be filled by the datagram source address
            name       = name,
            model      = model,
            firmwareVersion = firmware
        )
    }

    /**
     * Parse a /xinfo datagram to extract source IP.
     * The port is always 10024 for discovery responses.
     */
    fun extractSourceIp(datagram: com.illposed.osc.OSCMessage): String {
        // The actual IP is attached by the transport layer.
        // Placeholder — we return "" and let the caller fill it in.
        return ""
    }

    /**
     * Returns the channel number encoded in an OSC address like "/ch/02/mix/fader".
     * Returns null if the address doesn't match the expected pattern.
     */
    fun parseChannelNumber(address: String): Int? {
        val regex = Regex("^/ch/(\\d+)/")
        val match = regex.find(address) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /**
     * Returns the band number from an EQ address like "/ch/02/eq/1/g".
     */
    fun parseEqBand(address: String): Int? {
        val regex = Regex("^/ch/\\d+/eq/(\\d+)/g$")
        val match = regex.find(address) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /**
     * Extract the parameter name from an OSC address.
     * e.g. "/ch/02/mix/fader" -> "fader"
     */
    fun parseParam(address: String): String {
        return address.substringAfterLast("/")
    }

    /**
     * Extract the sub-address (first two path segments).
     * e.g. "/ch/02/mix/fader" -> "/ch/02/mix"
     */
    fun parseSubAddr(address: String): String {
        val parts = address.split("/").filter { it.isNotEmpty() }
        return if (parts.size >= 2) "/${parts[0]}/${parts[1]}" else address
    }

    /**
     * Get the float value from an OSCMessage, or null if not present / wrong type.
     */
    fun getFloat(msg: OSCMessage, index: Int = 0): Float? {
        return (msg.arguments.getOrNull(index) as? Number)?.toFloat()
    }

    /**
     * Get the int value from an OSCMessage, or null if not present / wrong type.
     */
    fun getInt(msg: OSCMessage, index: Int = 0): Int? {
        return (msg.arguments.getOrNull(index) as? Number)?.toInt()
    }
}
