package com.mixer.xr18.lib.data.osc

/**
 * Parser for XR18 OSC messages.
 * No external dependencies.
 */
object XR18MessageParser {

    fun parseChannelNumber(address: String): Int? {
        val match = Regex("""/ch/(\d+)/""").find(address)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun parseEqBand(address: String): Int? {
        val match = Regex("""/eq/(\d+)/""").find(address)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun getFloat(msg: OSCMessage): Float? {
        return msg.args.firstOrNull() as? Float
    }

    fun getInt(msg: OSCMessage): Int? {
        return msg.args.firstOrNull() as? Int
    }
}
