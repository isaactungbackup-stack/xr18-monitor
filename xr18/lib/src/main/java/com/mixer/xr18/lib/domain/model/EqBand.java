package com.mixer.xr18.lib.domain.model;

/**
 * Single EQ band parameter (frequency, gain, Q, type).
 * Values are in OSC normalized form (0.0–1.0).
 */
public class EqBand {
    public int type;    // 0=LCut, 1=LShv, 2=PEQ, 3=VEQ, 4=HShv, 5=HCut
    public float f;     // 0.0-1.0 → 20Hz-20kHz (logarithmic)
    public float g;     // 0.0-1.0 → -15dB-+15dB
    public float q;     // 0.0-1.0 → Q=10~0.3 (logarithmic)

    public EqBand() {
        this.type = 2; // default PEQ
        this.f = 0.5f;
        this.g = 0.5f;
        this.q = 0.5f;
    }

    public EqBand(int type, float f, float g, float q) {
        this.type = type;
        this.f = f;
        this.g = g;
        this.q = q;
    }

    /** Convert normalized frequency to Hz (logarithmic 20Hz–20kHz) */
    public float freqHz() {
        return (float)(20.0 * Math.pow(1000.0, f));
    }

    /** Convert normalized gain to dB (-15 to +15) */
    public float gainDb() {
        return (g * 30.0f) - 15.0f;
    }

    /** Convert normalized Q to quality factor (10.0–0.3) */
    public float qValue() {
        return (float)(10.0 * Math.pow(0.03, q));
    }

    /** Make a copy */
    public EqBand copy() {
        return new EqBand(type, f, g, q);
    }
}