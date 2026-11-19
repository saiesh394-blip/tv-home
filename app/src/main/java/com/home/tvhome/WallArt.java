package com.home.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;

import java.util.Random;

/**
 * Wallpapers drawn on the TV itself: gradients and solid colours, emoji patterns and fractal glass.
 * Made once at screen size, so they cost nothing while you use the home screen.
 */
final class WallArt {

    private WallArt() { }

    static final String[] STYLES = {"Diagonal", "Vertical", "Horizontal", "Radial", "Mesh", "Sweep"};

    /** Named colour sets: {name, colour 1, colour 2, colour 3, style index}. */
    static final String[][] PRESETS = {
            {"Nebula Glow", "#120A2E", "#5B2A86", "#E04F9F", "3"},
            {"Crimson Dunes", "#2A0610", "#A3123A", "#F06A3C", "0"},
            {"Ember & Jade", "#0B3D2E", "#1E7F5C", "#F2994A", "4"},
            {"Golden Grain", "#3E2A10", "#C9952C", "#F6E3A1", "1"},
            {"Prism Beam", "#0F2027", "#2C5364", "#7F7FD5", "5"},
            {"Midnight", "#05070D", "#14213D", "#1F3A68", "1"},
            {"Ocean", "#021B2E", "#035E7B", "#3BB4C1", "0"},
            {"Sunset", "#2B1055", "#D53369", "#FF9966", "0"},
            {"Forest", "#0B1F14", "#1D4D2B", "#5F9E50", "4"},
            {"Graphite", "#101113", "#2A2D33", "#4A4F57", "1"},
            {"Peach", "#3A1C1C", "#E8846B", "#FFD3B5", "3"},
            {"Mint", "#0C2A26", "#2BA38C", "#B8F2E6", "0"},
            {"Berry", "#1E0B24", "#7A1E5A", "#E0559A", "4"},
            {"Grape", "#140A2A", "#4B2C91", "#9F7AEA", "0"},
    };

    /** Emoji wallpapers: {name, emoji, background 1, background 2}. */
    static final String[][] EMOJI = {
            {"Sparkle", "✨", "#1B1035", "#3D2370"},
            {"Heart", "❤️", "#3A0B1A", "#8E1B3E"},
            {"Rocket", "🚀", "#0A1330", "#1F3C88"},
            {"Moon", "🌙", "#070B1E", "#1C2552"},
            {"Rainbow", "🌈", "#0E2A47", "#2F80ED"},
            {"Sunshine", "☀️", "#4A2A00", "#F2A900"},
            {"Blossom", "🌸", "#3A1430", "#C4568E"},
            {"Butterfly", "🦋", "#0B2A3A", "#2B8FB8"},
            {"Pizza", "🍕", "#3A1600", "#C2551A"},
            {"Candy", "🍬", "#3A0E33", "#D14D9C"},
            {"Panda", "🐼", "#122218", "#3E7A4F"},
            {"Kitty", "🐱", "#2E1F10", "#B07A3C"},
            {"Pup", "🐶", "#2B1D12", "#9B6A3A"},
            {"Bunny", "🐰", "#2E2238", "#9D86C2"},
            {"Fox", "🦊", "#331300", "#D9661F"},
            {"Lion", "🦁", "#3A2600", "#D4A017"},
            {"Tiger", "🐯", "#2E1600", "#E07A10"},
            {"Bear", "🐻", "#24160C", "#7A4B2A"},
            {"Koala", "🐨", "#1C2026", "#6B7787"},
            {"Monkey", "🐵", "#2A1A0E", "#8A5A32"},
            {"Penguin", "🐧", "#081A2E", "#3F7FBF"},
            {"Owl", "🦉", "#1E140A", "#6E5232"},
            {"Frog", "🐸", "#0E2A10", "#3FA34D"},
            {"Turtle", "🐢", "#0A2A22", "#2F9E80"},
            {"Dolphin", "🐬", "#04203A", "#1E88C8"},
            {"Octopus", "🐙", "#2A0A24", "#B03A7E"},
            {"Bee", "🐝", "#2E2300", "#E0B21B"},
            {"Dino", "🦖", "#0F2412", "#4C9A3B"},
            {"Unicorn", "🦄", "#2A1440", "#C77DDB"},
            {"Berry", "🍓", "#33060E", "#C2203A"},
    };

    static int parse(String hex, int fallback) {
        try { return Color.parseColor(hex); } catch (Exception e) { return fallback; }
    }

    // ---------- Gradients ----------

    static Bitmap gradient(int w, int h, int[] colours, int style) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (colours.length == 1) { c.drawColor(colours[0]); return b; }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        switch (style) {
            case 1: p.setShader(new LinearGradient(0, 0, 0, h, colours, null, Shader.TileMode.CLAMP)); break;
            case 2: p.setShader(new LinearGradient(0, 0, w, 0, colours, null, Shader.TileMode.CLAMP)); break;
            case 3: {
                int[] rev = reversed(colours);
                p.setShader(new RadialGradient(w * 0.5f, h * 0.45f, Math.max(w, h) * 0.75f, rev, null, Shader.TileMode.CLAMP));
                break;
            }
            case 4: mesh(c, w, h, colours); return b;
            case 5: {
                int[] loop = new int[colours.length + 1];
                System.arraycopy(colours, 0, loop, 0, colours.length);
                loop[colours.length] = colours[0];
                p.setShader(new SweepGradient(w * 0.5f, h * 0.5f, loop, null));
                c.drawRect(0, 0, w, h, p);
                // soften the sweep so it reads as light, not a pie chart
                Paint v = new Paint(Paint.DITHER_FLAG);
                v.setShader(new RadialGradient(w * 0.5f, h * 0.5f, Math.max(w, h) * 0.7f,
                        new int[]{0x00000000, 0x99000000}, null, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, v);
                return b;
            }
            default: p.setShader(new LinearGradient(0, 0, w, h, colours, null, Shader.TileMode.CLAMP));
        }
        c.drawRect(0, 0, w, h, p);
        return b;
    }

    /** Soft blobs of each colour over the darkest one, like a mesh gradient. */
    private static void mesh(Canvas c, int w, int h, int[] colours) {
        c.drawColor(colours[0]);
        float[][] spots = {{0.15f, 0.2f}, {0.85f, 0.3f}, {0.5f, 0.95f}, {0.2f, 0.85f}};
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        for (int i = 1; i < colours.length + 2; i++) {
            int col = colours[i % colours.length];
            float[] s = spots[(i - 1) % spots.length];
            float r = Math.max(w, h) * 0.6f;
            p.setShader(new RadialGradient(s[0] * w, s[1] * h, r,
                    new int[]{(col & 0x00FFFFFF) | 0xCC000000, col & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
    }

    private static int[] reversed(int[] a) {
        int[] r = new int[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[a.length - 1 - i];
        return r;
    }

    // ---------- Emoji ----------

    /** A calm, staggered pattern of one emoji over a soft gradient. Size 0 small, 1 medium, 2 large. */
    static Bitmap emoji(int w, int h, String emoji, int bg1, int bg2, int size) {
        Bitmap b = gradient(w, h, new int[]{bg1, bg2}, 0);
        Canvas c = new Canvas(b);
        float cell = h * (size == 0 ? 0.12f : size == 2 ? 0.26f : 0.18f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(cell * 0.5f);
        Paint.FontMetrics fm = p.getFontMetrics();
        float base = -(fm.ascent + fm.descent) / 2f;
        Random rnd = new Random(emoji.hashCode());
        int row = 0;
        for (float y = cell * 0.5f; y < h + cell; y += cell * 0.9f, row++) {
            float off = (row % 2) * cell * 0.5f;
            for (float x = -cell * 0.5f + off; x < w + cell; x += cell) {
                c.save();
                c.translate(x, y);
                c.rotate(rnd.nextFloat() * 30f - 15f);
                p.setAlpha(150 + rnd.nextInt(90));
                c.drawText(emoji, 0, base, p);
                c.restore();
            }
        }
        // a gentle dark wash at the bottom-left keeps the greeting and dock easy to read
        Paint shade = new Paint(Paint.DITHER_FLAG);
        shade.setShader(new LinearGradient(0, 0, w * 0.6f, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, shade);
        return b;
    }

    // ---------- Fractal glass ----------

    /** Colour light seen through reeded glass: vertical ribs, each bending (and mirroring) a slice of the light behind. */
    static Bitmap fractalGlass(int w, int h, int[] colours, int ribs) {
        int[] cols = colours.length >= 3 ? colours : new int[]{colours[0], colours[colours.length - 1], colours[0]};
        Bitmap base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas bc = new Canvas(base);
        bc.drawColor(blend(cols[0], 0xFF05060A, 0.6f));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new RadialGradient(w * 0.3f, h * 0.62f, h * 0.55f,
                new int[]{cols[1], alpha(cols[1], 0x55), 0}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        bc.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.68f, h * 0.35f, h * 0.32f,
                new int[]{cols[2], alpha(cols[2], 0x88), 0}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        bc.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(w * 0.2f, h, w * 0.8f, 0,
                new int[]{0, alpha(cols[2], 0xAA), 0x88FFFFFF, alpha(cols[1], 0xAA), 0},
                new float[]{0.35f, 0.47f, 0.5f, 0.53f, 0.65f}, Shader.TileMode.CLAMP));
        bc.drawRect(0, 0, w, h, p);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        int n = Math.max(6, ribs);
        float rib = w / (float) n;
        Paint hi = new Paint(Paint.DITHER_FLAG);
        hi.setShader(new LinearGradient(0, 0, rib, 0,
                new int[]{0x55FFFFFF, 0x08FFFFFF, 0x00000000, 0x40000000}, new float[]{0f, 0.18f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        for (int i = 0; i < n; i++) {
            float x = i * rib;
            float sw = rib * 2.4f;
            float cx = x + rib * 0.5f + (float) Math.sin(i * 1.3) * rib * 0.6f;
            float sx = Math.min(Math.max(cx - sw / 2f, 0), w - sw);
            float sy = Math.min(Math.max((float) Math.sin(i * 0.7) * h * 0.05f, 0), h * 0.1f);
            c.save();
            c.clipRect(x, 0, x + rib + 1, h);
            c.translate(x + rib, 0);
            c.scale(-1, 1);
            c.drawBitmap(base, new Rect((int) sx, (int) sy, (int) (sx + sw), (int) (sy + h * 0.9f)),
                    new RectF(0, 0, rib + 1, h), bmp);
            c.restore();
            c.save();
            c.translate(x, 0);
            c.drawRect(0, 0, rib, h, hi);
            c.restore();
        }
        base.recycle();
        return out;
    }

    private static int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    private static int blend(int a, int b, float t) {
        int r = (int) (Color.red(a) * (1 - t) + Color.red(b) * t);
        int g = (int) (Color.green(a) * (1 - t) + Color.green(b) * t);
        int bl = (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t);
        return Color.rgb(r, g, bl);
    }

    // ---------- Fonts ----------

    static Typeface loadFont(java.io.File f) {
        try { return f.exists() ? Typeface.createFromFile(f) : null; } catch (Throwable t) { return null; }
    }
}
