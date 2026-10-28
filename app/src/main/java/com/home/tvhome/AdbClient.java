package com.home.tvhome;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * A tiny client for the TV's own debugging connection (the same one a computer uses on port 5555).
 * TV Home uses it once, with your permission, to make itself the Home app and give itself
 * the permissions Android won't grant from a normal prompt. Only a fixed list of commands is ever sent.
 */
final class AdbClient implements Closeable {

    interface Status { void say(String message); }

    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f,
            OKAY = 0x59414b4f, CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int VERSION = 0x01000001;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private int nextId = 1;

    /** Connects and signs in. If the TV hasn't seen this key before, it shows "Allow debugging?". */
    void connect(Context ctx, String host, int port, Status status) throws Exception {
        KeyPair keys = keys(ctx);
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 4000);
        socket.setSoTimeout(10000);
        socket.setTcpNoDelay(true);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        send(CNXN, VERSION, 256 * 1024, "host::\0".getBytes("UTF-8"));
        boolean signed = false, offeredKey = false;
        while (true) {
            Msg m = read();
            if (m.cmd == CNXN) break;
            if (m.cmd != AUTH || m.arg0 != AUTH_TOKEN) continue;
            if (!signed) {
                signed = true;
                send(AUTH, AUTH_SIGNATURE, 0, sign((RSAPrivateKey) keys.getPrivate(), m.data));
            } else if (!offeredKey) {
                offeredKey = true;
                if (status != null) status.say("On the TV, tick Always allow and choose Allow.");
                socket.setSoTimeout(90000);   // time to answer the TV's question
                send(AUTH, AUTH_RSAPUBLICKEY, 0, publicKey((RSAPublicKey) keys.getPublic()));
            } else {
                throw new Exception("The TV didn't allow the connection");
            }
        }
        socket.setSoTimeout(20000);
    }

    /** Runs one command and returns what it printed. */
    String shell(String command) throws Exception {
        int local = nextId++;
        send(OPEN, local, 0, ("shell:" + command + "\0").getBytes("UTF-8"));
        int remote = 0;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        while (true) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == OKAY) { remote = m.arg0; continue; }
            if (m.cmd == WRTE) {
                result.write(m.data);
                send(OKAY, local, m.arg0, new byte[0]);
            } else if (m.cmd == CLSE) {
                send(CLSE, local, remote != 0 ? remote : m.arg0, new byte[0]);
                break;
            }
        }
        return result.toString("UTF-8").trim();
    }

    @Override public void close() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
    }

    // ---------- Messages ----------

    private static final class Msg { int cmd, arg0, arg1; byte[] data; }

    private void send(int cmd, int arg0, int arg1, byte[] data) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (byte x : data) sum += x & 0xFF;
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~cmd);
        b.put(data);
        out.write(b.array());
        out.flush();
    }

    private Msg read() throws Exception {
        byte[] h = new byte[24];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        if (len < 0 || len > 1024 * 1024) throw new Exception("Unexpected reply from the TV");
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    // ---------- Keys ----------

    /** This TV Home's own key, made once and kept in its private storage. */
    private static KeyPair keys(Context ctx) throws Exception {
        File priv = new File(ctx.getFilesDir(), "adbkey"), pub = new File(ctx.getFilesDir(), "adbkey.pub");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.exists() && pub.exists()) {
            try {
                return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(readAll(pub))),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv))));
            } catch (Exception ignored) { /* make a new one below */ }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        writeAll(priv, kp.getPrivate().getEncoded());
        writeAll(pub, kp.getPublic().getEncoded());
        return kp;
    }

    /** RSA signature of the TV's token, treated as an already-hashed SHA-1 value (what adbd expects). */
    private static byte[] sign(RSAPrivateKey key, byte[] token) {
        byte[] prefix = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
        int len = (key.getModulus().bitLength() + 7) / 8;
        byte[] em = new byte[len];
        int t = prefix.length + token.length;
        em[0] = 0;
        em[1] = 1;
        for (int i = 2; i < len - t - 1; i++) em[i] = (byte) 0xFF;
        em[len - t - 1] = 0;
        System.arraycopy(prefix, 0, em, len - t, prefix.length);
        System.arraycopy(token, 0, em, len - token.length, token.length);
        BigInteger s = new BigInteger(1, em).modPow(key.getPrivateExponent(), key.getModulus());
        return fixed(s, len, false);
    }

    /** The public key in Android's own format, base64, followed by a name the TV shows. */
    private static byte[] publicKey(RSAPublicKey key) throws Exception {
        BigInteger n = key.getModulus();
        int bytes = 256, words = bytes / 4;
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32));
        BigInteger rr = BigInteger.ONE.shiftLeft(bytes * 8 * 2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + bytes + bytes + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        b.put(fixed(n, bytes, true));
        b.put(fixed(rr, bytes, true));
        b.putInt(key.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " TV Home@tv\0";
        return text.getBytes("UTF-8");
    }

    private static byte[] fixed(BigInteger v, int len, boolean littleEndian) {
        byte[] raw = v.toByteArray();
        byte[] outB = new byte[len];
        int copy = Math.min(raw.length, len);
        System.arraycopy(raw, raw.length - copy, outB, len - copy, copy);
        if (littleEndian) {
            for (int i = 0, j = len - 1; i < j; i++, j--) { byte x = outB[i]; outB[i] = outB[j]; outB[j] = x; }
        }
        return outB;
    }

    private static byte[] readAll(File f) throws Exception {
        FileInputStream s = new FileInputStream(f);
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = s.read(buf)) > 0) o.write(buf, 0, n);
            return o.toByteArray();
        } finally { s.close(); }
    }

    private static void writeAll(File f, byte[] data) throws Exception {
        FileOutputStream s = new FileOutputStream(f);
        try { s.write(data); } finally { s.close(); }
    }

    // ---------- The setup itself ----------

    /** Result of the automatic setup, one line per step. */
    static final class Report { boolean home; final StringBuilder lines = new StringBuilder(); }

    /**
     * Connects to this TV, makes TV Home the Home app and gives it its permissions.
     * Runs on a background thread.
     */
    static Report runSetup(Context ctx, Status status, boolean remap) throws Exception {
        String pkg = ctx.getPackageName();
        Report r = new Report();
        AdbClient adb = new AdbClient();
        try {
            if (status != null) status.say("Connecting to this TV…");
            adb.connect(ctx, "127.0.0.1", 5555, status);
            if (status != null) status.say("Connected. Setting things up…");

            String home = adb.shell("cmd package set-home-activity --user 0 " + pkg + "/.MainActivity");
            if (!home.toLowerCase().contains("success")) {
                home = adb.shell("cmd package set-home-activity " + pkg + "/.MainActivity");
            }
            if (!home.toLowerCase().contains("success")) {
                home = adb.shell("cmd role add-role-holder --user 0 android.app.role.HOME " + pkg);
            }
            String check = adb.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME");
            r.home = check.contains(pkg);
            r.lines.append(r.home ? "✓  TV Home is the Home app\n" : "–  Couldn't change the Home app on this TV\n");

            String[] perms = {"android.permission.RECORD_AUDIO", "android.permission.READ_TV_LISTINGS",
                    "android.permission.READ_EXTERNAL_STORAGE", "android.permission.ACCESS_FINE_LOCATION"};
            int ok = 0;
            for (String p : perms) {
                String o = adb.shell("pm grant " + pkg + " " + p);
                if (o.isEmpty()) ok++;
            }
            adb.shell("appops set " + pkg + " ACCESS_RESTRICTED_SETTINGS allow");
            r.lines.append("✓  Permissions given (").append(ok).append(" of ").append(perms.length).append(")\n");

            if (remap) {
                String svc = pkg + "/" + pkg + ".RemapService";
                String cur = adb.shell("settings get secure enabled_accessibility_services");
                if (cur == null || cur.equals("null")) cur = "";
                if (!cur.contains(svc)) cur = cur.isEmpty() ? svc : cur + ":" + svc;
                adb.shell("settings put secure enabled_accessibility_services " + cur);
                adb.shell("settings put secure accessibility_enabled 1");
                r.lines.append("✓  Button remapping turned on\n");
            }
        } finally {
            adb.close();
        }
        return r;
    }
}
