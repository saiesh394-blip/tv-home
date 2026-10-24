package com.home.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Siri-style voice waves: a few glowing colour ribbons that rise and fall with your voice.
 * Cheap to draw (a handful of filled paths, no blur), and it only animates while listening.
 */
final class SiriWave extends View {

    private static final int[] COLOURS = {0xFF2F9BFF, 0xFFFF3D8B, 0xFF33E6C8, 0xFFB45CFF};
    private static final float[] SPEED = {0.11f, -0.08f, 0.14f, -0.12f};
    private static final float[] FREQ = {1.6f, 2.3f, 1.2f, 2.9f};
    private static final float[] WEIGHT = {1.0f, 0.75f, 0.6f, 0.45f};
    private static final int STEPS = 64;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] phase = new float[COLOURS.length];

    private boolean running;
    private float level = 0.08f;      // what we draw
    private float target = 0.08f;     // where the voice wants it
    private float idle = 0.08f;       // resting ripple

    SiriWave(Context c) {
        super(c);
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xCCFFFFFF);
        line.setStrokeWidth(c.getResources().getDisplayMetrics().density * 1.5f);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void start() {
        running = true;
        idle = 0.08f;
        level = 0f;
        target = idle;
        postInvalidateOnAnimation();
    }

    /** Settle down to a flat line, then stop drawing. */
    void stop() {
        running = false;
        target = 0f;
        idle = 0f;
        postInvalidateOnAnimation();
    }

    /** Thinking: a calm, steady ripple after you stop talking. */
    void thinking() {
        idle = 0.18f;
        target = idle;
    }

    /** RMS in dB from SpeechRecognizer, roughly -2 (quiet) to 10 (loud). */
    void setRms(float db) {
        float v = (db + 2f) / 12f;
        if (v < 0) v = 0;
        if (v > 1) v = 1;
        target = Math.max(idle, v);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // Rise quickly, fall gently, like a VU meter.
        level += (target - level) * (target > level ? 0.35f : 0.08f);
        target += (idle - target) * 0.06f;

        float mid = h * 0.55f;
        float maxAmp = h * 0.42f;
        for (int k = 0; k < COLOURS.length; k++) {
            phase[k] += SPEED[k] * (0.6f + level * 1.6f);
            float amp = maxAmp * level * WEIGHT[k];
            path.reset();
            path.moveTo(0, mid);
            for (int i = 0; i <= STEPS; i++) {
                float t = i / (float) STEPS;
                float x = t * w;
                path.lineTo(x, mid - amp * env(t) * (float) Math.abs(Math.sin(t * FREQ[k] * Math.PI * 2 + phase[k])));
            }
            for (int i = STEPS; i >= 0; i--) {
                float t = i / (float) STEPS;
                float x = t * w;
                path.lineTo(x, mid + amp * 0.6f * env(t) * (float) Math.abs(Math.sin(t * FREQ[k] * Math.PI * 2 + phase[k])));
            }
            path.close();
            fill.setColor(COLOURS[k]);
            fill.setAlpha(110);
            canvas.drawPath(path, fill);
        }

        // Thin white centre line that wobbles with the strongest ribbon.
        path.reset();
        float amp = maxAmp * level * 0.5f;
        for (int i = 0; i <= STEPS; i++) {
            float t = i / (float) STEPS;
            float y = mid - amp * env(t) * (float) Math.sin(t * 2.2f * Math.PI * 2 + phase[0] * 1.3f);
            if (i == 0) path.moveTo(0, y); else path.lineTo(t * w, y);
        }
        line.setAlpha((int) (90 + 120 * Math.min(1f, level * 2)));
        canvas.drawPath(path, line);

        if (running || level > 0.004f) postInvalidateOnAnimation();
    }

    /** Taper toward both edges so the waves bloom from the centre. */
    private static float env(float t) {
        float s = (float) Math.sin(t * Math.PI);
        return s * s * s;
    }
}
