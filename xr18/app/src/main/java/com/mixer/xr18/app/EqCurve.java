package com.mixer.xr18.app;

import com.mixer.xr18.lib.domain.model.EqBand;

/**
 * Compute EQ curve Y values (in dB) given 4 EQ bands.
 * Used to draw the EQ overlay on the RTA spectrogram.
 *
 * Freq X axis: 0.0 → 20Hz, 0.25 → ~100Hz, 0.5 → 1kHz, 0.75 → ~6kHz, 1.0 → 20kHz
 * Gain Y axis: -15dB to +15dB
 */
public class EqCurve {

    /** Number of frequency samples across the display */
    public static final int SAMPLES = 200;

    /**
     * Compute the composite EQ curve gain (in dB) at each of SAMPLES freq points.
     * @param bands 4-element array [band1, band2, band3, band4]
     * @param outDb output array of SAMPLES floats (pre-allocated by caller)
     */
    public static void computeCurve(EqBand[] bands, float[] outDb) {
        for (int i = 0; i < SAMPLES; i++) {
            outDb[i] = 0f;
        }

        for (int b = 0; b < 4 && b < bands.length; b++) {
            EqBand band = bands[b];
            if (band == null) continue;
            float[] bandDb = computeBandCurve(band);
            for (int i = 0; i < SAMPLES; i++) {
                outDb[i] += bandDb[i];
            }
        }
    }

    /**
     * Compute the contribution of a single band.
     * For PEQ/VEQ: bell curve centered at f with height = gainDb, width controlled by Q.
     * For LShv/HShv: shelving curve.
     * For LCut/HCut: no visual curve (just the marker dot).
     */
    private static float[] computeBandCurve(EqBand band) {
        float[] result = new float[SAMPLES];
        float centerX = band.f; // 0-1 normalized freq
        float gainDb = band.gainDb();
        float q = band.qValue();

        // Compute bandwidth in octaves from Q: BW = 1/(2^(1/Q)-2^(-1/Q))
        // Approximate: BW ≈ fcenter/Q  (in octaves, simplified)
        double bwOctaves = 1.0 / q; // very rough

        // For each sample position x (0-1), compute contribution
        for (int i = 0; i < SAMPLES; i++) {
            float x = (float) i / (SAMPLES - 1); // 0 to 1

            switch (band.type) {
                case 0: // LCut — just a marker, no curve contribution
                    continue;
                case 5: // HCut — just a marker, no curve contribution
                    continue;
                case 1: // LShv — low shelf
                    applyShelf(result, i, x, centerX, gainDb, true);
                    break;
                case 4: // HShv — high shelf
                    applyShelf(result, i, x, centerX, gainDb, false);
                    break;
                case 2: // PEQ
                case 3: // VEQ
                default:
                    applyBell(result, i, x, centerX, gainDb, bwOctaves);
                    break;
            }
        }
        return result;
    }

    /**
     * Bell curve: Gaussian-like response centered at centerX.
     * gainDb is the peak height at centerX.
     */
    private static void applyBell(float[] result, int i, float x,
                                  float centerX, float gainDb, double bwOctaves) {
        // Convert x (0-1 log freq) to log-space distance from center
        // logFreq = log10(20) + x * (log10(20000) - log10(20))
        double logFreq1 = Math.log10(20.0);
        double logFreq2 = Math.log10(20000.0);
        double logRange = logFreq2 - logFreq1;

        double logX = logFreq1 + x * logRange;
        double logCenter = logFreq1 + centerX * logRange;
        double logDist = logX - logCenter;

        // Convert octave distance: each octave = log10(2) ≈ 0.301
        double octaveDist = logDist / Math.log10(2.0);

        // Gaussian with sigma = bwOctaves/3 (so at ±bwOctaves/2, response is down ~3dB if gain=6dB)
        double sigma = bwOctaves / 3.0;
        double gaussian = Math.exp(-(octaveDist * octaveDist) / (2.0 * sigma * sigma));

        // Scale so peak value ≈ gainDb
        result[i] += (float)(gainDb * gaussian);
    }

    /**
     * Shelving curve: gradual transition from gainDb to 0dB across ~2 octaves.
     * @param isLow true = low shelf (applies gain below center), false = high shelf
     */
    private static void applyShelf(float[] result, int i, float x,
                                   float centerX, float gainDb, boolean isLow) {
        // Transition width in octaves around centerX
        double transitionOctaves = 2.0;
        double logFreq1 = Math.log10(20.0);
        double logFreq2 = Math.log10(20000.0);
        double logRange = logFreq2 - logFreq1;

        double logX = logFreq1 + x * logRange;
        double logCenter = logFreq1 + centerX * logRange;
        double logDist = logX - logCenter;
        double octaveDist = logDist / Math.log10(2.0);

        double factor;
        if (isLow) {
            // Low shelf: full gain when x << centerX, 0 when x >> centerX
            factor = 0.5 * (1.0 - Math.tanh(3.0 * octaveDist / transitionOctaves));
        } else {
            // High shelf: 0 when x << centerX, full gain when x >> centerX
            factor = 0.5 * (1.0 + Math.tanh(3.0 * octaveDist / transitionOctaves));
        }

        result[i] += (float)(gainDb * factor);
    }

    /**
     * Convert normalized freq (0-1) to Hz.
     */
    public static float freqHzFromNorm(float norm) {
        return (float)(20.0 * Math.pow(1000.0, norm));
    }

    /**
     * Convert Hz to normalized freq (0-1).
     */
    public static float freqNormFromHz(float hz) {
        return (float)(Math.log(hz / 20.0) / Math.log(1000.0));
    }
}