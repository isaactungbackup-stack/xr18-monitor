package com.mixer.xr18.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Vertical meter bar that fills from bottom to top.
 * Green portion represents current meter level (linear 0.0-1.0).
 * Dark background when no signal.
 */
public class MeterView extends View {
    private static final int COLOR_ACTIVE = 0xFF4CAF50;   // Green
    private static final int COLOR_INACTIVE = 0xFF1A1A1A; // Near-black

    private float meterLevel = 0f; // 0.0 to 1.0
    private final Paint activePaint = new Paint();
    private final Paint inactivePaint = new Paint();
    private final RectF barRect = new RectF();

    public MeterView(Context context) {
        super(context);
        init();
    }

    public MeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MeterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        activePaint.setColor(COLOR_ACTIVE);
        activePaint.setStyle(Paint.Style.FILL);

        inactivePaint.setColor(COLOR_INACTIVE);
        inactivePaint.setStyle(Paint.Style.FILL);

        setWillNotDraw(false);
    }

    /** Set meter level 0.0-1.0 */
    public void setMeterLevel(float level) {
        this.meterLevel = Math.max(0f, Math.min(1f, level));
        invalidate();
    }

    public float getMeterLevel() {
        return meterLevel;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Background (inactive / zero level)
        canvas.drawRect(0, 0, w, h, inactivePaint);

        // Active portion from bottom
        int barWidth = w;
        int barHeight = (int) (h * meterLevel);
        int top = h - barHeight;

        barRect.set(0, top, barWidth, h);
        canvas.drawRect(barRect, activePaint);
    }
}