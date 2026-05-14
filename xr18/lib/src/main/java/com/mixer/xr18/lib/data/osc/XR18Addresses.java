package com.mixer.xr18.lib.data.osc;

/**
 * OSC address patterns used by the XR18.
 */
public class XR18Addresses {
    // Discovery
    public static final String XINFO = "/xinfo";
    // Remote subscription (keep-alive)
    public static final String XREMOTE = "/xremote";

    // Channel mix params — ch = 1..16
    public static String chMixFader(int ch)   { return "/ch/" + pad(ch) + "/mix/fader"; }
    public static String chMixOn(int ch)       { return "/ch/" + pad(ch) + "/mix/on"; }
    public static String chMixPan(int ch)      { return "/ch/" + pad(ch) + "/mix/pan"; }

    // Preamp
    public static String headampGain(int ch)  { return "/headamp/" + pad(ch) + "/gain"; }

    // EQ
    public static String chEqOn(int ch)       { return "/ch/" + pad(ch) + "/eq/on"; }
    public static String chEqBandG(int ch, int band) { return "/ch/" + pad(ch) + "/eq/" + band + "/g"; }

    private static String pad(int ch) {
        return (ch < 10) ? ("0" + ch) : String.valueOf(ch);
    }
}