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

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (!remappable(k)) return false;
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);

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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
