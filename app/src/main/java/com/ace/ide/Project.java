package com.ace.ide;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A project in either the Java→DEX tab (KIND_DEX) or the App Builder tab (KIND_APP). */
public class Project {

    public static final int KIND_DEX = 0, KIND_APP = 1;
    public static final int ST_NONE = 0, ST_OK = 1, ST_ERROR = 2;

    public String id;
    public String name;
    public int kind = KIND_DEX;
    public long createdAt;
    public long builtAt;
    public int status = ST_NONE;
    public long dexSize;
    public long apkSize;

    // ---- app projects
    public String path;
    public String appName;
    public String pkg = "com.example.app";
    public String versionName = "1.0";
    public int versionCode = 1;
    public int minSdk = 21;
    public int targetSdk = 34;
    public String keyId;                 // null = debug key
    public boolean release = false;
    public final List<String> libs = new ArrayList<String>();         // catalog ids
    public final List<String> customLibs = new ArrayList<String>();   // group:artifact:version

    private static final Object LOCK = new Object();

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("ace_projects", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ store

    private static List<Project> allInOrder(Context c) {
        List<Project> out = new ArrayList<Project>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    /** newest first */
    public static List<Project> all(Context c) {
        List<Project> out = allInOrder(c);
        Collections.reverse(out);
        return out;
    }

    public static List<Project> ofKind(Context c, int kind) {
        List<Project> out = new ArrayList<Project>();
        for (Project p : all(c)) if (p.kind == kind) out.add(p);
        return out;
    }

    public static Project byId(Context c, String id) {
        for (Project p : allInOrder(c)) if (p.id.equals(id)) return p;
        return null;
    }

    public static void upsert(Context c, Project p) {
        synchronized (LOCK) {
            List<Project> list = allInOrder(c);
            boolean found = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id.equals(p.id)) { list.set(i, p); found = true; break; }
            }
            if (!found) list.add(p);
            saveAll(c, list);
        }
        if (p.kind == KIND_APP) p.writeConfigFile();
    }

    public static void delete(Context c, String id, boolean deleteFiles) {
        synchronized (LOCK) {
            Project p = byId(c, id);
            if (p != null) {
                if (p.kind == KIND_DEX) {
                    FileUtil.deleteDir(p.dir(c));
                    File pub = p.publicDexDirNoCreate(c);
                    if (pub.exists() && !isSharedWithOthers(c, p)) FileUtil.deleteDir(pub);
                } else if (deleteFiles && p.path != null) {
                    FileUtil.deleteDir(new File(p.path));
                }
            }
            List<Project> keep = new ArrayList<Project>();
            for (Project x : allInOrder(c)) if (!x.id.equals(id)) keep.add(x);
            saveAll(c, keep);
        }
    }

    private static boolean isSharedWithOthers(Context c, Project me) {
        for (Project x : allInOrder(c)) {
            if (x.kind == KIND_DEX && !x.id.equals(me.id) && x.safeName().equals(me.safeName())) return true;
        }
        return false;
    }

    public static int cleanOutputs(Context c) {
        int n = 0;
        for (Project p : allInOrder(c)) {
            if (p.kind != KIND_DEX) continue;
            File pub = p.publicDexDirNoCreate(c);
            if (!pub.exists()) continue;
            File[] fs = pub.listFiles();
            if (fs != null) for (File f : fs) {
                String nm = f.getName();
                if (nm.equals("smali") || (nm.startsWith("classes") && nm.endsWith(".dex"))) { FileUtil.deleteDir(f); n++; }
            }
            String[] left = pub.list();
            if (left != null && left.length == 0) pub.delete();
            p.status = ST_NONE;
            p.dexSize = 0;
            upsert(c, p);
        }
        return n;
    }

    /** unique name among DEX projects ("Name (2)" …) */
    public static String uniqueDexName(Context c, String wanted) {
        String base = wanted == null ? "" : wanted.trim();
        if (base.length() == 0) base = "project";
        String cand = base;
        int i = 2;
        while (true) {
            boolean clash = false;
            Project probe = new Project();
            probe.name = cand;
            for (Project x : allInOrder(c)) {
                if (x.kind == KIND_DEX && x.safeName().equalsIgnoreCase(probe.safeName())) { clash = true; break; }
            }
            if (!clash) return cand;
            cand = base + " (" + i++ + ")";
        }
    }

    public static void delete(Context c, String id) { delete(c, id, false); }
    public static void deleteDir(File f) { FileUtil.deleteDir(f); }
    public static String uniqueName(Context c, String wanted) { return uniqueDexName(c, wanted); }
    public static File java2dexRoot(Context c) { return dexRoot(c); }
    public static File java2dexRootNoCreate(Context c) { return dexRootNoCreate(c); }
    public File internalDexFile(Context c) { return new File(dexTmpDir(c), "classes.dex"); }

    public static void clearAll(Context c) {
        synchronized (LOCK) { prefs(c).edit().clear().apply(); }
    }

    private static void saveAll(Context c, List<Project> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Project p : list) arr.put(p.toJson());
            prefs(c).edit().putString("list", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ json

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("kind", kind);
        o.put("createdAt", createdAt);
        o.put("builtAt", builtAt);
        o.put("status", status);
        o.put("dexSize", dexSize);
        o.put("apkSize", apkSize);
        if (kind == KIND_APP) {
            o.put("path", path);
            o.put("appName", appName);
            o.put("pkg", pkg);
            o.put("versionName", versionName);
            o.put("versionCode", versionCode);
            o.put("minSdk", minSdk);
            o.put("targetSdk", targetSdk);
            if (keyId != null) o.put("keyId", keyId);
            o.put("release", release);
            o.put("libs", new JSONArray(libs));
            o.put("customLibs", new JSONArray(customLibs));
        }
        return o;
    }

    private static Project fromJson(JSONObject o) {
        Project p = new Project();
        p.id = o.optString("id");
        p.name = o.optString("name");
        p.kind = o.optInt("kind", KIND_DEX);
        p.createdAt = o.optLong("createdAt");
        p.builtAt = o.optLong("builtAt");
        p.status = o.optInt("status");
        p.dexSize = o.optLong("dexSize");
        p.apkSize = o.optLong("apkSize");
        if (p.kind == KIND_APP) p.applyConfig(o);
        return p;
    }

    /** project settings (also stored in ace.json inside the project folder) */
    public void applyConfig(JSONObject o) {
        path = o.optString("path", path);
        appName = o.optString("appName", appName);
        pkg = o.optString("pkg", pkg);
        versionName = o.optString("versionName", versionName);
        versionCode = o.optInt("versionCode", versionCode);
        minSdk = o.optInt("minSdk", minSdk);
        targetSdk = o.optInt("targetSdk", targetSdk);
        keyId = o.has("keyId") ? o.optString("keyId") : keyId;
        release = o.optBoolean("release", release);
        JSONArray l = o.optJSONArray("libs");
        if (l != null) { libs.clear(); for (int i = 0; i < l.length(); i++) libs.add(l.optString(i)); }
        JSONArray cl = o.optJSONArray("customLibs");
        if (cl != null) { customLibs.clear(); for (int i = 0; i < cl.length(); i++) customLibs.add(cl.optString(i)); }
    }

    public void writeConfigFile() {
        if (path == null) return;
        try {
            JSONObject o = toJson();
            o.remove("builtAt"); o.remove("status"); o.remove("dexSize"); o.remove("apkSize");
            o.remove("path"); o.remove("id"); o.remove("createdAt"); o.remove("kind");
            FileUtil.writeText(new File(path, "ace.json"), o.toString(2));
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ locations

    /** /storage/emulated/0/ACE (or the folder chosen in Settings) */
    public static File aceRoot(Context c) {
        File f = aceRootNoCreate(c);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File aceRootNoCreate(Context c) {
        String custom = Prefs.folder(c);
        if (custom != null && custom.trim().length() > 0) return new File(custom.trim());
        return new File(Environment.getExternalStorageDirectory(), "ACE");
    }

    public String safeName() { return FileUtil.safeName(name); }

    public String createdText() { return fmt(createdAt); }
    public String dateText() { return builtAt <= 0 ? "Never built" : fmt(builtAt); }
    public static String fmtTime(long t) { return fmt(t); }

    private static String fmt(long t) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(new Date(t));
    }

    // ---- app project folders
    public File root() { return new File(path); }
    public File outputDir() { return new File(path, "output"); }
    public File buildDir() { return new File(path, "build"); }
    public File apkFile() {
        return new File(outputDir(), FileUtil.safeName(name) + "-" + (release ? "release" : "debug") + ".apk");
    }

    // ---- DEX project folders (private storage, output goes to ACE/Java2Dex/<name>)
    public File dir(Context c) { return new File(new File(c.getFilesDir(), "projects"), id); }
    public File srcDir(Context c) { return new File(dir(c), "src"); }
    public File libsDir(Context c) { return new File(dir(c), "libs"); }
    public File classesDir(Context c) { return new File(dir(c), "classes"); }
    public File dexTmpDir(Context c) { return new File(dir(c), "dexout"); }
    public File logFile(Context c) { return new File(dir(c), "build.log.txt"); }

    public File ideRoot(Context c) { return kind == KIND_APP ? root() : dir(c); }

    public File publicDexDir(Context c) {
        File f = new File(dexRoot(c), safeName());
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public File publicDexDirNoCreate(Context c) { return new File(dexRootNoCreate(c), safeName()); }
    public File publicDexFile(Context c) { return new File(publicDexDir(c), "classes.dex"); }

    public List<File> publicDexFiles(Context c) {
        List<File> out = new ArrayList<File>();
        File[] fs = publicDexDirNoCreate(c).listFiles();
        if (fs != null) for (File f : fs) {
            String n = f.getName();
            if (f.isFile() && n.startsWith("classes") && n.endsWith(".dex")) out.add(f);
        }
        Collections.sort(out, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) { return dexOrder(a.getName()) - dexOrder(b.getName()); }
        });
        return out;
    }

    private static int dexOrder(String n) {
        String mid = n.substring("classes".length(), n.length() - ".dex".length());
        if (mid.length() == 0) return 1;
        try { return Integer.parseInt(mid); } catch (Exception e) { return 9999; }
    }

    public int libCount(Context c) {
        File[] fs = libsDir(c).listFiles();
        return fs == null ? 0 : fs.length;
    }

    public File smaliDir(Context c) { return new File(publicDexDir(c), "smali"); }

    public static File dexRoot(Context c) {
        File f = dexRootNoCreate(c);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File dexRootNoCreate(Context c) { return new File(aceRootNoCreate(c), "Java2Dex"); }
}
