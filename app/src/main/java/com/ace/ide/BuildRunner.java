package com.ace.ide;

import android.app.Activity;
import android.content.Context;

import com.ace.ide.build.ApkBuilder;
import com.ace.ide.build.D8Dexer;
import com.ace.ide.build.Keys;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs an APK build for an app project on a background thread and reports to the UI thread. */
public final class BuildRunner {

    public static final class Diag {
        public final String path;   // absolute path, or null
        public final int line;
        public final String message;
        Diag(String path, int line, String message) { this.path = path; this.line = line; this.message = message; }
    }

    public interface Listener {
        void onStep(String title);
        void onLog(String line);
        void onDone(ApkBuilder.Result r, List<Diag> diags);
    }

    private static volatile boolean running = false;

    public static boolean isRunning() { return running; }

    public static void run(final Activity a, final Project p, final Listener l) {
        if (running) {
            l.onDone(failed("Another build is already running."), new ArrayList<Diag>());
            return;
        }
        running = true;
        final Context app = a.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                ApkBuilder.Result res;
                final ProjectLayout layout = ProjectLayout.detect(p.root());
                try {
                    res = doBuild(a, app, p, layout, l);
                } catch (Throwable t) {
                    res = failed("Build failed: " + t.getMessage());
                }
                final ApkBuilder.Result fr = res;
                final List<Diag> diags = fr.ok ? new ArrayList<Diag>() : parse(fr.errors, layout);
                try {
                    p.builtAt = System.currentTimeMillis();
                    p.status = fr.ok ? Project.ST_OK : Project.ST_ERROR;
                    if (fr.ok && fr.apk != null) p.apkSize = fr.apk.length();
                    Project.upsert(app, p);
                    File out = p.outputDir();
                    out.mkdirs();
                    FileUtil.writeText(new File(out, "last-build.log"), fr.fullLog.toString());
                } catch (Throwable ignored) { }
                running = false;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() { l.onDone(fr, diags); }
                });
            }
        }).start();
    }

    private static ApkBuilder.Result failed(String msg) {
        ApkBuilder.Result r = new ApkBuilder.Result();
        r.ok = false;
        r.errors = msg;
        r.fullLog.append(msg).append('\n');
        return r;
    }

    private static ApkBuilder.Result doBuild(final Activity a, Context app, Project p, ProjectLayout layout, final Listener l) throws Exception {
        if (!layout.valid()) {
            return failed("No AndroidManifest.xml found in this project.\nExpected app/src/main/AndroidManifest.xml (or AndroidManifest.xml in the project root).");
        }
        File jar = Converter.ensureAndroidJar(app);
        if (jar == null || !jar.isFile()) jar = Converter.sysAndroidJar(app);

        post(a, l, "Resolving libraries", null);
        List<File> libs;
        try {
            libs = Libs.resolve(app, p, layout, new Libs.Progress() {
                @Override public void onStatus(String m) { post(a, l, null, m); }
            });
        } catch (Exception e) {
            return failed(e.getMessage());
        }

        ApkBuilder.Config c = new ApkBuilder.Config();
        c.projectDir = p.root();
        c.manifest = layout.manifest;
        for (File f : layout.javaDirs) if (f.isDirectory()) c.javaDirs.add(f);
        for (File f : layout.resDirs) if (f.isDirectory()) c.resDirs.add(f);
        if (layout.assetsDir != null && layout.assetsDir.isDirectory()) c.assetsDir = layout.assetsDir;
        for (File f : layout.jniDirs) if (f.isDirectory()) c.jniDirs.add(f);
        c.libs.addAll(libs);
        c.androidJar = jar;
        c.fwCache = new File(app.getFilesDir(), "sys/fw.cache");
        c.buildDir = p.buildDir();
        c.outApk = p.apkFile();
        c.pkg = p.pkg;
        c.minSdk = p.minSdk;
        c.targetSdk = p.targetSdk;
        c.versionCode = p.versionCode;
        c.versionName = p.versionName;
        c.debug = !p.release;
        try {
            c.signer = Signing.forProject(app, p);
        } catch (Exception e) {
            return failed("Could not open the signing key: " + e.getMessage()
                    + "\nCheck Settings → Signing keys (password / alias).");
        }
        ApkBuilder.Listener bl = new ApkBuilder.Listener() {
            @Override public void step(String title) { post(a, l, title, null); }
            @Override public void log(String line) { post(a, l, null, line); }
        };
        return ApkBuilder.build(c, bl, new D8Dexer());
    }

    private static void post(final Activity a, final Listener l, final String step, final String log) {
        a.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (step != null) l.onStep(step);
                if (log != null) l.onLog(log);
            }
        });
    }

    // ------------------------------------------------------------------ error parsing

    private static final Pattern ECJ = Pattern.compile("ERROR in (.+?) \\(at line (\\d+)\\)");
    private static final Pattern RES = Pattern.compile("^\\[res\\] ERROR: (.+?)(?::(\\d+))?: (.*)$");

    public static List<Diag> parse(String errors, ProjectLayout layout) {
        List<Diag> out = new ArrayList<Diag>();
        if (errors == null) return out;
        String[] blocks = errors.split("----------");
        for (String b : blocks) {
            Matcher m = ECJ.matcher(b);
            if (m.find()) {
                String[] lines = b.split("\n");
                String msg = "";
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].trim().matches("\\^+")) {
                        StringBuilder sb = new StringBuilder();
                        for (int k = i + 1; k < lines.length; k++) sb.append(lines[k].trim()).append(' ');
                        msg = sb.toString().trim();
                        break;
                    }
                }
                out.add(new Diag(m.group(1), Integer.parseInt(m.group(2)), msg));
            }
        }
        for (String line : errors.split("\n")) {
            Matcher m = RES.matcher(line.trim());
            if (!m.matches()) continue;
            String rel = m.group(1);
            int ln = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            String path = null;
            if (layout != null) {
                if (rel.equals("AndroidManifest.xml") && layout.manifest != null) path = layout.manifest.getAbsolutePath();
                else if (rel.startsWith("res/")) {
                    for (File rd : layout.resDirs) {
                        File f = new File(rd, rel.substring(4));
                        if (f.isFile()) { path = f.getAbsolutePath(); break; }
                    }
                }
            }
            out.add(new Diag(path, ln, m.group(3)));
        }
        return out;
    }

    static Keys.KeyInfo unused() { return null; }
}
