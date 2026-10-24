package com.home.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Picks the home screen wallpaper.
 *
 * Scenery mode: free "featured pictures" from Wikimedia Commons, chosen to suit the time of day.
 * My photos mode: your own pictures from Pictures/TV Home on the TV (with optional
 * Morning, Afternoon, Evening and Night folders).
 *
 * A new picture is chosen when the time of day changes, once a day, or when you shuffle.
 * Only one picture is downloaded at a time and a handful are kept for offline use.
 */
final class Wallpapers {

    interface Listener {
        void onWallpaper(Bitmap bitmap, String credit);
    }

    static final String MODE_SCENERY = "scenery";
    static final String MODE_MINE = "mine";
    static final String MODE_COLOUR = "colour";

    private static final String UA = "TVHome/5.1 (personal TV home screen)";
    private static final int KEEP_FILES = 8;

    private static final String[][] CATEGORIES = {
            // morning
            {"Featured_pictures_of_sunrises", "Featured_pictures_of_lakes", "Featured_pictures_of_mountains"},
            // afternoon
            {"Featured_pictures_of_landscapes", "Featured_pictures_of_mountains", "Featured_pictures_of_bodies_of_water"},
            // evening
            {"Featured_pictures_of_sunsets"},
            // night
            {"Featured_night_photography"}
    };
    static final String[] SLOT_NAMES = {"Morning", "Afternoon", "Evening", "Night"};

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private String shownKey;
    private boolean busy;

    Wallpapers(Context ctx, SharedPreferences prefs, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = prefs;
        this.listener = listener;
    }

    String mode() {
        return prefs.getString("wp_mode", MODE_SCENERY);
    }

    void setMode(String mode) {
        prefs.edit().putString("wp_mode", mode).apply();
    }

    static int slot() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 11) return 0;
        if (h >= 11 && h < 17) return 1;
        if (h >= 17 && h < 20) return 2;
        return 3;
    }

    static File myFolder() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TV Home");
    }

    /** Cheap to call often: does nothing unless the time of day, the day or the mode has changed. */
    void refresh(boolean shuffle) {
        final int slot = slot();
        final String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        final String mode = mode();
        if (MODE_COLOUR.equals(mode)) return;   // the app's colour is the backdrop; no photos needed
        final String key = mode + "|" + slot + "|" + day;
        if (!shuffle && key.equals(shownKey)) return;
        if (busy) return;
        busy = true;
        final int targetW = ctx.getResources().getDisplayMetrics().widthPixels;
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap bmp = null;
                String credit = null;
                try {
                    if (MODE_MINE.equals(mode)) {
                        File f = pickMine(slot);
                        if (f != null) bmp = decode(f, targetW);
                    }
                    if (bmp == null) {
                        String[] got = pickScenery(slot);
                        if (got != null) {
                            bmp = decode(new File(got[0]), targetW);
                            credit = got[1];
                        }
                    }
                } catch (Throwable ignored) { }
                final Bitmap b = bmp;
                final String c = credit;
                ui.post(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        if (b != null) {
                            shownKey = key;
                            listener.onWallpaper(b, c);
                        }
                    }
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- my photos

    /** Returns how many photos are in the folder, or -1 if it can't be read. */
    static int countMine() {
        File dir = myFolder();
        if (!dir.isDirectory()) return 0;
        List<File> all = new ArrayList<>();
        collect(dir, all);
        File[] subs = dir.listFiles();
        if (subs != null) for (File s : subs) if (s.isDirectory()) collect(s, all);
        return all.size();
    }

    private File pickMine(int slot) {
        File dir = myFolder();
        if (!dir.isDirectory()) return null;
        List<File> list = new ArrayList<>();
        File[] subs = dir.listFiles();
        if (subs != null) {
            for (File s : subs) {
                if (s.isDirectory() && s.getName().equalsIgnoreCase(SLOT_NAMES[slot])) collect(s, list);
            }
        }
        if (list.isEmpty()) collect(dir, list);
        if (list.isEmpty() && subs != null) {
            for (File s : subs) if (s.isDirectory()) collect(s, list);
        }
        return pickDifferent(list);
    }

    private static void collect(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName().toLowerCase(Locale.US);
            if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))) {
                out.add(f);
            }
        }
    }

    private File pickDifferent(List<File> list) {
        if (list.isEmpty()) return null;
        String last = prefs.getString("wp_last", "");
        File f = list.get(rnd.nextInt(list.size()));
        if (list.size() > 1 && f.getPath().equals(last)) f = list.get((list.indexOf(f) + 1) % list.size());
        prefs.edit().putString("wp_last", f.getPath()).apply();
        return f;
    }

    // ---------------------------------------------------------------- scenery

    /** Returns {local file path, credit}, downloading one picture if needed. */
    private String[] pickScenery(int slot) {
        File dir = new File(ctx.getCacheDir(), "scenery");
        dir.mkdirs();
        List<String[]> items = sceneryList(slot, dir);
        if (!items.isEmpty()) {
            String last = prefs.getString("wp_last", "");
            for (int attempt = 0; attempt < 4; attempt++) {
                String[] it = items.get(rnd.nextInt(items.size()));
                if (items.size() > 1 && it[0].equals(last)) continue;
                File f = new File(dir, "img_" + Integer.toHexString(it[0].hashCode()) + ".jpg");
                try {
                    if (!f.exists()) download(it[0], f);
                    prefs.edit().putString("wp_last", it[0]).putString("credit_" + f.getName(), it[1]).apply();
                    tidy(dir);
                    return new String[]{f.getPath(), it[1]};
                } catch (Exception e) {
                    f.delete();
                }
            }
        }
        // Offline: reuse a picture we already have.
        File[] cached = dir.listFiles();
        List<File> imgs = new ArrayList<>();
        if (cached != null) for (File f : cached) if (f.getName().startsWith("img_")) imgs.add(f);
        if (imgs.isEmpty()) return null;
        File f = imgs.get(rnd.nextInt(imgs.size()));
        return new String[]{f.getPath(), prefs.getString("credit_" + f.getName(), null)};
    }

    /** The list of suitable pictures for this time of day, refreshed weekly. */
    private List<String[]> sceneryList(int slot, File dir) {
        File cache = new File(dir, "list_" + slot + ".txt");
        List<String[]> items = readList(cache);
        boolean stale = System.currentTimeMillis() - cache.lastModified() > 7L * 24 * 3600 * 1000;
        if (!items.isEmpty() && !stale) return items;
        List<String[]> fresh = new ArrayList<>();
        for (String cat : CATEGORIES[slot]) {
            try { fresh.addAll(fetchCategory(cat)); } catch (Exception ignored) { }
        }
        if (fresh.isEmpty()) return items;
        try {
            FileWriter w = new FileWriter(cache);
            for (String[] it : fresh) w.write(it[0] + "\t" + it[1] + "\n");
            w.close();
        } catch (Exception ignored) { }
        return fresh;
    }

    private static List<String[]> readList(File f) {
        List<String[]> out = new ArrayList<>();
        if (!f.exists()) return out;
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", 2);
                if (p.length == 2 && p[0].startsWith("http")) out.add(p);
            }
            r.close();
        } catch (Exception ignored) { }
        return out;
    }

    private List<String[]> fetchCategory(String cat) throws Exception {
        // Standard Commons thumbnail sizes load fastest.
        int w = ctx.getResources().getDisplayMetrics().widthPixels > 1280 ? 1920 : 1280;
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json"
                + "&generator=categorymembers&gcmtype=file&gcmlimit=60"
                + "&gcmtitle=" + URLEncoder.encode("Category:" + cat, "UTF-8")
                + "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata"
                + "&iiextmetadatafilter=Artist%7CLicenseShortName"
                + "&iiurlwidth=" + w;
        JSONObject root = new JSONObject(getText(url));
        List<String[]> out = new ArrayList<>();
        JSONObject pages = root.optJSONObject("query") == null ? null
                : root.getJSONObject("query").optJSONObject("pages");
        if (pages == null) return out;
        Iterator<String> keys = pages.keys();
        while (keys.hasNext()) {
            JSONObject page = pages.getJSONObject(keys.next());
            if (page.optJSONArray("imageinfo") == null) continue;
            JSONObject ii = page.getJSONArray("imageinfo").getJSONObject(0);
            int iw = ii.optInt("width"), ih = ii.optInt("height");
            String mime = ii.optString("mime");
            String thumb = ii.optString("thumburl");
            // Only wide, landscape-shaped JPEG photos that fill a TV well.
            if (ih <= 0 || iw < 1600 || iw / (float) ih < 1.45f || iw / (float) ih > 2.4f) continue;
            if (!"image/jpeg".equals(mime) || thumb.isEmpty()) continue;
            String artist = "";
            JSONObject meta = ii.optJSONObject("extmetadata");
            if (meta != null && meta.optJSONObject("Artist") != null) {
                artist = Html.fromHtml(meta.getJSONObject("Artist").optString("value")).toString().trim();
            }
            if (artist.length() > 40) artist = artist.substring(0, 40) + "…";
            String credit = (artist.isEmpty() ? "Photo" : "Photo: " + artist.replace('\t', ' ').replace('\n', ' '))
                    + "  ·  Wikimedia Commons";
            out.add(new String[]{thumb, credit});
        }
        return out;
    }

    private static void tidy(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        List<File> imgs = new ArrayList<>();
        for (File f : fs) if (f.getName().startsWith("img_")) imgs.add(f);
        if (imgs.size() <= KEEP_FILES) return;
        File[] arr = imgs.toArray(new File[0]);
        Arrays.sort(arr, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        for (int i = 0; i < arr.length - KEEP_FILES; i++) arr[i].delete();
    }

    // ---------------------------------------------------------------- io

    private static String getText(String url) throws Exception {
        HttpURLConnection c = open(url);
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection c = open(url);
        File tmp = new File(to.getPath() + ".part");
        try {
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            if (!tmp.renameTo(to)) throw new Exception("rename failed");
        } finally {
            c.disconnect();
            tmp.delete();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", UA);
        return c;
    }

    /** Decodes at about screen size so large photos don't use much memory. */
    static Bitmap decode(File f, int targetW) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        o2.inPreferredConfig = Bitmap.Config.RGB_565;   // half the memory, fine for photos
        return BitmapFactory.decodeFile(f.getPath(), o2);
    }
}
