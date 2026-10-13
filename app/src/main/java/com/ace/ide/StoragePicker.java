package com.ace.ide;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** In-app storage browser: pick a folder or files from anywhere on the device. */
public final class StoragePicker {

    public interface FolderCb { void onFolder(File dir); }
    public interface FilesCb { void onFiles(List<File> files); }

    private final Activity a;
    private final Theme t;
    private final boolean folderMode, multi;
    private final String[] exts;
    private File cur;
    private final List<File> items = new ArrayList<File>();
    private final Set<String> selected = new HashSet<String>();
    private TextView pathView, actionBtn;
    private BaseAdapter adapter;
    private Dialog dialog;
    private final FolderCb folderCb;
    private final FilesCb filesCb;

    private StoragePicker(Activity a, boolean folderMode, boolean multi, String[] exts, File start, FolderCb fc, FilesCb xc) {
        this.a = a;
        this.t = Theme.get(a);
        this.folderMode = folderMode;
        this.multi = multi;
        this.exts = exts;
        this.folderCb = fc;
        this.filesCb = xc;
        File s = start;
        if (s == null || !s.isDirectory()) s = Environment.getExternalStorageDirectory();
        this.cur = s;
    }

    public static void pickFolder(Activity a, String title, File start, FolderCb cb) {
        new StoragePicker(a, true, false, null, start, cb, null).show(title);
    }

    public static void pickFiles(Activity a, String title, File start, String[] exts, boolean multi, FilesCb cb) {
        new StoragePicker(a, false, multi, exts, start, null, cb).show(title);
    }

    private void show(String title) {
        dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.outline(t.dark ? 0xF2262219 : 0xF8FBF8F5, t.cardStroke, 22, 1, a));
        int p = Ui.dp(14);
        root.setPadding(p, p, p, Ui.dp(10));

        root.addView(Ui.text(a, title, 16.5f, t.text, true));

        // quick locations
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout quick = new LinearLayout(a);
        hs.addView(quick);
        quick.addView(quickChip("Internal", Environment.getExternalStorageDirectory()));
        quick.addView(quickChip("Downloads", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)));
        quick.addView(quickChip("ACE", new File(Environment.getExternalStorageDirectory(), "ACE")));
        File[] vols = new File("/storage").listFiles();
        if (vols != null) for (File v : vols) {
            String n = v.getName();
            if (v.isDirectory() && !n.equals("emulated") && !n.equals("self") && v.canRead()) quick.addView(quickChip("SD " + n, v));
        }
        root.addView(hs, Ui.lp(-1, -2, 0, 8, 0, 0));

        LinearLayout pathRow = new LinearLayout(a);
        pathRow.setGravity(Gravity.CENTER_VERTICAL);
        pathRow.setBackground(Ui.outline(t.inputBg, t.cardStroke, 12, 1, a));
        pathRow.setPadding(Ui.dp(4), Ui.dp(2), Ui.dp(10), Ui.dp(2));
        pathRow.addView(Ui.iconButton(a, Icons.ARROW_L, t.text, 18, 8, new View.OnClickListener() {
            @Override public void onClick(View v) { up(); }
        }));
        pathView = Ui.text(a, "", 11.5f, t.textSub, false);
        pathView.setSingleLine(true);
        pathView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        pathRow.addView(pathView, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(pathRow, Ui.lp(-1, -2, 0, 8, 0, 6));

        ListView lv = new ListView(a);
        lv.setDivider(null);
        adapter = new Adapter();
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> parent, View view, int pos, long id) { tap(items.get(pos)); }
        });
        root.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = Ui.text(a, "Cancel", 14, t.textSub, true);
        cancel.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dialog.dismiss(); } });
        bar.addView(cancel);
        actionBtn = Ui.button(a, folderMode ? "Select this folder" : "Select", t.accent);
        actionBtn.setTextColor(t.onAccent);
        actionBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { done(); } });
        bar.addView(actionBtn, Ui.lp(-2, -2, 6, 0, 0, 0));
        root.addView(bar, Ui.lp(-1, -2, 0, 8, 0, 0));

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams wl = w.getAttributes();
            wl.width = Math.min(a.getResources().getDisplayMetrics().widthPixels - Ui.dp(24), Ui.dp(440));
            wl.height = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.82f);
            wl.dimAmount = 0.5f;
            w.setAttributes(wl);
        }
        load(cur);
        if (Ui.alive(a)) dialog.show();
    }

    private TextView quickChip(String label, final File f) {
        TextView c = Ui.text(a, label, 12, t.accent, true);
        c.setBackground(Ui.ripple(a, Ui.fill(t.accentSoft, 20, a)));
        c.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(6);
        c.setLayoutParams(lp);
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (f != null && f.isDirectory()) load(f); else Ui.toast(a, "Folder not found");
            }
        });
        return c;
    }

    private void up() {
        File p = cur.getParentFile();
        if (p != null && p.canRead()) load(p);
    }

    private void load(File dir) {
        cur = dir;
        items.clear();
        File[] fs = dir.listFiles();
        if (fs != null) {
            List<File> all = new ArrayList<File>();
            for (File f : fs) {
                if (f.getName().startsWith(".") && !f.isDirectory()) continue;
                if (f.isDirectory() || folderMode == false && match(f)) all.add(f);
            }
            java.util.Collections.sort(all, new java.util.Comparator<File>() {
                @Override public int compare(File x, File y) {
                    if (x.isDirectory() != y.isDirectory()) return x.isDirectory() ? -1 : 1;
                    return x.getName().compareToIgnoreCase(y.getName());
                }
            });
            items.addAll(all);
        }
        pathView.setText(dir.getAbsolutePath());
        adapter.notifyDataSetChanged();
        refreshAction();
    }

    private boolean match(File f) {
        if (exts == null) return true;
        String n = f.getName().toLowerCase();
        for (String e : exts) if (n.endsWith(e)) return true;
        return false;
    }

    private void tap(File f) {
        if (f.isDirectory()) { load(f); return; }
        if (multi) {
            if (!selected.add(f.getAbsolutePath())) selected.remove(f.getAbsolutePath());
        } else {
            selected.clear();
            selected.add(f.getAbsolutePath());
        }
        adapter.notifyDataSetChanged();
        refreshAction();
        if (!multi) done();
    }

    private void refreshAction() {
        if (actionBtn == null || folderMode) return;
        actionBtn.setText(selected.isEmpty() ? "Select" : "Select (" + selected.size() + ")");
    }

    private void done() {
        if (folderMode) {
            dialog.dismiss();
            folderCb.onFolder(cur);
        } else {
            if (selected.isEmpty()) { Ui.toast(a, "Tap a file to select it"); return; }
            List<File> out = new ArrayList<File>();
            for (String s : selected) out.add(new File(s));
            dialog.dismiss();
            filesCb.onFiles(out);
        }
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            File f = items.get(pos);
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(6), Ui.dp(9), Ui.dp(6), Ui.dp(9));
            boolean sel = selected.contains(f.getAbsolutePath());
            if (sel) row.setBackground(Ui.fill(t.accentSoft, 10, a));
            String icon = f.isDirectory() ? Icons.FOLDER : Icons.forFile(f.getName());
            row.addView(Icons.view(a, icon, f.isDirectory() ? t.accent : t.textSub), new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView n = Ui.text(a, f.getName(), 13.5f, t.text, f.isDirectory());
            n.setSingleLine(true);
            col.addView(n);
            if (f.isFile()) col.addView(Ui.text(a, Ui.size(f.length()), 10.5f, t.textSub, false));
            row.addView(col, Ui.lp(0, -2, 12, 0, 0, 0));
            ((LinearLayout.LayoutParams) col.getLayoutParams()).weight = 1f;
            if (sel) row.addView(Icons.view(a, Icons.CHECK, t.accent), new LinearLayout.LayoutParams(Ui.dp(20), Ui.dp(20)));
            return row;
        }
    }

    static FrameLayout unused(Activity a) { return new FrameLayout(a); }
}
