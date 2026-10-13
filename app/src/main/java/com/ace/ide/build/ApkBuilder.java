package com.ace.ide.build;

import com.android.apksig.ApkSigner;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Complete on-device APK pipeline:
 * resources -> R.java -> ECJ -> D8 -> package (aligned) -> sign (apksig v1+v2+v3).
 */
public final class ApkBuilder {

    public interface Listener {
        void step(String title);
        void log(String line);
    }

    public static final class Config {
        public File projectDir;
        public File manifest;
        public final List<File> javaDirs = new ArrayList<File>();
        public final List<File> resDirs = new ArrayList<File>();
        public File assetsDir;
        public final List<File> jniDirs = new ArrayList<File>();
        public final List<File> libs = new ArrayList<File>();      // .jar and .aar
        public File androidJar;
        public File fwCache;
        public File buildDir;
        public File outApk;
        public String pkg;
        public int minSdk = 21, targetSdk = 34, versionCode = 1;
        public String versionName = "1.0";
        public boolean debug = true;
        public Keys.KeyInfo signer;
    }

    public static final class Result {
        public boolean ok;
        public File apk;
        public long millis;
        /** everything that went wrong, ready to copy */
        public String errors = "";
        public final StringBuilder fullLog = new StringBuilder();
    }

    private final Config c;
    private final Listener l;
    private final Dexer dexer;
    private final Result r = new Result();

    private ApkBuilder(Config c, Listener l, Dexer dexer) {
        this.c = c;
        this.l = l;
        this.dexer = dexer;
    }

    public static Result build(Config c, Listener l, Dexer dexer) {
        ApkBuilder b = new ApkBuilder(c, l, dexer);
        long t0 = System.currentTimeMillis();
        try {
            b.r.ok = b.run();
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String st = sw.toString();
            if (st.length() > 3000) st = st.substring(0, 3000) + "\n…";
            b.fail("Unexpected build error: " + t + "\n" + st);
            b.r.ok = false;
        }
        b.r.millis = System.currentTimeMillis() - t0;
        return b.r;
    }

    // ------------------------------------------------------------------ helpers

    private void log(String s) {
        r.fullLog.append(s).append('\n');
        if (l != null) l.log(s);
    }

    private void step(String s) {
        log("");
        log("== " + s);
        if (l != null) l.step(s);
    }

    private boolean fail(String msg) {
        if (r.errors.length() > 0) r.errors += "\n";
        r.errors += msg;
        log(msg);
        return false;
    }

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) return;
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) deleteDir(x);
        f.delete();
    }

    private static void collect(File dir, List<File> out, String ext) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) {
            if (f.isDirectory()) {
                String n = f.getName();
                if (n.equals("build") || n.equals(".git")) continue;
                collect(f, out, ext);
            } else if (f.getName().toLowerCase().endsWith(ext)) out.add(f);
        }
    }

    // ------------------------------------------------------------------ pipeline

    private boolean run() throws Exception {
        File build = c.buildDir;
        deleteDir(build);
        File gen = new File(build, "gen");
        File classes = new File(build, "classes");
        File dexOut = new File(build, "dex");
        File libDir = new File(build, "libs");
        gen.mkdirs(); classes.mkdirs(); dexOut.mkdirs(); libDir.mkdirs();

        log("ACE build — " + c.pkg + "  v" + c.versionName + " (" + c.versionCode + ")  "
                + (c.debug ? "debug" : "release") + "  minSdk " + c.minSdk + " / target " + c.targetSdk);

        if (c.androidJar == null || !c.androidJar.isFile()) {
            return fail("android.jar is missing. The app bundles it at build time (GitHub Action step "
                    + "'Bundle android.jar'). Re-install ACE from a CI build.");
        }
        if (c.manifest == null || !c.manifest.isFile()) {
            return fail("AndroidManifest.xml not found in the project.");
        }
        if (c.signer == null) return fail("No signing key selected.");

        // ---- 1. libraries
        step("Preparing libraries");
        List<File> libJars = new ArrayList<File>();
        List<File> jniFromAars = new ArrayList<File>();
        List<File> aarAssets = new ArrayList<File>();
        prepareLibs(libDir, libJars, jniFromAars, aarAssets);

        // ---- 2. resources
        step("Compiling resources");
        FrameworkTable fw = FrameworkTable.load(c.androidJar, c.fwCache);
        ResCompiler.Input in = new ResCompiler.Input();
        in.manifest = c.manifest;
        in.resDirs.addAll(c.resDirs);
        in.pkg = c.pkg;
        in.minSdk = c.minSdk;
        in.targetSdk = c.targetSdk;
        in.versionCode = c.versionCode;
        in.versionName = c.versionName;
        in.debuggable = c.debug;
        in.fw = fw;
        ResCompiler.Output res = ResCompiler.compile(in);
        for (String w : res.warnings) log("[res] warning: " + w);
        if (!res.ok()) {
            StringBuilder sb = new StringBuilder();
            for (String e : res.errors) sb.append("[res] ERROR: ").append(e).append('\n');
            return fail(sb.toString().trim());
        }
        String pkgPath = res.pkg.replace('.', '/');
        File rFile = new File(gen, pkgPath + "/R.java");
        rFile.getParentFile().mkdirs();
        writeText(rFile, res.rJava);
        log("[res] " + res.compiled.size() + " xml compiled, " + res.copied.size() + " files copied, R.java written");

        // ---- 3. java
        step("Compiling Java (ECJ)");
        List<File> sources = new ArrayList<File>();
        for (File jd : c.javaDirs) collect(jd, sources, ".java");
        sources.add(rFile);
        Collections.sort(sources);
        log("[ecj] " + sources.size() + " source files");
        StringBuilder cp = new StringBuilder(c.androidJar.getAbsolutePath());
        for (File j : libJars) cp.append(File.pathSeparator).append(j.getAbsolutePath());
        StringWriter outW = new StringWriter(), errW = new StringWriter();
        PrintWriter pout = new PrintWriter(outW, true), perr = new PrintWriter(errW, true);
        List<String> args = new ArrayList<String>();
        args.add("-1.8"); args.add("-g"); args.add("-nowarn");
        args.add("-encoding"); args.add("UTF-8");
        args.add("-proc:none");
        args.add("-cp"); args.add(cp.toString());
        args.add("-d"); args.add(classes.getAbsolutePath());
        for (File s : sources) args.add(s.getAbsolutePath());
        boolean ok = BatchCompiler.compile(args.toArray(new String[0]), pout, perr, null);
        pout.flush(); perr.flush();
        String ecjOut = (outW.toString() + errW.toString()).trim();
        if (!ok) {
            StringBuilder sb = new StringBuilder(ecjOut);
            if (ecjOut.contains("wrong version") || ecjOut.contains("class file has wrong version")) {
                sb.append("\n\nHint: a library was built for a newer Java than the on-device compiler (Java 8) supports. "
                        + "Remove it or use an older version of that library.");
            }
            return fail(sb.toString());
        }
        if (ecjOut.length() > 0) log(ecjOut);
        log("[ecj] compiled successfully");

        // ---- 4. dex
        step("Converting to DEX (D8)");
        StringBuilder dlog = new StringBuilder();
        String derr = dexer.dex(Collections.singletonList(classes), libJars, c.androidJar, c.minSdk, c.debug, dexOut, dlog);
        if (dlog.length() > 0) log(dlog.toString().trim());
        if (derr != null) return fail((dlog.toString().contains("ERROR") ? dlog.toString().trim() + "\n" : "") + derr);
        File[] dexes = dexOut.listFiles();
        List<File> dexFiles = new ArrayList<File>();
        if (dexes != null) for (File f : dexes) if (f.getName().startsWith("classes") && f.getName().endsWith(".dex")) dexFiles.add(f);
        if (dexFiles.isEmpty()) return fail("D8 produced no classes.dex");
        Collections.sort(dexFiles);
        log("[d8] " + dexFiles.size() + " dex file(s)");

        // ---- 5. package
        step("Packaging APK");
        File unsigned = new File(build, "unsigned.apk");
        ApkZip zip = new ApkZip(unsigned);
        try {
            zip.addDeflated("AndroidManifest.xml", res.manifest);
            for (File d : dexFiles) zip.addDeflated(d.getName(), ApkZip.readAll(d));
            zip.addStored("resources.arsc", res.arsc, 4);
            for (Map.Entry<String, byte[]> e : res.compiled.entrySet()) zip.addDeflated(e.getKey(), e.getValue());
            for (Map.Entry<String, File> e : res.copied.entrySet()) zip.addFile(e.getKey(), e.getValue(), false);
            if (c.assetsDir != null) addTree(zip, c.assetsDir, "assets/");
            for (File a : aarAssets) addTree(zip, a, "assets/");
            for (File jni : c.jniDirs) addJni(zip, jni);
            for (File jni : jniFromAars) addJni(zip, jni);
            copyJavaResources(zip, libJars);
        } finally {
            zip.close();
        }
        log("[pkg] unsigned apk " + unsigned.length() + " bytes");

        // ---- 6. sign
        step("Signing APK");
        File outApk = c.outApk;
        outApk.getParentFile().mkdirs();
        if (outApk.exists()) outApk.delete();
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder(
                "ACE", c.signer.key, Arrays.asList(c.signer.chain)).build();
        new ApkSigner.Builder(Collections.singletonList(sc))
                .setInputApk(unsigned)
                .setOutputApk(outApk)
                .setMinSdkVersion(Math.max(1, c.minSdk))
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setV4SigningEnabled(false)
                .setCreatedBy("ACE Android Code Environment")
                .build()
                .sign();
        log("[sign] signed with " + c.signer.chain[0].getSubjectDN());
        log("[sign] SHA-256 " + Keys.fingerprint(c.signer.chain[0], "SHA-256"));
        r.apk = outApk;
        log("");
        log("BUILD SUCCESSFUL — " + outApk.getAbsolutePath() + " (" + outApk.length() + " bytes)");
        return true;
    }

    // ------------------------------------------------------------------ libraries

    private void prepareLibs(File libDir, List<File> jars, List<File> jni, List<File> assets) throws Exception {
        Set<String> seen = new HashSet<String>();
        for (File lib : c.libs) {
            if (lib == null || !lib.isFile()) continue;
            String n = lib.getName().toLowerCase();
            String base = lib.getName().replaceAll("[^A-Za-z0-9._-]", "_");
            if (n.endsWith(".jar")) {
                if (!seen.add(base)) continue;
                File clean = new File(libDir, base);
                cleanJar(lib, clean);
                jars.add(clean);
                log("[lib] " + lib.getName());
            } else if (n.endsWith(".aar")) {
                if (!seen.add(base)) continue;
                File dir = new File(libDir, base + ".x");
                dir.mkdirs();
                ZipFile zf = new ZipFile(lib);
                try {
                    boolean hasRes = false;
                    Enumeration<? extends ZipEntry> en = zf.entries();
                    int extra = 0;
                    while (en.hasMoreElements()) {
                        ZipEntry e = en.nextElement();
                        String en2 = e.getName();
                        if (e.isDirectory()) continue;
                        if (en2.equals("classes.jar")) {
                            File out = new File(libDir, base + ".classes.jar");
                            File tmp = new File(dir, "classes.jar");
                            extract(zf, e, tmp);
                            cleanJar(tmp, out);
                            jars.add(out);
                        } else if (en2.startsWith("libs/") && en2.endsWith(".jar")) {
                            File tmp = new File(dir, "libs_" + (extra++) + ".jar");
                            extract(zf, e, tmp);
                            File out = new File(libDir, base + "." + extra + ".jar");
                            cleanJar(tmp, out);
                            jars.add(out);
                        } else if (en2.startsWith("jni/")) {
                            extract(zf, e, new File(dir, "jni/" + en2.substring(4)));
                        } else if (en2.startsWith("assets/")) {
                            extract(zf, e, new File(dir, "assets/" + en2.substring(7)));
                        } else if (en2.startsWith("res/") && !en2.endsWith("/")) {
                            hasRes = true;
                        }
                    }
                    if (new File(dir, "jni").isDirectory()) jni.add(new File(dir, "jni"));
                    if (new File(dir, "assets").isDirectory()) assets.add(new File(dir, "assets"));
                    log("[lib] " + lib.getName() + " (classes only)");
                    if (hasRes) log("[lib] warning: " + lib.getName() + " contains Android resources (res/) — "
                            + "ACE does not merge library resources, views/styles from it will not work.");
                } finally {
                    zf.close();
                }
            }
        }
    }

    private static void extract(ZipFile zf, ZipEntry e, File out) throws Exception {
        out.getParentFile().mkdirs();
        InputStream in = zf.getInputStream(e);
        FileOutputStream fo = new FileOutputStream(out);
        byte[] buf = new byte[16384];
        int n;
        try { while ((n = in.read(buf)) > 0) fo.write(buf, 0, n); }
        finally { in.close(); fo.close(); }
    }

    /** copy a jar without multi-release / module-info entries (ECJ 4 and D8 choke on them) */
    private static void cleanJar(File in, File out) throws Exception {
        ZipFile zf = new ZipFile(in);
        ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(out));
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            byte[] buf = new byte[16384];
            Set<String> done = new HashSet<String>();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || n.startsWith("META-INF/versions/") || n.endsWith("module-info.class")
                        || n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC")) continue;
                if (!done.add(n)) continue;
                zo.putNextEntry(new ZipEntry(n));
                InputStream is = zf.getInputStream(e);
                int k;
                while ((k = is.read(buf)) > 0) zo.write(buf, 0, k);
                is.close();
                zo.closeEntry();
            }
        } finally {
            zo.close();
            zf.close();
        }
    }

    private void copyJavaResources(ApkZip zip, List<File> jars) throws Exception {
        for (File j : jars) {
            ZipFile zf = new ZipFile(j);
            try {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (e.isDirectory() || n.endsWith(".class") || n.endsWith(".java")) continue;
                    if (n.startsWith("META-INF/") && !n.startsWith("META-INF/services/")) continue;
                    if (n.equals("AndroidManifest.xml") || n.equals("resources.arsc") || n.startsWith("res/")
                            || n.startsWith("assets/") || n.startsWith("lib/")) continue;
                    if (zip.has(n)) continue;
                    InputStream in = zf.getInputStream(e);
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int k;
                    while ((k = in.read(buf)) > 0) bo.write(buf, 0, k);
                    in.close();
                    zip.addDeflated(n, bo.toByteArray());
                }
            } finally {
                zf.close();
            }
        }
    }

    private void addTree(ApkZip zip, File dir, String prefix) throws Exception {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) addTree(zip, f, prefix + f.getName() + "/");
            else zip.addFile(prefix + f.getName(), f, false);
        }
    }

    private void addJni(ApkZip zip, File jniDir) throws Exception {
        File[] abis = jniDir.listFiles();
        if (abis == null) return;
        for (File abi : abis) {
            if (!abi.isDirectory()) continue;
            File[] sos = abi.listFiles();
            if (sos == null) continue;
            for (File so : sos) {
                if (so.getName().endsWith(".so")) zip.addFile("lib/" + abi.getName() + "/" + so.getName(), so, false);
            }
        }
    }

    private static void writeText(File f, String s) throws Exception {
        FileOutputStream fo = new FileOutputStream(f);
        try { fo.write(s.getBytes("UTF-8")); } finally { fo.close(); }
    }
}
