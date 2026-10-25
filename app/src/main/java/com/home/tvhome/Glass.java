package com.home.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

/**
 * Frosted "liquid glass" backgrounds, made once from the wallpaper instead of blurring live,
 * so the TV's graphics chip draws them as cheaply as a plain colour.
 */
final class Glass {

    private Glass() { }

    /**
     * @param photo   the wallpaper as shown full-screen (centre-cropped)
     * @param screenW screen size in pixels
     * @param x,y,w,h where the panel sits on screen, in pixels
     * @param radii   corner radii in pixels (top-left, top-right, bottom-right, bottom-left)
     */
    static Drawable make(Resources res, Bitmap photo, int screenW, int screenH,
                         int x, int y, int w, int h, float[] radii) {
        if (photo == null || w <= 0 || h <= 0) return null;
        try {
            // Where the panel's area is in the photo (it's shown centre-cropped).
            float s = Math.max(screenW / (float) photo.getWidth(), screenH / (float) photo.getHeight());
            float ox = (screenW - photo.getWidth() * s) / 2f, oy = (screenH - photo.getHeight() * s) / 2f;
            int rx = clamp(Math.round((x - ox) / s), 0, photo.getWidth() - 1);
            int ry = clamp(Math.round((y - oy) / s), 0, photo.getHeight() - 1);
            int rw = clamp(Math.round(w / s), 1, photo.getWidth() - rx);
            int rh = clamp(Math.round(h / s), 1, photo.getHeight() - ry);

            // Blur: shrink a lot, soften, then scale back up smoothly.
            int sw = Math.max(4, w / 12), sh = Math.max(4, h / 12);
            Bitmap crop = Bitmap.createBitmap(photo, rx, ry, rw, rh);
            Bitmap small = Bitmap.createScaledBitmap(crop, sw, sh, true);
            if (crop != photo) crop.recycle();
            Bitmap soft = small.copy(Bitmap.Config.ARGB_8888, true);
            small.recycle();
            boxBlur(soft, 2);
            boxBlur(soft, 2);

            int ow = Math.max(8, w / 2), oh = Math.max(8, h / 2);
            Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            float[] r = new float[8];
            for (int i = 0; i < 4; i++) { r[i * 2] = radii[i] / 2f; r[i * 2 + 1] = radii[i] / 2f; }
            Path clip = new Path();
            clip.addRoundRect(new RectF(0, 0, ow, oh), r, Path.Direction.CW);
            c.clipPath(clip);
            Paint fp = new Paint(Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(soft, null, new RectF(0, 0, ow, oh), fp);
            soft.recycle();
            // Dark tint so text stays readable, a soft sheen at the top, and a hairline edge.
            c.drawColor(0xA6121620);
            Paint sheen = new Paint();
            sheen.setShader(new LinearGradient(0, 0, 0, oh * 0.45f, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, ow, oh * 0.45f, sheen);
            Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1.5f);
            edge.setColor(0x47FFFFFF);
            Path border = new Path();
            border.addRoundRect(new RectF(0.75f, 0.75f, ow - 0.75f, oh - 0.75f), r, Path.Direction.CW);
            c.drawPath(border, edge);
            BitmapDrawable d = new BitmapDrawable(res, out);
            d.setFilterBitmap(true);
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Blurs a small bitmap in place (used for the frosted screen behind Control Centre). */
    static void blur(Bitmap b) {
        boxBlur(b, 2);
        boxBlur(b, 2);
        boxBlur(b, 1);
    }

    /** A quick box blur on a small bitmap (horizontal then vertical). */
    private static void boxBlur(Bitmap b, int radius) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        pass(px, tmp, w, h, radius, true);
        pass(tmp, px, w, h, radius, false);
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int ra = 0, ga = 0, ba = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int p = horizontal ? src[line * w + j] : src[j * w + line];
                    ra += (p >> 16) & 0xFF; ga += (p >> 8) & 0xFF; ba += p & 0xFF; n++;
                }
                int idx = horizontal ? line * w + i : i * w + line;
                dst[idx] = 0xFF000000 | ((ra / n) << 16) | ((ga / n) << 8) | (ba / n);
            }
        }
    }
}
