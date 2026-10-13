package com.ace.ide;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds manifest / java / res folders of an Android project (ACE, Android Studio, AIDE …). */
public final class ProjectLayout {

    public File root;
    public File manifest;
    public File mainJava;
    public File assetsDir;
    public File moduleDir;
    public final List<File> javaDirs = new ArrayList<File>();
    public final List<File> resDirs = new ArrayList<File>();
    public final List<File> jniDirs = new ArrayList<File>();
    public final List<File> libDirs = new ArrayList<File>();

    public boolean valid() { return manifest != null && manifest.isFile(); }

    public static ProjectLayout detect(File root) {
        ProjectLayout l = new ProjectLayout();
        l.root = root;
        File base = null;
        File[] pref = {new File(root, "app/src/main"), new File(root, "src/main")};
        for (File p : pref) if (new File(p, "AndroidManifest.xml").isFile()) { base = p; break; }
        if (base == null) base = search(root, 0);
        if (base != null) {
            l.manifest = new File(base, "AndroidManifest.xml");
            l.mainJava = new File(base, "java");
            l.javaDirs.add(l.mainJava);
            File kotlinLess = new File(base, "kotlin");
            if (kotlinLess.isDirectory()) l.javaDirs.add(kotlinLess);
            l.resDirs.add(new File(base, "res"));
            l.assetsDir = new File(base, "assets");
            l.jniDirs.add(new File(base, "jniLibs"));
            File module = base.getParentFile() == null ? root : base.getParentFile().getParentFile();
            l.moduleDir = module == null ? root : module;
            l.libDirs.add(new File(l.moduleDir, "libs"));
            if (!l.moduleDir.equals(root)) l.libDirs.add(new File(root, "libs"));
            return l;
        }
        // flat layout (AIDE / Eclipse): AndroidManifest.xml in the project root
        File flat = new File(root, "AndroidManifest.xml");
        if (flat.isFile()) {
            l.manifest = flat;
            File src = new File(root, "src");
            File java = new File(root, "java");
            l.mainJava = java.isDirectory() ? java : src;
            l.javaDirs.add(l.mainJava);
            File gen = new File(root, "gen");
            l.resDirs.add(new File(root, "res"));
            l.assetsDir = new File(root, "assets");
            l.jniDirs.add(new File(root, "jniLibs"));
            l.jniDirs.add(new File(root, "libs"));
            l.moduleDir = root;
            l.libDirs.add(new File(root, "libs"));
            if (gen.isDirectory()) { /* generated R.java is ignored, ACE makes its own */ }
        }
        return l;
    }

    private static File search(File dir, int depth) {
        if (depth > 4) return null;
        File[] fs = dir.listFiles();
        if (fs == null) return null;
        java.util.Arrays.sort(fs);
        for (File f : fs) {
            if (!f.isDirectory()) continue;
            String n = f.getName();
            if (n.equals("build") || n.startsWith(".") || n.equals("output") || n.equals("node_modules")) continue;
            if (n.equals("main") && new File(f, "AndroidManifest.xml").isFile()
                    && f.getParentFile() != null && f.getParentFile().getName().equals("src")) return f;
            File r = search(f, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    /** fill project settings from manifest + gradle files (used after import / clone) */
    public static void guessConfig(Project p, ProjectLayout l) {
        try {
            File ace = new File(p.path, "ace.json");
            if (ace.isFile()) {
                p.applyConfig(new JSONObject(FileUtil.readText(ace)));
                return;
            }
        } catch (Throwable ignored) { }
        if (!l.valid()) return;
        try {
            String mf = FileUtil.readText(l.manifest);
            Matcher m = Pattern.compile("package\\s*=\\s*\"([^\"]+)\"").matcher(mf);
            if (m.find()) p.pkg = m.group(1);
            for (String gname : new String[]{"build.gradle", "build.gradle.kts"}) {
                File g = new File(l.moduleDir, gname);
                if (!g.isFile()) continue;
                String gs = FileUtil.readText(g);
                String v;
                // the R class lives in the namespace / manifest package, so prefer that over applicationId
                if ((v = find(gs, "namespace\\s*=?\\s*[\"']([^\"']+)[\"']")) != null) p.pkg = v;
                else if (m.find(0) == false && (v = find(gs, "applicationId\\s*=?\\s*[\"']([^\"']+)[\"']")) != null) p.pkg = v;
                if ((v = find(gs, "minSdk(?:Version)?\\s*=?\\s*(\\d+)")) != null) p.minSdk = Integer.parseInt(v);
                if ((v = find(gs, "targetSdk(?:Version)?\\s*=?\\s*(\\d+)")) != null) p.targetSdk = Integer.parseInt(v);
                if ((v = find(gs, "versionCode\\s*=?\\s*(\\d+)")) != null) p.versionCode = Integer.parseInt(v);
                if ((v = find(gs, "versionName\\s*=?\\s*[\"']([^\"']+)[\"']")) != null) p.versionName = v;
                break;
            }
            if (p.minSdk < 21) p.minSdk = 21;
            if (p.targetSdk < p.minSdk) p.targetSdk = Math.max(p.minSdk, 34);
            // app name from strings.xml
            for (File rd : l.resDirs) {
                File sx = new File(rd, "values/strings.xml");
                if (sx.isFile()) {
                    String s = FileUtil.readText(sx);
                    String n = find(s, "<string\\s+name=\"app_name\"[^>]*>([^<]*)</string>");
                    if (n != null) { p.appName = n.trim(); break; }
                }
            }
        } catch (Throwable ignored) { }
        if (p.appName == null || p.appName.length() == 0) p.appName = p.name;
    }

    private static String find(String s, String re) {
        Matcher m = Pattern.compile(re).matcher(s);
        return m.find() ? m.group(1) : null;
    }
}
