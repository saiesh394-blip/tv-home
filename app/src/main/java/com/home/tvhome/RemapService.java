package com.home.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Button remapping. Watches only the remote buttons you've chosen in TV Home (Control Centre →
 * Remap buttons) and opens your chosen app or HDMI input instead. It can't see the screen, and every
 * other button passes straight through.
 */
public class RemapService extends AccessibilityService {

    static final String PREFS = "tvhome";
    private int swallowUp = -1;
    private static String foreground = "";
    private long lastHomeUp;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable singleHome = new Runnable() { @Override public void run() { goHome(false); } };

    /** The app on screen right now (from window changes), for app cards in Recent Apps. */
    static String foreground() { return foreground; }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (k == KeyEvent.KEYCODE_HOME && p.getBoolean("catch_home", false)) {
            // Home opens TV Home (even where the TV keeps its own launcher); twice quickly opens Recent Apps.
            if (e.getAction() == KeyEvent.ACTION_UP) {
                long now = System.currentTimeMillis();
                boolean dbl = p.getBoolean("home_double", true);
                if (dbl && now - lastHomeUp < 450) {
                    ui.removeCallbacks(singleHome);
                    lastHomeUp = 0;
                    goHome(true);
                } else {
                    lastHomeUp = now;
                    if (dbl) ui.postDelayed(singleHome, 450); else goHome(false);
                }
            }
            return true;
        }
        if (!remappable(k)) return false;

        // TV Home is waiting for you to press the button you want to change.
        if (p.getBoolean("remap_capture", false)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                p.edit().putBoolean("remap_capture", false).putInt("remap_captured", k)
                        .putLong("remap_captured_at", System.currentTimeMillis()).apply();
                swallowUp = k;
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && k == swallowUp) {
            swallowUp = -1;
            return true;
        }

        String target = p.getString("remap_" + k, null);
        if (target == null) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) open(target);
        return true;
    }

    /** Buttons TV Home will never take over: moving around, OK, Back, Home, volume and power. */
    static boolean remappable(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_SLEEP: case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_UNKNOWN:
                return false;
            default:
                return true;
        }
    }

    private void open(String target) {
        try {
            Intent i;
            if (target.startsWith("input:")) {
                i = new Intent(Intent.ACTION_VIEW,
                        android.media.tv.TvContract.buildChannelUriForPassthroughInput(target.substring(6)));
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            } else {
                PackageManager pm = getPackageManager();
                i = pm.getLeanbackLaunchIntentForPackage(target);
                if (i == null) i = pm.getLaunchIntentForPackage(target);
                if (i == null) return;
            }
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    private void goHome(final boolean switcher) {
        final String fg = foreground;
        final SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        final boolean cards = p.getBoolean("app_cards", false) && !fg.isEmpty() && !fg.equals(getPackageName());
        if (!cards) { startHome(switcher); return; }
        // Keep a small picture of the app you're leaving, for its card in Recent Apps.
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    byte[] png = TvTools.screenshot(RemapService.this);
                    if (png != null && png.length > 1000) {
                        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                        o.inSampleSize = 4;
                        android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length, o);
                        if (b != null) {
                            java.io.File dir = new java.io.File(getCacheDir(), "cards");
                            dir.mkdirs();
                            java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(dir, fg + ".jpg"));
                            b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out);
                            out.close();
                            b.recycle();
                        }
                    }
                } catch (Throwable ignored) { }
                ui.post(new Runnable() { @Override public void run() { startHome(switcher); } });
            }
        }).start();
    }

    private void startHome(boolean switcher) {
        try {
            Intent i = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            if (switcher) i.putExtra("switcher", true);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null) {
            String pkg = event.getPackageName().toString();
            if (!pkg.equals("com.android.systemui") && !pkg.startsWith("com.google.android.inputmethod")
                    && !pkg.contains("leanback.ime")) foreground = pkg;
        }
    }
    @Override public void onInterrupt() { }
}
