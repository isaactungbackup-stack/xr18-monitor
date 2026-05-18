package com.mixer.wing.app;

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

    private static final float[] DB_TICKS = {
        +10f,   // 100% → top
         0f,    // 90%
        -10f,   // 80%
        -20f,   // 70%
        -30f,   // 60%
        -40f,   // 50%
        -50f    // 40% → bottom
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

    private static final float LEFT_MARGIN_DP = 22f;
    private static final float TICK_SIZE_DP = 5f;
    private static final float TEXT_SIZE_DP = 9f;

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

    private float dbToY(float db, int viewHeight) {
        float level = (db - MIN_DB) / 100f;
        level = Math.max(0f, Math.min(1f, level));
        return viewHeight * (1f - level);
    }

    public void setMeterDb(float db) {
        float level = (db - MIN_DB) / 100f;
        setMeterLevel(level);
    }

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

        canvas.drawRect(leftMargin, 0, w, h, inactivePaint);

        int barHeight = (int) (h * meterLevel);
        int barTop = h - barHeight;

        barRect.set(leftMargin, barTop, w, h);
        canvas.drawRect(barRect, activePaint);

        if (peakLevel > 0.01f && System.currentTimeMillis() < peakHoldTime) {
            int peakY = (int) (h * (1f - peakLevel));
            canvas.drawRect(leftMargin, peakY, w, peakY + 2, peakPaint);
        }

        Paint strokePaint = new Paint();
        strokePaint.setColor(0xFF444444);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1f);
        canvas.drawRect(leftMargin, 0, w, h, strokePaint);

        drawDbScale(canvas, leftMargin, h);
    }

    private void drawDbScale(Canvas canvas, float barLeft, int h) {
        float tickEnd = barLeft;
        float tickStart = barLeft - TICK_SIZE_DP * density;

        for (float db : DB_TICKS) {
            float y = dbToY(db, h);

            if (db == 0f) {
                float lineTop = y - 1f;
                float lineBottom = y + 1f;
                Paint fill = new Paint();
                fill.setColor(0xFFFFFFFF);
                fill.setStyle(Paint.Style.FILL);
                canvas.drawRect(barLeft, lineTop, barLeft + 4f, lineBottom, fill);
            }

            canvas.drawLine(tickStart, y, tickEnd, y, tickPaint);

            String label;
            if (db == 0f) {
                label = "0";
            } else if (db > 0f) {
                label = "+" + (int) db;
            } else {
                label = String.valueOf((int) db);
            }

            Paint labelPaint = new Paint(textPaint);
            if (db >= 0f) {
                labelPaint.setColor(0xFFFFD700);
            } else {
                labelPaint.setColor(0xFFBBBBBB);
            }

            float textX = tickStart - density * 2f;
            float textY = y + (textPaint.getTextSize() * 0.35f);
            canvas.drawText(label, textX - textPaint.measureText(label), textY, labelPaint);
        }
    }
}