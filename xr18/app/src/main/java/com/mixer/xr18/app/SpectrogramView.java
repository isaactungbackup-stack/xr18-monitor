package com.mixer.xr18.app;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import com.mixer.xr18.lib.domain.model.EqBand;
import com.mixer.xr18.lib.domain.model.EqBands;

import java.util.Arrays;

/**
 * Real-time spectrogram + RTA bar + EQ curve overlay.
 *
 * Layout:
 *   - Bottom area: scrolling 2D spectrogram (freq → X, time → Y)
 *   - Top strip: RTA bars, same freq axis, updated every frame
 *   - Overlay: EQ curve (white/cyan), computed from EqBands
 *
 * Spectrogram color: intensity → deep blue (#0000CC) → cyan → yellow → red (#FF0000)
 * RTA bars: green-to-red gradient per bar
 * EQ curve: white semi-transparent, lineWidth 2px
 */
public class SpectrogramView extends View {
    private static final String TAG = "SpectrogramView";

    // ── Spectrogram buffer ────────────────────────────────────────────
    // Width  = number of freq bins (fixed at 64)
    // Height = number of time rows (fixed at 200)
    private static final int FREQ_BINS = 64;
    private static final int TIME_ROWS = 200;

    // Rolling spectrogram bitmap (BGRA_8888 for direct pixel manipulation)
    private final Bitmap spectrogramBmp;
    private final int[] spectrogramPixels; // row-major: row=y, col=x

    // RTA: real-time amplitude per freq bin (float 0-1), updated each frame
    private final float[] rtaLevels = new float[FREQ_BINS];
    // RTA decay: exponential falloff each frame
    private static final float RTA_DECAY = 0.85f;
    // RTA peak hold: tracks max, decays slowly
    private final float[] rtaPeak = new float[FREQ_BINS];
    private static final float PEAK_DECAY = 0.995f;

    // EQ bands for curve overlay
    private EqBands eqBands = new EqBands();

    // Precomputed log-frequency → bin mapping
    private final float[] binFreqHz = new float[FREQ_BINS + 1];
    private final float[] binCenterHz = new float[FREQ_BINS];

    // Cached paints (reused every draw)
    private final Paint rtaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rtaPeakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eqCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint freqLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrogramPaint = new Paint();
    private final RectF spectrogramRect = new RectF();
    private final RectF rtaRect = new RectF();

    // Spectrogram rendering: we draw the bitmap via Canvas.drawBitmap
    // After each frame, we scroll the bitmap down by 1 row and push new row at top
    private int scrollOffset = 0; // tracks rolling time position

    // Frequency axis labels (Hz)
    private static final String[] FREQ_LABELS = {"20", "50", "100", "200", "500", "1k", "2k", "5k", "10k", "20k"};

    // RTA bar configuration
    private static final float RTA_HEIGHT_DP = 40f;

    public SpectrogramView(Context context) {
        this(context, null);
    }

    public SpectrogramView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpectrogramView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // Allocate bitmap and pixel array
        spectrogramBmp = Bitmap.createBitmap(FREQ_BINS, TIME_ROWS, Bitmap.Config.ARGB_8888);
        spectrogramPixels = new int[FREQ_BINS * TIME_ROWS];
        // Fill with black initially
        Arrays.fill(spectrogramPixels, 0xFF000000); // opaque black

        // Precompute bin frequency boundaries (log scale 20Hz–20kHz)
        double log20 = Math.log10(20.0);
        double logRange = Math.log10(20000.0) - log20;
        for (int i = 0; i <= FREQ_BINS; i++) {
            double t = (double) i / FREQ_BINS;
            binFreqHz[i] = (float) Math.pow(10.0, log20 + t * logRange);
        }
        for (int i = 0; i < FREQ_BINS; i++) {
            binCenterHz[i] = (binFreqHz[i] + binFreqHz[i + 1]) / 2f;
        }

        // Paints
        gridPaint.setColor(0xFF1A1A1A);
        gridPaint.setStrokeWidth(0.5f);
        gridPaint.setStyle(Paint.Style.STROKE);

        freqLabelPaint.setColor(0xFF666666);
        freqLabelPaint.setTextSize(18f);
        freqLabelPaint.setTypeface(Typeface.MONOSPACE);

        eqCurvePaint.setColor(0xCCFFFFFF);
        eqCurvePaint.setStrokeWidth(3f);
        eqCurvePaint.setStyle(Paint.Style.STROKE);
        eqCurvePaint.setAntiAlias(true);

        spectrogramPaint.setFilterBitmap(true);

        rtaPaint.setStyle(Paint.Style.FILL);
        rtaPeakPaint.setStyle(Paint.Style.FILL);
        rtaPeakPaint.setColor(0xFF444444);

        setWillNotDraw(false);
    }

    /**
     * Push new energy levels (per frequency bin, 0-1 linear) from current meter snapshot.
     * Called every ~100-200ms with fresh channel energy data.
     */
    public void pushEnergyLevels(float[] energy) {
        if (energy == null || energy.length < FREQ_BINS) return;

        int len = Math.min(energy.length, FREQ_BINS);
        for (int i = 0; i < len; i++) {
            float v = Math.max(0f, Math.min(1f, energy[i]));
            rtaLevels[i] = v;
            if (v > rtaPeak[i]) {
                rtaPeak[i] = v;
            }
        }
        // Decay peaks
        for (int i = 0; i < FREQ_BINS; i++) {
            rtaPeak[i] *= PEAK_DECAY;
        }
    }

    /**
     * Update the EQ bands (triggers re-computation of curve overlay).
     */
    public void setEqBands(EqBands bands) {
        this.eqBands = bands;
    }

    /**
     * Compute a simulated energy array from a single dB meter value.
     * Uses a pink-noise-like spectrum centered around mid frequencies.
     * In real usage, energy[] would come from RTA / spectrum analysis.
     */
    public static float[] energyFromMeterDb(float meterDb) {
        float[] energy = new float[FREQ_BINS];
        // Simulate spectrum: low freq ~ -18dB, mid ~ -6dB, high ~ -12dB (relative)
        // Map meterDb (-96 to +12) to a 0-1 range for overall scale
        float overall = (meterDb + 60f) / 72f; // rough 0-1 scale
        overall = Math.max(0f, Math.min(1f, overall));

        for (int i = 0; i < FREQ_BINS; i++) {
            // Pink noise shape: energy ~ 1/sqrt(f) falloff from low to high
            float fNorm = (float) i / (FREQ_BINS - 1);
            // rough pink noise: peaks around 200Hz-2kHz
            float shape;
            if (fNorm < 0.25f) {
                shape = 0.7f + fNorm * 1.2f;
            } else if (fNorm < 0.5f) {
                shape = 1.0f - (fNorm - 0.25f) * 0.8f;
            } else if (fNorm < 0.75f) {
                shape = 0.8f - (fNorm - 0.5f) * 0.4f;
            } else {
                shape = 0.7f - (fNorm - 0.75f) * 0.8f;
            }
            energy[i] = Math.max(0f, Math.min(1f, overall * shape * (float)(0.7f + Math.random() * 0.6f)));
        }
        return energy;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float rtaHeightPx = RTA_HEIGHT_DP * density;
        float spectroTop = rtaHeightPx + 4f; // small gap between RTA and spectrogram

        // ── 1. Draw RTA bars ──────────────────────────────────────────
        drawRTA(canvas, 0f, 0f, w, rtaHeightPx);

        // ── 2. Scroll spectrogram bitmap down by 1 row ─────────────
        scrollSpectrogramDown();

        // ── 3. Push new energy row into spectrogram at top ───────────
        pushEnergyToSpectrogram();

        // ── 4. Draw spectrogram bitmap ──────────────────────────────
        spectrogramRect.set(0f, spectroTop, w, h);
        canvas.drawBitmap(spectrogramBmp, null, spectrogramRect, spectrogramPaint);

        // ── 5. Draw EQ curve overlay ────────────────────────────────
        drawEQCurve(canvas, 0f, spectroTop, w, h - spectroTop);

        // ── 6. Draw frequency grid lines ────────────────────────────
        drawFreqGrid(canvas, 0f, spectroTop, w, h - spectroTop);

        // Commit spectrogram pixels before drawing
        commitPixels();

        // Schedule next frame
        postInvalidateOnAnimation();
    }

    private void scrollSpectrogramDown() {
        // Copy each row down by 1 (from bottom to top to avoid overwrite)
        for (int y = TIME_ROWS - 1; y >= 1; y--) {
            System.arraycopy(spectrogramPixels, (y - 1) * FREQ_BINS,
                             spectrogramPixels, y * FREQ_BINS, FREQ_BINS);
        }
        // Row 0 will be filled by pushEnergyToSpectrogram
    }

    private void pushEnergyToSpectrogram() {
        // Use current rtaLevels as the new top row
        int rowOffset = 0;
        for (int x = 0; x < FREQ_BINS; x++) {
            float level = x < FREQ_BINS ? rtaLevels[x] : 0f;
            spectrogramPixels[rowOffset + x] = energyToPixel(level);
        }
    }

    /**
     * Map 0-1 energy level to a BGRA pixel color.
     * Color map: 0.0 → deep blue (#0000AA)
     *            0.25 → cyan (#00CCCC)
     *            0.5 → yellow (#CCCC00)
     *            0.75 → orange (#FF8800)
     *            1.0 → red (#FF0000)
     */
    private int energyToPixel(float energy) {
        float e = Math.max(0f, Math.min(1f, energy));

        int r, g, b;
        if (e < 0.25f) {
            float t = e / 0.25f;
            r = 0;
            g = (int)(0xCC * t);
            b = (int)(0xAA + (0xCC - 0xAA) * t);
        } else if (e < 0.5f) {
            float t = (e - 0.25f) / 0.25f;
            r = (int)(0xCC * t);
            g = 0xCC;
            b = (int)(0xCC * (1f - t));
        } else if (e < 0.75f) {
            float t = (e - 0.5f) / 0.25f;
            r = 0xCC + (int)((0xFF - 0xCC) * t);
            g = (int)(0xCC * (1f - t * 0.5f));
            b = 0;
        } else {
            float t = (e - 0.75f) / 0.25f;
            r = 0xFF;
            g = (int)(0x66 * (1f - t));
            b = 0;
        }

        // BGRA format for ARGB_8888
        return (0xFF << 24) | (b << 16) | (g << 8) | r;
    }

    private void drawRTA(Canvas canvas, float x, float y, float w, float h) {
        if (w <= 0 || h <= 0) return;

        float barW = w / FREQ_BINS;
        float gap = 1f;

        // Background
        rtaPaint.setColor(0xFF0A0A14);
        canvas.drawRect(x, y, x + w, y + h, rtaPaint);

        for (int i = 0; i < FREQ_BINS; i++) {
            float level = rtaLevels[i];
            float peak = rtaPeak[i];
            float barX = x + i * barW;

            // Color: green at bottom → yellow → red at top based on level
            int color;
            if (level < 0.4f) {
                float t = level / 0.4f;
                // Boost green at low levels so bars aren't near-black
                int gr = (int)(0x33 + 0xCC * t);
                int rd = (int)(0x22 * t);
                color = (0xFF << 24) | (0x00 << 16) | (gr << 8) | rd;
            } else if (level < 0.7f) {
                float t = (level - 0.4f) / 0.3f;
                int rd = 0x44 + (int)(0xBB * t);
                int gn = 0xAA - (int)(0x55 * t);
                color = (0xFF << 24) | (0x00 << 16) | (gn << 8) | rd;
            } else {
                float t = (level - 0.7f) / 0.3f;
                int rd = 0xFF;
                int gn = (int)(0x55 * (1f - t));
                color = (0xFF << 24) | (0x00 << 16) | (gn << 8) | rd;
            }

            // Draw bar from bottom
            float barH = level * h;
            rtaPaint.setColor(color);
            canvas.drawRect(barX + gap / 2, y + h - barH, barX + barW - gap / 2, y + h, rtaPaint);

            // Peak hold indicator (bright white line at peak)
            if (peak > 0.01f) {
                rtaPeakPaint.setColor(0xCCFFFFFF);
                float peakY = y + h - peak * h;
                canvas.drawRect(barX + gap / 2, peakY - 1f, barX + barW - gap / 2, peakY + 1f, rtaPeakPaint);
            }
        }

        // Frequency labels (at key points)
        drawRTALabels(canvas, x, y, w, h);
    }

    private void drawRTALabels(Canvas canvas, float x, float y, float w, float h) {
        // Draw octave frequency labels at bottom of RTA strip
        float[] labelFreqs = {20f, 50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f, 20000f};
        for (float freq : labelFreqs) {
            float binPos = freqToBinPos(freq, w);
            if (binPos < 0 || binPos > w) continue;
            freqLabelPaint.setColor(0xFF555555);
            canvas.drawText(String.valueOf((int)freq), x + binPos, y + h - 2f, freqLabelPaint);
        }
    }

    /**
     * Convert frequency (Hz) to X pixel position within [0, w].
     */
    private float freqToBinPos(float freq, float w) {
        double log20 = Math.log10(20.0);
        double log20000 = Math.log10(20000.0);
        double logFreq = Math.log10(Math.max(20f, Math.min(20000f, freq)));
        double t = (logFreq - log20) / (log20000 - log20);
        return (float)(t * w);
    }

    private void drawEQCurve(Canvas canvas, float x, float y, float w, float h) {
        if (eqBands == null) return;

        // Build EqBand array from EqBands
        EqBand[] bands = new EqBand[4];
        // Default: 4 PEQ bands (Lo, LM, HM, Hi) at common positions
        float[] defaultFreqs = {0.15f, 0.35f, 0.6f, 0.8f};
        float[] defaultQs   = {0.5f,  0.5f,  0.5f,  0.5f};

        for (int b = 0; b < 4; b++) {
            float gainNorm = 0.5f; // neutral
            if (b == 0) gainNorm = (eqBands.band1 + 15f) / 30f;
            else if (b == 1) gainNorm = (eqBands.band2 + 15f) / 30f;
            else if (b == 2) gainNorm = (eqBands.band3 + 15f) / 30f;
            else if (b == 3) gainNorm = (eqBands.band4 + 15f) / 30f;
            gainNorm = Math.max(0f, Math.min(1f, gainNorm));

            bands[b] = new EqBand(2, defaultFreqs[b], gainNorm, defaultQs[b]);
        }

        // Compute curve
        float[] curveDb = new float[256];
        EqCurve.computeCurve(bands, curveDb);

        // Build path
        Path eqPath = new Path();
        boolean first = true;
        for (int i = 0; i < 256; i++) {
            float px = x + (float) i / 255f * w;
            float db = curveDb[i];
            // Map dB (-15 to +15) to pixel Y within spectrogram area
            float py = y + h - ((db + 15f) / 30f) * h;
            py = Math.max(y, Math.min(y + h, py));
            if (first) {
                eqPath.moveTo(px, py);
                first = false;
            } else {
                eqPath.lineTo(px, py);
            }
        }

        // Draw glow (outer, thicker, semi-transparent)
        eqCurvePaint.setColor(0x4400FFFF);
        eqCurvePaint.setStrokeWidth(6f);
        canvas.drawPath(eqPath, eqCurvePaint);

        // Draw main curve (white)
        eqCurvePaint.setColor(0xCCFFFFFF);
        eqCurvePaint.setStrokeWidth(2.5f);
        canvas.drawPath(eqPath, eqCurvePaint);
    }

    private void drawFreqGrid(Canvas canvas, float x, float y, float w, float h) {
        gridPaint.setColor(0x22FFFFFF);
        gridPaint.setStrokeWidth(1f);

        // Draw vertical lines at octave frequencies
        float[] octaveFreqs = {20f, 50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f, 20000f};
        for (float freq : octaveFreqs) {
            float px = freqToBinPos(freq, w);
            if (px < 0 || px > w) continue;
            canvas.drawLine(x + px, y, x + px, y + h, gridPaint);
        }

        // Draw horizontal dB lines at -60, -40, -20, 0 dB
        // (only labels, actual grid lines optional)
        float[] dbLines = {-60f, -40f, -20f, 0f};
        gridPaint.setColor(0x15FFFFFF);
        for (float db : dbLines) {
            float py = y + h - ((db + 96f) / 96f) * h;
            if (py < y || py > y + h) continue;
            canvas.drawLine(x, py, x + w, py, gridPaint);
        }
    }

    /**
     * Commit the spectrogram pixels to the bitmap.
     * Call this before drawBitmap each frame.
     */
    private void commitPixels() {
        spectrogramBmp.setPixels(spectrogramPixels, 0, FREQ_BINS, 0, 0, FREQ_BINS, TIME_ROWS);
    }

    /**
     * Trigger a re-render of the spectrogram from current rtaLevels.
     * Call this from the UI thread after pushEnergyLevels.
     */
    public void invalidateView() {
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        commitPixels();
    }
}