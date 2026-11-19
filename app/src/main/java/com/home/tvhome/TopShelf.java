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

    static final String[] GENRES = {"Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama",
            "Family", "Fantasy", "History", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "War", "Western"};

    /** kind: 0 movies and series, 1 movies, 2 series. genre: "" for all. */
    static List<Card> watch(int kind, String genre) {
        List<Card> movies = kind == 2 ? new ArrayList<Card>() : catalog("movie", genre);
        List<Card> shows = kind == 1 ? new ArrayList<Card>() : catalog("series", genre);
        List<Card> out = new ArrayList<>();
        for (int i = 0; i < Math.max(movies.size(), shows.size()) && out.size() < 20; i++) {
            if (i < movies.size()) out.add(movies.get(i));
            if (i < shows.size()) out.add(shows.get(i));
        }
        return out;
    }

    private static List<Card> catalog(String type, String genre) {
        List<Card> out = new ArrayList<>();
        try {
            String url = "https://v3-cinemeta.strem.io/catalog/" + type + "/top"
                    + (genre == null || genre.isEmpty() ? "" : "/genre=" + java.net.URLEncoder.encode(genre, "UTF-8").replace("+", "%20"))
                    + ".json";
            JSONArray metas = new JSONObject(text(url)).getJSONArray("metas");
            for (int i = 0; i < metas.length() && out.size() < 14; i++) {
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

    /** Newsrooms: {id, name, group, feed}. */
    static final String[][] NEWSROOMS = {
            {"ndtv", "NDTV", "India", "https://feeds.feedburner.com/ndtvnews-top-stories"},
            {"toi", "The Times of India", "India", "https://timesofindia.indiatimes.com/rssfeedstopstories.cms"},
            {"hindu", "The Hindu", "India", "https://www.thehindu.com/news/national/feeder/default.rss"},
            {"ie", "The Indian Express", "India", "https://indianexpress.com/section/india/feed/"},
            {"ht", "Hindustan Times", "India", "https://www.hindustantimes.com/feeds/rss/india-news/rssfeed.xml"},
            {"indiatoday", "India Today", "India", "https://www.indiatoday.in/rss/1206578"},
            {"news18", "News18", "India", "https://www.news18.com/commonfeeds/v1/eng/rss/india.xml"},
            {"bbc", "BBC News", "International", "https://feeds.bbci.co.uk/news/world/rss.xml"},
            {"guardian", "The Guardian", "International", "https://www.theguardian.com/world/rss"},
            {"nyt", "The New York Times", "International", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml"},
            {"aljazeera", "Al Jazeera", "International", "https://www.aljazeera.com/xml/rss/all.xml"},
            {"sky", "Sky News", "International", "https://feeds.skynews.com/feeds/rss/world.xml"},
            {"dw", "Deutsche Welle", "International", "https://rss.dw.com/xml/rss-en-world"},
            {"france24", "France 24", "International", "https://www.france24.com/en/rss"},
            {"npr", "NPR", "International", "https://feeds.npr.org/1004/rss.xml"},
            {"cnn", "CNN", "International", "http://rss.cnn.com/rss/edition_world.rss"},
            {"cbs", "CBS News", "International", "https://www.cbsnews.com/latest/rss/world"},
            {"abc", "ABC News Australia", "International", "https://www.abc.net.au/news/feed/2942460/rss.xml"},
            {"japantimes", "The Japan Times", "International", "https://www.japantimes.co.jp/feed/"},
            {"ndtvworld", "NDTV World", "International", "https://feeds.feedburner.com/ndtvnews-world-news"},
            {"bbchindi", "BBC News Hindi", "Indian languages", "https://feeds.bbci.co.uk/hindi/rss.xml"},
            {"ndtvkhabar", "NDTV Khabar (Hindi)", "Indian languages", "https://feeds.feedburner.com/ndtvkhabar-india"},
            {"bbcmarathi", "BBC News Marathi", "Indian languages", "https://feeds.bbci.co.uk/marathi/rss.xml"},
            {"bbcgujarati", "BBC News Gujarati", "Indian languages", "https://feeds.bbci.co.uk/gujarati/rss.xml"},
            {"bbctamil", "BBC News Tamil", "Indian languages", "https://feeds.bbci.co.uk/tamil/rss.xml"},
            {"bbctelugu", "BBC News Telugu", "Indian languages", "https://feeds.bbci.co.uk/telugu/rss.xml"},
            {"bbcbangla", "BBC News Bangla", "Indian languages", "https://feeds.bbci.co.uk/bengali/rss.xml"},
            {"abpananda", "ABP Ananda (Bengali)", "Indian languages", "https://bengali.abplive.com/home/feed"},
            {"bbcpunjabi", "BBC News Punjabi", "Indian languages", "https://feeds.bbci.co.uk/punjabi/rss.xml"},
            {"bbcurdu", "BBC News Urdu", "Indian languages", "https://feeds.bbci.co.uk/urdu/rss.xml"},
    };
    static final String NEWS_DEFAULT = "ndtv,toi,hindu,ie";

    private static final Pattern ITEM = Pattern.compile("<item[\\s>](.*?)</item>", Pattern.DOTALL);
    private static final Pattern IMG = Pattern.compile(
            "(https?://[^\"'<>\\s]+?\\.(?:jpe?g|png|webp)(?:\\?[^\"'<>\\s]*)?)", Pattern.CASE_INSENSITIVE);

    static List<Card> news(java.util.Collection<String> ids) {
        List<Card> all = new ArrayList<>();
        List<String[]> feeds = new ArrayList<>();
        for (String[] r : NEWSROOMS) if (ids.contains(r[0])) feeds.add(new String[]{r[1], r[3]});
        if (feeds.isEmpty()) feeds.add(new String[]{NEWSROOMS[0][1], NEWSROOMS[0][3]});
        int per = Math.max(3, 24 / feeds.size());
        for (String[] f : feeds) {
            try {
                String xml = text(f[1]);
                Matcher it = ITEM.matcher(xml);
                int n = 0;
                while (it.find() && n < per) {
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
        return all.size() > 24 ? new ArrayList<>(all.subList(0, 24)) : all;
    }

    /** The article's own words, read from its page (paragraphs only). Blocking: call off the main thread. */
    static String article(String link) {
        if (link == null || !link.startsWith("http")) return null;
        try {
            String html = text(link);
            html = html.replaceAll("(?is)<script.*?</script>", " ").replaceAll("(?is)<style.*?</style>", " ");
            Matcher p = Pattern.compile("(?is)<p[^>]*>(.*?)</p>").matcher(html);
            StringBuilder sb = new StringBuilder();
            int n = 0;
            while (p.find() && n < 30) {
                String t = clean(p.group(1));
                if (t.length() < 60 || t.startsWith("©") || t.toLowerCase(Locale.ROOT).contains("cookie")) continue;
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(t);
                n++;
                if (sb.length() > 7000) break;
            }
            return sb.length() > 200 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
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
        Matcher m = Pattern.compile("<(?:media:content|media:thumbnail|enclosure)\\b([^>]*)>", Pattern.CASE_INSENSITIVE).matcher(item);
        String best = null;
        int bestW = -1;
        while (m.find()) {
            String attrs = m.group(1);
            Matcher u = Pattern.compile("url=[\"']([^\"']+)[\"']").matcher(attrs);
            if (!u.find()) continue;
            if (attrs.contains("type=") && !attrs.matches("(?s).*type=[\"']image.*")) continue;
            Matcher w = Pattern.compile("width=[\"'](\\d+)[\"']").matcher(attrs);
            int wv = w.find() ? Integer.parseInt(w.group(1)) : 0;
            if (wv > bestW) { bestW = wv; best = u.group(1); }
        }
        if (best != null) return best.replace("&amp;", "&");
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
        if (url.contains("ichef.bbci.co.uk")) return url.replaceAll("/(\\d{2,4})/", "/1024/");
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
