package com.mixer.xr18.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Vertical meter bar that fills from bottom to top.
 * dB scale markings on the LEFT side following professional mixer style:
 * +10 (top), 0 (center with white line), -10, -20, -30, -40, -50 (bottom)
 *
 * dB-to-linear mapping: level = (db + 90) / 120
 * Maps: -90dB → 0% (bottom), 0dB → 75% height, +10dB → 100% (top)
 * This matches real VU meter behavior where 0dB sits at ~3/4 of full scale.
 */
public class MeterBarView extends View {
    private static final int COLOR_ACTIVE = 0xFF4CAF50;   // Green
    private static final int COLOR_INACTIVE = 0xFF2A2A2A; // Dark gray
    private static final int COLOR_PEAK = 0xFFFF8800;     // Orange

    private static final float MIN_DB = -90f;   // 0% bar height
    private static final float MAX_DB = +10f;   // 100% bar height

    private static final float TOP_DB = +10f;
    private static final float BOTTOM_DB = -90f;

    // Tick marks at key dB values — positions match meter fill mapping:
    // Fill formula: level = (db + 90) / 100 → 0dB=75%, +10dB=100%, -90dB=0%
    // Y formula: y = h * (1 - level) → all ticks fit within [0, h] for display range -50 to +10
    private static final float[] DB_TICKS = {
        +10f,   // 100% → top
         0f,    // 90%
        -10f,   // 80%
        -20f,   // 70%
        -30f,   // 60%
        -40f,   // 50%
        -50f    // 40% → bottom (within view, no clip)
    };

    private float meterLevel = 0f; // 0.0 to 1.0
    private final Paint activePaint = new Paint();
    private final Paint inactivePaint = new Paint();
    private final Paint tickPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint zeroLinePaint = new Paint();
    private final Paint peakPaint = new Paint();
    private final RectF barRect = new RectF();
    private float peakLevel = 0f;
    private long peakHoldTime = 0;
    private static final long PEAK_HOLD_MS = 1500;

    // Layout constants (dp)
    private static final float LEFT_MARGIN_DP = 22f;   // space for scale labels
    private static final float TICK_SIZE_DP = 5f;      // tick line length
    private static final float TEXT_SIZE_DP = 9f;      // label text size

    private float density = 1f;

    public MeterBarView(Context context) {
        super(context);
        init();
    }

    public MeterBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MeterBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getContext().getResources().getDisplayMetrics().density;

        activePaint.setColor(COLOR_ACTIVE);
        activePaint.setStyle(Paint.Style.FILL);

        inactivePaint.setColor(COLOR_INACTIVE);
        inactivePaint.setStyle(Paint.Style.FILL);

        tickPaint.setColor(0xFF888888);
        tickPaint.setStrokeWidth(1f);
        tickPaint.setAntiAlias(true);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(TEXT_SIZE_DP * density);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        zeroLinePaint.setColor(0xFFFFFFFF);
        zeroLinePaint.setStrokeWidth(2f);
        zeroLinePaint.setAntiAlias(true);

        peakPaint.setColor(COLOR_PEAK);
        peakPaint.setStyle(Paint.Style.FILL);

        setWillNotDraw(false);
    }

    /**
     * Convert dB value to Y position (from top of view).
     */
    private float dbToY(float db, int viewHeight) {
        // Use the same formula as meter fill: (db + 90) / 100
        // Range: -90dB → y=h (bottom), +10dB → y=0 (top)
        float level = (db - MIN_DB) / 100f;
        level = Math.max(0f, Math.min(1f, level));
        return viewHeight * (1f - level);
    }

    /**
     * Set meter level by dB value.
     * Uses same dB mapping as scale ticks so fill height aligns with labels.
     * Formula: level = (db + 90) / 100 → -90dB=0%, 0dB=75%, +10dB=100%
     */
    public void setMeterDb(float db) {
        float level = (db - MIN_DB) / 100f;
        setMeterLevel(level);
    }

    /** Set meter level 0.0-1.0 directly */
    public void setMeterLevel(float level) {
        float clamped = Math.max(0f, Math.min(1f, level));
        if (clamped > peakLevel || System.currentTimeMillis() > peakHoldTime) {
            peakLevel = clamped;
            peakHoldTime = System.currentTimeMillis() + PEAK_HOLD_MS;
        }
        this.meterLevel = clamped;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float leftMargin = LEFT_MARGIN_DP * density;
        float barWidth = w - leftMargin;

        // Background (inactive) - full bar area
        canvas.drawRect(leftMargin, 0, w, h, inactivePaint);

        // Active portion from bottom (fills upward)
        int barHeight = (int) (h * meterLevel);
        int barTop = h - barHeight;

        barRect.set(leftMargin, barTop, w, h);
        canvas.drawRect(barRect, activePaint);

        // Peak hold indicator (orange tick at peak position)
        if (peakLevel > 0.01f && System.currentTimeMillis() < peakHoldTime) {
            int peakY = (int) (h * (1f - peakLevel));
            canvas.drawRect(leftMargin, peakY, w, peakY + 2, peakPaint);
        }

        // Draw bar outline
        Paint strokePaint = new Paint();
        strokePaint.setColor(0xFF444444);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1f);
        canvas.drawRect(leftMargin, 0, w, h, strokePaint);

        // Draw dB scale on the LEFT side
        drawDbScale(canvas, leftMargin, h);
    }

    private void drawDbScale(Canvas canvas, float barLeft, int h) {
        float tickEnd = barLeft;
        float tickStart = barLeft - TICK_SIZE_DP * density;

        for (float db : DB_TICKS) {
            float y = dbToY(db, h);

            if (db == 0f) {
                // 0 — white horizontal line spanning the bar area
                float lineTop = y - 1f;
                float lineBottom = y + 1f;
                Paint fill = new Paint();
                fill.setColor(0xFFFFFFFF);
                fill.setStyle(Paint.Style.FILL);
                canvas.drawRect(barLeft, lineTop, barLeft + 4f, lineBottom, fill);
            }

            // Tick mark
            canvas.drawLine(tickStart, y, tickEnd, y, tickPaint);

            // Label text
            String label;
            if (db == 0f) {
                label = "0";
            } else if (db > 0f) {
                label = "+" + (int) db;
            } else {
                label = String.valueOf((int) db);
            }

            // Text color: gold for 0 and positive, white for negative
            Paint labelPaint = new Paint(textPaint);
            if (db >= 0f) {
                labelPaint.setColor(0xFFFFD700); // Gold for 0 and +
            } else {
                labelPaint.setColor(0xFFBBBBBB); // Gray for negative
            }

            float textX = tickStart - density * 2f;
            // Vertically center text on the tick: baseline is below center,
            // so offset by ~1/3 of text size to center it properly
            float textY = y + (textPaint.getTextSize() * 0.35f);
            canvas.drawText(label, textX - textPaint.measureText(label), textY, labelPaint);
        }
    }
}