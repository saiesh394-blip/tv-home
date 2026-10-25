package com.home.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * StandBy: a bedside display on black, in six styles (like iPhone StandBy).
 * Left / Right switch styles, any other button goes back. It shifts by a few pixels now and then
 * so nothing burns in, while staying centred.
 */
public class StandbyActivity extends Activity {

    static final String[] STYLES = {"Analogue + Calendar", "Modern + Weather", "Date + Weather",
            "Analogue", "Float", "Digital"};

    private static final int RED = 0xFFFF453A, ORANGE = 0xFFFF9F0A;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private FrameLayout root, stage;
    private TextView hint;
    private int style;
    private int nudge;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refreshTexts(stage);
            // Every 5 minutes, shift everything by a few pixels to protect the screen.
            if (++nudge % 5 == 0) {
                float d = getResources().getDisplayMetrics().density * 4;
                stage.setTranslationX(((nudge / 5) % 3 - 1) * d);
                stage.setTranslationY((((nudge / 5) + 1) % 3 - 1) * d);
            }
            long now = System.currentTimeMillis();
            ui.postDelayed(this, 60000 - now % 60000 + 50);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tvhome", MODE_PRIVATE);
        style = Math.max(0, Math.min(STYLES.length - 1, prefs.getInt("standby_style", 0)));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.2f;
        getWindow().setAttributes(lp);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        hint = new TextView(this);
        hint.setTextColor(0x80FFFFFF);
        hint.setTextSize(13);
        hint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.setMargins(0, 0, 0, dp(22));
        root.addView(hint, hl);
        setContentView(root);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        show(style, true);
    }

    @Override protected void onResume() { super.onResume(); ui.removeCallbacks(tick); tick.run(); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(tick); }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int n = STYLES.length;
            style = (style + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : n - 1)) % n;
            prefs.edit().putInt("standby_style", style).apply();
            show(style, true);
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && k != KeyEvent.KEYCODE_DPAD_LEFT && k != KeyEvent.KEYCODE_DPAD_RIGHT) finish();
        return true;
    }

    private void show(int s, boolean withHint) {
        stage.removeAllViews();
        View v;
        switch (s) {
            case 0: v = pair(new ClockFace(this, ClockFace.CLASSIC), calendar()); break;
            case 1: v = pair(new ClockFace(this, ClockFace.MODERN), weather()); break;
            case 2: v = pair(date(), weather()); break;
            case 3: v = new ClockFace(this, ClockFace.FULL); break;
            case 4: v = floatClock(); break;
            default: v = digital(); break;
        }
        stage.addView(v, new FrameLayout.LayoutParams(-1, -1));
        refreshTexts(stage);
        stage.setAlpha(0f);
        stage.animate().alpha(1f).setDuration(500).start();
        if (withHint) {
            hint.setText("◀   " + STYLES[s] + "   ▶");
            hint.animate().cancel();
            hint.setAlpha(1f);
            hint.animate().alpha(0f).setStartDelay(2200).setDuration(600).start();
        }
    }

    /** Two widgets side by side, each centred in its half. */
    private View pair(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(40), dp(40), dp(40), dp(40));
        row.addView(centred(left), new LinearLayout.LayoutParams(0, -1, 1f));
        row.addView(centred(right), new LinearLayout.LayoutParams(0, -1, 1f));
        return row;
    }

    private View centred(View v) {
        FrameLayout f = new FrameLayout(this);
        boolean square = v instanceof ClockFace;
        int size = Math.round(getResources().getDisplayMetrics().heightPixels * 0.66f);
        f.addView(v, new FrameLayout.LayoutParams(square ? size : -2, square ? size : -2, Gravity.CENTER));
        return f;
    }

    // ---------------------------------------------------------------- widgets

    private TextView text(float sp, int color, String family, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private View calendar() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("calendar");
        return box;
    }

    private View weather() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView city = text(20, 0xE6FFFFFF, "sans-serif-medium", false);
        city.setTag("city");
        box.addView(city);
        TextView temp = text(88, Color.WHITE, "sans-serif-medium", false);
        temp.setTag("temp");
        temp.setPadding(0, dp(4), 0, dp(10));
        box.addView(temp);
        TextView cond = text(20, 0xE6FFFFFF, "sans-serif-medium", false);
        cond.setTag("cond");
        box.addView(cond);
        return box;
    }

    private View date() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView top = text(28, Color.WHITE, "sans-serif-medium", false);
        top.setTag("datetop");
        box.addView(top);
        TextView day = text(120, Color.WHITE, "sans-serif-medium", true);
        day.setTag("dayno");
        box.addView(day);
        return box;
    }

    private View floatClock() {
        FrameLayout f = new FrameLayout(this);
        TextView t = text(190, 0xFF5FE3B6, "sans-serif-black", true);
        t.setTag("float");
        t.setLetterSpacing(-0.08f);
        f.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return f;
    }

    private View digital() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView d = text(15, RED, "sans-serif-medium", false);
        d.setTag("digdate");
        d.setLetterSpacing(0.06f);
        d.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(d, new LinearLayout.LayoutParams(-2, -2));
        TextView t = text(150, Color.WHITE, "sans-serif-medium", false);
        t.setTag("digtime");
        t.setPadding(0, dp(8), 0, 0);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(t, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout f = new FrameLayout(this);
        f.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return f;
    }

    /** Fills in anything time- or weather-dependent. */
    private void refreshTexts(View v) {
        Calendar now = Calendar.getInstance();
        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        Object tag = v.getTag();
        if ("calendar".equals(tag)) fillCalendar((LinearLayout) v, now);
        else if ("city".equals(tag)) ((TextView) v).setText(prefs.getString("weather_city", "Weather") + "  ➤");
        else if ("temp".equals(tag)) ((TextView) v).setText(weatherPart(0));
        else if ("cond".equals(tag)) ((TextView) v).setText(weatherPart(1));
        else if ("datetop".equals(tag)) {
            String wd = new SimpleDateFormat("EEE", Locale.ENGLISH).format(now.getTime());
            String mo = new SimpleDateFormat("MMM", Locale.ENGLISH).format(now.getTime());
            SpannableString sp = new SpannableString(wd + " " + mo);
            sp.setSpan(new ForegroundColorSpan(RED), 0, wd.length(), 0);
            sp.setSpan(new ForegroundColorSpan(0xB3FFFFFF), wd.length() + 1, sp.length(), 0);
            ((TextView) v).setText(sp);
        } else if ("dayno".equals(tag)) ((TextView) v).setText(String.valueOf(now.get(Calendar.DAY_OF_MONTH)));
        else if ("float".equals(tag)) {
            String hh = new SimpleDateFormat(h24 ? "HH" : "h", Locale.ENGLISH).format(now.getTime());
            String mm = new SimpleDateFormat("mm", Locale.ENGLISH).format(now.getTime());
            SpannableString sp = new SpannableString(hh + mm);
            sp.setSpan(new ForegroundColorSpan(0xFF2F8C6A), 0, hh.length(), 0);
            ((TextView) v).setText(sp);
        } else if ("digdate".equals(tag)) {
            ((TextView) v).setText(new SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH).format(now.getTime()).toUpperCase(Locale.ENGLISH));
        } else if ("digtime".equals(tag)) {
            ((TextView) v).setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.ENGLISH).format(now.getTime()));
        }
        if (v instanceof ClockFace) v.invalidate();
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) refreshTexts(g.getChildAt(i));
        }
    }

    /** "31°  Drizzle" → part 0 "31°", part 1 "☾  Drizzle". */
    private String weatherPart(int part) {
        String w = prefs.getString("weather_text", "");
        int i = w.indexOf('°');
        if (i < 0) return part == 0 ? "--°" : "Weather unavailable";
        if (part == 0) return w.substring(0, i + 1);
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String icon = h >= 19 || h < 6 ? "☾" : "☀";
        return icon + "  " + w.substring(i + 1).trim();
    }

    private void fillCalendar(LinearLayout box, Calendar now) {
        box.removeAllViews();
        TextView month = text(15, RED, "sans-serif-medium", true);
        month.setText(new SimpleDateFormat("MMMM", Locale.ENGLISH).format(now.getTime()).toUpperCase(Locale.ENGLISH));
        month.setLetterSpacing(0.05f);
        month.setPadding(dp(6), 0, 0, dp(12));
        box.addView(month);
        String[] wd = {"S", "M", "T", "W", "T", "F", "S"};
        LinearLayout head = new LinearLayout(this);
        for (String d : wd) head.addView(cell(d, 0x80FFFFFF, false));
        box.addView(head);
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        int offset = c.get(Calendar.DAY_OF_WEEK) - 1;
        int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        int today = now.get(Calendar.DAY_OF_MONTH);
        LinearLayout row = null;
        for (int i = 0; i < offset + days; i++) {
            if (i % 7 == 0) { row = new LinearLayout(this); box.addView(row); }
            if (i < offset) { row.addView(cell("", 0, false)); continue; }
            int d = i - offset + 1;
            row.addView(cell(String.valueOf(d), Color.WHITE, d == today));
        }
    }

    private TextView cell(String s, int color, boolean today) {
        TextView t = text(15, color, "sans-serif-medium", today);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        if (today) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(RED);
            t.setBackground(g);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.setMargins(dp(2), dp(1), dp(2), dp(1));
        t.setLayoutParams(lp);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---------------------------------------------------------------- analogue clock

    /** Analogue clock drawn by hand: CLASSIC (round, numbers), MODERN (bold ticks), FULL (whole screen). */
    static final class ClockFace extends View {
        static final int CLASSIC = 0, MODERN = 1, FULL = 2;
        private final int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler h = new Handler(Looper.getMainLooper());
        private final Runnable second = new Runnable() {
            @Override public void run() { invalidate(); h.postDelayed(this, 1000 - System.currentTimeMillis() % 1000); }
        };

        ClockFace(Context c, int kind) {
            super(c);
            this.kind = kind;
            txt.setColor(Color.WHITE);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); h.post(second); }
        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); h.removeCallbacks(second); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), ht = getHeight();
            float cx = w / 2f, cy = ht / 2f;
            float r = Math.min(w, ht) / 2f * 0.96f;
            if (kind == FULL) r = Math.max(w, ht) * 0.62f;
            float numR = kind == FULL ? Math.min(w, ht) * 0.37f : r * 0.74f;

            // Ticks
            for (int i = 0; i < 60; i++) {
                boolean hour = i % 5 == 0;
                double a = Math.toRadians(i * 6 - 90);
                float outer, inner;
                if (kind == FULL) {
                    // Ticks run out to the edges of the screen, like a watch face filling the TV.
                    double ca = Math.abs(Math.cos(a)), sa = Math.abs(Math.sin(a));
                    float edge = (float) Math.min(ca < 1e-6 ? 1e9 : (w / 2f) / ca, sa < 1e-6 ? 1e9 : (ht / 2f) / sa);
                    if (i % 15 == 0) { outer = edge * 0.97f; inner = edge * 0.84f; }
                    else if (hour) { outer = edge * 0.97f; inner = edge * 0.74f; }
                    else { outer = edge * 0.975f; inner = edge * 0.935f; }
                } else {
                    outer = r;
                    inner = hour ? r * (kind == MODERN ? 0.84f : 0.9f) : r * 0.95f;
                }
                p.setColor(hour ? 0xFFFFFFFF : 0x66FFFFFF);
                float base = kind == FULL ? Math.min(w, ht) / 2f : r;
                p.setStrokeWidth(hour ? base * (kind == MODERN ? 0.022f : kind == FULL ? 0.012f : 0.014f) : base * 0.006f);
                p.setStrokeCap(Paint.Cap.ROUND);
                if (kind == MODERN && hour) {
                    // Square-ish modern face: push hour bars out toward the corners.
                    float sq = (float) Math.min(1.0 / Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a))), 1.38);
                    outer = r * 0.98f * sq;
                    inner = outer - r * 0.16f;
                }
                c.drawLine(cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner,
                        cx + (float) Math.cos(a) * outer, cy + (float) Math.sin(a) * outer, p);
            }

            // Numbers
            txt.setTextSize(kind == FULL ? Math.min(w, ht) * 0.075f : r * (kind == MODERN ? 0.2f : 0.17f));
            int[] nums = kind == CLASSIC ? new int[]{12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11} : new int[]{12, 3, 6, 9};
            for (int n : nums) {
                double a = Math.toRadians(n * 30 - 90);
                float rr = kind == MODERN ? r * 0.62f : numR;
                float x = cx + (float) Math.cos(a) * rr;
                float y = cy + (float) Math.sin(a) * rr - (txt.descent() + txt.ascent()) / 2f;
                c.drawText(String.valueOf(n), x, y, txt);
            }

            // Hands
            Calendar now = Calendar.getInstance();
            float sec = now.get(Calendar.SECOND);
            float min = now.get(Calendar.MINUTE) + sec / 60f;
            float hr = now.get(Calendar.HOUR) + min / 60f;
            float handR = kind == FULL ? Math.min(w, ht) * 0.42f : r;
            hand(c, cx, cy, hr * 30, handR * 0.5f, handR * 0.035f, Color.WHITE);
            hand(c, cx, cy, min * 6, handR * 0.82f, handR * 0.03f, Color.WHITE);
            hand(c, cx, cy, sec * 6, handR * 0.9f, handR * 0.008f, ORANGE);
            p.setStyle(Paint.Style.FILL);
            p.setColor(ORANGE);
            c.drawCircle(cx, cy, handR * 0.022f, p);
            p.setColor(Color.BLACK);
            c.drawCircle(cx, cy, handR * 0.009f, p);
            p.setStyle(Paint.Style.STROKE);
        }

        private void hand(Canvas c, float cx, float cy, float deg, float len, float width, int color) {
            double a = Math.toRadians(deg - 90);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(color);
            p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(cx - (float) Math.cos(a) * len * 0.12f, cy - (float) Math.sin(a) * len * 0.12f,
                    cx + (float) Math.cos(a) * len, cy + (float) Math.sin(a) * len, p);
        }
    }
}
