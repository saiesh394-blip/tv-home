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
}
