package com.ace.ide;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Settings of one app project: name, version, SDK, signing, icon, permissions. */
public class ProjectSettingsActivity extends Activity {

    private static final String[][] PERMS = {
            {"android.permission.INTERNET", "Internet"},
            {"android.permission.ACCESS_NETWORK_STATE", "Network state"},
            {"android.permission.CAMERA", "Camera"},
            {"android.permission.RECORD_AUDIO", "Microphone"},
            {"android.permission.ACCESS_FINE_LOCATION", "Precise location"},
            {"android.permission.READ_EXTERNAL_STORAGE", "Read storage"},
            {"android.permission.WRITE_EXTERNAL_STORAGE", "Write storage"},
            {"android.permission.VIBRATE", "Vibrate"},
            {"android.permission.WAKE_LOCK", "Keep awake"},
            {"android.permission.POST_NOTIFICATIONS", "Notifications"},
            {"android.permission.BLUETOOTH", "Bluetooth"},
            {"android.permission.READ_CONTACTS", "Read contacts"},
    };

    private Project p;
    private Theme t;
    private LinearLayout col;
    private ProjectLayout layout;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent().getStringExtra("id");
        p = id == null ? null : Project.byId(this, id);
        if (p == null) { finish(); return; }
        t = Theme.get(this);
        layout = ProjectLayout.detect(p.root());
        getWindow().setStatusBarColor(t.card);
        getWindow().setNavigationBarColor(t.bg);
        if (Build.VERSION.SDK_INT >= 23 && !t.dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.bg);
        root.setFitsSystemWindows(true);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(t.card);
        top.setPadding(Ui.dp(6), 0, Ui.dp(10), 0);
        top.setMinimumHeight(Ui.dp(52));
        top.addView(Ui.iconButton(this, Icons.ARROW_L, t.text, 20, 12, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        }));
        top.addView(Ui.text(this, "Project settings", 17, t.text, true));
        root.addView(top);
        root.addView(Ui.divider(this, t.cardStroke));
        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(14), Ui.dp(4), Ui.dp(14), Ui.dp(40));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        build();
    }

    private void save() { Project.upsert(this, p); }

    private SettingsUi.Store store() {
        return new SettingsUi.Store() {
            @Override public boolean getB(String k, boolean d) {
                if (k.equals("release")) return p.release;
                if (k.startsWith("perm:")) return hasPermission(k.substring(5));
                return d;
            }
            @Override public void putB(String k, boolean v) {
                if (k.equals("release")) { p.release = v; save(); }
                else if (k.startsWith("perm:")) setPermission(k.substring(5), v);
            }
            @Override public int getI(String k, int d) {
                if (k.equals("minSdk")) return p.minSdk;
                if (k.equals("targetSdk")) return p.targetSdk;
                return d;
            }
            @Override public void putI(String k, int v) {
                if (k.equals("minSdk")) { p.minSdk = v; if (p.targetSdk < v) p.targetSdk = v; }
                else if (k.equals("targetSdk")) { p.targetSdk = Math.max(v, p.minSdk); }
                save();
            }
            @Override public String getS(String k, String d) {
                if (k.equals("appName")) return p.appName == null ? p.name : p.appName;
                if (k.equals("versionName")) return p.versionName;
                if (k.equals("versionCode")) return String.valueOf(p.versionCode);
                return d;
            }
            @Override public void putS(String k, String v) {
                if (k.equals("appName") && v.length() > 0) { p.appName = v; applyAppName(v); }
                else if (k.equals("versionName") && v.length() > 0) p.versionName = v;
                else if (k.equals("versionCode")) { try { p.versionCode = Math.max(1, Integer.parseInt(v.trim())); } catch (Exception ignored) { } }
                save();
            }
        };
    }

    private void build() {
        col.removeAllViews();
        SettingsUi s = new SettingsUi(this, col, store());

        s.header("App", Icons.PACKAGE);
        s.text("appName", "App name", "Shown under the launcher icon", p.name);
        s.text("versionName", "Version name", "e.g. 1.0.0", "1.0");
        s.text("versionCode", "Version code", "Whole number, increase for every release", "1");
        s.actionValue("Package name", "Defined by your code — shown for reference", p.pkg, new Runnable() {
            @Override public void run() {
                Dialogs.info(ProjectSettingsActivity.this, "Package name",
                        p.pkg + "\n\nThe package is part of your source code (package statements, R class, manifest). "
                                + "To use another package, create a new project with it and copy your classes over.");
            }
        });

        s.header("SDK", Icons.SETTINGS);
        s.choice("minSdk", "Minimum SDK", new String[]{"21 (Android 5.0)", "23 (Android 6)", "24 (Android 7)", "26 (Android 8)", "28 (Android 9)", "29 (Android 10)", "30 (Android 11)"},
                new int[]{21, 23, 24, 26, 28, 29, 30}, 21, null);
        s.choice("targetSdk", "Target SDK", new String[]{"28", "29", "30", "31", "32", "33", "34"}, new int[]{28, 29, 30, 31, 32, 33, 34}, 34, null);

        s.header("Build & signing", Icons.KEY);
        s.sw("release", "Release build", "Sign with your release key instead of the debug key", false, null);
        s.actionValue("Release key", "Used when “Release build” is on", keyName(), new Runnable() { @Override public void run() { chooseKey(); } });

        s.header("App icon", Icons.IMAGE);
        s.action("Change app icon", "Pick an image and set the corner radius", new Runnable() { @Override public void run() { iconDialog(); } });

        s.header("Permissions", Icons.LINK);
        for (String[] pm : PERMS) s.sw("perm:" + pm[0], pm[1], pm[0].replace("android.permission.", ""), false, null);
        s.info("Other permissions can be added by editing AndroidManifest.xml.");

        s.header("Project", Icons.FOLDER);
        s.action("Libraries", "Gson, OkHttp, Jsoup, your own .jar …", new Runnable() {
            @Override public void run() {
                Intent i = new Intent(ProjectSettingsActivity.this, LibrariesActivity.class);
                i.putExtra("id", p.id);
                startActivity(i);
            }
        });
        s.actionValue("Location", p.root().getAbsolutePath(), "", new Runnable() {
            @Override public void run() { Ui.copy(ProjectSettingsActivity.this, "path", p.root().getAbsolutePath()); }
        });
        s.action("Remove from ACE", "Keeps the files on your phone", new Runnable() {
            @Override public void run() {
                Dialogs.confirm(ProjectSettingsActivity.this, "Remove project?", "The project disappears from the list. Files stay in " + p.root().getAbsolutePath(), "Remove", new Runnable() {
                    @Override public void run() { Project.delete(ProjectSettingsActivity.this, p.id, false); finishAffinity(); startActivity(new Intent(ProjectSettingsActivity.this, MainActivity.class)); }
                });
            }
        });
        s.action("Delete project and files", "Cannot be undone", new Runnable() {
            @Override public void run() {
                Dialogs.confirm(ProjectSettingsActivity.this, "Delete everything?", "All files of \"" + p.name + "\" will be deleted, including built APKs.", "Delete", new Runnable() {
                    @Override public void run() { Project.delete(ProjectSettingsActivity.this, p.id, true); finishAffinity(); startActivity(new Intent(ProjectSettingsActivity.this, MainActivity.class)); }
                });
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private String keyName() {
        Signing.Entry e = Signing.byId(this, p.keyId);
        if (e == null) e = Signing.byId(this, Signing.defaultId(this));
        return e == null ? "none (debug key)" : e.name;
    }

    private void chooseKey() {
        final List<Signing.Entry> all = Signing.all(this);
        if (all.isEmpty()) {
            Dialogs.confirm(this, "No release key yet", "Create or import a key in Settings → Signing keys.", "Open", new Runnable() {
                @Override public void run() { startActivity(new Intent(ProjectSettingsActivity.this, SigningActivity.class)); }
            });
            return;
        }
        String[] labels = new String[all.size()];
        int sel = 0;
        for (int i = 0; i < all.size(); i++) {
            labels[i] = all.get(i).name + "  (" + all.get(i).alias + ")";
            if (all.get(i).id.equals(p.keyId)) sel = i;
        }
        Dialogs.choose(this, "Release key", labels, sel, new Dialogs.IndexCb() {
            @Override public void onIndex(int i) { p.keyId = all.get(i).id; save(); build(); }
        });
    }

    private File manifestFile() { return layout.valid() ? layout.manifest : null; }

    private boolean hasPermission(String perm) {
        try {
            File m = manifestFile();
            return m != null && FileUtil.readText(m).contains("\"" + perm + "\"");
        } catch (Exception e) { return false; }
    }

    private void setPermission(String perm, boolean on) {
        try {
            File m = manifestFile();
            if (m == null) return;
            String s = FileUtil.readText(m);
            boolean has = s.contains("\"" + perm + "\"");
            if (on && !has) {
                Matcher mt = Pattern.compile("<manifest[^>]*>").matcher(s);
                if (mt.find()) {
                    s = s.substring(0, mt.end()) + "\n\n    <uses-permission android:name=\"" + perm + "\" />" + s.substring(mt.end());
                }
            } else if (!on && has) {
                s = s.replaceAll("[ \\t]*<uses-permission[^>]*\"" + Pattern.quote(perm) + "\"[^>]*/>[ \\t]*\\n?", "");
            }
            FileUtil.writeText(m, s);
        } catch (Exception e) {
            Ui.toast(this, "Could not edit the manifest: " + e.getMessage());
        }
    }

    private void applyAppName(String v) {
        try {
            for (File rd : layout.resDirs) {
                File[] dirs = rd.listFiles();
                if (dirs == null) continue;
                for (File d : dirs) {
                    if (!d.getName().startsWith("values")) continue;
                    File f = new File(d, "strings.xml");
                    if (!f.isFile()) continue;
                    String s = FileUtil.readText(f);
                    Pattern pat = Pattern.compile("(<string\\s+name=\"app_name\"[^>]*>)[^<]*(</string>)");
                    Matcher m = pat.matcher(s);
                    if (m.find() && d.getName().equals("values")) {
                        String esc = v.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'");
                        FileUtil.writeText(f, m.replaceFirst(Matcher.quoteReplacement(m.group(1) + esc + m.group(2))));
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    private Bitmap pickedIcon;
    private int radius = 22;

    private void iconDialog() {
        radius = Prefs.i(this, "defIconRadius", 22);
        pickedIcon = null;
        final Dialogs.Glass g = new Dialogs.Glass(this, "App icon");
        final ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        g.add(iv, Ui.lp(Ui.dp(96), Ui.dp(96), 0, 12, 0, 0));
        ((LinearLayout.LayoutParams) iv.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                Bitmap src = pickedIcon != null ? pickedIcon : currentIcon();
                iv.setImageBitmap(IconGen.render(src, 192, radius));
            }
        };
        refresh.run();
        final TextView lab = Ui.text(this, "Corner radius: " + radius + " %", 12, t.textSub, false);
        g.add(lab, Ui.lp(-1, -2, 0, 12, 0, 0));
        SeekBar sb = new SeekBar(this);
        sb.setMax(50);
        sb.setProgress(radius);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(t.accent));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(t.accent));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) { radius = v; lab.setText("Corner radius: " + v + " %"); refresh.run(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        g.add(sb);
        LinearLayout row = new LinearLayout(this);
        TextView pick = Ui.button(this, "Choose image", t.chipBg);
        pick.setTextColor(t.text);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                StoragePicker.pickFiles(ProjectSettingsActivity.this, "Choose an icon image", null,
                        new String[]{".png", ".jpg", ".jpeg", ".webp"}, false, new StoragePicker.FilesCb() {
                            @Override public void onFiles(List<File> files) {
                                Bitmap b = IconGen.load(files.get(0));
                                if (b == null) Ui.toast(ProjectSettingsActivity.this, "Cannot read that image");
                                else { pickedIcon = b; refresh.run(); }
                            }
                        });
            }
        });
        TextView def = Ui.button(this, "ACE logo", t.chipBg);
        def.setTextColor(t.text);
        def.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickedIcon = IconGen.defaultIcon(ProjectSettingsActivity.this); refresh.run(); }
        });
        row.addView(pick, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(def, Ui.lp(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) def.getLayoutParams()).weight = 1f;
        g.add(row, Ui.lp(-1, -2, 0, 12, 0, 0));
        g.buttons("Apply", new Runnable() {
            @Override public void run() {
                try {
                    Bitmap src = pickedIcon != null ? pickedIcon : currentIcon();
                    File res = layout.resDirs.isEmpty() ? new File(p.root(), "app/src/main/res") : layout.resDirs.get(0);
                    IconGen.writeAll(src, radius, res);
                    Ui.toast(ProjectSettingsActivity.this, "Icon updated ✔  (manifest must use @mipmap/ic_launcher)");
                } catch (Exception e) { Ui.toast(ProjectSettingsActivity.this, "Failed: " + e.getMessage()); }
                g.dismiss();
            }
        }, "Cancel", null);
        g.show();
    }

    private Bitmap currentIcon() {
        for (File rd : layout.resDirs) {
            File f = new File(rd, "mipmap-xxxhdpi/ic_launcher.png");
            if (f.isFile()) { Bitmap b = IconGen.load(f); if (b != null) return b; }
        }
        return IconGen.defaultIcon(this);
    }

    static List<String> unused() { return new ArrayList<String>(); }
}
