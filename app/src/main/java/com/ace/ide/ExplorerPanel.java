package com.ace.ide;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Glass side panel with the project tree and all file operations. */
final class ExplorerPanel {

    interface Host {
        Activity act();
        void openFile(File f);
        void onRenamed(File from, File to);
        void onDeleted(File f);
        List<File> javaDirs();
        View blurSource();
    }

    private static final class Node {
        final File f;
        final int depth;
        Node(File f, int depth) { this.f = f; this.depth = depth; }
    }

    private final Host host;
    private final Activity a;
    private final Theme t;
    private final File root;
    private final boolean dexProject;
    private final FrameLayout overlay;
    private final LinearLayout panel;
    private final View scrim;
    private final ListView list;
    private final TextView footer;
    private final List<Node> nodes = new ArrayList<Node>();
    private final Set<String> expanded = new HashSet<String>();
    private final BaseAdapter adapter;
    private boolean open;
    private File lastTouchedDir;

    ExplorerPanel(final Host host, File root, boolean dexProject, String title) {
        this.host = host;
        this.a = host.act();
        this.t = Theme.get(a);
        this.root = root;
        this.dexProject = dexProject;
        expanded.add(root.getAbsolutePath());

        overlay = new FrameLayout(a);
        overlay.setVisibility(View.GONE);
        scrim = new View(a);
        scrim.setBackgroundColor(t.glassScrim);
        scrim.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { hide(); } });
        overlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        int w = Math.min(Ui.dp(320), (int) (a.getResources().getDisplayMetrics().widthPixels * 0.86f));
        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        overlay.addView(panel, new FrameLayout.LayoutParams(w, -1, Gravity.START));

        // header
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(14), Ui.dp(34), Ui.dp(6), Ui.dp(8));
        head.addView(Icons.view(a, Icons.FOLDER_OPEN, t.accent), new LinearLayout.LayoutParams(Ui.dp(20), Ui.dp(20)));
        TextView tt = Ui.text(a, title, 15, t.text, true);
        tt.setSingleLine(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(10);
        head.addView(tt, tlp);
        head.addView(Ui.iconButton(a, Icons.PLUS, t.text, 18, 9, new View.OnClickListener() {
            @Override public void onClick(View v) { createMenu(targetDir()); }
        }));
        head.addView(Ui.iconButton(a, Icons.IMPORT, t.text, 18, 9, new View.OnClickListener() {
            @Override public void onClick(View v) { importMenu(targetDir()); }
        }));
        head.addView(Ui.iconButton(a, Icons.REFRESH, t.textSub, 17, 9, new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        }));
        panel.addView(head);
        panel.addView(Ui.divider(a, t.glassStroke));

        list = new ListView(a);
        list.setDivider(null);
        list.setPadding(0, Ui.dp(4), 0, Ui.dp(4));
        adapter = new Adapter();
        list.setAdapter(adapter);
        panel.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        footer = Ui.text(a, "", 10.5f, t.textSub, false);
        footer.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(10));
        panel.addView(footer);
        refresh();
    }

    View view() { return overlay; }

    boolean isOpen() { return open; }

    private File targetDir() {
        if (lastTouchedDir != null && lastTouchedDir.isDirectory()) return lastTouchedDir;
        return root;
    }

    /** where new source files should go by default */
    private File defaultCodeDir() {
        List<File> j = host.javaDirs();
        if (j != null) for (File d : j) if (d.isDirectory()) return d;
        return root;
    }

    void show() {
        Drawable blur = GlassBlur.leftStrip(host.blurSource(), panel.getLayoutParams().width);
        Drawable tint = Ui.fill(t.panelTint, 0, a);
        if (blur != null) panel.setBackground(new LayerDrawable(new Drawable[]{blur, tint}));
        else panel.setBackground(Ui.fill(0xF02A4A72, 0, a));
        refresh();
        overlay.setVisibility(View.VISIBLE);
        open = true;
        panel.setTranslationX(-panel.getLayoutParams().width);
        scrim.setAlpha(0f);
        ObjectAnimator.ofFloat(panel, "translationX", 0f).setDuration(220).start();
        ObjectAnimator.ofFloat(scrim, "alpha", 1f).setDuration(220).start();
    }

    void hide() {
        if (!open) return;
        open = false;
        ObjectAnimator an = ObjectAnimator.ofFloat(panel, "translationX", -panel.getLayoutParams().width);
        an.setDuration(180);
        an.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (!open) overlay.setVisibility(View.GONE);
            }
        });
        an.start();
        ObjectAnimator.ofFloat(scrim, "alpha", 0f).setDuration(180).start();
    }

    // ------------------------------------------------------------------ tree

    void refresh() {
        nodes.clear();
        boolean hidden = Prefs.b(a, "showHidden", false);
        add(root, 0, hidden);
        adapter.notifyDataSetChanged();
        int files = 0;
        for (Node n : nodes) if (n.f.isFile()) files++;
        footer.setText(root.getAbsolutePath() + "\n" + nodes.size() + " items visible");
    }

    private void add(File dir, int depth, boolean hidden) {
        for (File f : FileUtil.sorted(dir, hidden)) {
            String n = f.getName();
            if (dexProject && depth == 0 && (n.equals("classes") || n.equals("dexout") || n.equals("build.log.txt"))) continue;
            if (!hidden && n.equals(".gitignore")) { /* show */ }
            nodes.add(new Node(f, depth));
            if (f.isDirectory() && expanded.contains(f.getAbsolutePath())) add(f, depth + 1, hidden);
        }
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return nodes.size(); }
        @Override public Object getItem(int i) { return nodes.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View cv, ViewGroup parent) {
            final Node n = nodes.get(pos);
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(8 + n.depth * 14), Ui.dp(8), Ui.dp(8), Ui.dp(8));
            row.setBackground(Ui.ripple(a, Ui.fill(0x00000000, 0, a)));
            boolean dir = n.f.isDirectory();
            boolean ex = dir && expanded.contains(n.f.getAbsolutePath());
            if (dir) row.addView(Icons.view(a, ex ? Icons.CHEV_D : Icons.CHEV_R, t.textSub), new LinearLayout.LayoutParams(Ui.dp(14), Ui.dp(14)));
            else row.addView(new View(a), new LinearLayout.LayoutParams(Ui.dp(14), Ui.dp(14)));
            String icon = dir ? (ex ? Icons.FOLDER_OPEN : Icons.FOLDER) : Icons.forFile(n.f.getName());
            int ic = dir ? t.accent : (icon.equals(Icons.JAVA) ? 0xFFD8A657 : icon.equals(Icons.XML) ? 0xFF86B7C9 : t.textSub);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(18), Ui.dp(18));
            ilp.leftMargin = Ui.dp(6);
            row.addView(Icons.view(a, icon, ic), ilp);
            TextView tv = Ui.text(a, n.f.getName(), 13.5f, t.text, dir);
            tv.setSingleLine(true);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
            tlp.leftMargin = Ui.dp(8);
            row.addView(tv, tlp);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (n.f.isDirectory()) {
                        lastTouchedDir = n.f;
                        String k = n.f.getAbsolutePath();
                        if (!expanded.add(k)) expanded.remove(k);
                        refresh();
                    } else {
                        lastTouchedDir = n.f.getParentFile();
                        hide();
                        host.openFile(n.f);
                    }
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    if (n.f.isDirectory()) folderMenu(n.f); else fileMenu(n.f);
                    return true;
                }
            });
            return row;
        }
    }

    // ------------------------------------------------------------------ menus

    private void createMenu(final File dir) {
        Dialogs.Glass g = new Dialogs.Glass(a, "Create in " + rel(dir));
        g.row(Icons.NEWFILE, "New file", "Java class, layout, values …", new Runnable() { @Override public void run() { newFile(dir); } });
        g.row(Icons.NEWFOLDER, "New folder", null, new Runnable() { @Override public void run() { newFolder(dir); } });
        g.show();
    }

    private void importMenu(final File dir) {
        Dialogs.Glass g = new Dialogs.Glass(a, "Import into " + rel(dir));
        g.row(Icons.FILE, "Import files", "Pick one or more files", new Runnable() { @Override public void run() { importFiles(dir); } });
        g.row(Icons.FOLDER, "Import folder", "Copy a whole folder here", new Runnable() { @Override public void run() { importFolder(dir); } });
        g.show();
    }

    private void fileMenu(final File f) {
        Dialogs.Glass g = new Dialogs.Glass(a, f.getName());
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        Dialogs.Glass inner = g;
        // rows are added to the dialog body inside a scroll view
        g.body.addView(sv, new LinearLayout.LayoutParams(-1, Math.min(Ui.dp(420), (int) (a.getResources().getDisplayMetrics().heightPixels * 0.62f))));
        addRow(g, col, Icons.EDIT, "Open", null, new Runnable() { @Override public void run() { hide(); host.openFile(f); } });
        addRow(g, col, Icons.EDIT, "Rename", null, new Runnable() { @Override public void run() { rename(f); } });
        addRow(g, col, Icons.COPY, "Duplicate", null, new Runnable() { @Override public void run() { duplicate(f); } });
        addRow(g, col, Icons.MOVE, "Change path (move)", "Move to another folder", new Runnable() { @Override public void run() { move(f); } });
        addRow(g, col, Icons.EXPORT, "Export", "To Downloads/ACE or a folder", new Runnable() { @Override public void run() { export(f); } });
        addRow(g, col, Icons.SHARE, "Share", null, new Runnable() { @Override public void run() { Ui.shareFile(a, f, f.getName()); } });
        addRow(g, col, Icons.LINK, "Copy path", null, new Runnable() { @Override public void run() { Ui.copy(a, "path", f.getAbsolutePath()); } });
        addRow(g, col, Icons.INFO, "Properties", null, new Runnable() { @Override public void run() { info(f); } });
        addRow(g, col, Icons.TRASH, "Delete", null, new Runnable() { @Override public void run() { delete(f); } });
        g.show();
    }

    private void folderMenu(final File d) {
        lastTouchedDir = d;
        Dialogs.Glass g = new Dialogs.Glass(a, d.getName());
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        g.body.addView(sv, new LinearLayout.LayoutParams(-1, Math.min(Ui.dp(460), (int) (a.getResources().getDisplayMetrics().heightPixels * 0.66f))));
        addRow(g, col, Icons.NEWFILE, "New file here", null, new Runnable() { @Override public void run() { newFile(d); } });
        addRow(g, col, Icons.NEWFOLDER, "New folder here", null, new Runnable() { @Override public void run() { newFolder(d); } });
        addRow(g, col, Icons.IMPORT, "Import files here", null, new Runnable() { @Override public void run() { importFiles(d); } });
        addRow(g, col, Icons.FOLDER, "Import folder here", null, new Runnable() { @Override public void run() { importFolder(d); } });
        if (!d.equals(root)) {
            addRow(g, col, Icons.EDIT, "Rename", null, new Runnable() { @Override public void run() { rename(d); } });
            addRow(g, col, Icons.COPY, "Duplicate", null, new Runnable() { @Override public void run() { duplicate(d); } });
            addRow(g, col, Icons.MOVE, "Change path (move)", null, new Runnable() { @Override public void run() { move(d); } });
        }
        addRow(g, col, Icons.EXPORT, "Export folder (zip)", "To Downloads/ACE or a folder", new Runnable() { @Override public void run() { export(d); } });
        addRow(g, col, Icons.LINK, "Copy path", null, new Runnable() { @Override public void run() { Ui.copy(a, "path", d.getAbsolutePath()); } });
        addRow(g, col, Icons.INFO, "Properties", null, new Runnable() { @Override public void run() { info(d); } });
        addRow(g, col, Icons.CHEV_D, "Expand all", null, new Runnable() { @Override public void run() { expandAll(d); } });
        addRow(g, col, Icons.CHEV_R, "Collapse all", null, new Runnable() { @Override public void run() { expanded.clear(); expanded.add(root.getAbsolutePath()); refresh(); } });
        if (!d.equals(root)) addRow(g, col, Icons.TRASH, "Delete folder", null, new Runnable() { @Override public void run() { delete(d); } });
        g.show();
    }

    private void addRow(final Dialogs.Glass g, LinearLayout col, String icon, String title, String sub, final Runnable r) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(8), Ui.dp(10), Ui.dp(8), Ui.dp(10));
        row.setBackground(Ui.ripple(a, Ui.fill(0x00000000, 12, a)));
        FrameLayout ib = new FrameLayout(a);
        boolean danger = icon.equals(Icons.TRASH);
        ib.setBackground(Ui.fill(danger ? t.dangerSoft : t.accentSoft, 11, a));
        ib.addView(Icons.view(a, icon, danger ? t.danger : t.accent), new FrameLayout.LayoutParams(Ui.dp(18), Ui.dp(18), Gravity.CENTER));
        row.addView(ib, new LinearLayout.LayoutParams(Ui.dp(34), Ui.dp(34)));
        LinearLayout tc = new LinearLayout(a);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.addView(Ui.text(a, title, 14, danger ? t.danger : t.text, true));
        if (sub != null) tc.addView(Ui.text(a, sub, 11, t.textSub, false));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(12);
        row.addView(tc, tlp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { g.dismiss(); r.run(); }
        });
        col.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void expandAll(File d) {
        expanded.add(d.getAbsolutePath());
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) if (f.isDirectory()) expandAll(f);
        refresh();
    }

    private String rel(File f) {
        String r = f.getAbsolutePath();
        String b = root.getAbsolutePath();
        if (r.equals(b)) return root.getName();
        return r.startsWith(b) ? root.getName() + r.substring(b.length()) : r;
    }

    // ------------------------------------------------------------------ operations

    private void newFile(final File dir) {
        Dialogs.prompt(a, "New file", "Name (e.g. Utils.java, row.xml)", null, "Create", new Dialogs.Text() {
            @Override public void onText(String name) {
                if (name.contains("/") || name.contains("\\")) { Ui.toast(a, "Name cannot contain /"); return; }
                File f = new File(dir, name);
                if (f.exists()) { Ui.toast(a, "Already exists"); return; }
                try {
                    FileUtil.writeText(f, template(dir, name));
                    expanded.add(dir.getAbsolutePath());
                    refresh();
                    hide();
                    host.openFile(f);
                } catch (Exception e) { Ui.toast(a, "Could not create: " + e.getMessage()); }
            }
        });
    }

    private String template(File dir, String name) {
        String ext = FileUtil.ext(name);
        String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        if (ext.equals("java")) {
            String pkg = packageOf(dir);
            return (pkg.length() > 0 ? "package " + pkg + ";\n\n" : "") + "public class " + base + " {\n\n}\n";
        }
        if (ext.equals("xml")) {
            String dn = dir.getName();
            if (dn.startsWith("layout")) return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"vertical\">\n\n</LinearLayout>\n";
            if (dn.startsWith("values")) return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n\n</resources>\n";
            if (dn.startsWith("drawable")) return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:shape=\"rectangle\">\n    <solid android:color=\"#FFFFFF\" />\n    <corners android:radius=\"8dp\" />\n</shape>\n";
            if (dn.startsWith("menu")) return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<menu xmlns:android=\"http://schemas.android.com/apk/res/android\">\n\n</menu>\n";
            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
        }
        return "";
    }

    private String packageOf(File dir) {
        List<File> js = host.javaDirs();
        if (js == null) return "";
        for (File j : js) {
            if (FileUtil.isInside(dir, j)) {
                String r = dir.getAbsolutePath().substring(j.getAbsolutePath().length());
                if (r.startsWith("/")) r = r.substring(1);
                return r.replace('/', '.');
            }
        }
        return "";
    }

    private void newFolder(final File dir) {
        Dialogs.prompt(a, "New folder", "Folder name", null, "Create", new Dialogs.Text() {
            @Override public void onText(String name) {
                File f = new File(dir, name);
                if (f.exists()) { Ui.toast(a, "Already exists"); return; }
                if (!f.mkdirs()) { Ui.toast(a, "Could not create folder"); return; }
                expanded.add(dir.getAbsolutePath());
                expanded.add(f.getAbsolutePath());
                refresh();
            }
        });
    }

    private void importFiles(final File dir) {
        StoragePicker.pickFiles(a, "Import files into " + dir.getName(), null, null, true, new StoragePicker.FilesCb() {
            @Override public void onFiles(final List<File> files) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        int n = 0;
                        for (File f : files) {
                            try { FileUtil.copyFile(f, FileUtil.unique(dir, f.getName())); n++; } catch (Exception ignored) { }
                        }
                        final int cnt = n;
                        a.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                expanded.add(dir.getAbsolutePath());
                                refresh();
                                Ui.toast(a, cnt + " file(s) imported");
                            }
                        });
                    }
                }).start();
            }
        });
    }

    private void importFolder(final File dir) {
        StoragePicker.pickFolder(a, "Import a folder into " + dir.getName(), null, new StoragePicker.FolderCb() {
            @Override public void onFolder(final File src) {
                if (FileUtil.isInside(dir, src)) { Ui.toast(a, "Cannot import a folder into itself"); return; }
                new Thread(new Runnable() {
                    @Override public void run() {
                        String res;
                        try {
                            FileUtil.copyTree(src, FileUtil.unique(dir, src.getName()));
                            res = "Folder imported";
                        } catch (Exception e) { res = "Import failed: " + e.getMessage(); }
                        final String m = res;
                        a.runOnUiThread(new Runnable() {
                            @Override public void run() { expanded.add(dir.getAbsolutePath()); refresh(); Ui.toast(a, m); }
                        });
                    }
                }).start();
            }
        });
    }

    private void rename(final File f) {
        Dialogs.prompt(a, "Rename", "New name", f.getName(), "Rename", new Dialogs.Text() {
            @Override public void onText(String name) {
                if (name.equals(f.getName())) return;
                File to = new File(f.getParentFile(), name);
                if (to.exists()) { Ui.toast(a, "A file with that name exists"); return; }
                if (!f.renameTo(to)) { Ui.toast(a, "Rename failed"); return; }
                if (expanded.remove(f.getAbsolutePath())) expanded.add(to.getAbsolutePath());
                host.onRenamed(f, to);
                refresh();
            }
        });
    }

    private void duplicate(File f) {
        try {
            File to = FileUtil.unique(f.getParentFile(), f.getName().contains(".") && f.isFile()
                    ? f.getName().substring(0, f.getName().lastIndexOf('.')) + "_copy" + f.getName().substring(f.getName().lastIndexOf('.'))
                    : f.getName() + "_copy");
            FileUtil.copyTree(f, to);
            refresh();
            Ui.toast(a, "Duplicated as " + to.getName());
        } catch (Exception e) { Ui.toast(a, "Duplicate failed: " + e.getMessage()); }
    }

    private void delete(final File f) {
        Dialogs.confirm(a, "Delete " + (f.isDirectory() ? "folder" : "file") + "?",
                "\"" + f.getName() + "\" will be removed permanently" + (f.isDirectory() ? " with everything inside." : "."), "Delete",
                new Runnable() {
                    @Override public void run() {
                        FileUtil.deleteDir(f);
                        host.onDeleted(f);
                        refresh();
                    }
                });
    }

    private void move(final File f) {
        final List<File> dirs = new ArrayList<File>();
        collectDirs(root, dirs);
        final Dialogs.Glass g = new Dialogs.Glass(a, "Move " + f.getName() + " to …");
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        g.body.addView(sv, new LinearLayout.LayoutParams(-1, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.5f)));
        for (final File d : dirs) {
            if (d.equals(f.getParentFile()) || FileUtil.isInside(d, f)) continue;
            TextView tv = Ui.text(a, "📁  " + rel(d), 13.5f, t.text, false);
            tv.setPadding(Ui.dp(10), Ui.dp(11), Ui.dp(10), Ui.dp(11));
            tv.setBackground(Ui.ripple(a, Ui.fill(0x00000000, 10, a)));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { g.dismiss(); moveTo(f, d); }
            });
            col.addView(tv);
        }
        g.buttons("Other location…", new Runnable() {
            @Override public void run() {
                g.dismiss();
                StoragePicker.pickFolder(a, "Move to …", root, new StoragePicker.FolderCb() {
                    @Override public void onFolder(File d) { moveTo(f, d); }
                });
            }
        }, "Cancel", null);
        g.show();
    }

    private void collectDirs(File d, List<File> out) {
        out.add(d);
        for (File f : FileUtil.sorted(d, false)) if (f.isDirectory()) collectDirs(f, out);
    }

    private void moveTo(File f, File dir) {
        File to = new File(dir, f.getName());
        if (to.exists()) { Ui.toast(a, "Target already has \"" + f.getName() + "\""); return; }
        boolean ok = f.renameTo(to);
        if (!ok) {
            try { FileUtil.copyTree(f, to); FileUtil.deleteDir(f); ok = true; } catch (Exception ignored) { }
        }
        if (!ok) { Ui.toast(a, "Move failed"); return; }
        expanded.add(dir.getAbsolutePath());
        host.onRenamed(f, to);
        refresh();
        Ui.toast(a, "Moved");
    }

    private void export(final File f) {
        Dialogs.Glass g = new Dialogs.Glass(a, "Export " + f.getName());
        g.row(Icons.DOWNLOAD, "Downloads/ACE", "Quick export", new Runnable() {
            @Override public void run() {
                try {
                    File src = f;
                    String name = f.getName();
                    if (f.isDirectory()) {
                        src = new File(a.getCacheDir(), f.getName() + ".zip");
                        FileUtil.zipDir(f, src, "build", ".git");
                        name = f.getName() + ".zip";
                    }
                    String err = null;
                    Ui.exportToDownloads(a, src, name);
                    Ui.toast(a, "Exported to Downloads/ACE/" + name);
                } catch (Exception e) { Ui.toast(a, "Export failed: " + e.getMessage()); }
            }
        });
        g.row(Icons.FOLDER, "Choose a folder…", "Copy to any location", new Runnable() {
            @Override public void run() {
                StoragePicker.pickFolder(a, "Export to …", null, new StoragePicker.FolderCb() {
                    @Override public void onFolder(File d) {
                        try {
                            if (f.isDirectory()) FileUtil.copyTree(f, FileUtil.unique(d, f.getName()));
                            else FileUtil.copyFile(f, FileUtil.unique(d, f.getName()));
                            Ui.toast(a, "Exported to " + d.getAbsolutePath());
                        } catch (Exception e) { Ui.toast(a, "Export failed: " + e.getMessage()); }
                    }
                });
            }
        });
        g.show();
    }

    private void info(File f) {
        long size = FileUtil.size(f);
        String s = "Path: " + f.getAbsolutePath() + "\nType: " + (f.isDirectory() ? "Folder" : "File") + "\nSize: " + Ui.size(size)
                + "\nModified: " + Project.fmtTime(f.lastModified());
        if (f.isDirectory()) {
            String[] c = f.list();
            s += "\nItems: " + (c == null ? 0 : c.length);
        }
        Dialogs.info(a, f.getName(), s);
    }

    static int unusedColor() { return Color.TRANSPARENT; }
}
