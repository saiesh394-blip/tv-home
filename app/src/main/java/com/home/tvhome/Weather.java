package com.home.tvhome;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Weather from Open-Meteo (free, no account): now, the next hours, sun, UV and air quality. */
final class Weather {

    static final class Hour { String label; int code, temp, rain; boolean day; }

    static final class Data {
        String city;
        int temp, feels, high, low, code, humidity, wind, uv = -1, aqi = -1, rain;
        boolean day = true;
        String sunrise = "", sunset = "";
        final List<Hour> hours = new ArrayList<>();
    }

    static final class Place { String name, detail; double lat, lon; }

    private Weather() { }

    /** Where to show weather for: a city you chose, or the TV's location from its internet connection. */
    static double[] location(SharedPreferences prefs) throws Exception {
        if (prefs.getBoolean("weather_fixed", false) && prefs.contains("lat")) {
            return new double[]{prefs.getFloat("lat", 0), prefs.getFloat("lon", 0)};
        }
        long locTime = prefs.getLong("loc_time", 0);
        if (prefs.contains("lat") && System.currentTimeMillis() - locTime < 24 * 3600 * 1000L
                && prefs.contains("weather_city")) {
            return new double[]{prefs.getFloat("lat", 0), prefs.getFloat("lon", 0)};
        }
        double lat, lon;
        String city;
        try {
            JSONObject loc = new JSONObject(TopShelf.text("https://ipapi.co/json/"));
            lat = loc.getDouble("latitude");
            lon = loc.getDouble("longitude");
            city = loc.optString("city", "");
        } catch (Exception e) {
            JSONObject loc = new JSONObject(TopShelf.text("https://ipwho.is/"));
            lat = loc.getDouble("latitude");
            lon = loc.getDouble("longitude");
            city = loc.optString("city", "");
        }
        SharedPreferences.Editor ed = prefs.edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon)
                .putLong("loc_time", System.currentTimeMillis());
        if (!city.isEmpty()) ed.putString("weather_city", city);
        ed.apply();
        return new double[]{lat, lon};
    }

    static Data fetch(SharedPreferences prefs) throws Exception {
        double[] ll = location(prefs);
        String q = "latitude=" + ll[0] + "&longitude=" + ll[1];
        JSONObject w = new JSONObject(TopShelf.text("https://api.open-meteo.com/v1/forecast?" + q
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day,precipitation_probability"
                + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max"
                + "&timezone=auto&timeformat=unixtime&forecast_days=2"));
        Data d = new Data();
        d.city = prefs.getString("weather_city", "");
        JSONObject cur = w.getJSONObject("current");
        d.temp = (int) Math.round(cur.getDouble("temperature_2m"));
        d.feels = (int) Math.round(cur.optDouble("apparent_temperature", d.temp));
        d.humidity = cur.optInt("relative_humidity_2m", 0);
        d.code = cur.optInt("weather_code", 0);
        d.wind = (int) Math.round(cur.optDouble("wind_speed_10m", 0));
        d.day = cur.optInt("is_day", 1) == 1;
        d.rain = cur.optInt("precipitation_probability", 0);
        JSONObject daily = w.getJSONObject("daily");
        d.high = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        d.low = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        d.uv = (int) Math.round(daily.getJSONArray("uv_index_max").optDouble(0, -1));
        SimpleDateFormat hm = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
        d.sunrise = hm.format(new Date(daily.getJSONArray("sunrise").getLong(0) * 1000L)).toLowerCase(Locale.ROOT);
        d.sunset = hm.format(new Date(daily.getJSONArray("sunset").getLong(0) * 1000L)).toLowerCase(Locale.ROOT);

        JSONObject hourly = w.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        long now = System.currentTimeMillis() / 1000L;
        SimpleDateFormat h = new SimpleDateFormat("h a", Locale.ENGLISH);
        for (int i = 0; i < times.length() && d.hours.size() < 6; i++) {
            long t = times.getLong(i);
            if (t + 3600 <= now) continue;
            Hour x = new Hour();
            x.label = d.hours.isEmpty() ? "Now" : h.format(new Date(t * 1000L)).toLowerCase(Locale.ROOT);
            x.temp = (int) Math.round(hourly.getJSONArray("temperature_2m").getDouble(i));
            x.code = hourly.getJSONArray("weather_code").getInt(i);
            x.rain = hourly.getJSONArray("precipitation_probability").optInt(i, 0);
            x.day = hourly.getJSONArray("is_day").optInt(i, 1) == 1;
            d.hours.add(x);
        }
        try {
            JSONObject a = new JSONObject(TopShelf.text("https://air-quality-api.open-meteo.com/v1/air-quality?" + q
                    + "&current=us_aqi"));
            d.aqi = a.getJSONObject("current").optInt("us_aqi", -1);
        } catch (Exception ignored) { }
        return d;
    }

    /** Finds cities by name, for choosing another city. */
    static List<Place> search(String name) throws Exception {
        List<Place> out = new ArrayList<>();
        JSONObject r = new JSONObject(TopShelf.text("https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&name="
                + URLEncoder.encode(name, "UTF-8")));
        JSONArray res = r.optJSONArray("results");
        if (res == null) return out;
        for (int i = 0; i < res.length(); i++) {
            JSONObject o = res.getJSONObject(i);
            Place p = new Place();
            p.name = o.optString("name");
            StringBuilder det = new StringBuilder();
            String a1 = o.optString("admin1", ""), c = o.optString("country", "");
            if (!a1.isEmpty()) det.append(a1);
            if (!c.isEmpty()) { if (det.length() > 0) det.append(", "); det.append(c); }
            p.detail = det.toString();
            p.lat = o.optDouble("latitude");
            p.lon = o.optDouble("longitude");
            out.add(p);
        }
        return out;
    }

    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    static String uvWord(int uv) {
        if (uv < 0) return "";
        if (uv <= 2) return "Low";
        if (uv <= 5) return "Moderate";
        if (uv <= 7) return "High";
        if (uv <= 10) return "Very high";
        return "Extreme";
    }

    static String aqiWord(int aqi) {
        if (aqi < 0) return "";
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy for some";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very unhealthy";
        return "Hazardous";
    }
}
