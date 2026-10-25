package com.home.tvhome;

import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;

/**
 * "Send from Phone": a tiny web page on your home Wi-Fi. Open the link on your phone, pick a photo,
 * and it arrives on the TV. The link has a random one-time code, it only accepts pictures, and it
 * switches itself off after one photo or ten minutes.
 */
final class PhoneUpload {

    interface Callback {
        void onPhoto(File photo);
        void onError(String message);
    }

    private static final int PORT = 8765;
    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final long LIFETIME_MS = 10 * 60 * 1000L;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final File dest;
    private final Callback callback;
    private volatile boolean running;
    private ServerSocket server;
    private String token;

    PhoneUpload(File dest, Callback callback) {
        this.dest = dest;
        this.callback = callback;
    }

    /** Starts the page and returns its address (http://192.168.x.x:8765/code), or null if there's no Wi-Fi. */
    String start() {
        String ip = localIp();
        if (ip == null) return null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            try {
                server.bind(new java.net.InetSocketAddress(PORT));
            } catch (Exception busy) {
                server = new ServerSocket(0);
            }
            server.setSoTimeout(1000);
        } catch (Exception e) {
            return null;
        }
        SecureRandom r = new SecureRandom();
        String abc = "abcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(abc.charAt(r.nextInt(abc.length())));
        token = sb.toString();
        running = true;
        final long until = System.currentTimeMillis() + LIFETIME_MS;
        new Thread(new Runnable() {
            @Override public void run() {
                while (running && System.currentTimeMillis() < until) {
                    try {
                        Socket s = server.accept();
                        handle(s);
                    } catch (SocketTimeoutException t) {
                        // check whether we should stop, then keep listening
                    } catch (Exception ignored) { }
                }
                stop();
            }
        }, "PhoneUpload").start();
        return "http://" + ip + ":" + server.getLocalPort() + "/" + token;
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            String requestLine = readLine(in);
            if (requestLine == null) { s.close(); return; }
            long length = -1;
            String line;
            while ((line = readLine(in)) != null && line.length() > 0) {
                String l = line.toLowerCase(Locale.US);
                if (l.startsWith("content-length:")) {
                    try { length = Long.parseLong(line.substring(15).trim()); } catch (Exception ignored) { }
                }
            }
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "", path = parts.length > 1 ? parts[1] : "";
            if (method.equals("GET") && path.equals("/" + token)) {
                reply(out, 200, "text/html; charset=utf-8", page());
            } else if (method.equals("POST") && path.equals("/" + token + "/upload")) {
                if (length <= 0 || length > MAX_BYTES) {
                    reply(out, 413, "text/plain", "That picture is too big. Try a smaller one.");
                } else {
                    File tmp = new File(dest.getParentFile(), "upload.tmp");
                    dest.getParentFile().mkdirs();
                    FileOutputStream fo = new FileOutputStream(tmp);
                    byte[] buf = new byte[65536];
                    long left = length;
                    int n;
                    while (left > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, left))) > 0) {
                        fo.write(buf, 0, n);
                        left -= n;
                    }
                    fo.close();
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(tmp.getPath(), o);
                    if (left > 0 || o.outWidth <= 0) {
                        tmp.delete();
                        reply(out, 415, "text/plain", "That doesn't look like a photo. Try a JPEG or PNG.");
                    } else {
                        dest.delete();
                        tmp.renameTo(dest);
                        reply(out, 200, "text/plain", "ok");
                        running = false;
                        ui.post(new Runnable() { @Override public void run() { callback.onPhoto(dest); } });
                    }
                }
            } else {
                reply(out, 404, "text/plain", "Not found. Use the link shown on your TV.");
            }
            s.close();
        } catch (Exception e) {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) break;
        }
        if (c == -1 && b.size() == 0) return null;
        return b.toString("UTF-8");
    }

    private static void reply(OutputStream out, int code, String type, String body) throws Exception {
        byte[] data = body.getBytes("UTF-8");
        String status = code == 200 ? "OK" : code == 404 ? "Not Found" : "Error";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + data.length + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.write(data);
        out.flush();
    }

    /** The page your phone sees. It shrinks the photo on the phone first, so it sends quickly. */
    private String page() {
        return "<!doctype html><html><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'><title>TV Home</title>"
                + "<style>body{margin:0;font-family:-apple-system,system-ui,sans-serif;background:#0d0f14;color:#fff;"
                + "display:flex;min-height:100vh;align-items:center;justify-content:center}"
                + "main{max-width:420px;padding:24px;text-align:center}h1{font-weight:300;font-size:28px;margin:0 0 8px}"
                + "p{color:#b8bcc6;margin:0 0 28px}label{display:inline-block;background:#fff;color:#111317;"
                + "padding:14px 26px;border-radius:28px;font-weight:600;font-size:17px}input{display:none}"
                + "#s{margin-top:22px;min-height:24px;color:#d6d9e0}</style></head><body><main>"
                + "<h1>Send a photo to your TV</h1><p>It becomes your TV Home wallpaper.</p>"
                + "<label>Choose a photo<input id=f type=file accept='image/*'></label><div id=s></div></main>"
                + "<script>const s=document.getElementById('s');"
                + "document.getElementById('f').onchange=async e=>{const file=e.target.files[0];if(!file)return;"
                + "s.textContent='Sending…';try{const img=await createImageBitmap(file);"
                + "const k=Math.min(1,1920/Math.max(img.width,img.height));const c=document.createElement('canvas');"
                + "c.width=Math.round(img.width*k);c.height=Math.round(img.height*k);"
                + "c.getContext('2d').drawImage(img,0,0,c.width,c.height);"
                + "const blob=await new Promise(r=>c.toBlob(r,'image/jpeg',0.9));"
                + "const r=await fetch(location.pathname+'/upload',{method:'POST',body:blob});"
                + "s.textContent=r.ok?'Done! Look at your TV.':await r.text();}"
                + "catch(err){s.textContent='Could not reach the TV. Start Send from Phone again.';}};</script>"
                + "</body></html>";
    }

    private static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
}
