package com.mixer.wing.lib.data.osc;

/**
 * OSC address patterns used by the WING mixer.
 * WING uses shorter paths than XR18: /ch/{n}/fdr instead of /ch/{nn}/mix/fader
 *
 * Key differences from XR18:
 * - No zero-padding: /ch/1/fdr (not /ch/01/mix/fader)
 * - WING OSC port: 10024
 * - Meter blob is little-endian (WING) vs big-endian (XR18)
 */
public class WingAddresses {

    // Discovery — WING uses /xinfo like XR18
    public static final String XINFO = "/xinfo";

    // Remote subscription — WING uses /S~ (capital S)
    public static final String SUBSCRIBE_ALL = "/S~";

    // Specific subscription
    public static String SUBSCRIBE_SPECIFIC = "/s~";

    // Meter batches — /meters/X (X = 0-9)
    // Batch 1: 40 values — 16 ch pre, aux pre L/R, fx1-4 pre L/R, bus1-6 pre, fxsend1-4, main post, mon
    public static final String METERS_1 = "/meters/1";

    // Channel fader: /ch/{n}/fdr (n = 1-48)
    public static String chFader(int ch)    { return "/ch/" + ch + "/fdr"; }

    // Channel mute: /ch/{n}/mute
    public static String chMute(int ch)     { return "/ch/" + ch + "/mute"; }

    // Channel pan: /ch/{n}/pan
    public static String chPan(int ch)      { return "/ch/" + ch + "/pan"; }

    // Preamp gain: /ch/{n}/pha
    public static String chPreamp(int ch)   { return "/ch/" + ch + "/pha"; }

    // EQ on: /ch/{n}/eq/on
    public static String chEqOn(int ch)     { return "/ch/" + ch + "/eq/on"; }

    // EQ band gain: /ch/{n}/eq/{band}/g
    public static String chEqBandGain(int ch, int band) { return "/ch/" + ch + "/eq/" + band + "/g"; }

    // Main LR meter
    public static final String LR_METER = "/lr/meter";
}