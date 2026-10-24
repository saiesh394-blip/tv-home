package com.home.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;

import java.util.Random;

/**
 * TV Home screensaver: soft coloured light drifting slowly across a dark screen,
 * with a large, thin clock and the date. The clock moves now and then so nothing
 * stays in one place on the panel for long.
 */
public class TVDream extends DreamService {

    private LinearLayout clockBlock;
    private final Random rnd = new Random();
    private final Runnable drift = new Runnable() {
        @Override public void run() {
            if (clockBlock == null) return;
            View parent = (View) clockBlock.getParent();
            int maxX = Math.max(0, parent.getWidth() - clockBlock.getWidth() - dp(120));
            int maxY = Math.max(0, parent.getHeight() - clockBlock.getHeight() - dp(120));
            float x = rnd.nextInt(maxX + 1) - clockBlock.getLeft() + dp(60);
            float y = rnd.nextInt(maxY + 1) - clockBlock.getTop() + dp(60);
            clockBlock.animate().translationX(x).translationY(y).setDuration(6000)
                    .setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f)).start();
            clockBlock.postDelayed(this, 45000);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        FrameLayout root = new FrameLayout(this);
        root.addView(new AmbientView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        clockBlock = new LinearLayout(this);
        clockBlock.setOrientation(LinearLayout.VERTICAL);

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(110);
        time.setTextColor(0xF2FFFFFF);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        clockBlock.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(22);
        date.setTextColor(0xA6FFFFFF);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date.setPadding(dp(6), dp(4), 0, 0);
        clockBlock.addView(date);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        lp.setMargins(dp(80), 0, 0, dp(80));
        root.addView(clockBlock, lp);

        root.setAlpha(0f);
        setContentView(root);
        root.animate().alpha(1f).setDuration(2500).start();
        clockBlock.postDelayed(drift, 45000);
    }

    @Override
    public void onDetachedFromWindow() {
        if (clockBlock != null) clockBlock.removeCallbacks(drift);
        super.onDetachedFromWindow();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Three large, soft colour glows wandering slowly over a near-black background. */
    static class AmbientView extends View {
        private final long start = System.nanoTime();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Matrix m = new Matrix();
        private Shader[] glows;
        private final int[] colors = {0x8C1F5FD1, 0x7A7A2FBF, 0x6614A38B};
        private final float[][] path = {
                {0.050f, 0.037f, 0.0f, 1.3f},
                {0.031f, 0.047f, 2.1f, 0.4f},
                {0.041f, 0.029f, 4.2f, 2.6f}};

        AmbientView(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            float r = Math.max(w, h) * 0.55f;
            glows = new Shader[colors.length];
            for (int i = 0; i < colors.length; i++) {
                glows[i] = new RadialGradient(0, 0, r,
                        new int[]{colors[i], (((colors[i] >>> 24) / 3) << 24) | (colors[i] & 0xFFFFFF), Color.TRANSPARENT},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.drawColor(0xFF05070C);
            if (glows == null) return;
            float t = (System.nanoTime() - start) / 1e9f;
            for (int i = 0; i < glows.length; i++) {
                float[] p = path[i];
                float cx = w * (0.5f + 0.38f * (float) Math.sin(t * p[0] + p[2]));
                float cy = h * (0.5f + 0.34f * (float) Math.cos(t * p[1] + p[3]));
                m.setTranslate(cx, cy);
                glows[i].setLocalMatrix(m);
                paint.setShader(glows[i]);
                canvas.drawRect(0, 0, w, h, paint);
            }
            postInvalidateOnAnimation();
        }
    }
}
