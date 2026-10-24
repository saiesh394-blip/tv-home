package com.home.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Installs apps you choose, using Android's own installer (the TV always asks you to confirm).
 * Sources: Aurora Store and F-Droid from f-droid.org, an APK link you type, or an APK file
 * already on the TV or a USB drive.
 */
final class Installer {

    private static final String ACTION_DONE = "com.home.tvhome.INSTALL_DONE";
    private static final String UA = "TVHome/5.6";

    private final Activity act;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean receiverOn;

    Installer(Activity act) {
        this.act = act;
    }

    void showMenu() {
        final String[] items = {
                "Aurora Store  ·  Play Store apps without a Google account",
                "F-Droid  ·  Free, open-source apps",
                "From a link…",
                "From this TV or a USB drive"};
        new AlertDialog.Builder(act)
                .setTitle("Install apps")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (!allowedToInstall()) return;
                        if (which == 0) installFromFDroid("com.aurora.store", "Aurora Store");
                        else if (which == 1) download("https://f-droid.org/F-Droid.apk", "F-Droid");
                        else if (which == 2) askForLink();
                        else pickLocalFile();
                    }
                })
                .show();
    }

    /** Android asks once whether TV Home may install apps. */
    private boolean allowedToInstall() {
        if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(act, "Allow TV Home to install apps, then come back and try again.",
                    Toast.LENGTH_LONG).show();
            try {
                act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + act.getPackageName())));
            } catch (Exception e) {
                try {
                    act.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                } catch (Exception ignored) { }
            }
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- sources

    private void installFromFDroid(final String pkg, final String name) {
        toast("Finding the latest " + name + "…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject j = new JSONObject(getText("https://f-droid.org/api/v1/packages/" + pkg));
                    long code = j.getLong("suggestedVersionCode");
                    final String url = "https://f-droid.org/repo/" + pkg + "_" + code + ".apk";
                    ui.post(new Runnable() { @Override public void run() { download(url, name); } });
                } catch (Exception e) {
                    toast("Couldn't reach F-Droid. Check the internet and try again.");
                }
            }
        }).start();
    }

    private void askForLink() {
        final EditText field = new EditText(act);
        field.setHint("https://example.com/app.apk");
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        FrameLayout box = new FrameLayout(act);
        int pad = Math.round(20 * act.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field);
        new AlertDialog.Builder(act)
                .setTitle("Install from a link")
                .setMessage("Only use links from developers you trust.")
                .setView(box)
                .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String url = field.getText().toString().trim();
                        if (!url.startsWith("https://")) {
                            toast("Use a link that starts with https://");
                            return;
                        }
                        String name = Uri.parse(url).getLastPathSegment();
                        download(url, name == null ? "the app" : name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickLocalFile() {
        if (Build.VERSION.SDK_INT >= 23 && act.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 44);
            toast("Allow access to files, then choose \"From this TV or a USB drive\" again.");
            return;
        }
        final List<File> found = new ArrayList<>();
        List<File> roots = new ArrayList<>();
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        roots.add(Environment.getExternalStorageDirectory());
        File storage = new File("/storage");
        File[] vols = storage.listFiles();
        if (vols != null) for (File v : vols) {
            String n = v.getName();
            if (!n.equals("emulated") && !n.equals("self")) roots.add(v);   // USB drives
        }
        for (File r : roots) scan(r, found, 2);
        if (found.isEmpty()) {
            toast("No APK files found in Downloads or on a USB drive.");
            return;
        }
        String[] names = new String[found.size()];
        for (int i = 0; i < found.size(); i++) {
            File f = found.get(i);
            names[i] = f.getName() + "  ·  " + (f.length() / (1024 * 1024)) + " MB";
        }
        new AlertDialog.Builder(act)
                .setTitle("Choose an APK")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        final File f = found.get(which);
                        toast("Preparing " + f.getName() + "…");
                        new Thread(new Runnable() {
                            @Override public void run() { install(f, f.getName()); }
                        }).start();
                    }
                })
                .show();
    }

    private static void scan(File dir, List<File> out, int depth) {
        if (dir == null || depth < 0) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".") && !f.getName().equals("Android")) scan(f, out, depth - 1);
            } else if (f.getName().toLowerCase(Locale.US).endsWith(".apk") && !out.contains(f)) {
                out.add(f);
            }
        }
    }

    // ---------------------------------------------------------------- download and install

    private void download(final String url, final String name) {
        toast("Downloading " + name + "…");
        new Thread(new Runnable() {
            @Override public void run() {
                File dir = new File(act.getCacheDir(), "apks");
                dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File o : old) o.delete();
                File f = new File(dir, "download.apk");
                try {
                    HttpURLConnection c = open(url);
                    try {
                        InputStream in = c.getInputStream();
                        OutputStream out = new FileOutputStream(f);
                        byte[] buf = new byte[65536];
                        int n;
                        long total = 0, size = c.getContentLength(), nextReport = 5L * 1024 * 1024;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            total += n;
                            if (size > 0 && total >= nextReport) {
                                toast("Downloading " + name + "… " + (100 * total / size) + "%");
                                nextReport += 5L * 1024 * 1024;
                            }
                        }
                        out.close();
                        in.close();
                    } finally {
                        c.disconnect();
                    }
                    install(f, name);
                } catch (Exception e) {
                    f.delete();
                    toast("Download failed. Check the link and the internet, then try again.");
                }
            }
        }).start();
    }

    /** Hands the APK to Android's installer, which shows its own confirmation screen. */
    private void install(File apk, String name) {
        try {
            registerReceiver();
            PackageInstaller pi = act.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int id = pi.createSession(params);
            PackageInstaller.Session s = pi.openSession(id);
            InputStream in = new FileInputStream(apk);
            OutputStream out = s.openWrite("app.apk", 0, apk.length());
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            s.fsync(out);
            out.close();
            in.close();
            Intent done = new Intent(ACTION_DONE).setPackage(act.getPackageName()).putExtra("name", name);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= 0x02000000;   // FLAG_MUTABLE: the installer adds the result
            PendingIntent pend = PendingIntent.getBroadcast(act, id, done, flags);
            s.commit(pend.getIntentSender());
            s.close();
        } catch (Exception e) {
            toast("Couldn't install " + name + ". The file may be damaged or not made for this TV.");
        }
    }

    private void registerReceiver() {
        if (receiverOn) return;
        receiverOn = true;
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                String name = i.getStringExtra("name");
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try { act.startActivity(confirm); } catch (Exception ignored) { }
                    }
                } else if (status == PackageInstaller.STATUS_SUCCESS) {
                    toast((name == null ? "App" : name) + " installed. It's now in All apps.");
                } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                    toast("Install cancelled.");
                } else {
                    String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    toast("Couldn't install" + (msg == null ? "." : ": " + msg));
                }
            }
        };
        act.getApplicationContext().registerReceiver(r, new IntentFilter(ACTION_DONE));
    }

    // ---------------------------------------------------------------- helpers

    private void toast(final String msg) {
        ui.post(new Runnable() {
            @Override public void run() { Toast.makeText(act, msg, Toast.LENGTH_LONG).show(); }
        });
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        return c;
    }

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
}
