package com.home.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Icon packs in the standard format most launchers use (ADW / Nova / Go): an installed app with an
 * appfilter.xml that maps each app to one of its drawings. TV Home uses them for the dock icons.
 */
final class IconPack {

    private static final String[] ACTIONS = {
            "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME", "com.teslacoilsw.launcher.THEME"};

    final String pkg;
    private final Resources res;
    private final Map<String, String> byComponent = new HashMap<>();
    private final Map<String, String> byPackage = new HashMap<>();

    /** Installed icon packs: package name → label. */
    static Map<String, String> installed(PackageManager pm) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String a : ACTIONS) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(a), 0)) {
                String p = ri.activityInfo.packageName;
                if (!out.containsKey(p)) out.put(p, String.valueOf(ri.loadLabel(pm)));
            }
        }
        return out;
    }

    /** Reads the pack's app list. Slow-ish for big packs, so call it off the main thread. */
    IconPack(PackageManager pm, String pkg) throws Exception {
        this.pkg = pkg;
        this.res = pm.getResourcesForApplication(pkg);
        XmlPullParser x;
        int id = res.getIdentifier("appfilter", "xml", pkg);
        if (id != 0) {
            x = res.getXml(id);
        } else {
            InputStream in = res.getAssets().open("appfilter.xml");
            x = XmlPullParserFactory.newInstance().newPullParser();
            x.setInput(in, "utf-8");
        }
        for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = x.next()) {
            if (ev != XmlPullParser.START_TAG || !"item".equals(x.getName())) continue;
            String comp = null, draw = null;
            for (int i = 0; i < x.getAttributeCount(); i++) {
                if ("component".equals(x.getAttributeName(i))) comp = x.getAttributeValue(i);
                else if ("drawable".equals(x.getAttributeName(i))) draw = x.getAttributeValue(i);
            }
            if (comp == null || draw == null || !comp.startsWith("ComponentInfo{")) continue;
            String c = comp.substring(14, comp.length() - (comp.endsWith("}") ? 1 : 0));
            byComponent.put(c, draw);
            int slash = c.indexOf('/');
            if (slash > 0 && !byPackage.containsKey(c.substring(0, slash))) byPackage.put(c.substring(0, slash), draw);
        }
    }

    Drawable get(ComponentName cn) {
        if (cn == null) return null;
        String name = byComponent.get(cn.getPackageName() + "/" + cn.getClassName());
        if (name == null) name = byPackage.get(cn.getPackageName());
        if (name == null) return null;
        int id = res.getIdentifier(name, "drawable", pkg);
        if (id == 0) return null;
        try {
            return res.getDrawableForDensity(id, android.util.DisplayMetrics.DENSITY_XXHIGH, null);
        } catch (Throwable t) {
            return null;
        }
    }

    static List<String> labels(Map<String, String> m) { return new ArrayList<>(m.values()); }
}
