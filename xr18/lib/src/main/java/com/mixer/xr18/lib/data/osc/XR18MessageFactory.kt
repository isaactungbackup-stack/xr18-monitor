package com.mixer.xr18.lib.data.osc

import com.illposed.osc.OSCMessage
import com.illposed.osc.messages.OSCRequest
import com.illposed.osc.messages.OSCResponse

/**
 * Thin wrappers around osc-java message builders.
 */
object XR18MessageFactory {

    fun buildXRemote(): OSCRequest {
        return OSCRequest("/xremote")
    }

    fun buildChannelQuery(channel: Int): List<OSCRequest> {
        return listOf(
            OSCRequest(XR18Addresses.chMixFader(channel)),
            OSCRequest(XR18Addresses.chMixOn(channel)),
            OSCRequest(XR18Addresses.chMixPan(channel)),
            OSCRequest(XR18Addresses.headampGain(channel)),
            OSCRequest(XR18Addresses.chEqOn(channel)),
            OSCRequest(XR18Addresses.chEqBandG(channel, 1)),
            OSCRequest(XR18Addresses.chEqBandG(channel, 2)),
            OSCRequest(XR18Addresses.chEqBandG(channel, 3)),
            OSCRequest(XR18Addresses.chEqBandG(channel, 4)),
        )
    }
}
