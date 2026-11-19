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
    static final String MODE_PHONE = "phone";
    static final String MODE_ONLINE = "online";
    static final String MODE_GRADIENT = "gradient";
    static final String MODE_EMOJI = "emoji";
    static final String MODE_FRACTAL = "fractal";

    /** Online picture sources: {key, name}. */
    static final String[][] SOURCES = {
            {"bing", "Bing Daily Scenery"}, {"wallhaven", "Wallhaven"}, {"nasa", "NASA Images"},
            {"wikimedia", "Wikimedia Commons"}, {"met", "The Met Museum"}, {"cleveland", "Cleveland Museum of Art"},
            {"unsplash", "Unsplash"}};
    static final String[] TOPICS = {"Nature", "Landscape", "Mountains", "Ocean", "Space", "Night Sky", "Cities",
            "Architecture", "Forest", "Flowers", "Abstract", "Paintings", "Animals", "Minimal"};
    /** Change photo every: minutes (0 means with the time of day). */
    static final int[] EVERY = {0, 15, 30, 60, 180, 1440};
    static final String[] EVERY_NAMES = {"With the time of day", "15 minutes", "30 minutes", "1 hour", "3 hours", "Once a day"};

    static String sourceName(String key) {
        for (String[] s : SOURCES) if (s[0].equals(key)) return s[1];
        return SOURCES[0][1];
    }

    /** The photo sent from your phone (Send from Phone). */
    static File phoneFile(android.content.Context c) {
        return new File(new File(c.getFilesDir(), "phone"), "wallpaper.jpg");
    }

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
        int every = prefs.getInt("wp_every", 0);
        String when = every > 0 ? String.valueOf(System.currentTimeMillis() / (every * 60000L)) : slot + "|" + day;
        String what = MODE_ONLINE.equals(mode) ? prefs.getString("wp_source", "bing") + "|" + prefs.getString("wp_topic", "Nature")
                + "|" + prefs.getInt("wp_quality", 0) : "";
        boolean drawn = MODE_GRADIENT.equals(mode) || MODE_EMOJI.equals(mode) || MODE_FRACTAL.equals(mode);
        final String key = drawn ? mode + "|" + prefs.getInt("wp_cfg", 0) : mode + "|" + what + "|" + when;
        if (!shuffle && key.equals(shownKey)) return;
        if (busy) return;
        busy = true;
        final int targetW = ctx.getResources().getDisplayMetrics().widthPixels;
        final int targetH = ctx.getResources().getDisplayMetrics().heightPixels;
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap bmp = null;
                String credit = null;
                try {
                    if (MODE_GRADIENT.equals(mode) || MODE_EMOJI.equals(mode) || MODE_FRACTAL.equals(mode)) {
                        bmp = drawn(mode, Math.max(targetW, targetH), Math.min(targetW, targetH));
                        credit = "";
                    } else if (MODE_ONLINE.equals(mode)) {
                        String[] got = pickOnline();
                        if (got != null) {
                            bmp = decode(new File(got[0]), targetW);
                            credit = got[1];
                        }
                    } else if (MODE_MINE.equals(mode)) {
                        File f = pickMine(slot);
                        if (f != null) bmp = decode(f, targetW);
                    } else if (MODE_PHONE.equals(mode)) {
                        File f = phoneFile(ctx);
                        if (f.exists()) bmp = decode(f, targetW);
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


    // ---------------------------------------------------------------- drawn wallpapers

    private Bitmap drawn(String mode, int w, int h) {
        if (MODE_EMOJI.equals(mode)) {
            int i = prefs.getInt("emoji_index", 0);
            String e;
            int b1, b2;
            if (i < 0 || i >= WallArt.EMOJI.length) {
                e = prefs.getString("emoji_custom", "✨");
                b1 = prefs.getInt("emoji_bg1", 0xFF1B1035);
                b2 = prefs.getInt("emoji_bg2", 0xFF3D2370);
            } else {
                e = WallArt.EMOJI[i][1];
                b1 = WallArt.parse(WallArt.EMOJI[i][2], 0xFF111111);
                b2 = WallArt.parse(WallArt.EMOJI[i][3], 0xFF333333);
            }
            return WallArt.emoji(w, h, e, b1, b2, prefs.getInt("emoji_size", 1));
        }
        if (MODE_FRACTAL.equals(mode)) {
            int i = Math.max(0, Math.min(WallArt.PRESETS.length - 1, prefs.getInt("fractal_preset", 0)));
            String[] pr = WallArt.PRESETS[i];
            int[] cols = {WallArt.parse(pr[1], 0), WallArt.parse(pr[2], 0), WallArt.parse(pr[3], 0)};
            return WallArt.fractalGlass(w, h, cols, prefs.getInt("fractal_ribs", 18));
        }
        return WallArt.gradient(w, h, gradientColours(prefs), gradientStyle(prefs));
    }

    static int[] gradientColours(SharedPreferences prefs) {
        int i = prefs.getInt("grad_preset", 0);
        if (i >= 0 && i < WallArt.PRESETS.length) {
            String[] pr = WallArt.PRESETS[i];
            return new int[]{WallArt.parse(pr[1], 0), WallArt.parse(pr[2], 0), WallArt.parse(pr[3], 0)};
        }
        int n = Math.max(1, Math.min(3, prefs.getInt("grad_count", 2)));
        int[] all = {prefs.getInt("grad_c1", 0xFF14213D), prefs.getInt("grad_c2", 0xFF5B2A86), prefs.getInt("grad_c3", 0xFFE04F9F)};
        int[] out = new int[n];
        System.arraycopy(all, 0, out, 0, n);
        return out;
    }

    static int gradientStyle(SharedPreferences prefs) {
        int i = prefs.getInt("grad_preset", 0);
        if (i >= 0 && i < WallArt.PRESETS.length) return Integer.parseInt(WallArt.PRESETS[i][4]);
        return prefs.getInt("grad_style", 0);
    }

    // ---------------------------------------------------------------- online pictures

    /** Returns {local file, credit} for a picture from the chosen online source, or a saved one when offline. */
    private String[] pickOnline() {
        String source = prefs.getString("wp_source", "bing");
        String topic = prefs.getString("wp_topic", "Nature");
        boolean uhd = prefs.getInt("wp_quality", 0) == 1;
        File dir = new File(ctx.getCacheDir(), "online");
        dir.mkdirs();
        String tag = Integer.toHexString((source + "|" + topic + "|" + uhd).hashCode());
        File cache = new File(dir, "list_" + tag + ".txt");
        List<String[]> items = readList(cache);
        boolean stale = System.currentTimeMillis() - cache.lastModified() > 24L * 3600 * 1000;
        if (items.isEmpty() || stale) {
            List<String[]> fresh = new ArrayList<>();
            try { fresh = onlineList(source, topic, uhd); } catch (Exception ignored) { }
            if (!fresh.isEmpty()) {
                items = fresh;
                try {
                    FileWriter w = new FileWriter(cache);
                    for (String[] it : fresh) w.write(it[0] + "\t" + it[1].replace('\t', ' ').replace('\n', ' ') + "\n");
                    w.close();
                } catch (Exception ignored) { }
            }
        }
        String last = prefs.getString("wp_last", "");
        for (int attempt = 0; attempt < 6 && !items.isEmpty(); attempt++) {
            String[] it = items.get(rnd.nextInt(items.size()));
            if (items.size() > 1 && it[0].equals(last)) continue;
            File f = new File(dir, "img_" + Integer.toHexString(it[0].hashCode()) + ".jpg");
            try {
                if (!f.exists()) download(it[0], f);
                if (!landscape(f)) { f.delete(); items.remove(it); continue; }
                prefs.edit().putString("wp_last", it[0]).putString("credit_" + f.getName(), it[1]).apply();
                tidy(dir);
                return new String[]{f.getPath(), it[1]};
            } catch (Exception e) {
                f.delete();
            }
        }
        File[] cached = dir.listFiles();
        List<File> imgs = new ArrayList<>();
        if (cached != null) for (File f : cached) if (f.getName().startsWith("img_")) imgs.add(f);
        if (imgs.isEmpty()) return null;
        File f = imgs.get(rnd.nextInt(imgs.size()));
        return new String[]{f.getPath(), prefs.getString("credit_" + f.getName(), "")};
    }

    private static boolean landscape(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        return o.outWidth > 0 && o.outHeight > 0 && o.outWidth / (float) o.outHeight >= 1.25f && o.outWidth >= 900;
    }

    private List<String[]> onlineList(String source, String topic, boolean uhd) throws Exception {
        String q = URLEncoder.encode(topic, "UTF-8");
        List<String[]> out = new ArrayList<>();
        if ("bing".equals(source)) {
            for (int idx = 0; idx <= 7; idx += 7) {
                org.json.JSONArray imgs = new JSONObject(getText("https://www.bing.com/HPImageArchive.aspx?format=js&n=8&mkt=en-IN&idx=" + idx))
                        .getJSONArray("images");
                for (int i = 0; i < imgs.length(); i++) {
                    JSONObject im = imgs.getJSONObject(i);
                    String base = im.optString("urlbase", "");
                    if (base.isEmpty()) continue;
                    String credit = im.optString("copyright", "").replaceAll("\\s*\\(©[^)]*\\)", "");
                    out.add(new String[]{"https://www.bing.com" + base + (uhd ? "_UHD.jpg" : "_1920x1080.jpg"),
                            (credit.isEmpty() ? "" : credit + "  ·  ") + "Bing"});
                }
            }
        } else if ("wallhaven".equals(source)) {
            org.json.JSONArray data = new JSONObject(getText("https://wallhaven.cc/api/v1/search?q=" + q
                    + "&categories=100&purity=100&ratios=16x9&sorting=toplist&topRange=1M&atleast="
                    + (uhd ? "3840x2160" : "1920x1080"))).getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                String path = data.getJSONObject(i).optString("path", "");
                if (!path.isEmpty()) out.add(new String[]{path, "Wallhaven"});
            }
        } else if ("nasa".equals(source)) {
            String topicQ = "Space".equals(topic) || "Night Sky".equals(topic) || "Nature".equals(topic)
                    ? URLEncoder.encode("galaxy nebula", "UTF-8") : q;
            org.json.JSONArray items = new JSONObject(getText("https://images-api.nasa.gov/search?media_type=image&page_size=60&q=" + topicQ))
                    .getJSONObject("collection").getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                org.json.JSONArray links = it.optJSONArray("links");
                if (links == null || links.length() == 0) continue;
                String href = links.getJSONObject(0).optString("href", "");
                if (!href.contains("~thumb")) continue;
                href = href.replace("~thumb", uhd ? "~orig" : "~large").replace(" ", "%20");
                String title = it.getJSONArray("data").getJSONObject(0).optString("title", "");
                out.add(new String[]{href, (title.isEmpty() ? "" : shorten(title) + "  ·  ") + "NASA"});
            }
        } else if ("met".equals(source)) {
            org.json.JSONArray ids = new JSONObject(getText("https://collectionapi.metmuseum.org/public/collection/v1/search?hasImages=true&isPublicDomain=true&q="
                    + ("Paintings".equals(topic) ? "landscape" : q))).optJSONArray("objectIDs");
            if (ids != null) {
                List<Integer> pick = new ArrayList<>();
                for (int i = 0; i < Math.min(ids.length(), 200); i++) pick.add(ids.getInt(i));
                java.util.Collections.shuffle(pick, rnd);
                for (int i = 0; i < pick.size() && out.size() < 24 && i < 40; i++) {
                    try {
                        JSONObject o = new JSONObject(getText("https://collectionapi.metmuseum.org/public/collection/v1/objects/" + pick.get(i)));
                        String img = o.optString(uhd ? "primaryImage" : "primaryImageSmall", "");
                        if (img.isEmpty()) img = o.optString("primaryImage", "");
                        if (img.isEmpty()) continue;
                        String artist = o.optString("artistDisplayName", "");
                        out.add(new String[]{img.replace(" ", "%20"), shorten(o.optString("title", "Artwork"))
                                + (artist.isEmpty() ? "" : " — " + shorten(artist)) + "  ·  The Met"});
                    } catch (Exception ignored) { }
                }
            }
        } else if ("cleveland".equals(source)) {
            org.json.JSONArray data = new JSONObject(getText("https://openaccess-api.clevelandart.org/api/artworks/?has_image=1&cc0=1&limit=60&skip="
                    + rnd.nextInt(60) + "&q=" + ("Paintings".equals(topic) ? "landscape painting" : q))).getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject a = data.getJSONObject(i);
                JSONObject images = a.optJSONObject("images");
                if (images == null) continue;
                JSONObject im = images.optJSONObject(uhd ? "print" : "web");
                if (im == null) im = images.optJSONObject("web");
                if (im == null) continue;
                int iw = im.optInt("width"), ih = im.optInt("height");
                if (ih <= 0 || iw / (float) ih < 1.25f) continue;
                String who = "";
                org.json.JSONArray cr = a.optJSONArray("creators");
                if (cr != null && cr.length() > 0) who = cr.getJSONObject(0).optString("description", "").replaceAll("\\s*\\(.*", "");
                out.add(new String[]{im.optString("url"), shorten(a.optString("title", "Artwork"))
                        + (who.isEmpty() ? "" : " — " + shorten(who)) + "  ·  Cleveland Museum of Art"});
            }
        } else if ("unsplash".equals(source)) {
            String key = prefs.getString("unsplash_key", "").trim();
            if (!key.isEmpty()) {
                org.json.JSONArray arr = new org.json.JSONArray(getText("https://api.unsplash.com/photos/random?count=24&orientation=landscape&content_filter=high&query="
                        + q + "&client_id=" + URLEncoder.encode(key, "UTF-8")));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject ph = arr.getJSONObject(i);
                    String raw = ph.getJSONObject("urls").optString("raw", "");
                    if (raw.isEmpty()) continue;
                    String who = ph.optJSONObject("user") == null ? "" : ph.getJSONObject("user").optString("name", "");
                    out.add(new String[]{raw + "&w=" + (uhd ? 3840 : 1920) + "&q=80&fm=jpg",
                            "Photo by " + (who.isEmpty() ? "an Unsplash photographer" : shorten(who)) + " on Unsplash"});
                }
            } else {
                // No key yet: random free photos (they ignore the topic).
                for (int i = 0; i < 16; i++) {
                    out.add(new String[]{"https://picsum.photos/seed/tvhome" + rnd.nextInt(100000) + "/"
                            + (uhd ? "3840/2160" : "1920/1080"), "Random photo  ·  Picsum"});
                }
            }
        } else {   // wikimedia
            int w = uhd ? 3840 : 1920;
            String search = topic + " filetype:bitmap incategory:\"Featured_pictures_on_Wikimedia_Commons\"";
            out.addAll(commons("https://commons.wikimedia.org/w/api.php?action=query&format=json"
                    + "&generator=search&gsrnamespace=6&gsrlimit=50&gsrsearch=" + URLEncoder.encode(search, "UTF-8")
                    + "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata"
                    + "&iiextmetadatafilter=Artist%7CLicenseShortName&iiurlwidth=" + w));
        }
        return out;
    }

    private static String shorten(String s) {
        s = s.replace('\t', ' ').replace('\n', ' ').trim();
        return s.length() > 48 ? s.substring(0, 47) + "…" : s;
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
        return commons(url);
    }

    private List<String[]> commons(String url) throws Exception {
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
