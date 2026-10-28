package com.home.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Top Shelf: big artwork behind the dock. Popular movies and shows (free Cinemeta catalogue,
 * no account needed) or the latest Indian headlines (public RSS feeds). Images are cached on the TV.
 */
final class TopShelf {

    static final String MODE_OFF = "off", MODE_WATCH = "watch", MODE_NEWS = "news";

    static final class Card {
        String kind;        // "movie", "series" or "news"
        String eyebrow, title, meta, desc, image, logo, link, source;
        long time;
    }

    private TopShelf() { }

    // ---------- Movies & shows ----------

    static List<Card> watch() {
        List<Card> movies = catalog("movie"), shows = catalog("series");
        List<Card> out = new ArrayList<>();
        for (int i = 0; i < Math.max(movies.size(), shows.size()) && out.size() < 20; i++) {
            if (i < movies.size()) out.add(movies.get(i));
            if (i < shows.size()) out.add(shows.get(i));
        }
        return out;
    }

    private static List<Card> catalog(String type) {
        List<Card> out = new ArrayList<>();
        try {
            JSONArray metas = new JSONObject(text("https://v3-cinemeta.strem.io/catalog/" + type + "/top.json"))
                    .getJSONArray("metas");
            for (int i = 0; i < metas.length() && out.size() < 10; i++) {
                JSONObject m = metas.getJSONObject(i);
                String bg = m.optString("background", "");
                String name = m.optString("name", "");
                if (bg.isEmpty() || name.isEmpty()) continue;
                Card c = new Card();
                c.kind = type;
                c.title = name;
                c.image = bg.replace("http://", "https://");
                c.logo = m.optString("logo", "").replace("http://", "https://");
                c.desc = m.optString("description", "");
                c.eyebrow = "series".equals(type) ? "POPULAR SHOW" : "POPULAR MOVIE";
                StringBuilder meta = new StringBuilder();
                String year = m.optString("releaseInfo", m.optString("year", ""));
                if (!year.isEmpty()) meta.append(year);
                JSONArray g = m.optJSONArray("genres");
                if (g == null) g = m.optJSONArray("genre");
                if (g != null && g.length() > 0) {
                    if (meta.length() > 0) meta.append("  ·  ");
                    meta.append(g.optString(0));
                    if (g.length() > 1) meta.append(", ").append(g.optString(1));
                }
                String rating = m.optString("imdbRating", "");
                if (!rating.isEmpty()) {
                    if (meta.length() > 0) meta.append("  ·  ");
                    meta.append("IMDb ").append(rating);
                }
                String runtime = m.optString("runtime", "");
                if (!runtime.isEmpty() && "movie".equals(type)) {
                    if (meta.length() > 0) meta.append("  ·  ");
                    meta.append(runtime);
                }
                c.meta = meta.toString();
                out.add(c);
            }
        } catch (Exception ignored) { }
        return out;
    }

    // ---------- News ----------

    private static final String[][] FEEDS = {
            {"NDTV", "https://feeds.feedburner.com/ndtvnews-top-stories"},
            {"The Times of India", "https://timesofindia.indiatimes.com/rssfeedstopstories.cms"},
            {"The Hindu", "https://www.thehindu.com/news/national/feeder/default.rss"},
            {"The Indian Express", "https://indianexpress.com/section/india/feed/"},
    };

    private static final Pattern ITEM = Pattern.compile("<item[\\s>](.*?)</item>", Pattern.DOTALL);
    private static final Pattern IMG = Pattern.compile(
            "(https?://[^\"'<>\\s]+?\\.(?:jpe?g|png|webp)(?:\\?[^\"'<>\\s]*)?)", Pattern.CASE_INSENSITIVE);

    static List<Card> news() {
        List<Card> all = new ArrayList<>();
        for (String[] f : FEEDS) {
            try {
                String xml = text(f[1]);
                Matcher it = ITEM.matcher(xml);
                int n = 0;
                while (it.find() && n < 6) {
                    String item = it.group(1);
                    String title = clean(tag(item, "title"));
                    if (title.isEmpty()) continue;
                    String image = image(item);
                    if (image == null) continue;
                    Card c = new Card();
                    c.kind = "news";
                    c.source = f[0];
                    c.title = title;
                    c.desc = clean(tag(item, "description"));
                    c.link = clean(tag(item, "link"));
                    c.image = bigger(image);
                    c.time = date(tag(item, "pubDate"));
                    c.eyebrow = "NEWS  ·  " + f[0].toUpperCase(Locale.ROOT);
                    all.add(c);
                    n++;
                }
            } catch (Exception ignored) { }
        }
        Collections.sort(all, new Comparator<Card>() {
            @Override public int compare(Card a, Card b) { return Long.compare(b.time, a.time); }
        });
        return all.size() > 20 ? new ArrayList<>(all.subList(0, 20)) : all;
    }

    /** "2 h ago" for a news card. */
    static String ago(long time) {
        if (time <= 0) return "";
        long m = (System.currentTimeMillis() - time) / 60000;
        if (m < 1) return "Just now";
        if (m < 60) return m + " min ago";
        if (m < 48 * 60) return (m / 60) + " h ago";
        return (m / 1440) + " days ago";
    }

    private static String tag(String item, String name) {
        Matcher m = Pattern.compile("<" + name + "(?:\\s[^>]*)?>(.*?)</" + name + ">", Pattern.DOTALL).matcher(item);
        return m.find() ? m.group(1) : "";
    }

    private static String image(String item) {
        // Prefer an explicit media / enclosure tag, then any picture link inside the item.
        Matcher m = Pattern.compile("<(?:media:content|media:thumbnail|enclosure)[^>]*url=\"([^\"]+)\"",
                Pattern.CASE_INSENSITIVE).matcher(item);
        if (m.find()) return m.group(1).replace("&amp;", "&");
        m = IMG.matcher(item);
        return m.find() ? m.group(1).replace("&amp;", "&") : null;
    }

    /** Asks the news sites for a screen-sized picture where they support it. */
    private static String bigger(String url) {
        if (url.contains("toiimg.com/thumb/")) {
            return url.replaceAll("width-\\d+", "width-1280").replaceAll("height-\\d+", "height-720")
                    .replaceAll("resizemode-\\d+", "resizemode-4")
                    .replaceAll(",overlay-[^,/]+", "").replaceAll(",pt-\\d+", "").replaceAll(",y_pad-\\d+", "");
        }
        if (url.contains("indianexpress.com") && url.contains("?w=")) return url.replaceAll("\\?w=\\d+", "?w=1280");
        return url;
    }

    private static String clean(String s) {
        s = s.replace("<![CDATA[", "").replace("]]>", "");
        s = s.replaceAll("<[^>]+>", " ");
        s = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&#8217;", "’")
                .replace("&#8216;", "‘").replace("&#8220;", "“").replace("&#8221;", "”").replace("&#8211;", "–");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static long date(String s) {
        s = clean(s);
        String[] forms = {"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz"};
        for (String f : forms) {
            try { return new SimpleDateFormat(f, Locale.ENGLISH).parse(s).getTime(); } catch (Exception ignored) { }
        }
        return 0;
    }

    // ---------- Network and pictures ----------

    static String text(String url) throws Exception {
        return new String(bytes(url), "UTF-8");
    }

    private static byte[] bytes(String url) throws Exception {
        for (int hop = 0; hop < 4; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) TVHome");
            try {
                int code = c.getResponseCode();
                if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                    url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                    continue;
                }
                InputStream in = c.getInputStream();
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                in.close();
                return o.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new Exception("Too many redirects");
    }

    /** Downloads (or reuses) a picture and decodes it close to the size asked for. */
    static Bitmap picture(Context ctx, String url, int w, int h, boolean alpha) {
        if (url == null || url.isEmpty()) return null;
        try {
            File dir = new File(ctx.getCacheDir(), "shelf");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, Integer.toHexString(url.hashCode()) + ".img");
            if (!f.exists() || f.length() == 0) {
                byte[] data = bytes(url);
                FileOutputStream o = new FileOutputStream(f);
                o.write(data);
                o.close();
                trim(dir);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Keeps the picture cache small (about 60 pictures). */
    private static void trim(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= 60) return;
        java.util.Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        for (int i = 0; i < files.length - 60; i++) files[i].delete();
    }
}
