package com.ace.ide;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** All user settings live here. Generic getters + a few named shortcuts. */
public class Prefs {

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("ace_prefs", Context.MODE_PRIVATE);
    }

    // ---- generic
    public static boolean b(Context c, String k, boolean d) { return p(c).getBoolean(k, d); }
    public static void putB(Context c, String k, boolean v) { p(c).edit().putBoolean(k, v).apply(); }
    public static int i(Context c, String k, int d) { return p(c).getInt(k, d); }
    public static void putI(Context c, String k, int v) { p(c).edit().putInt(k, v).apply(); }
    public static String s(Context c, String k, String d) { return p(c).getString(k, d); }
    public static void putS(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }
    public static void remove(Context c, String k) { p(c).edit().remove(k).apply(); }

    // ---- theme: 0 = follow system, 1 = light, 2 = dark
    public static int themeMode(Context c) { return i(c, "themeMode", 0); }
    public static void themeMode(Context c, int v) { putI(c, "themeMode", v); }

    public static boolean dark(Context c) {
        int m = themeMode(c);
        if (m == 1) return false;
        if (m == 2) return true;
        int ui = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    // ---- storage
    public static String folder(Context c) { return s(c, "folder", null); }
    public static void folder(Context c, String v) { if (v == null) remove(c, "folder"); else putS(c, "folder", v); }

    // ---- editor
    public static int fontSize(Context c) { return i(c, "fontSize", 12); }
    public static void fontSize(Context c, int v) { putI(c, "fontSize", v); }
    public static boolean wrap(Context c) { return b(c, "wrap", false); }
    public static void wrap(Context c, boolean v) { putB(c, "wrap", v); }

    // ---- build (DEX tab)
    public static int minApi(Context c) { return i(c, "minApi", 21); }
    public static void minApi(Context c, int v) { putI(c, "minApi", v); }
    public static boolean bundleLibs(Context c) { return b(c, "bundleLibs", false); }
    public static void bundleLibs(Context c, boolean v) { putB(c, "bundleLibs", v); }

    public static long jarStamp(Context c) { return p(c).getLong("jarStamp", 0L); }
    public static void jarStamp(Context c, long v) { p(c).edit().putLong("jarStamp", v).apply(); }
}
