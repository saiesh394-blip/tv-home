package com.home.tvhome;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifications in Control Centre. Only reads the notifications the TV is already showing, so
 * Control Centre can list them; nothing leaves the TV. Needs notification access, which you can
 * allow from Settings → Control Centre.
 */
public class NotifListener extends NotificationListenerService {

    private static NotifListener live;
    static Runnable changed;

    @Override public void onListenerConnected() { live = this; tell(); }
    @Override public void onListenerDisconnected() { if (live == this) live = null; }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { tell(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { tell(); }

    private static void tell() { if (changed != null) changed.run(); }

    static boolean connected() { return live != null; }

    /** The notifications worth showing: newest first, without ongoing ones like "playing" or system status. */
    static List<StatusBarNotification> current(String self) {
        List<StatusBarNotification> out = new ArrayList<>();
        NotifListener l = live;
        if (l == null) return out;
        try {
            StatusBarNotification[] all = l.getActiveNotifications();
            if (all == null) return out;
            for (StatusBarNotification s : all) {
                if (s.isOngoing() || s.getPackageName().equals(self) || s.getPackageName().equals("android")) continue;
                CharSequence t = s.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
                if (t == null || t.length() == 0) continue;
                out.add(s);
            }
        } catch (Throwable ignored) { }
        java.util.Collections.sort(out, new java.util.Comparator<StatusBarNotification>() {
            @Override public int compare(StatusBarNotification a, StatusBarNotification b) { return Long.compare(b.getPostTime(), a.getPostTime()); }
        });
        return out;
    }

    static void dismiss(String key) {
        NotifListener l = live;
        try { if (l != null) l.cancelNotification(key); } catch (Throwable ignored) { }
    }
}
