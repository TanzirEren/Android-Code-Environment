package com.ace.ide;

import android.content.Context;
import android.content.SharedPreferences;

import com.ace.ide.build.Keys;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Manages the signing keys stored inside ACE (create / generate / import / default). */
public final class Signing {

    public static final class Entry {
        public String id, name, file, alias, storePass, keyPass;
    }

    private Signing() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("ace_keys", Context.MODE_PRIVATE);
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "keys");
        d.mkdirs();
        return d;
    }

    public static List<Entry> all(Context c) {
        List<Entry> out = new ArrayList<Entry>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.id = o.optString("id"); e.name = o.optString("name"); e.file = o.optString("file");
                e.alias = o.optString("alias"); e.storePass = o.optString("sp"); e.keyPass = o.optString("kp");
                out.add(e);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static void save(Context c, List<Entry> l) {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : l) {
                JSONObject o = new JSONObject();
                o.put("id", e.id); o.put("name", e.name); o.put("file", e.file); o.put("alias", e.alias);
                o.put("sp", e.storePass); o.put("kp", e.keyPass);
                a.put(o);
            }
            prefs(c).edit().putString("list", a.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static Entry byId(Context c, String id) {
        if (id == null) return null;
        for (Entry e : all(c)) if (e.id.equals(id)) return e;
        return null;
    }

    public static String defaultId(Context c) { return prefs(c).getString("default", null); }

    public static void setDefault(Context c, String id) {
        if (id == null) prefs(c).edit().remove("default").apply();
        else prefs(c).edit().putString("default", id).apply();
    }

    public static Entry add(Context c, Entry e) {
        List<Entry> l = all(c);
        l.add(e);
        save(c, l);
        if (defaultId(c) == null) setDefault(c, e.id);
        return e;
    }

    public static void remove(Context c, String id) {
        List<Entry> keep = new ArrayList<Entry>();
        for (Entry e : all(c)) {
            if (e.id.equals(id)) new File(e.file).delete(); else keep.add(e);
        }
        save(c, keep);
        if (id.equals(defaultId(c))) setDefault(c, keep.isEmpty() ? null : keep.get(0).id);
    }

    /** create a brand new keystore inside ACE */
    public static Entry generate(Context c, String name, String alias, String pass, String cn, String ou, String o,
                                 String l, String st, String country, int years, int bits) throws Exception {
        String id = String.valueOf(System.currentTimeMillis());
        File f = new File(dir(c), id + ".p12");
        Keys.generate(f, alias, pass.toCharArray(), cn, ou, o, l, st, country, years, bits);
        Entry e = new Entry();
        e.id = id; e.name = name; e.file = f.getAbsolutePath(); e.alias = alias; e.storePass = pass; e.keyPass = pass;
        return add(c, e);
    }

    /** import an existing .jks / .keystore / .p12 / .bks file */
    public static Entry importKey(Context c, File src, String name, String alias, String storePass, String keyPass) throws Exception {
        Keys.KeyInfo ki = Keys.load(src, storePass.toCharArray(), alias.length() == 0 ? null : alias,
                keyPass.length() == 0 ? null : keyPass.toCharArray());
        String id = String.valueOf(System.currentTimeMillis());
        String ext = FileUtil.ext(src.getName());
        if (ext.length() == 0) ext = "ks";
        File f = new File(dir(c), id + "." + ext);
        FileUtil.copyFile(src, f);
        Entry e = new Entry();
        e.id = id; e.name = name; e.file = f.getAbsolutePath(); e.alias = ki.alias;
        e.storePass = storePass; e.keyPass = keyPass.length() == 0 ? storePass : keyPass;
        return add(c, e);
    }

    public static Keys.KeyInfo load(Entry e) throws Exception {
        return Keys.load(new File(e.file), e.storePass.toCharArray(), e.alias, e.keyPass.toCharArray());
    }

    public static Keys.KeyInfo debugKey(Context c) throws Exception {
        return Keys.debugKey(new File(dir(c), "ace-debug.p12"));
    }

    /** key used for a project: release → project key / default key, debug → built-in debug key */
    public static Keys.KeyInfo forProject(Context c, Project p) throws Exception {
        if (p.release) {
            Entry e = byId(c, p.keyId);
            if (e == null) e = byId(c, defaultId(c));
            if (e != null) return load(e);
        }
        return debugKey(c);
    }
}
