package com.home.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** Hand-drawn artwork: the metallic Settings gear and its light silver tile. */
final class Art {

    private Art() { }

    /** A metallic gear on a transparent background. */
    static Bitmap gear(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        drawGear(new Canvas(b), size / 2f, size / 2f, size * 0.48f);
        return b;
    }

    /** The dock's Settings icon: a light silver rounded tile with the gear. */
    static Bitmap gearTile(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, 0, size, 0xFFF4F5F7, 0xFFBFC3CA, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, size, size, bg);
        drawGear(c, size / 2f, size / 2f, size * 0.36f);
        return b;
    }

    private static void drawGear(Canvas c, float cx, float cy, float r) {
        int teeth = 36;
        float outer = r, root = r * 0.9f;
        Path g = new Path();
        for (int i = 0; i < teeth; i++) {
            double a0 = Math.PI * 2 * i / teeth;
            double step = Math.PI * 2 / teeth;
            double[] angles = {a0, a0 + step * 0.18, a0 + step * 0.42, a0 + step * 0.58, a0 + step * 0.82};
            float[] radii = {root, outer, outer, root, root};
            for (int k = 0; k < angles.length; k++) {
                float x = cx + (float) Math.cos(angles[k]) * radii[k];
                float y = cy + (float) Math.sin(angles[k]) * radii[k];
                if (i == 0 && k == 0) g.moveTo(x, y); else g.lineTo(x, y);
            }
        }
        g.close();
        g.addCircle(cx, cy, r * 0.72f, Path.Direction.CCW);
        g.setFillType(Path.FillType.EVEN_ODD);

        Paint metal = new Paint(Paint.ANTI_ALIAS_FLAG);
        metal.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                new int[]{0xFF9EA2A9, 0xFF5D6168, 0xFF8A8E95}, null, Shader.TileMode.CLAMP));
        c.drawPath(g, metal);

        // Inner dark well
        Paint well = new Paint(Paint.ANTI_ALIAS_FLAG);
        well.setShader(new RadialGradient(cx, cy, r * 0.72f, 0xFF3A3D43, 0xFF55595F, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.66f, well);

        // Inner ring with fine teeth
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(r * 0.08f);
        ring.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFB5B9BF, 0xFF6E7279, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.52f, ring);

        // Three spokes and the hub
        Paint spoke = new Paint(Paint.ANTI_ALIAS_FLAG);
        spoke.setStrokeCap(Paint.Cap.ROUND);
        spoke.setStrokeWidth(r * 0.09f);
        spoke.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFC9CCD1, 0xFF7C8087, Shader.TileMode.CLAMP));
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(-90 + i * 120);
            c.drawLine(cx, cy, cx + (float) Math.cos(a) * r * 0.5f, cy + (float) Math.sin(a) * r * 0.5f, spoke);
        }
        Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
        hub.setShader(new RadialGradient(cx - r * 0.03f, cy - r * 0.03f, r * 0.12f, 0xFFD8DBDF, 0xFF7C8087, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.11f, hub);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFF3A3D43);
        c.drawCircle(cx, cy, r * 0.04f, dot);
    }
    // ---------- Profile picture ----------

    /** A round profile picture: a symbol, or your initial when symbol is empty, on a soft colour. */
    static Bitmap avatar(String symbol, String initial, int colour, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(colour, hsv);
        int top = android.graphics.Color.HSVToColor(new float[]{hsv[0], hsv[1] * 0.75f, Math.min(1f, hsv[2] * 1.05f)});
        int bottom = android.graphics.Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1] * 1.05f), hsv[2] * 0.7f});
        p.setShader(new LinearGradient(0, 0, 0, size, top, bottom, Shader.TileMode.CLAMP));
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setShader(null);
        boolean emoji = symbol != null && !symbol.isEmpty();
        String text = emoji ? symbol : (initial == null || initial.isEmpty() ? "?" : initial);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size * (emoji ? 0.5f : 0.46f));
        p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(text, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
        return b;
    }

    /** A plain colour dot (for choosing the picture's colour). */
    static Bitmap dot(int colour, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colour);
        new Canvas(b).drawCircle(size / 2f, size / 2f, size / 2f, p);
        return b;
    }

    // ---------- Weather icons ----------

    /** A small colour weather symbol for an Open-Meteo weather code. */
    static Bitmap weather(int code, boolean day, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float s = size;
        boolean clear = code == 0, partly = code == 1 || code == 2, fog = code == 45 || code == 48;
        boolean rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
        boolean snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
        boolean storm = code >= 95;
        if (clear) {
            if (day) sun(c, s * 0.5f, s * 0.5f, s * 0.2f, s); else moon(c, s * 0.5f, s * 0.5f, s * 0.3f);
            return b;
        }
        if (partly) {
            if (day) sun(c, s * 0.62f, s * 0.36f, s * 0.15f, s * 0.75f); else moon(c, s * 0.64f, s * 0.36f, s * 0.2f);
            cloud(c, s * 0.44f, s * 0.62f, s * 0.62f, 0xFFF2F4F7);
            return b;
        }
        if (fog) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFDDE1E7);
            p.setStrokeWidth(s * 0.07f);
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 4; i++) {
                float y = s * (0.3f + i * 0.14f), in = (i % 2) * s * 0.08f;
                c.drawLine(s * 0.18f + in, y, s * 0.82f - in, y, p);
            }
            return b;
        }
        cloud(c, s * 0.5f, s * (rain || snow || storm ? 0.42f : 0.5f), s * 0.78f,
                storm ? 0xFFB8BEC8 : 0xFFE9ECF1);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (rain) {
            p.setColor(0xFF5AC8FA);
            p.setStrokeWidth(s * 0.06f);
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 3; i++) {
                float x = s * (0.34f + i * 0.16f);
                c.drawLine(x, s * 0.7f, x - s * 0.05f, s * 0.86f, p);
            }
        } else if (snow) {
            p.setColor(0xFFFFFFFF);
            for (int i = 0; i < 3; i++) c.drawCircle(s * (0.34f + i * 0.16f), s * (0.76f + (i % 2) * 0.07f), s * 0.04f, p);
        } else if (storm) {
            p.setColor(0xFFFFD60A);
            Path bolt = new Path();
            bolt.moveTo(s * 0.54f, s * 0.6f);
            bolt.lineTo(s * 0.4f, s * 0.8f);
            bolt.lineTo(s * 0.5f, s * 0.8f);
            bolt.lineTo(s * 0.44f, s * 0.95f);
            bolt.lineTo(s * 0.62f, s * 0.72f);
            bolt.lineTo(s * 0.52f, s * 0.72f);
            bolt.close();
            c.drawPath(bolt, p);
        }
        return b;
    }

    private static void sun(Canvas c, float cx, float cy, float r, float span) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFCC33);
        p.setStrokeWidth(r * 0.32f);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            c.drawLine(cx + (float) Math.cos(a) * r * 1.45f, cy + (float) Math.sin(a) * r * 1.45f,
                    cx + (float) Math.cos(a) * r * 1.9f, cy + (float) Math.sin(a) * r * 1.9f, p);
        }
        p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f, 0xFFFFE27A, 0xFFFFB300, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.15f, p);
    }

    private static void moon(Canvas c, float cx, float cy, float r) {
        Bitmap layer = Bitmap.createBitmap(c.getWidth(), c.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFF5E6A8);
        lc.drawCircle(cx, cy, r, p);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        lc.drawCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, p);
        c.drawBitmap(layer, 0, 0, null);
        layer.recycle();
    }

    private static void cloud(Canvas c, float cx, float cy, float w, int colour) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colour);
        float h = w * 0.36f;
        c.drawRoundRect(new android.graphics.RectF(cx - w / 2f, cy - h * 0.1f, cx + w / 2f, cy + h * 0.9f), h / 2f, h / 2f, p);
        c.drawCircle(cx - w * 0.16f, cy, w * 0.22f, p);
        c.drawCircle(cx + w * 0.12f, cy - w * 0.08f, w * 0.27f, p);
    }
}
