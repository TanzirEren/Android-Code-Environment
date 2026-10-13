package com.ace.ide;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** Choose libraries for a project: catalog, Maven coordinates, own jar / aar files. */
public class LibrariesActivity extends Activity {

    private Project p;
    private Theme t;
    private LinearLayout col;
    private TextView status;
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
        top.addView(Ui.text(this, "Libraries", 17, t.text, true), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(top);
        status = Ui.text(this, "", 11.5f, t.accent, true);
        status.setPadding(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(6));
        status.setBackgroundColor(t.card);
        status.setVisibility(View.GONE);
        root.addView(status);
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

    private void build() {
        col.removeAllViews();
        SettingsUi.Store st = new SettingsUi.Store() {
            @Override public boolean getB(String k, boolean d) { return k.startsWith("lib:") && p.libs.contains(k.substring(4)); }
            @Override public void putB(String k, boolean v) {
                if (!k.startsWith("lib:")) return;
                String id = k.substring(4);
                if (v && !p.libs.contains(id)) { p.libs.add(id); warm(Libs.byId(id)); }
                if (!v) p.libs.remove(id);
                save();
            }
            @Override public int getI(String k, int d) { return d; }
            @Override public void putI(String k, int v) { }
            @Override public String getS(String k, String d) { return d; }
            @Override public void putS(String k, String v) { }
        };
        SettingsUi s = new SettingsUi(this, col, st);

        s.header("Add your own", Icons.PLUS);
        s.action("Maven coordinate", "group:artifact:version  (add @aar for aar files)", new Runnable() { @Override public void run() { addCoordinate(); } });
        s.action("Import .jar / .aar", "Copy a file from your phone into libs/", new Runnable() { @Override public void run() { importFile(); } });
        s.info("Pure-Java libraries (.jar) work fully. .aar files are supported when they contain classes only — Android resources inside libraries (AppCompat, Material, RecyclerView …) are not merged.");

        if (!p.customLibs.isEmpty() || libsDirFiles().length > 0) {
            s.header("In this project", Icons.LIB);
            for (final String c : p.customLibs) {
                s.action(c, "Maven · tap to remove", new Runnable() {
                    @Override public void run() { p.customLibs.remove(c); save(); build(); }
                });
            }
            for (final File f : libsDirFiles()) {
                s.action(f.getName(), "libs/ · " + Ui.size(f.length()) + " · tap to delete", new Runnable() {
                    @Override public void run() { f.delete(); build(); }
                });
            }
        }

        s.header("Library catalog (" + Libs.CATALOG.length + ")", Icons.LIB);
        for (Libs.Lib l : Libs.CATALOG) s.sw("lib:" + l.id, l.title, l.desc, false, null);
        s.info("Libraries are downloaded from Maven Central the first time you build (internet needed) and cached on your phone.");
    }

    private File[] libsDirFiles() {
        File d = layout.libDirs.isEmpty() ? new File(p.root(), "libs") : layout.libDirs.get(0);
        File[] fs = d.listFiles();
        if (fs == null) return new File[0];
        java.util.List<File> out = new java.util.ArrayList<File>();
        for (File f : fs) if (f.isFile() && (f.getName().endsWith(".jar") || f.getName().endsWith(".aar"))) out.add(f);
        return out.toArray(new File[0]);
    }

    private void addCoordinate() {
        Dialogs.prompt(this, "Maven coordinate", "com.squareup.okhttp3:okhttp:3.14.9", null, "Add", new Dialogs.Text() {
            @Override public void onText(final String v) {
                if (Libs.parse(v) == null) { Ui.toast(LibrariesActivity.this, "Use group:artifact:version"); return; }
                status.setVisibility(View.VISIBLE);
                status.setText("Checking " + v + " …");
                new Thread(new Runnable() {
                    @Override public void run() {
                        String msg = null;
                        try { Libs.ensure(LibrariesActivity.this, v, null); } catch (Exception e) { msg = e.getMessage(); }
                        final String m = msg;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                status.setVisibility(View.GONE);
                                if (m != null) { Dialogs.info(LibrariesActivity.this, "Not found", m); return; }
                                if (!p.customLibs.contains(v)) p.customLibs.add(v);
                                save();
                                build();
                                Ui.toast(LibrariesActivity.this, "Added ✔");
                            }
                        });
                    }
                }).start();
            }
        });
    }

    private void importFile() {
        StoragePicker.pickFiles(this, "Choose .jar or .aar files", null, new String[]{".jar", ".aar"}, true, new StoragePicker.FilesCb() {
            @Override public void onFiles(List<File> files) {
                File d = layout.libDirs.isEmpty() ? new File(p.root(), "libs") : layout.libDirs.get(0);
                d.mkdirs();
                int n = 0;
                for (File f : files) {
                    try { FileUtil.copyFile(f, new File(d, f.getName())); n++; } catch (Exception ignored) { }
                }
                Ui.toast(LibrariesActivity.this, n + " file(s) copied to libs/");
                build();
            }
        });
    }

    /** download a catalog entry in the background so the next build works offline */
    private void warm(final Libs.Lib l) {
        if (l == null) return;
        status.setVisibility(View.VISIBLE);
        status.setText("Downloading " + l.title + " …");
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                for (String c : l.coords) {
                    try { Libs.ensure(LibrariesActivity.this, c, new Libs.Progress() {
                        @Override public void onStatus(final String m) { runOnUiThread(new Runnable() { @Override public void run() { status.setText(m); } }); }
                    }); } catch (Exception e) { err = e.getMessage(); break; }
                }
                final String e = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        status.setVisibility(View.GONE);
                        Ui.toast(LibrariesActivity.this, e == null ? l.title + " ready ✔" : "Download failed — it will retry when you build.");
                    }
                });
            }
        }).start();
    }
}
