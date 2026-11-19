package com.home.tvhome;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TV controls that Android keeps for system apps: Wi-Fi, Private DNS, removing apps, sleep timers and so on.
 * They run through the TV's own debugging connection (see {@link AdbClient}), one at a time, off the main thread.
 * Nothing here runs unless you choose it in Settings.
 */
final class TvTools {

    interface Result { void done(String output, Exception error); }

    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();
    private static Handler ui() { return new Handler(Looper.getMainLooper()); }
    private static AdbClient adb;

    private TvTools() { }

    /** Runs shell commands on this TV and hands back what the last one printed. */
    static void run(final Context ctx, final Result result, final String... commands) {
        final Context app = ctx.getApplicationContext();
        ONE.execute(new Runnable() {
            @Override public void run() {
                String out = null;
                Exception err = null;
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        if (adb == null) {
                            AdbClient a = new AdbClient();
                            a.connect(app, "127.0.0.1", 5555, null);
                            adb = a;
                        }
                        for (String c : commands) out = adb.shell(c);
                        err = null;
                        break;
                    } catch (Exception e) {
                        err = e;
                        if (adb != null) adb.close();
                        adb = null;   // the connection dropped: try once more with a fresh one
                    }
                }
                final String o = out;
                final Exception e = err;
                if (result != null) ui().post(new Runnable() { @Override public void run() { result.done(o, e); } });
            }
        });
    }

    /** Takes a screenshot of whatever is on screen (for app cards in Recent Apps). Blocking: call off the main thread. */
    static byte[] screenshot(Context ctx) {
        final Context app = ctx.getApplicationContext();
        final byte[][] out = new byte[1][];
        java.util.concurrent.Future<?> f = ONE.submit(new Runnable() {
            @Override public void run() {
                try {
                    if (adb == null) {
                        AdbClient a = new AdbClient();
                        a.connect(app, "127.0.0.1", 5555, null);
                        adb = a;
                    }
                    out[0] = adb.execBytes("screencap -p");
                } catch (Exception e) {
                    if (adb != null) adb.close();
                    adb = null;
                }
            }
        });
        try { f.get(2500, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (Exception ignored) { }
        return out[0];
    }

    /** A friendly line for a failed command. */
    static String why(Exception e) {
        String m = String.valueOf(e == null ? "" : e.getMessage());
        if (e instanceof java.net.ConnectException || m.contains("ECONNREFUSED")) {
            return "Debugging is off. Turn on USB debugging in Developer options, then try again.";
        }
        if (e instanceof java.net.SocketTimeoutException) return "The TV didn't answer. If it asked to allow debugging, choose Allow.";
        return "That didn't work: " + m;
    }

    static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }

    // ---------- Wi-Fi ----------

    static final class Network { String ssid, security; int level; boolean saved; String id; }

    /** Parses "cmd wifi list-scan-results" (BSSID, frequency, RSSI, age, SSID, flags). */
    static List<Network> parseScan(String out) {
        List<Network> list = new ArrayList<>();
        if (out == null) return list;
        for (String line : out.split("\n")) {
            line = line.trim();
            if (!line.matches("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}.*")) continue;
            line = line.replaceAll("\\([^)]*\\)", "");   // some TVs add per-antenna levels in brackets
            String[] p = line.split("\\s+");
            if (p.length < 5) continue;
            int rssi;
            try { rssi = Integer.parseInt(p[2]); } catch (Exception e) { continue; }
            int flagsAt = line.indexOf('[');
            String flags = flagsAt >= 0 ? line.substring(flagsAt) : "";
            // the SSID sits between the age column and the flags
            String head = flagsAt >= 0 ? line.substring(0, flagsAt).trim() : line;
            String[] hp = head.split("\\s+", 5);
            String ssid = hp.length >= 5 ? hp[4].trim() : "";
            if (ssid.isEmpty() || ssid.equals("\"\"")) continue;
            Network n = new Network();
            n.ssid = ssid;
            n.level = rssi;
            n.security = flags.contains("SAE") ? "wpa3" : flags.contains("WPA") || flags.contains("RSN") ? "wpa2"
                    : flags.contains("WEP") ? "wep" : "open";
            boolean dup = false;
            for (Network x : list) if (x.ssid.equals(n.ssid)) { dup = true; x.level = Math.max(x.level, n.level); }
            if (!dup) list.add(n);
        }
        Collections.sort(list, new Comparator<Network>() {
            @Override public int compare(Network a, Network b) { return b.level - a.level; }
        });
        return list;
    }

    /** Parses "cmd wifi list-networks" (Network Id, SSID, Security type). */
    static List<Network> parseSaved(String out) {
        List<Network> list = new ArrayList<>();
        if (out == null) return list;
        for (String line : out.split("\n")) {
            line = line.trim();
            if (!line.matches("^\\d+\\s+.*")) continue;
            String[] p = line.split("\\s{2,}|\\t");
            if (p.length < 2) {
                String[] q = line.split("\\s+", 3);
                if (q.length < 2) continue;
                p = q;
            }
            Network n = new Network();
            n.id = p[0].trim();
            n.ssid = p[1].trim();
            n.security = p.length > 2 ? p[2].trim() : "";
            n.saved = true;
            list.add(n);
        }
        return list;
    }

    static String currentWifi(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo i = wm.getConnectionInfo();
            String s = i == null ? null : i.getSSID();
            if (s == null || s.contains("unknown ssid") || s.equals("0x")) return null;
            return s.replace("\"", "");
        } catch (Exception e) { return null; }
    }

    static String ip(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int a = wm.getConnectionInfo().getIpAddress();
            if (a != 0) return (a & 0xFF) + "." + ((a >> 8) & 0xFF) + "." + ((a >> 16) & 0xFF) + "." + ((a >> 24) & 0xFF);
            for (java.net.NetworkInterface ni : Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress ad : Collections.list(ni.getInetAddresses())) {
                    if (!ad.isLoopbackAddress() && ad instanceof java.net.Inet4Address) return ad.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return "–";
    }

    // ---------- Private DNS ----------

    /** {name, hostname ("" = Automatic), what it does}. */
    static final String[][] DNS = {
            {"Automatic", "", "No filtering by TV Home"},
            {"AdGuard DNS", "dns.adguard-dns.com", "Blocks ads and trackers"},
            {"AdGuard Family Protection", "family.adguard-dns.com", "Ads and trackers, and also blocks adult sites"},
            {"AdGuard Non-filtering", "unfiltered.adguard-dns.com", "Private, blocks nothing"},
            {"Cloudflare", "one.one.one.one", "Fast and private, blocks nothing"},
            {"Cloudflare Family", "family.cloudflare-dns.com", "Blocks malware and adult sites"},
            {"Quad9", "dns.quad9.net", "Blocks known malicious sites"},
            {"Google", "dns.google", "Fast, blocks nothing"},
    };

    static String dnsNow(Context c) {
        try {
            String mode = Settings.Global.getString(c.getContentResolver(), "private_dns_mode");
            String host = Settings.Global.getString(c.getContentResolver(), "private_dns_specifier");
            if ("hostname".equals(mode) && host != null && !host.isEmpty()) return host;
        } catch (Exception ignored) { }
        return "";
    }

    static String dnsName(String host) {
        if (host == null || host.isEmpty()) return "Automatic";
        for (String[] d : DNS) if (d[1].equals(host)) return d[0];
        return host;
    }

    /** Sets Private DNS directly when TV Home has been given the right, otherwise through debugging. */
    static void setDns(Context c, String host, Result r) {
        boolean off = host == null || host.isEmpty();
        try {
            if (c.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED) {
                Settings.Global.putString(c.getContentResolver(), "private_dns_mode", off ? "opportunistic" : "hostname");
                if (!off) Settings.Global.putString(c.getContentResolver(), "private_dns_specifier", host);
                r.done("", null);
                return;
            }
        } catch (Exception ignored) { }
        if (off) run(c, r, "settings put global private_dns_mode opportunistic");
        else run(c, r, "settings put global private_dns_specifier " + q(host), "settings put global private_dns_mode hostname");
    }

    static final String[] AD_DOMAINS = {"doubleclick.net", "googleadservices.com", "pagead2.googlesyndication.com",
            "adservice.google.com", "ads.yahoo.com", "app-measurement.com", "analytics.tiktok.com", "ad.doubleclick.net"};

    /** Looks up well-known ad domains; blocked ones come back as 0.0.0.0 or not at all. Returns {blocked, total}. */
    static int[] testAds() {
        int blocked = 0;
        for (String d : AD_DOMAINS) {
            try {
                InetAddress a = InetAddress.getByName(d);
                String h = a.getHostAddress();
                if (h == null || h.equals("0.0.0.0") || h.equals("::") || h.startsWith("127.")) blocked++;
            } catch (Exception e) {
                blocked++;
            }
        }
        return new int[]{blocked, AD_DOMAINS.length};
    }

    // ---------- Apps (TV Debloat) ----------

    static final class AppItem { String pkg, label; boolean system, enabled; String verdict; }

    /** Extras that most people never use: safe to switch off. */
    private static final String[] SAFE = {
            "com.google.android.play.games", "com.google.android.videos", "com.google.android.music",
            "com.google.android.youtube.tvmusic", "com.google.android.apps.mediashell", "com.google.android.tv.remote.service",
            "com.google.android.feedback", "com.google.android.marvin.talkback", "com.google.android.tvrecommendations",
            "com.google.android.apps.tv.dreamx", "com.google.android.tvtutorials", "com.google.android.apps.tv.launcherx.ambient",
            "com.mitv.tvhome", "com.xiaomi.mitv.tvmanager", "com.xiaomi.mitv.calendar", "com.xiaomi.mitv.appstore",
            "com.xiaomi.mitv.upgrade", "com.xiaomi.mitv.karaoke", "com.xiaomi.mitv.shop", "com.mitv.videoplayer",
            "com.xiaomi.mitv.gallery", "com.xiaomi.mitv.ota", "com.xiaomi.statistic", "com.xiaomi.mitv.advertise",
            "com.xiaomi.mitv.screensaver", "com.mitv.patchwall", "com.xiaomi.mitv.pay", "com.miui.analytics",
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.facebook.services", "com.facebook.system",
            "com.android.printspooler", "com.android.bips", "com.android.egg", "com.android.dreams.basic",
            "com.android.dreams.phototable", "com.android.wallpaper.livepicker", "com.android.providers.partnerbookmarks",
    };

    /** Removing these can break the TV: they're never offered for removal. */
    private static final String[] NEEDED = {
            "android", "com.android.systemui", "com.android.settings", "com.android.tv.settings", "com.android.providers.",
            "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.google.android.gms", "com.google.android.gsf",
            "com.android.vending", "com.google.android.katniss", "com.google.android.tvsetup", "com.google.android.leanbacklauncher",
            "com.android.inputmethod", "com.google.android.inputmethod", "com.google.android.leanback.ime",
            "com.android.bluetooth", "com.android.phone", "com.android.shell", "com.android.server.", "com.android.networkstack",
            "com.google.android.networkstack", "com.android.webview", "com.google.android.webview", "com.android.externalstorage",
            "com.android.tv.", "com.google.android.tv.frameworkpackagestubs", "com.android.wifi", "com.google.android.wifi",
            "com.android.se", "com.android.keychain", "com.android.location", "com.android.media", "com.google.android.media",
            "com.android.documentsui", "com.google.android.documentsui", "com.android.certinstaller", "com.android.cellbroadcast",
            "com.android.ons", "com.android.sharedstoragebackup", "com.android.backupconfirm", "com.android.statementservice",
            "com.android.localtransport", "com.android.proxyhandler", "com.android.vpndialogs", "com.android.htmlviewer",
            "com.mediatek", "com.mstar", "com.droidlogic", "com.amlogic", "com.realtek", "com.xiaomi.mitv.tvplayer",
            "com.xiaomi.tv.", "com.xiaomi.mitv.settings", "com.xiaomi.mitv.systemui", "com.home.tvhome",
    };

    static String verdict(String pkg, boolean system) {
        if (!system) return "Downloaded";
        for (String s : NEEDED) if (pkg.equals(s) || (s.endsWith(".") && pkg.startsWith(s)) || pkg.startsWith(s + ".")) return "Needed by the TV";
        for (String s : SAFE) if (pkg.equals(s)) return "Safe to remove";
        return "Caution";
    }

    static List<AppItem> apps(Context c, boolean system) {
        PackageManager pm = c.getPackageManager();
        List<AppItem> out = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)) {
            boolean sys = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (sys != system) continue;
            if (ai.packageName.equals(c.getPackageName())) continue;
            AppItem a = new AppItem();
            a.pkg = ai.packageName;
            a.label = String.valueOf(pm.getApplicationLabel(ai));
            a.system = sys;
            a.enabled = ai.enabled;
            a.verdict = verdict(a.pkg, sys);
            out.add(a);
        }
        Collections.sort(out, new Comparator<AppItem>() {
            @Override public int compare(AppItem x, AppItem y) {
                int r = rank(x.verdict) - rank(y.verdict);
                return r != 0 ? r : x.label.compareToIgnoreCase(y.label);
            }
        });
        return out;
    }

    private static int rank(String v) {
        return "Safe to remove".equals(v) ? 0 : "Downloaded".equals(v) ? 0 : "Caution".equals(v) ? 1 : 2;
    }

    // ---------- Device ----------

    static List<String[]> deviceInfo(Context c) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Model", (Build.MANUFACTURER + " " + Build.MODEL).trim()});
        rows.add(new String[]{"Android", Build.VERSION.RELEASE + "  (API " + Build.VERSION.SDK_INT + ")"});
        if (Build.VERSION.SDK_INT >= 23) rows.add(new String[]{"Security Patch", Build.VERSION.SECURITY_PATCH});
        rows.add(new String[]{"Build", Build.DISPLAY});
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            rows.add(new String[]{"RAM", String.format(Locale.US, "%.1f GB used of %.1f GB",
                    (mi.totalMem - mi.availMem) / 1e9, mi.totalMem / 1e9)});
        } catch (Exception ignored) { }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            rows.add(new String[]{"Internal Storage", String.format(Locale.US, "%.1f GB free of %.1f GB", free / 1e9, total / 1e9)});
        } catch (Exception ignored) { }
        long up = SystemClock.elapsedRealtime() / 60000;
        rows.add(new String[]{"Up Time", (up / 1440 > 0 ? up / 1440 + " d " : "") + (up / 60) % 24 + " h " + up % 60 + " min"});
        String wifi = currentWifi(c);
        rows.add(new String[]{"Network", wifi == null ? "Ethernet or not connected" : wifi});
        rows.add(new String[]{"IP Address", ip(c)});
        rows.add(new String[]{"Private DNS", dnsName(dnsNow(c))});
        rows.add(new String[]{"Debugging", adbOn(c) ? "On" : "Off"});
        return rows;
    }

    static boolean adbOn(Context c) {
        try { return Settings.Global.getInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1; }
        catch (Exception e) { return false; }
    }

    // ---------- Bluetooth ----------

    static BluetoothAdapter bt() {
        try { return BluetoothAdapter.getDefaultAdapter(); } catch (Throwable t) { return null; }
    }

    static List<BluetoothDevice> paired() {
        List<BluetoothDevice> out = new ArrayList<>();
        BluetoothAdapter a = bt();
        try { if (a != null) out.addAll(a.getBondedDevices()); } catch (Throwable ignored) { }
        return out;
    }

    static String name(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null || n.isEmpty() ? d.getAddress() : n;
        } catch (Throwable t) { return "Device"; }
    }

    /** Forgets a paired device (Android keeps this method hidden, but it is the same one Settings uses). */
    static boolean forget(BluetoothDevice d) {
        try { return (Boolean) d.getClass().getMethod("removeBond").invoke(d); } catch (Throwable t) { return false; }
    }
}
