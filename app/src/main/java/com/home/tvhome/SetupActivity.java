package com.home.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * First-run setup, one calm step at a time: welcome, your profile, making TV Home the Home app
 * (through the TV's own debugging connection, no computer needed), and done.
 * It can be run again from Settings.
 */
public class SetupActivity extends Activity {

    static final String[] AVATARS = {"", "🎬", "🍿", "🎮", "🎧",
            "🏏", "⚽", "🌙"};
    static final int[] AVATAR_COLOURS = {0xFF0A84FF, 0xFFFF9F0A, 0xFF30D158, 0xFFBF5AF2, 0xFFFF375F, 0xFF64D2FF};

    private static final PathInterpolator GLIDE = new PathInterpolator(0.16f, 1f, 0.3f, 1f);

    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout stage;
    private int step;
    private boolean busy;
    private EditText nameField;
    private ImageView preview;
    private TextView status;
    private String avatar;
    private int avatarColour;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tvhome", MODE_PRIVATE);
        avatar = prefs.getString("avatar", "");
        avatarColour = prefs.getInt("avatar_colour", 0);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B2333, 0xFF0D0F14, 0xFF0A0B10}));
        stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(stage, new FrameLayout.LayoutParams(dp(620), -2, Gravity.CENTER));
        setContentView(root);
        show(getIntent().getIntExtra("step", 0));
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        if (step == 0 || step == 3) { finishSetup(); return; }
        show(step - 1);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (busy && e.getKeyCode() != KeyEvent.KEYCODE_HOME) return true;
        return super.dispatchKeyEvent(e);
    }

    private void show(int s) {
        step = s;
        stage.removeAllViews();
        if (s == 0) welcome();
        else if (s == 1) profile();
        else if (s == 2) homeApp();
        else done(null);
        stage.setAlpha(0f);
        stage.setTranslationY(dp(14));
        stage.animate().alpha(1f).translationY(0).setInterpolator(GLIDE).setDuration(520).start();
    }

    // ---------- Steps ----------

    private void welcome() {
        ImageView g = new ImageView(this);
        g.setImageBitmap(Art.gearTile(dp(96)));
        g.setClipToOutline(true);
        GradientDrawable round = new GradientDrawable();
        round.setCornerRadius(dp(22));
        g.setBackground(round);
        stage.addView(g, new LinearLayout.LayoutParams(dp(96), dp(96)));
        title("Welcome to TV Home");
        body("A calm, ad-free home screen for your TV. Let's set it up — it takes a minute.");
        View go = button("Get Started", true, new Runnable() { @Override public void run() { show(1); } });
        button("Skip Setup", false, new Runnable() { @Override public void run() { finishSetup(); } });
        go.requestFocus();
    }

    private void profile() {
        preview = new ImageView(this);
        stage.addView(preview, new LinearLayout.LayoutParams(dp(84), dp(84)));
        title("Create Your Profile");
        body("Your name and picture appear in the greeting and in Control Centre.");

        nameField = new EditText(this);
        nameField.setHint("Your first name");
        nameField.setText(prefs.getString("user_name", ""));
        nameField.setSingleLine(true);
        nameField.setTextSize(18);
        nameField.setTextColor(Color.WHITE);
        nameField.setHintTextColor(0x80FFFFFF);
        nameField.setGravity(Gravity.CENTER);
        nameField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final GradientDrawable fb = new GradientDrawable();
        fb.setCornerRadius(dp(14));
        fb.setColor(0x26FFFFFF);
        nameField.setBackground(fb);
        nameField.setPadding(dp(16), 0, dp(16), 0);
        nameField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                fb.setStroke(has ? dp(2) : 0, 0xFFFFFFFF);
                if (!has) recolour();
            }
        });
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(dp(360), dp(48));
        nl.setMargins(0, dp(18), 0, dp(16));
        stage.addView(nameField, nl);

        // Picture: your initial or a symbol, on a colour.
        final LinearLayout pics = new LinearLayout(this);
        pics.setGravity(Gravity.CENTER);
        for (int i = 0; i < AVATARS.length; i++) {
            final int k = i;
            pics.addView(chip(Art.avatar(AVATARS[i], initial(), AVATAR_COLOURS[avatarColour], dp(40)),
                    new Runnable() { @Override public void run() { avatar = AVATARS[k]; updatePreview(); } }));
        }
        avatarChips = pics;
        stage.addView(pics);
        LinearLayout cols = new LinearLayout(this);
        cols.setGravity(Gravity.CENTER);
        for (int i = 0; i < AVATAR_COLOURS.length; i++) {
            final int k = i;
            cols.addView(chip(Art.dot(AVATAR_COLOURS[i], dp(28)),
                    new Runnable() { @Override public void run() { avatarColour = k; recolour(); } }));
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.setMargins(0, dp(6), 0, dp(10));
        stage.addView(cols, cl);
        updatePreview();

        View next = button("Continue", true, new Runnable() {
            @Override public void run() {
                prefs.edit().putString("user_name", nameField.getText().toString().trim())
                        .putString("avatar", avatar).putInt("avatar_colour", avatarColour).apply();
                if (getIntent().getBooleanExtra("single", false)) { finishSetup(); return; }
                show(2);
            }
        });
        next.requestFocus();
    }

    private void homeApp() {
        title("Make TV Home the Home Screen");
        body("So the Home button and switching the TV on open TV Home. TV Home can do this itself "
                + "through the TV's own debugging connection — no computer needed. It also gives itself "
                + "the permissions it needs, and only runs a short, fixed list of commands.");
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xCCFFFFFF);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.setMargins(0, 0, 0, dp(8));
        stage.addView(status, sl);
        View auto = button("Set Up Automatically (Recommended)", true, new Runnable() {
            @Override public void run() { runAuto(); }
        });
        button("Skip for Now", false, new Runnable() { @Override public void run() { done(null); step = 3; } });
        auto.requestFocus();
    }

    private void runAuto() {
        if (busy) return;
        busy = true;
        status.setText("Connecting to this TV…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final AdbClient.Report r = AdbClient.runSetup(SetupActivity.this, new AdbClient.Status() {
                        @Override public void say(final String m) {
                            ui.post(new Runnable() { @Override public void run() { status.setText(m); } });
                        }
                    }, false);
                    ui.post(new Runnable() {
                        @Override public void run() {
                            busy = false;
                            prefs.edit().putBoolean("adb_setup_ok", true).apply();
                            step = 3;
                            stage.removeAllViews();
                            done(r.lines.toString().trim());
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            busy = false;
                            String why = e instanceof java.net.ConnectException
                                    || String.valueOf(e.getMessage()).contains("ECONNREFUSED")
                                    ? "Debugging isn't switched on yet."
                                    : e instanceof java.net.SocketTimeoutException
                                    ? "The TV didn't answer in time."
                                    : "That didn't work: " + e.getMessage();
                            status.setText(why + "\n\n1.  Settings › System › About: select Build seven times.\n"
                                    + "2.  Settings › System › Developer options: turn on USB debugging (and Network debugging if listed).\n"
                                    + "3.  Come back and choose Set Up Automatically. When the TV asks, tick Always allow and choose Allow.");
                            status.setGravity(Gravity.START);
                        }
                    });
                }
            }
        }).start();
    }

    private void done(String report) {
        stage.removeAllViews();
        ImageView av = new ImageView(this);
        av.setImageBitmap(Art.avatar(prefs.getString("avatar", ""), initial(),
                AVATAR_COLOURS[prefs.getInt("avatar_colour", 0)], dp(84)));
        stage.addView(av, new LinearLayout.LayoutParams(dp(84), dp(84)));
        String name = prefs.getString("user_name", "");
        title(name.isEmpty() ? "You're All Set" : "You're All Set, " + name);
        body(report != null ? report
                : "You can change any of this, or run setup again, from TV Home Settings.");
        View go = button("Go to Home Screen", true, new Runnable() { @Override public void run() { finishSetup(); } });
        go.requestFocus();
        stage.setAlpha(0f);
        stage.animate().alpha(1f).setInterpolator(GLIDE).setDuration(520).start();
    }

    private void finishSetup() {
        prefs.edit().putBoolean("setup_done", true).apply();
        if (!prefs.contains("user_name")) prefs.edit().putString("user_name", "").apply();
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ---------- Pieces ----------

    private String initial() {
        String n = nameField != null ? nameField.getText().toString().trim() : prefs.getString("user_name", "");
        return n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase();
    }

    private LinearLayout avatarChips;

    private void recolour() {
        if (avatarChips != null) {
            for (int i = 0; i < avatarChips.getChildCount(); i++) {
                ((ImageView) avatarChips.getChildAt(i)).setImageBitmap(
                        Art.avatar(AVATARS[i], initial(), AVATAR_COLOURS[avatarColour], dp(40)));
            }
        }
        updatePreview();
    }

    private void updatePreview() {
        if (preview != null) preview.setImageBitmap(Art.avatar(avatar, initial(), AVATAR_COLOURS[avatarColour], dp(84)));
    }

    private void title(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(30);
        v.setTextColor(Color.WHITE);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.setMargins(0, dp(22), 0, dp(8));
        stage.addView(v, l);
    }

    private void body(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(16);
        v.setTextColor(0xB3FFFFFF);
        v.setGravity(Gravity.CENTER);
        v.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.setMargins(0, 0, 0, dp(18));
        stage.addView(v, l);
    }

    private View button(String text, boolean primary, final Runnable action) {
        final TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setFocusable(true);
        b.setClickable(true);
        if (Build.VERSION.SDK_INT >= 26) b.setDefaultFocusHighlightEnabled(false);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        final int rest = primary ? 0x40FFFFFF : 0x1AFFFFFF;
        bg.setColor(rest);
        b.setBackground(bg);
        b.setTextColor(Color.WHITE);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? 0xFFF2F3F5 : rest);
                b.setTextColor(has ? 0xFF111317 : Color.WHITE);
                v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(220).start();
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(380), dp(46));
        l.setMargins(0, dp(5), 0, dp(5));
        stage.addView(b, l);
        return b;
    }

    private View chip(android.graphics.Bitmap art, final Runnable action) {
        final ImageView v = new ImageView(this);
        v.setImageBitmap(art);
        v.setFocusable(true);
        v.setClickable(true);
        v.setPadding(dp(3), dp(3), dp(3), dp(3));
        if (Build.VERSION.SDK_INT >= 26) v.setDefaultFocusHighlightEnabled(false);
        final GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        v.setBackground(ring);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View x, boolean has) {
                ring.setStroke(has ? dp(2) : 0, 0xFFFFFFFF);
                x.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(200).start();
            }
        });
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { action.run(); }
        });
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.setMargins(dp(6), 0, dp(6), 0);
        v.setLayoutParams(l);
        return v;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
