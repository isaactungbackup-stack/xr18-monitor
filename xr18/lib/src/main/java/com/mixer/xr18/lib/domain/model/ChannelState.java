package com.mixer.xr18.lib.domain.model;

/**
 * Complete state for one mixer input channel (CH01-CH16).
 */
public class ChannelState {
    public static final float PAN_LEFT = 0f;
    public static final float PAN_CENTER = 0.5f;
    public static final float PAN_RIGHT = 1f;

    public int channelNumber;         // 1-16
    public float fader;               // 0.0–1.0 (linear)
    public float faderDb;             // dB equivalent
    public boolean muted;
    public float pan;                 // 0.0 (L) – 0.5 (C) – 1.0 (R)
    public float preampGain;          // raw float 0.0-1.0
    public float preampGainDb;         // gain in dB (raw * 72 - 12, range -12 to +60)
    public boolean eqEnabled;
    public EqBands eqBands;

    public ChannelState(int channelNumber) {
        this.channelNumber = channelNumber;
        this.fader = 0.707f;
        this.faderDb = 0f;
        this.muted = false;
        this.pan = 0.5f;
        this.preampGain = 0f;
        this.preampGainDb = -12f;
        this.eqEnabled = false;
        this.eqBands = new EqBands();
    }

    public static float faderToDb(float linear) {
        if (linear <= 0f) return Float.NEGATIVE_INFINITY;
        return (float) (20.0 * Math.log10(linear) + 10.0);
    }

    public String faderPercent() {
        return String.format("%.1f%%", fader * 100f);
    }

    public static float gainToDb(float rawGain) {
        // XR18 headamp gain: raw 0.0-1.0 maps to -12 to +60 dB
        return rawGain * 72f - 12f;
    }

    public String preampGainDbString() {
        if (preampGainDb <= -90f) return "–∞ dB";
        return String.format("%+.1f dB", preampGainDb);
    }

    public String faderDbString() {
        if (faderDb <= -90f) return "–∞ dB";
        return String.format("%+.1f dB", faderDb);
    }

    public String panString() {
        return panToString(this.pan);
    }

    public static String panToString(float pan) {
        if (pan <= 0f) return "L100";
        if (pan < 0.25f) return "L" + (int) (100 * (1 - pan * 4));
        if (pan < 0.5f) return "L" + (int) (50 * (0.5f - pan) * 4);
        if (pan == 0.5f) return "C";
        if (pan < 0.75f) return "R" + (int) (50 * (pan - 0.5f) * 4);
        if (pan < 1f) return "R" + (int) (100 * (pan - 0.25f) * 4);
        return "R100";
    }

    public ChannelState withFader(float v) { this.fader = v; this.faderDb = faderToDb(v); return this; }
    public ChannelState withMuted(boolean v) { this.muted = v; return this; }
    public ChannelState withPan(float v) { this.pan = v; return this; }
    public ChannelState withPreampGain(float v) { this.preampGain = v; return this; }
    public ChannelState withEqEnabled(boolean v) { this.eqEnabled = v; return this; }
    public ChannelState withEqBands(EqBands v) { this.eqBands = v; return this; }
}