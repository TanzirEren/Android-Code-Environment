package com.ace.ide;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** Home: two pages (Java → DEX, App Builder) switched with a floating glass nav. */
public class MainActivity extends Activity {

    private static final int REQ_STORAGE = 77;

    private Theme t;
    private FrameLayout content;
    private LinearLayout navPill;
    private View navDex, navBuild;
    private int page = 1;
    private LinearLayout dexPage, buildPage;
    private LinearLayout dexList, buildList;
    private FrameLayout root;
    private boolean askedStorage;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        t = Theme.get(this);
        getWindow().setStatusBarColor(t.bg);
        getWindow().setNavigationBarColor(t.bg);
        if (Build.VERSION.SDK_INT >= 23 && !t.dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        page = Prefs.i(this, "homePage", 1);

        root = new FrameLayout(this);
        root.setBackgroundColor(t.bg);
        root.setFitsSystemWindows(true);
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        buildNav();
        setContentView(root);
        showPage(page);
        splash();
        Converter.ensureAndroidJar(this);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        if (!askedStorage && !hasStorage()) { askedStorage = true; askStorage(); }
    }

    // ------------------------------------------------------------------ splash

    private void splash() {
        final FrameLayout sp = new FrameLayout(this);
        sp.setBackgroundColor(0xFFD9D0CB);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        c.addView(logo, new LinearLayout.LayoutParams(Ui.dp(120), Ui.dp(120)));
        TextView n = Ui.text(this, "ACE", 30, 0xFF55523F, true);
        n.setLetterSpacing(0.2f);
        c.addView(n, Ui.lp(-2, -2, 0, 14, 0, 0));
        c.addView(Ui.text(this, "Android Code Environment", 13, 0xFF6F6A5A, false));
        sp.addView(c, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(sp, new FrameLayout.LayoutParams(-1, -1));
        sp.setAlpha(1f);
        sp.postDelayed(new Runnable() {
            @Override public void run() {
                sp.animate().alpha(0f).setDuration(320).withEndAction(new Runnable() {
                    @Override public void run() { root.removeView(sp); }
                }).start();
            }
        }, 650);
    }

    // ------------------------------------------------------------------ storage permission

    private boolean hasStorage() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void askStorage() {
        final Dialogs.Glass g = new Dialogs.Glass(this, "Allow file access");
        g.message("ACE saves your projects and APKs in /storage/emulated/0/ACE so you can see them in any file manager. "
                + "Please allow access to all files.");
        g.buttons("Allow", new Runnable() {
            @Override public void run() {
                g.dismiss();
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName())), REQ_STORAGE);
                    } catch (Exception e) {
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_STORAGE);
                    }
                } else {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
                }
            }
        }, "Later", null);
        g.show();
    }

    // ------------------------------------------------------------------ nav

    private void buildNav() {
        navPill = new LinearLayout(this);
        navPill.setGravity(Gravity.CENTER);
        navPill.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        GradientDrawable g = Ui.glass(this, t, 40);
        g.setCornerRadius(Ui.dp(60));       // maximum radius = pill
        g.setColor(t.dark ? 0xDD2A281F : 0xE6FFFFFF);
        navPill.setBackground(g);
        navPill.setElevation(Ui.dp(10));
        navDex = navItem(Icons.CONVERT, "Java → DEX", 0);
        navBuild = navItem(Icons.BUILD, "App Builder", 1);
        navPill.addView(navDex, new LinearLayout.LayoutParams(Ui.dp(120), Ui.dp(52)));
        navPill.addView(navBuild, Ui.lp(Ui.dp(120), Ui.dp(52), 6, 0, 0, 0));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.dp(16);
        root.addView(navPill, lp);
    }

    private View navItem(String icon, String label, final int idx) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER);
        item.setTag(new Object[]{icon, label});
        item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPage(idx); } });
        return item;
    }

    private void styleNav(View v, boolean sel) {
        LinearLayout item = (LinearLayout) v;
        item.removeAllViews();
        Object[] tag = (Object[]) item.getTag();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(40));
        bg.setColor(sel ? t.accent : 0x00000000);
        item.setBackground(bg);
        item.addView(Icons.view(this, (String) tag[0], sel ? t.onAccent : t.textSub), new LinearLayout.LayoutParams(Ui.dp(20), Ui.dp(20)));
        if (sel) {
            TextView tv = Ui.text(this, (String) tag[1], 11.5f, t.onAccent, true);
            tv.setSingleLine(true);
            item.addView(tv, Ui.lp(-2, -2, 6, 0, 0, 0));
        }
    }

    private void showPage(int idx) {
        page = idx;
        Prefs.putI(this, "homePage", idx);
        content.removeAllViews();
        if (idx == 0) {
            if (dexPage == null) dexPage = buildDexPage();
            content.addView(dexPage, new FrameLayout.LayoutParams(-1, -1));
        } else {
            if (buildPage == null) buildPage = buildAppPage();
            content.addView(buildPage, new FrameLayout.LayoutParams(-1, -1));
        }
        styleNav(navDex, idx == 0);
        styleNav(navBuild, idx == 1);
        refresh();
    }

    private void refresh() {
        if (page == 0 && dexList != null) fillDex();
        if (page == 1 && buildList != null) fillBuild();
    }

    // ------------------------------------------------------------------ shared pieces

    private View header(String title, String sub, boolean settings) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(Ui.dp(20), Ui.dp(18), Ui.dp(10), Ui.dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        h.addView(logo, new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42)));
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.addView(Ui.text(this, title, 21, t.text, true));
        tc.addView(Ui.text(this, sub, 11.5f, t.textSub, false));
        h.addView(tc, Ui.lp(0, -2, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) tc.getLayoutParams()).weight = 1f;
        if (settings) {
            h.addView(Ui.iconButton(this, Icons.SETTINGS, t.text, 22, 12, new View.OnClickListener() {
                @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
            }));
        } else {
            h.addView(Ui.iconButton(this, Icons.INFO, t.text, 22, 12, new View.OnClickListener() {
                @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, InfoActivity.class)); }
            }));
        }
        return h;
    }

    private View fab(final Runnable r) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(t.accent);
        f.setBackground(bg);
        f.setElevation(Ui.dp(8));
        f.addView(Icons.view(this, Icons.PLUS, t.onAccent), new FrameLayout.LayoutParams(Ui.dp(26), Ui.dp(26), Gravity.CENTER));
        f.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Ui.popIn(v, 0); r.run(); } });
        return f;
    }

    private FrameLayout pageShell(View header, LinearLayout[] listOut, final Runnable fabAction) {
        FrameLayout shell = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(header);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(170));
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        shell.addView(col, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.dp(58), Ui.dp(58), Gravity.BOTTOM | Gravity.END);
        fl.rightMargin = Ui.dp(20);
        fl.bottomMargin = Ui.dp(96);
        shell.addView(fab(fabAction), fl);
        listOut[0] = list;
        return shell;
    }

    // ------------------------------------------------------------------ App Builder page

    private LinearLayout buildAppPage() {
        LinearLayout[] out = new LinearLayout[1];
        FrameLayout shell = pageShell(header("App Builder", "Build Android apps with Java & XML", true), out, new Runnable() {
            @Override public void run() {
                ProjectDialogs.fabMenu(MainActivity.this, new ProjectDialogs.Done() {
                    @Override public void onProject(Project p) { openIde(p); }
                });
            }
        });
        buildList = out[0];
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(shell, new LinearLayout.LayoutParams(-1, -1));
        return wrap;
    }

    private void fillBuild() {
        buildList.removeAllViews();
        List<Project> ps = Project.ofKind(this, Project.KIND_APP);
        if (ps.isEmpty()) {
            empty(buildList, "No projects yet", "Tap + to create a new app, import a folder or clone a Git repository.");
            return;
        }
        for (final Project p : ps) buildList.addView(appCard(p));
    }

    private View appCard(final Project p) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(12), Ui.dp(14));
        c.setBackground(Ui.ripple(this, Ui.outline(t.card, t.cardStroke, 20, 1, this)));
        ImageView icon = new ImageView(this);
        File ic = new File(p.path == null ? "" : p.path, "app/src/main/res/mipmap-xxhdpi/ic_launcher.png");
        if (ic.isFile()) icon.setImageBitmap(IconGen.load(ic));
        else icon.setImageBitmap(IconGen.render(IconGen.defaultIcon(this), 144, 22));
        c.addView(icon, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        TextView n = Ui.text(this, p.name, 16, t.text, true);
        n.setSingleLine(true);
        tc.addView(n);
        TextView pk = Ui.text(this, p.pkg + "  ·  v" + p.versionName, 11.5f, t.textSub, false);
        pk.setSingleLine(true);
        tc.addView(pk);
        String st = p.status == Project.ST_OK ? "✓ Built  " + Project.fmtTime(p.builtAt)
                : p.status == Project.ST_ERROR ? "✕ Last build failed" : "Not built yet";
        tc.addView(Ui.text(this, st, 10.5f, p.status == Project.ST_ERROR ? t.danger : t.accent, true), Ui.lp(-2, -2, 0, 4, 0, 0));
        c.addView(tc, Ui.lp(0, -2, 14, 0, 0, 0));
        ((LinearLayout.LayoutParams) tc.getLayoutParams()).weight = 1f;
        c.addView(Icons.view(this, Icons.CHEV_R, t.textSub), new LinearLayout.LayoutParams(Ui.dp(16), Ui.dp(16)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(10);
        c.setLayoutParams(lp);
        c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openIde(p); } });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { appMenu(p); return true; }
        });
        return c;
    }

    private void appMenu(final Project p) {
        Dialogs.Glass g = new Dialogs.Glass(this, p.name);
        g.row(Icons.CODE, "Open", null, new Runnable() { @Override public void run() { openIde(p); } });
        g.row(Icons.SETTINGS, "Project settings", null, new Runnable() {
            @Override public void run() {
                Intent i = new Intent(MainActivity.this, ProjectSettingsActivity.class);
                i.putExtra("id", p.id);
                startActivity(i);
            }
        });
        g.row(Icons.APK, "Built APKs", p.outputDir().getAbsolutePath(), new Runnable() {
            @Override public void run() {
                File[] fs = p.outputDir().listFiles();
                StringBuilder sb = new StringBuilder();
                if (fs != null) for (File f : fs) if (f.getName().endsWith(".apk")) sb.append(f.getName()).append("  (").append(Ui.size(f.length())).append(")\n");
                Dialogs.info(MainActivity.this, "Output", sb.length() == 0 ? "No APK yet — open the project and press ▶" : sb.toString() + "\n" + p.outputDir().getAbsolutePath());
            }
        });
        g.row(Icons.TRASH, "Remove from list", "Files stay on your phone", new Runnable() {
            @Override public void run() { Project.delete(MainActivity.this, p.id, false); refresh(); }
        });
        g.row(Icons.TRASH, "Delete project and files", "Cannot be undone", new Runnable() {
            @Override public void run() {
                Dialogs.confirm(MainActivity.this, "Delete everything?", "All files of \"" + p.name + "\" will be deleted.", "Delete", new Runnable() {
                    @Override public void run() { Project.delete(MainActivity.this, p.id, true); refresh(); }
                });
            }
        });
        g.show();
    }

    private void openIde(Project p) {
        Intent i = new Intent(this, IdeActivity.class);
        i.putExtra("id", p.id);
        startActivity(i);
    }

    // ------------------------------------------------------------------ Java → DEX page

    private LinearLayout buildDexPage() {
        LinearLayout[] out = new LinearLayout[1];
        FrameLayout shell = pageShell(header("Java → DEX", "Convert Java source to classes.dex", false), out, new Runnable() {
            @Override public void run() { startActivity(new Intent(MainActivity.this, NewProjectActivity.class)); }
        });
        dexList = out[0];
        LinearLayout wrap = new LinearLayout(this);
        wrap.addView(shell, new LinearLayout.LayoutParams(-1, -1));
        return wrap;
    }

    private void fillDex() {
        dexList.removeAllViews();
        List<Project> ps = Project.ofKind(this, Project.KIND_DEX);
        // hero card
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable hb = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.headerTop, t.headerBottom});
        hb.setCornerRadius(Ui.dp(22));
        hero.setBackground(hb);
        hero.setPadding(Ui.dp(18), Ui.dp(18), Ui.dp(18), Ui.dp(16));
        hero.addView(Ui.text(this, "Java → classes.dex", 18, Color.WHITE, true));
        TextView sub = Ui.text(this, "Paste or import .java files, add libraries, convert on-device with ECJ + D8 and get a ready dex.", 12, 0xFFEDE8D8, false);
        sub.setLineSpacing(0, 1.15f);
        hero.addView(sub, Ui.lp(-1, -2, 0, 6, 0, 12));
        long total = 0; int ok = 0;
        for (Project p : ps) { total += p.dexSize; if (p.status == Project.ST_OK) ok++; }
        hero.addView(Ui.text(this, ps.size() + " projects  ·  " + ok + " converted  ·  " + Ui.size(total), 11.5f, 0xFFEDE8D8, true));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.bottomMargin = Ui.dp(14);
        dexList.addView(hero, hl);
        if (ps.isEmpty()) {
            empty(dexList, "No conversions yet", "Tap + to start. Each project keeps its sources, libraries and output.");
            return;
        }
        for (final Project p : ps) dexList.addView(dexCard(p));
    }

    private View dexCard(final Project p) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(12), Ui.dp(14));
        c.setBackground(Ui.ripple(this, Ui.outline(t.card, t.cardStroke, 20, 1, this)));
        FrameLayout ib = new FrameLayout(this);
        ib.setBackground(Ui.fill(p.status == Project.ST_ERROR ? t.dangerSoft : t.accentSoft, 14, this));
        ib.addView(Icons.view(this, Icons.CONVERT, p.status == Project.ST_ERROR ? t.danger : t.accent), new FrameLayout.LayoutParams(Ui.dp(22), Ui.dp(22), Gravity.CENTER));
        c.addView(ib, new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.addView(Ui.text(this, p.name, 16, t.text, true));
        String st = p.status == Project.ST_OK ? "✓ " + Ui.size(p.dexSize) + "  ·  " + Project.fmtTime(p.builtAt)
                : p.status == Project.ST_ERROR ? "✕ Conversion failed" : "Not converted yet";
        tc.addView(Ui.text(this, st, 11.5f, p.status == Project.ST_ERROR ? t.danger : t.textSub, false));
        c.addView(tc, Ui.lp(0, -2, 14, 0, 0, 0));
        ((LinearLayout.LayoutParams) tc.getLayoutParams()).weight = 1f;
        c.addView(Icons.view(this, Icons.CHEV_R, t.textSub), new LinearLayout.LayoutParams(Ui.dp(16), Ui.dp(16)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(10);
        c.setLayoutParams(lp);
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, DetailActivity.class);
                i.putExtra("id", p.id);
                startActivity(i);
            }
        });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                Dialogs.confirm(MainActivity.this, "Delete \"" + p.name + "\"?", "Removes sources, libraries and DEX output.", "Delete", new Runnable() {
                    @Override public void run() { Project.delete(MainActivity.this, p.id); refresh(); }
                });
                return true;
            }
        });
        return c;
    }

    private void empty(LinearLayout parent, String title, String sub) {
        LinearLayout e = new LinearLayout(this);
        e.setOrientation(LinearLayout.VERTICAL);
        e.setGravity(Gravity.CENTER);
        e.setPadding(Ui.dp(30), Ui.dp(50), Ui.dp(30), Ui.dp(20));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAlpha(0.55f);
        e.addView(logo, new LinearLayout.LayoutParams(Ui.dp(86), Ui.dp(86)));
        TextView a = Ui.text(this, title, 16, t.text, true);
        a.setGravity(Gravity.CENTER);
        e.addView(a, Ui.lp(-2, -2, 0, 14, 0, 4));
        TextView b = Ui.text(this, sub, 12.5f, t.textSub, false);
        b.setGravity(Gravity.CENTER);
        b.setLineSpacing(0, 1.15f);
        e.addView(b);
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_STORAGE) refresh();
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        refresh();
    }
}
