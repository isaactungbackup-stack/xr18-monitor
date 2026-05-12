package com.mixer.xr18.lib.data.osc

/**
 * OSC address patterns used by the XR18.
 */
object XR18Addresses {
    // Discovery
    const val XINFO = "/xinfo"

    // Remote subscription (keep-alive)
    const val XREMOTE = "/xremote"

    // Channel mix params — xx = 01..16
    fun chMixFader(ch: Int)   = "/ch/$ch/mix/fader"
    fun chMixOn(ch: Int)      = "/ch/$ch/mix/on"
    fun chMixPan(ch: Int)     = "/ch/$ch/mix/pan"

    // Preamp
    fun headampGain(ch: Int)  = "/headamp/$ch/gain"

    // EQ
    fun chEqOn(ch: Int)       = "/ch/$ch/eq/on"
    fun chEqBandG(ch: Int, band: Int) = "/ch/$ch/eq/$band/g"
}
