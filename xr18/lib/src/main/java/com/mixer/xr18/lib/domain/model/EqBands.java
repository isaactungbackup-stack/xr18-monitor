package com.mixer.xr18.lib.domain.model;

/**
 * EQ band gain values for a single channel.
 */
public class EqBands {
    public float band1 = 0f;
    public float band2 = 0f;
    public float band3 = 0f;
    public float band4 = 0f;

    public EqBands() {}

    public EqBands(float band1, float band2, float band3, float band4) {
        this.band1 = band1;
        this.band2 = band2;
        this.band3 = band3;
        this.band4 = band4;
    }

    public EqBands withBand1(float v) { this.band1 = v; return this; }
    public EqBands withBand2(float v) { this.band2 = v; return this; }
    public EqBands withBand3(float v) { this.band3 = v; return this; }
    public EqBands withBand4(float v) { this.band4 = v; return this; }

    public EqBands copy() {
        return new EqBands(band1, band2, band3, band4);
    }
}