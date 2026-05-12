package com.mixer.xr18.lib.data.osc

/**
 * Factory for building XR18 OSC messages.
 * No external dependencies.
 */
object XR18MessageFactory {

    fun buildXRemote(): String = "/xremote"

    fun buildChannelQuery(channel: Int): List<String> {
        val ch = channel.toString().padStart(2, '0')
        return listOf(
            "/ch/$ch/mix/fader",
            "/ch/$ch/mix/on",
            "/ch/$ch/mix/pan",
            "/ch/$ch/eq/on",
            "/ch/$ch/eq/1/g",
            "/ch/$ch/eq/2/g",
            "/ch/$ch/eq/3/g",
            "/ch/$ch/eq/4/g",
            "/headamp/$channel/gain"
        )
    }

    fun buildSubscribeRequest(param: String): String = "/$param/subscribe"
}
