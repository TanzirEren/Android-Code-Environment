package com.ace.ide;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Library catalog (pure-Java jars from Maven Central) + downloader + project classpath resolver. */
public final class Libs {

    public static final class Lib {
        public final String id, title, desc;
        public final String[] coords;
        Lib(String id, String title, String desc, String... coords) {
            this.id = id; this.title = title; this.desc = desc; this.coords = coords;
        }
    }

    public interface Progress { void onStatus(String msg); }

    private static final String OKHTTP = "com.squareup.okhttp3:okhttp:3.14.9";
    private static final String OKIO = "com.squareup.okio:okio:1.17.2";
    private static final String GSON = "com.google.code.gson:gson:2.10.1";
    private static final String RETROFIT = "com.squareup.retrofit2:retrofit:2.9.0";
    private static final String LANG3 = "org.apache.commons:commons-lang3:3.14.0";
    private static final String RS = "org.reactivestreams:reactive-streams:1.0.4";
    private static final String SLF4J = "org.slf4j:slf4j-api:1.7.36";

    public static final Lib[] CATALOG = {
            new Lib("gson", "Gson 2.10.1", "JSON ⇄ Java objects (Google)", GSON),
            new Lib("okhttp", "OkHttp 3.14.9", "HTTP client for APIs and downloads", OKHTTP, OKIO),
            new Lib("okhttp-log", "OkHttp Logging 3.14.9", "Log HTTP requests / responses", "com.squareup.okhttp3:logging-interceptor:3.14.9", OKHTTP, OKIO),
            new Lib("retrofit", "Retrofit 2.9.0", "Type-safe REST client", RETROFIT, OKHTTP, OKIO),
            new Lib("retrofit-gson", "Retrofit Gson converter", "Retrofit + Gson JSON converter", "com.squareup.retrofit2:converter-gson:2.9.0", RETROFIT, GSON, OKHTTP, OKIO),
            new Lib("jsoup", "Jsoup 1.17.2", "HTML parsing and scraping", "org.jsoup:jsoup:1.17.2"),
            new Lib("lang3", "Commons Lang 3.14", "String / Object / Date helpers", LANG3),
            new Lib("commons-io", "Commons IO 2.15", "File and stream utilities", "commons-io:commons-io:2.15.1"),
            new Lib("commons-codec", "Commons Codec 1.16", "Base64, hex, hashes", "commons-codec:commons-codec:1.16.0"),
            new Lib("commons-coll", "Commons Collections 4.4", "Extra collection types", "org.apache.commons:commons-collections4:4.4"),
            new Lib("commons-csv", "Commons CSV 1.10", "Read / write CSV files", "org.apache.commons:commons-csv:1.10.0"),
            new Lib("commons-text", "Commons Text 1.11", "Text similarity, escaping, templates", "org.apache.commons:commons-text:1.11.0", LANG3),
            new Lib("commons-math", "Commons Math 3.6.1", "Statistics, matrices, numerics", "org.apache.commons:commons-math3:3.6.1"),
            new Lib("guava", "Guava 32 (Android)", "Google core libraries", "com.google.guava:guava:32.1.3-android", "com.google.guava:failureaccess:1.0.2"),
            new Lib("rxjava2", "RxJava 2.2.21", "Reactive streams", "io.reactivex.rxjava2:rxjava:2.2.21", RS),
            new Lib("rxjava3", "RxJava 3.1.8", "Reactive streams (v3)", "io.reactivex.rxjava3:rxjava:3.1.8", RS),
            new Lib("eventbus", "EventBus 3.3.1", "Publish / subscribe between components", "org.greenrobot:eventbus-java:3.3.1"),
            new Lib("joda", "Joda-Time 2.12", "Date and time API", "joda-time:joda-time:2.12.7"),
            new Lib("zxing", "ZXing core 3.5.3", "Generate and decode QR / barcodes", "com.google.zxing:core:3.5.3"),
            new Lib("zip4j", "Zip4j 2.11.5", "ZIP with password / AES", "net.lingala.zip4j:zip4j:2.11.5"),
            new Lib("websocket", "Java-WebSocket 1.5.6", "WebSocket client and server", "org.java-websocket:Java-WebSocket:1.5.6", SLF4J),
            new Lib("mqtt", "Eclipse Paho MQTT 1.2.5", "MQTT client", "org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5"),
            new Lib("commonmark", "CommonMark 0.21", "Markdown → HTML", "org.commonmark:commonmark:0.21.0"),
            new Lib("bouncy", "BouncyCastle 1.77", "Crypto provider (large, ~7 MB)", "org.bouncycastle:bcprov-jdk15to18:1.77"),
            new Lib("ormlite", "ORMLite 5.1", "Simple SQLite ORM", "com.j256.ormlite:ormlite-core:5.1", "com.j256.ormlite:ormlite-android:5.1"),
            new Lib("picasso", "Picasso 2.5.2", "Image loading", "com.squareup.picasso:picasso:2.5.2"),
            new Lib("jbcrypt", "jBCrypt 0.4", "Password hashing", "org.mindrot:jbcrypt:0.4"),
            new Lib("httpreq", "HttpRequest 6.0", "Tiny fluent HttpURLConnection wrapper", "com.github.kevinsawicki:http-request:6.0"),
            new Lib("slf4j", "SLF4J + Android logger", "Logging facade to Logcat", SLF4J, "org.slf4j:slf4j-android:1.7.36"),
            new Lib("annotations", "JetBrains Annotations", "@NotNull / @Nullable", "org.jetbrains:annotations:24.1.0"),
    };

    private Libs() {}

    public static Lib byId(String id) {
        for (Lib l : CATALOG) if (l.id.equals(id)) return l;
        return null;
    }

    public static File cacheDir(Context c) {
        File d = new File(c.getFilesDir(), "libcache");
        d.mkdirs();
        return d;
    }

    /** "group:artifact:version[@aar]" */
    public static String[] parse(String coord) {
        String ext = "jar";
        String s = coord.trim();
        int at = s.indexOf('@');
        if (at > 0) { ext = s.substring(at + 1); s = s.substring(0, at); }
        String[] p = s.split(":");
        if (p.length != 3) return null;
        return new String[]{p[0], p[1], p[2], ext};
    }

    public static File cacheFile(Context c, String coord) {
        String[] p = parse(coord);
        if (p == null) return null;
        return new File(new File(cacheDir(c), p[0]), p[1] + "-" + p[2] + "." + p[3]);
    }

    private static final String[] REPOS = {
            "https://repo1.maven.org/maven2/", "https://dl.google.com/dl/android/maven2/", "https://jitpack.io/"};

    /** returns the cached file, downloading it when needed */
    public static File ensure(Context c, String coord, Progress pr) throws IOException {
        String[] p = parse(coord);
        if (p == null) throw new IOException("Bad library coordinate: " + coord + " (use group:artifact:version)");
        File f = cacheFile(c, coord);
        if (f.isFile() && f.length() > 0) return f;
        f.getParentFile().mkdirs();
        String path = p[0].replace('.', '/') + "/" + p[1] + "/" + p[2] + "/" + p[1] + "-" + p[2] + "." + p[3];
        IOException last = null;
        for (String repo : REPOS) {
            try {
                if (pr != null) pr.onStatus("Downloading " + p[1] + "-" + p[2] + "." + p[3] + " …");
                download(repo + path, f);
                return f;
            } catch (IOException e) {
                last = e;
            }
        }
        throw new IOException("Could not download " + coord + " — " + (last == null ? "" : last.getMessage())
                + "\nCheck the coordinate and your internet connection.");
    }

    private static void download(String url, File out) throws IOException {
        File tmp = new File(out.getPath() + ".part");
        HttpURLConnection con = null;
        try {
            String u = url;
            for (int hop = 0; hop < 5; hop++) {
                con = (HttpURLConnection) new URL(u).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(30000);
                con.setRequestProperty("User-Agent", "ACE-Android-Code-Environment");
                int code = con.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = con.getHeaderField("Location");
                    con.disconnect();
                    if (loc == null) throw new IOException("redirect without location");
                    u = loc;
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                break;
            }
            InputStream in = con.getInputStream();
            FileOutputStream fo = new FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int n;
            try { while ((n = in.read(buf)) > 0) fo.write(buf, 0, n); }
            finally { fo.close(); in.close(); }
            if (out.exists()) out.delete();
            if (!tmp.renameTo(out)) throw new IOException("cannot move download");
        } finally {
            if (con != null) con.disconnect();
            if (tmp.exists()) tmp.delete();
        }
    }

    /** coordinates a project needs (catalog entries + custom), de-duplicated */
    public static Set<String> coordsOf(Project p) {
        Set<String> s = new LinkedHashSet<String>();
        for (String id : p.libs) {
            Lib l = byId(id);
            if (l != null) for (String c : l.coords) s.add(c);
        }
        s.addAll(p.customLibs);
        return s;
    }

    /** all jars/aars for the project: downloaded dependencies + files in the project's libs folders */
    public static List<File> resolve(Context c, Project p, ProjectLayout layout, Progress pr) throws IOException {
        List<File> out = new ArrayList<File>();
        Set<String> names = new LinkedHashSet<String>();
        for (String coord : coordsOf(p)) {
            File f = ensure(c, coord, pr);
            if (names.add(f.getName())) out.add(f);
        }
        if (layout != null) {
            for (File d : layout.libDirs) {
                File[] fs = d.listFiles();
                if (fs == null) continue;
                java.util.Arrays.sort(fs);
                for (File f : fs) {
                    String n = f.getName().toLowerCase();
                    if (f.isFile() && (n.endsWith(".jar") || n.endsWith(".aar")) && names.add(f.getName())) out.add(f);
                }
            }
        }
        return out;
    }
}
