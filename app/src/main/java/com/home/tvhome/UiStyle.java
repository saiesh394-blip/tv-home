package com.home.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Look &amp; Style settings that apply across the whole app: your own font, a text colour,
 * text size, animation speed and interface sounds. Text views created through {@link FontText}
 * pick these up by themselves.
 */
final class UiStyle {

    static Typeface font;            // null: the TV's own font
    static int fontColour;           // 0: white as designed
    static float textScale = 1f;
    static float animScale = 1f;
    static int sounds;               // 0 off, 1 soft, 2 click
    static int outline = 2;          // focus outline in dp: 0, 1, 2 or 4
    static int panels;               // 0 tinted glass, 1 dark, 2 black (AMOLED)

    static final String[] TEXT_SIZES = {"Small", "Normal", "Large", "Extra Large"};
    static final float[] TEXT_SCALES = {0.9f, 1f, 1.1f, 1.2f};
    static final String[] ANIM_SPEEDS = {"Fast", "Normal", "Relaxed"};
    static final float[] ANIM_SCALES = {0.65f, 1f, 1.4f};
    static final String[] SOUNDS = {"Off", "Soft", "Click"};
    static final String[] OUTLINES = {"Off", "Thin", "Normal", "Thick"};
    static final int[] OUTLINE_DP = {0, 1, 2, 4};
    static final String[] PANELS = {"Tinted Glass", "Dark", "Black (AMOLED)"};
    static final String[] FONT_COLOURS = {"White", "Warm White", "Soft Grey", "Gold", "Sky Blue", "Mint", "Rose"};
    static final int[] FONT_COLOUR_VALUES = {0, 0xFFFFF4E0, 0xFFD5D8DE, 0xFFFFD27A, 0xFF9FD3FF, 0xFFA8F0D0, 0xFFFFB3C7};

    private UiStyle() { }

    static File fontFile(Context c) { return new File(c.getFilesDir(), "font.ttf"); }

    static void load(Context c, SharedPreferences p) {
        font = p.getBoolean("custom_font", false) ? WallArt.loadFont(fontFile(c)) : null;
        fontColour = FONT_COLOUR_VALUES[clamp(p.getInt("font_colour", 0), FONT_COLOUR_VALUES.length)];
        textScale = TEXT_SCALES[clamp(p.getInt("text_size", 1), TEXT_SCALES.length)];
        animScale = ANIM_SCALES[clamp(p.getInt("anim_speed", 1), ANIM_SCALES.length)];
        sounds = clamp(p.getInt("ui_sounds", 0), SOUNDS.length);
        outline = OUTLINE_DP[clamp(p.getInt("focus_outline", 2), OUTLINE_DP.length)];
        panels = clamp(p.getInt("panels", 0), PANELS.length);
    }

    private static int clamp(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }

    static long ms(long d) { return Math.max(0, Math.round(d * animScale)); }

    /** Swaps white text for your chosen text colour, keeping its transparency. Dark text is left alone. */
    static int colour(int c) {
        if (fontColour == 0) return c;
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        if (r < 0xE0 || g < 0xE0 || b < 0xE0) return c;
        return (c & 0xFF000000) | (fontColour & 0x00FFFFFF);
    }

    static Typeface face(Typeface tf) {
        if (font == null) return tf;
        int style = tf == null ? Typeface.NORMAL : tf.getStyle();
        if (tf != null && tf.isBold()) style = Typeface.BOLD;
        return Typeface.create(font, style);
    }

    // ---------- Text views that follow these settings ----------

    static class FontText extends TextView {
        FontText(Context c) { super(c); super.setTypeface(face(getTypeface())); setTextColor(getCurrentTextColor()); }
        FontText(Context c, AttributeSet a) { super(c, a); }
        @Override public void setTypeface(Typeface tf) { super.setTypeface(face(tf)); }
        @Override public void setTypeface(Typeface tf, int style) { super.setTypeface(face(tf), style); }
        @Override public void setTextColor(int c) { super.setTextColor(colour(c)); }
        @Override public void setTextSize(int unit, float size) {
            super.setTextSize(unit, unit == TypedValue.COMPLEX_UNIT_SP ? size * textScale : size);
        }
    }

    static class FontClock extends TextClock {
        FontClock(Context c) { super(c); super.setTypeface(face(getTypeface())); }
        @Override public void setTypeface(Typeface tf) { super.setTypeface(face(tf)); }
        @Override public void setTypeface(Typeface tf, int style) { super.setTypeface(face(tf), style); }
        @Override public void setTextColor(int c) { super.setTextColor(colour(c)); }
        @Override public void setTextSize(int unit, float size) {
            super.setTextSize(unit, unit == TypedValue.COMPLEX_UNIT_SP ? size * textScale : size);
        }
    }

    // ---------- Interface sounds ----------

    private static SoundPool pool;
    private static int softId;
    private static long lastSound;

    /** A short, quiet sound as the selection moves. */
    static void tick(Context c) {
        if (sounds == 0) return;
        long now = System.currentTimeMillis();
        if (now - lastSound < 60) return;   // holding a direction stays quiet
        lastSound = now;
        if (sounds == 2) {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT, 0.5f);
            return;
        }
        if (pool == null) {
            pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            softId = pool.load(softTick(c).getPath(), 1);
            return;   // the first one is still loading
        }
        pool.play(softId, 0.35f, 0.35f, 1, 0, 1f);
    }

    /** A 30 ms soft "tock", made once and kept as a little WAV file. */
    private static File softTick(Context c) {
        File f = new File(c.getCacheDir(), "tick.wav");
        if (f.exists()) return f;
        int rate = 44100, n = rate * 30 / 1000;
        byte[] pcm = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / (double) rate;
            double env = Math.exp(-t * 180);
            double s = (Math.sin(2 * Math.PI * 1400 * t) * 0.6 + Math.sin(2 * Math.PI * 2600 * t) * 0.2) * env;
            short v = (short) (s * 12000);
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        try {
            FileOutputStream o = new FileOutputStream(f);
            o.write(wavHeader(pcm.length, rate));
            o.write(pcm);
            o.close();
        } catch (Exception ignored) { }
        return f;
    }

    private static byte[] wavHeader(int dataLen, int rate) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataLen).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate)
                .putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes()).putInt(dataLen);
        return b.array();
    }
}
