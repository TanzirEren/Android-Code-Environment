package com.ace.ide;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ace.ide.build.ApkBuilder;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The ACE code IDE — shared by the Java→DEX tab and the App Builder tab. */
public class IdeActivity extends Activity implements ExplorerPanel.Host {

    private static final class OpenFile {
        File file;
        String text;
        boolean dirty;
        CodeEditor.UndoStack undo = new CodeEditor.UndoStack();
        int sel, scrollY;
        int lang;
    }

    private Project p;
    private Theme t;
    private boolean isApp;
    private File root;
    private ProjectLayout layout;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private FrameLayout rootView;
    private LinearLayout main;
    private CodeEditor editor;
    private TextView titleView, subView, status, errChip;
    private LinearLayout tabsRow;
    private HorizontalScrollView tabsScroll, sugScroll, symScroll;
    private LinearLayout sugRow;
    private View emptyView;
    private ExplorerPanel explorer;
    private View undoBtn, redoBtn;
    private DownloadStrip dlStrip;

    private final List<OpenFile> open = new ArrayList<OpenFile>();
    private OpenFile cur;
    private boolean loading;
    private final Map<String, List<BuildRunner.Diag>> diags = new HashMap<String, List<BuildRunner.Diag>>();
    private long liveStamp;

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent() != null ? getIntent().getStringExtra("id") : null;
        p = id == null ? null : Project.byId(this, id);
        if (p == null) { finish(); return; }
        t = Theme.get(this);
        isApp = p.kind == Project.KIND_APP;
        root = p.ideRoot(this);
        if (!root.isDirectory()) root.mkdirs();
        layout = isApp ? ProjectLayout.detect(root) : null;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(t.card);
        getWindow().setNavigationBarColor(t.bg);
        if (Build.VERSION.SDK_INT >= 23 && !t.dark) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        buildUi();
        applyEditorPrefs();
        openInitialFile();
        startLibSync(true);
    }

    @Override protected void onPause() {
        super.onPause();
        if (Prefs.b(this, "autosave", true)) saveAll(false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (editor != null) applyEditorPrefs();
        if (explorer != null) explorer.refresh();
        if (isApp && dlStrip != null) {
            Project np = Project.byId(this, p.id);
            if (np != null) { p = np; startLibSync(false); }
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
    }

    @Override public void onBackPressed() {
        if (explorer != null && explorer.isOpen()) { explorer.hide(); return; }
        saveAll(false);
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(t.bg);
        rootView.setFitsSystemWindows(true);

        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(t.bg);
        rootView.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // --- top bar (small icons)
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(t.card);
        top.setPadding(Ui.dp(4), 0, Ui.dp(4), 0);
        top.addView(Ui.iconButton(this, Icons.MENU, t.text, 18, 10, new View.OnClickListener() {
            @Override public void onClick(View v) { saveAll(false); explorer.show(); }
        }));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleView = Ui.text(this, isApp ? p.name : p.name + "  ·  DEX", 13.5f, t.text, true);
        titleView.setSingleLine(true);
        subView = Ui.text(this, "", 10.5f, t.textSub, false);
        subView.setSingleLine(true);
        titles.addView(titleView);
        titles.addView(subView);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(4);
        top.addView(titles, tl);
        top.addView(Ui.iconButton(this, Icons.PLAY, t.accent, 18, 10, new View.OnClickListener() {
            @Override public void onClick(View v) { run(); }
        }));
        undoBtn = Ui.iconButton(this, Icons.UNDO, t.text, 18, 10, new View.OnClickListener() {
            @Override public void onClick(View v) { editor.undo(); refreshButtons(); }
        });
        redoBtn = Ui.iconButton(this, Icons.REDO, t.text, 18, 10, new View.OnClickListener() {
            @Override public void onClick(View v) { editor.redo(); refreshButtons(); }
        });
        top.addView(undoBtn);
        top.addView(redoBtn);
        top.addView(Ui.iconButton(this, Icons.SAVE, t.text, 18, 10, new View.OnClickListener() {
            @Override public void onClick(View v) { saveAll(true); }
        }));
        top.addView(Ui.iconButton(this, Icons.MORE, t.text, 18, 10, new View.OnClickListener() {
            @Override public void onClick(View v) { moreMenu(); }
        }));
        main.addView(top, new LinearLayout.LayoutParams(-1, Ui.dp(48)));
        main.addView(Ui.divider(this, t.cardStroke));
        dlStrip = new DownloadStrip(this);
        dlStrip.setOnRetry(new Runnable() { @Override public void run() { startLibSync(false); } });
        main.addView(dlStrip, new LinearLayout.LayoutParams(-1, 0));

        // --- tabs
        tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsScroll.setBackgroundColor(t.bg);
        tabsRow = new LinearLayout(this);
        tabsRow.setGravity(Gravity.CENTER_VERTICAL);
        tabsScroll.addView(tabsRow, new FrameLayout.LayoutParams(-2, -1));
        main.addView(tabsScroll, new LinearLayout.LayoutParams(-1, Ui.dp(36)));
        main.addView(Ui.divider(this, t.cardStroke));

        // --- editor
        FrameLayout frame = new FrameLayout(this);
        editor = new CodeEditor(this);
        editor.setListener(new CodeEditor.Listener() {
            @Override public void onChanged() { editorChanged(); }
            @Override public void onZoom(float sp) { Prefs.fontSize(IdeActivity.this, Math.round(sp)); }
            @Override public void onCursorWord(String prefix) { showSuggestions(prefix); updateStatus(); }
        });
        frame.addView(editor, new FrameLayout.LayoutParams(-1, -1));
        emptyView = Ui.text(this, "Open a file from the explorer  ☰", 14, t.textSub, false);
        ((TextView) emptyView).setGravity(Gravity.CENTER);
        frame.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
        main.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));

        // --- suggestions
        sugScroll = new HorizontalScrollView(this);
        sugScroll.setHorizontalScrollBarEnabled(false);
        sugScroll.setBackgroundColor(t.card);
        sugRow = new LinearLayout(this);
        sugRow.setGravity(Gravity.CENTER_VERTICAL);
        sugScroll.addView(sugRow);
        sugScroll.setVisibility(View.GONE);
        main.addView(sugScroll, new LinearLayout.LayoutParams(-1, Ui.dp(34)));

        // --- status
        LinearLayout st = new LinearLayout(this);
        st.setGravity(Gravity.CENTER_VERTICAL);
        st.setBackgroundColor(t.card);
        st.setPadding(Ui.dp(12), Ui.dp(3), Ui.dp(12), Ui.dp(3));
        status = Ui.text(this, "", 10.5f, t.textSub, false);
        st.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        errChip = Ui.text(this, "", 10.5f, t.danger, true);
        errChip.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showErrorList(); } });
        st.addView(errChip);
        main.addView(Ui.divider(this, t.cardStroke));
        main.addView(st, new LinearLayout.LayoutParams(-1, -2));

        // --- symbol bar
        symScroll = new HorizontalScrollView(this);
        symScroll.setHorizontalScrollBarEnabled(false);
        symScroll.setBackgroundColor(t.card);
        LinearLayout sym = new LinearLayout(this);
        sym.setGravity(Gravity.CENTER_VERTICAL);
        String[] keys = {"⇥", "{", "}", "(", ")", "[", "]", ";", "\"", "'", "<", ">", "/", "=", ".", ",", ":", "_", "!", "&", "|", "+", "-", "*", "@", "#", "$", "%", "\\", "?"};
        for (final String k : keys) {
            TextView b = Ui.text(this, k, 15, t.text, true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Ui.ripple(this, Ui.fill(0x00000000, 8, this)));
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { insert(k.equals("⇥") ? "    " : k); }
            });
            sym.addView(b, new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(38)));
        }
        symScroll.addView(sym);
        main.addView(Ui.divider(this, t.cardStroke));
        main.addView(symScroll, new LinearLayout.LayoutParams(-1, Ui.dp(40)));

        explorer = new ExplorerPanel(this, root, !isApp, isApp ? p.name : p.name + " (DEX)");
        rootView.addView(explorer.view(), new FrameLayout.LayoutParams(-1, -1));
        setContentView(rootView);
        refreshTabs();
        refreshButtons();
    }

    private void applyEditorPrefs() {
        editor.setTheme(t);
        editor.setTextSizeSp(Prefs.fontSize(this));
        editor.setOptions(Prefs.b(this, "lineNumbers", true), Prefs.wrap(this), Prefs.b(this, "autoIndent", true),
                Prefs.b(this, "autoClose", true), Prefs.b(this, "hlLine", true), Prefs.b(this, "pinchZoom", true));
        symScroll.setVisibility(Prefs.b(this, "symbolBar", true) ? View.VISIBLE : View.GONE);
        if (Prefs.b(this, "keepScreenOn", false)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void refreshButtons() {
        if (undoBtn != null) undoBtn.setAlpha(editor.canUndo() ? 1f : 0.35f);
        if (redoBtn != null) redoBtn.setAlpha(editor.canRedo() ? 1f : 0.35f);
    }

    private void refreshTabs() {
        tabsRow.removeAllViews();
        for (final OpenFile of : open) {
            boolean sel = of == cur;
            LinearLayout tab = new LinearLayout(this);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(Ui.dp(10), 0, Ui.dp(4), 0);
            tab.setBackground(sel ? Ui.fill(t.accentSoft, 0, this) : null);
            TextView n = Ui.text(this, (of.dirty ? "● " : "") + of.file.getName(), 12, sel ? t.accent : t.textSub, sel);
            n.setSingleLine(true);
            tab.addView(n);
            tab.addView(Ui.iconButton(this, Icons.CLOSE, t.textSub, 11, 7, new View.OnClickListener() {
                @Override public void onClick(View v) { closeTab(of); }
            }));
            tab.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { switchTo(of); } });
            tabsRow.addView(tab, new LinearLayout.LayoutParams(-2, -1));
        }
        emptyView.setVisibility(cur == null ? View.VISIBLE : View.GONE);
        editor.setVisibility(cur == null ? View.INVISIBLE : View.VISIBLE);
        subView.setText(cur == null ? "" : cur.file.getName());
        updateStatus();
    }

    // ------------------------------------------------------------------ libraries

    /** detect libraries from the project, add what is missing and download in parallel with an animated bar */
    private void startLibSync(boolean detect) {
        if (!isApp || layout == null || !layout.valid() || LibFetcher.isRunning()) return;
        LibFetcher.run(this, p, layout, detect, new LibFetcher.Listener() {
            @Override public void onStart(String message) { dlStrip.start(message); }
            @Override public void onProgress(float fraction, String message) { dlStrip.progress(fraction, message); }
            @Override public void onDone(String message, boolean error, boolean changed) {
                if (message != null) dlStrip.finish(message, error);
                if (changed) { Project np = Project.byId(IdeActivity.this, p.id); if (np != null) p = np; }
            }
        });
    }

    /** a new import of a known library appeared in a saved file */
    private void checkImports(String text) {
        if (!isApp || !Prefs.b(this, "autoLibs", true)) return;
        for (String id : Libs.detectIdsInText(text.substring(0, Math.min(text.length(), 20000)))) {
            if (!p.libs.contains(id)) { startLibSync(true); return; }
        }
    }

    // ------------------------------------------------------------------ files

    @Override public Activity act() { return this; }
    @Override public View blurSource() { return main; }

    @Override public List<File> javaDirs() {
        List<File> l = new ArrayList<File>();
        if (isApp && layout != null) l.addAll(layout.javaDirs);
        else l.add(p.srcDir(this));
        return l;
    }

    private void openInitialFile() {
        File first = null;
        for (File d : javaDirs()) {
            first = firstJava(d, 0);
            if (first != null) break;
        }
        if (first != null) openFile(first);
    }

    private File firstJava(File dir, int depth) {
        if (depth > 8) return null;
        List<File> fs = FileUtil.sorted(dir, false);
        for (File f : fs) if (f.isFile() && f.getName().equals("MainActivity.java")) return f;
        for (File f : fs) if (f.isFile() && f.getName().endsWith(".java")) return f;
        for (File f : fs) if (f.isDirectory()) { File r = firstJava(f, depth + 1); if (r != null) return r; }
        return null;
    }

    @Override public void openFile(File f) {
        if (!f.isFile()) return;
        for (OpenFile of : open) if (of.file.equals(f)) { switchTo(of); return; }
        String ext = FileUtil.ext(f.getName());
        if (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("webp") || ext.equals("gif")) { preview(f); return; }
        if (!FileUtil.isText(f.getName())) { Ui.toast(this, "Binary file — use Export / Share from the explorer"); return; }
        if (f.length() > 2 * 1024 * 1024) { Ui.toast(this, "File is too large to edit here (" + Ui.size(f.length()) + ")"); return; }
        try {
            OpenFile of = new OpenFile();
            of.file = f;
            of.text = FileUtil.readText(f);
            of.lang = Highlighter.langOf(f.getName());
            open.add(of);
            switchTo(of);
        } catch (Exception e) {
            Ui.toast(this, "Cannot open: " + e.getMessage());
        }
    }

    private void preview(File f) {
        Dialogs.Glass g = new Dialogs.Glass(this, f.getName());
        ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setBackgroundColor(0x11808080);
        iv.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
        g.add(iv, Ui.lp(-1, Ui.dp(300), 0, 10, 0, 0));
        g.message(Ui.size(f.length()));
        g.buttons(null, null, "Close", null);
        g.show();
    }

    private void capture() {
        if (cur == null) return;
        cur.text = editor.getText().toString();
        cur.sel = editor.getSelectionStart();
        cur.scrollY = editor.getScrollY();
    }

    private void switchTo(OpenFile of) {
        capture();
        cur = of;
        loading = true;
        editor.load(of.text, of.undo);
        loading = false;
        Highlighter.apply(editor.getText(), of.lang, t);
        applyErrors();
        final OpenFile fin = of;
        editor.post(new Runnable() {
            @Override public void run() {
                try {
                    editor.setSelection(Math.max(0, Math.min(fin.sel, editor.getText().length())));
                    editor.scrollTo(editor.getScrollX(), fin.scrollY);
                } catch (Throwable ignored) { }
            }
        });
        refreshTabs();
        refreshButtons();
        sugScroll.setVisibility(View.GONE);
    }

    private void closeTab(final OpenFile of) {
        if (of == cur) capture();
        if (of.dirty) {
            if (Prefs.b(this, "autosave", true)) writeFile(of);
            else {
                Dialogs.confirm(this, "Unsaved changes", "Close " + of.file.getName() + " without saving?", "Discard", new Runnable() {
                    @Override public void run() { of.dirty = false; closeTab(of); }
                });
                return;
            }
        }
        int idx = open.indexOf(of);
        open.remove(of);
        if (of == cur) {
            cur = null;
            if (!open.isEmpty()) switchTo(open.get(Math.max(0, Math.min(idx, open.size() - 1))));
            else { loading = true; editor.load("", null); loading = false; refreshTabs(); }
        } else refreshTabs();
    }

    @Override public void onRenamed(File from, File to) {
        for (int i = 0; i < open.size(); i++) {
            OpenFile of = open.get(i);
            if (of.file.equals(from)) { of.file = to; of.lang = Highlighter.langOf(to.getName()); }
            else if (FileUtil.isInside(of.file, from)) {
                String rel = of.file.getAbsolutePath().substring(from.getAbsolutePath().length());
                of.file = new File(to.getAbsolutePath() + rel);
            }
        }
        refreshTabs();
    }

    @Override public void onDeleted(File f) {
        List<OpenFile> gone = new ArrayList<OpenFile>();
        for (OpenFile of : open) if (of.file.equals(f) || FileUtil.isInside(of.file, f)) gone.add(of);
        for (OpenFile of : gone) { of.dirty = false; closeTab(of); }
    }

    private boolean writeFile(OpenFile of) {
        try {
            FileUtil.writeText(of.file, of.text);
            of.dirty = false;
            if (of.lang == Highlighter.JAVA) checkImports(of.text);
            return true;
        } catch (Exception e) {
            Ui.toast(this, "Save failed: " + e.getMessage());
            return false;
        }
    }

    private void saveAll(boolean toast) {
        capture();
        int n = 0;
        for (OpenFile of : open) if (of.dirty && writeFile(of)) n++;
        refreshTabs();
        if (toast) Ui.toast(this, n > 0 ? "Saved " + n + " file(s) ✔" : "Nothing to save");
    }

    // ------------------------------------------------------------------ editing

    private final Runnable hlRun = new Runnable() {
        @Override public void run() {
            if (cur != null) Highlighter.apply(editor.getText(), cur.lang, t);
        }
    };

    private final Runnable liveRun = new Runnable() {
        @Override public void run() { liveCheck(); }
    };

    private void editorChanged() {
        if (loading || cur == null) return;
        if (!cur.dirty) { cur.dirty = true; refreshTabs(); }
        refreshButtons();
        h.removeCallbacks(hlRun);
        h.postDelayed(hlRun, 320);
        if (Prefs.b(this, "liveCheck", true)) {
            h.removeCallbacks(liveRun);
            h.postDelayed(liveRun, 1800);
        }
        updateStatus();
    }

    private void insert(String s) {
        if (cur == null) return;
        int a = Math.max(0, editor.getSelectionStart()), b = Math.max(0, editor.getSelectionEnd());
        editor.getText().replace(Math.min(a, b), Math.max(a, b), s);
    }

    private void showSuggestions(String prefix) {
        sugRow.removeAllViews();
        if (cur == null || !Prefs.b(this, "suggestions", true) || prefix == null || prefix.length() < 2) {
            sugScroll.setVisibility(View.GONE);
            return;
        }
        List<String> s = Suggest.get(cur.lang, prefix, editor.getText().toString());
        if (s.isEmpty()) { sugScroll.setVisibility(View.GONE); return; }
        for (final String w : s) {
            TextView c = Ui.text(this, w, 13, t.accent, true);
            c.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
            c.setBackground(Ui.ripple(this, Ui.fill(0x00000000, 8, this)));
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int sel = editor.getSelectionStart();
                    int st = sel;
                    Editable e = editor.getText();
                    while (st > 0 && (Character.isLetterOrDigit(e.charAt(st - 1)) || e.charAt(st - 1) == '_' || e.charAt(st - 1) == ':' || e.charAt(st - 1) == '.')) st--;
                    e.replace(st, sel, w);
                }
            });
            sugRow.addView(c);
        }
        sugScroll.setVisibility(View.VISIBLE);
        sugScroll.scrollTo(0, 0);
    }

    private void updateStatus() {
        if (cur == null) { status.setText(""); errChip.setText(""); return; }
        int line = editor.currentLine();
        int sel = Math.max(0, editor.getSelectionStart());
        int col = 1;
        Editable e = editor.getText();
        for (int i = sel - 1; i >= 0 && i < e.length() && e.charAt(i) != '\n'; i--) col++;
        String lang = cur.lang == Highlighter.JAVA ? "Java" : cur.lang == Highlighter.XML ? "XML" : cur.lang == Highlighter.JSON ? "JSON" : "Text";
        status.setText("Ln " + line + ", Col " + col + "  ·  " + lang + "  ·  " + Math.round(editor.sizeSp()) + "sp");
        List<BuildRunner.Diag> d = diags.get(cur.file.getAbsolutePath());
        errChip.setText(d == null || d.isEmpty() ? "" : "● " + d.size() + (d.size() == 1 ? " error" : " errors"));
    }

    // ------------------------------------------------------------------ diagnostics

    private void applyErrors() {
        Set<Integer> lines = new HashSet<Integer>();
        if (cur != null) {
            List<BuildRunner.Diag> d = diags.get(cur.file.getAbsolutePath());
            if (d != null) for (BuildRunner.Diag x : d) if (x.line > 0) lines.add(x.line);
        }
        editor.setErrorLines(lines);
        updateStatus();
    }

    private void setDiags(List<BuildRunner.Diag> list) {
        diags.clear();
        for (BuildRunner.Diag d : list) {
            if (d.path == null) continue;
            List<BuildRunner.Diag> l = diags.get(d.path);
            if (l == null) { l = new ArrayList<BuildRunner.Diag>(); diags.put(d.path, l); }
            l.add(d);
        }
        applyErrors();
    }

    private void showErrorList() {
        final Dialogs.Glass g = new Dialogs.Glass(this, "Errors");
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        int n = 0;
        for (Map.Entry<String, List<BuildRunner.Diag>> en : diags.entrySet()) {
            for (final BuildRunner.Diag d : en.getValue()) {
                n++;
                final File f = new File(en.getKey());
                TextView tv = Ui.text(this, f.getName() + ":" + d.line + "\n" + d.message, 12.5f, t.text, false);
                tv.setPadding(Ui.dp(8), Ui.dp(9), Ui.dp(8), Ui.dp(9));
                tv.setBackground(Ui.ripple(this, Ui.fill(0x00000000, 8, this)));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        g.dismiss();
                        openFile(f);
                        editor.post(new Runnable() { @Override public void run() { editor.goToLine(d.line); } });
                    }
                });
                col.addView(tv);
            }
        }
        if (n == 0) { Ui.toast(this, "No errors 🎉"); return; }
        g.add(sv, Ui.lp(-1, Math.min(Ui.dp(360), Ui.dp(70) * n), 0, 8, 0, 0));
        g.buttons(null, null, "Close", null);
        g.show();
    }

    /** background syntax / semantic check of the current file */
    private void liveCheck() {
        if (cur == null || BuildRunner.isRunning()) return;
        final OpenFile of = cur;
        final String text = editor.getText().toString();
        final String path = of.file.getAbsolutePath();
        if (of.lang == Highlighter.XML) {
            List<BuildRunner.Diag> out = new ArrayList<BuildRunner.Diag>();
            try {
                com.ace.ide.build.XmlCheck.check(text);
            } catch (com.ace.ide.build.XmlCheck.Error e) {
                out.add(makeDiag(path, e.line, e.getMessage()));
            }
            diags.remove(path);
            if (!out.isEmpty()) diags.put(path, out);
            applyErrors();
            return;
        }
        if (of.lang != Highlighter.JAVA || !isApp && false) { return; }
        final File jar = Converter.sysAndroidJar(this);
        if (jar == null || !jar.isFile()) return;
        final long stamp = ++liveStamp;
        final List<File> sources = new ArrayList<File>(javaDirs());
        final File gen = new File(p.buildDir(), "gen");
        if (isApp) sources.add(gen);
        final StringBuilder cp = new StringBuilder(jar.getAbsolutePath());
        if (isApp) {
            for (String c : Libs.coordsOf(p)) {
                File f = Libs.cacheFile(this, c);
                if (f != null && f.isFile() && f.getName().endsWith(".jar")) cp.append(File.pathSeparator).append(f.getAbsolutePath());
            }
            if (layout != null) for (File d : layout.libDirs) {
                File[] fs = d.listFiles();
                if (fs != null) for (File f : fs) if (f.getName().endsWith(".jar")) cp.append(File.pathSeparator).append(f.getAbsolutePath());
            }
        } else {
            File[] fs = p.libsDir(this).listFiles();
            if (fs != null) for (File f : fs) if (f.getName().endsWith(".jar")) cp.append(File.pathSeparator).append(f.getAbsolutePath());
        }
        final boolean haveR = new File(gen, "R.java").exists() || findR(gen);
        bg.execute(new Runnable() {
            @Override public void run() {
                final List<BuildRunner.Diag> res = new ArrayList<BuildRunner.Diag>();
                try {
                    File tmpDir = new File(getCacheDir(), "live");
                    tmpDir.mkdirs();
                    File tmp = new File(tmpDir, of.file.getName());
                    FileUtil.writeText(tmp, text);
                    List<String> args = new ArrayList<String>();
                    args.add("-1.8"); args.add("-nowarn"); args.add("-proc:none"); args.add("-encoding"); args.add("UTF-8");
                    args.add("-cp"); args.add(cp.toString());
                    StringBuilder sp = new StringBuilder();
                    for (File d : sources) if (d.isDirectory()) { if (sp.length() > 0) sp.append(File.pathSeparator); sp.append(d.getAbsolutePath()); }
                    if (sp.length() > 0) { args.add("-sourcepath"); args.add(sp.toString()); }
                    args.add("-d"); args.add("none");
                    args.add(tmp.getAbsolutePath());
                    StringWriter o = new StringWriter(), er = new StringWriter();
                    BatchCompiler.compile(args.toArray(new String[0]), new PrintWriter(o, true), new PrintWriter(er, true), null);
                    for (BuildRunner.Diag d : BuildRunner.parse(o.toString() + er.toString(), null)) {
                        if (d.path == null || !d.path.equals(tmp.getAbsolutePath())) continue;
                        String m = d.message;
                        if (!haveR && m.matches(".*\\bR\\b.*cannot be resolved.*")) continue;
                        if (!haveR && m.contains("R.") && m.contains("cannot be resolved")) continue;
                        res.add(makeDiag(path, d.line, m));
                    }
                } catch (Throwable ignored) { return; }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (stamp != liveStamp || cur != of) return;
                        diags.remove(path);
                        if (!res.isEmpty()) diags.put(path, res);
                        applyErrors();
                    }
                });
            }
        });
    }

    private boolean findR(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return false;
        for (File f : fs) {
            if (f.isFile() && f.getName().equals("R.java")) return true;
            if (f.isDirectory() && findR(f)) return true;
        }
        return false;
    }

    private BuildRunner.Diag makeDiag(String path, int line, String msg) {
        return new BuildRunner.Diag(path, line, msg);
    }

    // ------------------------------------------------------------------ run

    private void run() {
        if (cur == null && open.isEmpty() && false) return;
        saveAll(false);
        if (isApp) runApp(); else runDex();
    }

    private void runApp() {
        if (BuildRunner.isRunning()) { Ui.toast(this, "A build is already running"); return; }
        final RunDialog rd = new RunDialog(this, "Building " + p.name, true);
        rd.show();
        BuildRunner.run(this, p, new BuildRunner.Listener() {
            @Override public void onStep(String title) { rd.step(title); }
            @Override public void onLog(String line) { rd.log(line); }
            @Override public void onDone(final ApkBuilder.Result r, List<BuildRunner.Diag> d) {
                rd.dismiss();
                p = Project.byId(IdeActivity.this, p.id) != null ? Project.byId(IdeActivity.this, p.id) : p;
                if (r.ok) {
                    setDiags(new ArrayList<BuildRunner.Diag>());
                    showSuccessApk(r);
                } else {
                    setDiags(d);
                    Dialogs.log(IdeActivity.this, "Build failed", r.errors, true);
                    if (!d.isEmpty() && d.get(0).path != null) {
                        File f = new File(d.get(0).path);
                        if (f.isFile()) { openFile(f); final int ln = d.get(0).line; editor.post(new Runnable() { @Override public void run() { editor.goToLine(ln); } }); }
                    }
                }
            }
        });
    }

    private void showSuccessApk(final ApkBuilder.Result r) {
        final Dialogs.Glass g = new Dialogs.Glass(this, "Build successful ✔");
        final File apk = r.apk;
        g.message(apk.getName() + "  ·  " + Ui.size(apk.length()) + "  ·  " + String.format(java.util.Locale.US, "%.1f s", r.millis / 1000f)
                + "\nSaved in the project: output/" + apk.getName());
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        TextView dl = Ui.button(this, "Download", t.accent);
        dl.setTextColor(t.onAccent);
        dl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Ui.exportToDownloads(IdeActivity.this, apk, apk.getName());
                    Ui.toast(IdeActivity.this, "Saved to Downloads/ACE/" + apk.getName() + " ✔");
                } catch (Exception e) { Ui.toast(IdeActivity.this, "Download failed: " + e.getMessage()); }
            }
        });
        TextView in = Ui.button(this, "Install", t.accentDark);
        in.setTextColor(Color.WHITE);
        in.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Installer.install(IdeActivity.this, apk); }
        });
        TextView sh = Ui.button(this, "Share", t.chipBg);
        sh.setTextColor(t.text);
        sh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.shareFile(IdeActivity.this, apk, apk.getName()); }
        });
        row.addView(dl, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(in, Ui.lp(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) in.getLayoutParams()).weight = 1f;
        row.addView(sh, Ui.lp(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) sh.getLayoutParams()).weight = 1f;
        g.add(row, Ui.lp(-1, -2, 0, 12, 0, 0));
        g.buttons(null, null, "Close", null);
        g.show();
    }

    private void runDex() {
        if (Converter.isRunning()) { Ui.toast(this, "A conversion is already running"); return; }
        final RunDialog rd = new RunDialog(this, "Converting " + p.name, false);
        rd.show();
        Converter.convert(this, p, new Converter.Callback() {
            @Override public void onStep(String step) { rd.step(step); }
            @Override public void onDone(boolean success, String log) {
                rd.dismiss();
                Project np = Project.byId(IdeActivity.this, p.id);
                if (np != null) p = np;
                if (success) {
                    setDiags(new ArrayList<BuildRunner.Diag>());
                    showSuccessDex(log);
                } else {
                    setDiags(BuildRunner.parse(log, null));
                    Dialogs.log(IdeActivity.this, "Conversion failed", log, true);
                }
            }
        });
    }

    private void showSuccessDex(String log) {
        final Dialogs.Glass g = new Dialogs.Glass(this, "DEX ready ✔");
        final List<File> files = p.publicDexFiles(this);
        long total = 0;
        for (File f : files) total += f.length();
        g.message(files.size() + " dex file(s)  ·  " + Ui.size(total) + "\nSaved to " + p.publicDexDirNoCreate(this).getAbsolutePath());
        LinearLayout row = new LinearLayout(this);
        TextView dl = Ui.button(this, "Download", t.accent);
        dl.setTextColor(t.onAccent);
        dl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    for (File f : files) Ui.exportToDownloads(IdeActivity.this, f, p.safeName() + "_" + f.getName());
                    Ui.toast(IdeActivity.this, files.size() + " file(s) saved to Downloads/ACE ✔");
                } catch (Exception e) { Ui.toast(IdeActivity.this, "Download failed: " + e.getMessage()); }
            }
        });
        TextView sm = Ui.button(this, "Smali", t.accentDark);
        sm.setTextColor(Color.WHITE);
        sm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(IdeActivity.this, DexViewerActivity.class);
                i.putExtra("id", p.id);
                startActivity(i);
            }
        });
        TextView sh = Ui.button(this, "Share", t.chipBg);
        sh.setTextColor(t.text);
        sh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.shareFiles(IdeActivity.this, files, p.safeName()); }
        });
        row.addView(dl, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(sm, Ui.lp(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) sm.getLayoutParams()).weight = 1f;
        row.addView(sh, Ui.lp(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) sh.getLayoutParams()).weight = 1f;
        g.add(row, Ui.lp(-1, -2, 0, 12, 0, 0));
        g.buttons(null, null, "Close", null);
        g.show();
    }

    // ------------------------------------------------------------------ run dialog

    private final class RunDialog {
        final Dialogs.Glass g;
        final TextView stage, logView;
        final ScrollView sv;
        final StringBuilder buf = new StringBuilder();

        RunDialog(Activity a, String title, boolean app) {
            g = new Dialogs.Glass(a, title);
            g.dialog.setCancelable(false);
            LinearLayout head = new LinearLayout(a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.ProgressBar pb = new android.widget.ProgressBar(a);
            pb.setIndeterminate(true);
            head.addView(pb, new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
            stage = Ui.text(a, "Starting …", 13.5f, t.accent, true);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -2, 1f);
            sl.leftMargin = Ui.dp(10);
            head.addView(stage, sl);
            g.add(head, Ui.lp(-1, -2, 0, 10, 0, 0));
            logView = Ui.text(a, "", 10.5f, t.text, false);
            logView.setTypeface(Typeface.MONOSPACE);
            logView.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
            sv = new ScrollView(a);
            sv.setBackground(Ui.outline(t.inputBg, t.cardStroke, 12, 1, a));
            sv.addView(logView);
            g.add(sv, Ui.lp(-1, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.38f), 0, 10, 0, 6));
        }

        void show() { g.show(); }
        void dismiss() { g.dismiss(); }
        void step(String s) { stage.setText(s + " …"); }
        void log(String line) {
            if (buf.length() > 60000) buf.delete(0, 20000);
            buf.append(line).append('\n');
            logView.setText(buf);
            sv.post(new Runnable() { @Override public void run() { sv.fullScroll(View.FOCUS_DOWN); } });
        }
    }

    // ------------------------------------------------------------------ menu

    private void moreMenu() {
        final Dialogs.Glass g = new Dialogs.Glass(this, null);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        g.body.addView(sv, new LinearLayout.LayoutParams(-1, (int) (getResources().getDisplayMetrics().heightPixels * 0.66f)));
        menuRow(g, col, Icons.SETTINGS, "Settings", "Editor, build, signing, appearance", new Runnable() {
            @Override public void run() { saveAll(false); startActivity(new Intent(IdeActivity.this, SettingsActivity.class)); }
        });
        if (isApp) {
            menuRow(g, col, Icons.PACKAGE, "Project settings", "Name, package, version, icon, SDK, signing", new Runnable() {
                @Override public void run() {
                    saveAll(false);
                    Intent i = new Intent(IdeActivity.this, ProjectSettingsActivity.class);
                    i.putExtra("id", p.id);
                    startActivity(i);
                }
            });
            menuRow(g, col, Icons.LIB, "Libraries", "Add Gson, OkHttp, Jsoup … or your own jar", new Runnable() {
                @Override public void run() {
                    Intent i = new Intent(IdeActivity.this, LibrariesActivity.class);
                    i.putExtra("id", p.id);
                    startActivity(i);
                }
            });
        }
        menuRow(g, col, Icons.SEARCH, "Find & replace", null, new Runnable() { @Override public void run() { findReplace(); } });
        menuRow(g, col, Icons.ARROW_L, "Go to line", null, new Runnable() { @Override public void run() { gotoLine(); } });
        menuRow(g, col, Icons.CODE, "Snippets", "Insert common Android code", new Runnable() { @Override public void run() { snippets(); } });
        menuRow(g, col, Icons.REFRESH, "Format code", "Re-indent the current file", new Runnable() { @Override public void run() { format(); } });
        menuRow(g, col, Icons.EDIT, "Comment / uncomment", null, new Runnable() { @Override public void run() { toggleComment(); } });
        menuRow(g, col, Icons.COPY, "Duplicate line", null, new Runnable() { @Override public void run() { duplicateLine(); } });
        menuRow(g, col, Icons.BUG, "Show errors", null, new Runnable() { @Override public void run() { showErrorList(); } });
        if (isApp) {
            menuRow(g, col, Icons.LOG, "Last build log", null, new Runnable() { @Override public void run() { lastLog(); } });
            menuRow(g, col, Icons.TRASH, "Clean build", "Delete build/ and output/", new Runnable() {
                @Override public void run() {
                    FileUtil.deleteDir(p.buildDir());
                    FileUtil.deleteDir(p.outputDir());
                    explorer.refresh();
                    Ui.toast(IdeActivity.this, "Cleaned");
                }
            });
            menuRow(g, col, Icons.EXPORT, "Export project (zip)", "To Downloads/ACE", new Runnable() { @Override public void run() { exportZip(); } });
        }
        menuRow(g, col, Icons.CLOSE, "Close all tabs", null, new Runnable() {
            @Override public void run() {
                saveAll(false);
                open.clear();
                cur = null;
                loading = true;
                editor.load("", null);
                loading = false;
                refreshTabs();
            }
        });
        g.show();
    }

    private void menuRow(final Dialogs.Glass g, LinearLayout col, String icon, String title, String sub, final Runnable r) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(8), Ui.dp(10), Ui.dp(8), Ui.dp(10));
        row.setBackground(Ui.ripple(this, Ui.fill(0x00000000, 12, this)));
        FrameLayout ib = new FrameLayout(this);
        ib.setBackground(Ui.fill(t.accentSoft, 11, this));
        ib.addView(Icons.view(this, icon, t.accent), new FrameLayout.LayoutParams(Ui.dp(18), Ui.dp(18), Gravity.CENTER));
        row.addView(ib, new LinearLayout.LayoutParams(Ui.dp(34), Ui.dp(34)));
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.addView(Ui.text(this, title, 14, t.text, true));
        if (sub != null) tc.addView(Ui.text(this, sub, 11, t.textSub, false));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(12);
        row.addView(tc, tlp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { g.dismiss(); r.run(); }
        });
        col.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void lastLog() {
        File f = new File(p.outputDir(), "last-build.log");
        try {
            Dialogs.log(this, "Last build log", f.isFile() ? FileUtil.readText(f) : "No build yet.", false);
        } catch (Exception e) { Ui.toast(this, "Cannot read log"); }
    }

    private void exportZip() {
        saveAll(false);
        final Activity a = this;
        new Thread(new Runnable() {
            @Override public void run() {
                String msg;
                try {
                    File z = new File(getCacheDir(), p.safeName() + ".zip");
                    FileUtil.zipDir(root, z, "build", ".git");
                    Ui.exportToDownloads(a, z, p.safeName() + ".zip");
                    msg = "Exported to Downloads/ACE/" + p.safeName() + ".zip ✔";
                } catch (Exception e) { msg = "Export failed: " + e.getMessage(); }
                final String m = msg;
                runOnUiThread(new Runnable() { @Override public void run() { Ui.toast(a, m); } });
            }
        }).start();
    }

    // ------------------------------------------------------------------ editor tools

    private void gotoLine() {
        if (cur == null) return;
        Dialogs.prompt(this, "Go to line", "Line number", null, "Go", new Dialogs.Text() {
            @Override public void onText(String v) {
                try { editor.goToLine(Integer.parseInt(v.trim())); } catch (NumberFormatException e) { Ui.toast(IdeActivity.this, "Not a number"); }
            }
        });
    }

    private int findFrom = 0;

    private void findReplace() {
        if (cur == null) return;
        final Dialogs.Glass g = new Dialogs.Glass(this, "Find & replace");
        final android.widget.EditText f = Ui.input(this, "Find", t);
        final android.widget.EditText r = Ui.input(this, "Replace with", t);
        final boolean[] opts = {false, false};
        LinearLayout o = new LinearLayout(this);
        final TextView cs = Ui.chip(this, "Aa  match case", t.textSub, t.chipBg);
        final TextView rx = Ui.chip(this, ".*  regex", t.textSub, t.chipBg);
        cs.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { opts[0] = !opts[0]; cs.setBackground(Ui.fill(opts[0] ? t.accentSoft : t.chipBg, 20, IdeActivity.this)); cs.setTextColor(opts[0] ? t.accent : t.textSub); }
        });
        rx.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { opts[1] = !opts[1]; rx.setBackground(Ui.fill(opts[1] ? t.accentSoft : t.chipBg, 20, IdeActivity.this)); rx.setTextColor(opts[1] ? t.accent : t.textSub); }
        });
        o.addView(cs);
        o.addView(rx, Ui.lp(-2, -2, 8, 0, 0, 0));
        g.add(f, Ui.lp(-1, -2, 0, 10, 0, 0));
        g.add(r, Ui.lp(-1, -2, 0, 8, 0, 0));
        g.add(o, Ui.lp(-1, -2, 0, 10, 0, 0));
        final TextView info = Ui.text(this, "", 11.5f, t.textSub, false);
        g.add(info, Ui.lp(-1, -2, 0, 8, 0, 0));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.END);
        TextView next = Ui.button(this, "Next", t.accent);
        next.setTextColor(t.onAccent);
        TextView rep = Ui.button(this, "Replace", t.accentDark);
        rep.setTextColor(Color.WHITE);
        TextView all = Ui.button(this, "All", t.accentDark);
        all.setTextColor(Color.WHITE);
        bar.addView(all);
        bar.addView(rep, Ui.lp(-2, -2, 6, 0, 0, 0));
        bar.addView(next, Ui.lp(-2, -2, 6, 0, 0, 0));
        g.add(bar, Ui.lp(-1, -2, 0, 10, 0, 0));
        g.buttons(null, null, "Close", null);
        if (g.dialog.getWindow() != null) g.dialog.getWindow().setDimAmount(0.15f);
        final Runnable doNext = new Runnable() {
            @Override public void run() {
                String q = f.getText().toString();
                if (q.length() == 0) return;
                String text = editor.getText().toString();
                int[] m = match(text, q, opts, Math.max(editor.getSelectionEnd(), 0));
                if (m == null) m = match(text, q, opts, 0);
                if (m == null) { info.setText("No matches"); return; }
                editor.requestFocus();
                editor.setSelection(m[0], m[1]);
                info.setText("Found at line " + editor.currentLine());
            }
        };
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { doNext.run(); } });
        rep.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String q = f.getText().toString();
                int a = editor.getSelectionStart(), b = editor.getSelectionEnd();
                if (q.length() > 0 && b > a) {
                    String sel = editor.getText().subSequence(a, b).toString();
                    int[] m = match(sel, q, opts, 0);
                    if (m != null && m[0] == 0 && m[1] == sel.length()) {
                        String with = opts[1] ? sel.replaceFirst(flags(opts) + q, r.getText().toString()) : r.getText().toString();
                        editor.replaceRecorded(a, b, with);
                    }
                }
                doNext.run();
            }
        });
        all.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String q = f.getText().toString();
                if (q.length() == 0) return;
                String text = editor.getText().toString();
                String out;
                try {
                    if (opts[1]) out = text.replaceAll(flags(opts) + q, r.getText().toString());
                    else out = Pattern.compile(Pattern.quote(q), opts[0] ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                            .matcher(text).replaceAll(Matcher.quoteReplacement(r.getText().toString()));
                } catch (Exception e) { info.setText("Bad pattern: " + e.getMessage()); return; }
                if (out.equals(text)) { info.setText("No matches"); return; }
                editor.replaceRecorded(0, text.length(), out);
                Highlighter.apply(editor.getText(), cur.lang, t);
                info.setText("Replaced all");
            }
        });
        g.show();
    }

    private static String flags(boolean[] o) { return o[0] ? "" : "(?i)"; }

    private static int[] match(String text, String q, boolean[] opts, int from) {
        try {
            Pattern p = opts[1] ? Pattern.compile(q, opts[0] ? 0 : Pattern.CASE_INSENSITIVE)
                    : Pattern.compile(Pattern.quote(q), opts[0] ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher m = p.matcher(text);
            if (from <= text.length() && m.find(from)) return new int[]{m.start(), m.end()};
        } catch (Exception ignored) { }
        return null;
    }

    private void snippets() {
        if (cur == null) { Ui.toast(this, "Open a file first"); return; }
        final String[][] s = {
                {"onCreate", "@Override\nprotected void onCreate(Bundle savedInstanceState) {\n    super.onCreate(savedInstanceState);\n    setContentView(R.layout.activity_main);\n}\n"},
                {"Toast", "Toast.makeText(this, \"Hello\", Toast.LENGTH_SHORT).show();"},
                {"Log", "android.util.Log.d(\"ACE\", \"message\");"},
                {"OnClickListener", "view.setOnClickListener(new View.OnClickListener() {\n    @Override\n    public void onClick(View v) {\n        \n    }\n});"},
                {"Start Activity", "startActivity(new Intent(this, OtherActivity.class));"},
                {"Handler delay", "new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {\n    @Override\n    public void run() {\n        \n    }\n}, 1000);"},
                {"Thread + UI", "new Thread(new Runnable() {\n    @Override\n    public void run() {\n        // background work\n        runOnUiThread(new Runnable() {\n            @Override\n            public void run() {\n                // update UI\n            }\n        });\n    }\n}).start();"},
                {"SharedPreferences", "SharedPreferences prefs = getSharedPreferences(\"app\", MODE_PRIVATE);\nprefs.edit().putString(\"key\", \"value\").apply();\nString v = prefs.getString(\"key\", \"\");"},
                {"AlertDialog", "new AlertDialog.Builder(this)\n    .setTitle(\"Title\")\n    .setMessage(\"Message\")\n    .setPositiveButton(\"OK\", null)\n    .setNegativeButton(\"Cancel\", null)\n    .show();"},
                {"ListView adapter", "ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items);\nlistView.setAdapter(adapter);"},
                {"HTTP GET (thread)", "HttpURLConnection c = (HttpURLConnection) new URL(\"https://example.com\").openConnection();\nBufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));\nString line;\nStringBuilder sb = new StringBuilder();\nwhile ((line = r.readLine()) != null) sb.append(line);\nr.close();"},
                {"Button (XML)", "<Button\n    android:id=\"@+id/button\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"Button\" />"},
                {"TextView (XML)", "<TextView\n    android:id=\"@+id/text\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"Text\"\n    android:textSize=\"16sp\" />"},
                {"EditText (XML)", "<EditText\n    android:id=\"@+id/input\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:hint=\"Type here\"\n    android:inputType=\"text\" />"},
        };
        String[] labels = new String[s.length];
        for (int i = 0; i < s.length; i++) labels[i] = s[i][0];
        Dialogs.choose(this, "Insert snippet", labels, -1, new Dialogs.IndexCb() {
            @Override public void onIndex(int i) { insert(s[i][1]); }
        });
    }

    private void toggleComment() {
        if (cur == null) return;
        Editable e = editor.getText();
        String s = e.toString();
        int a = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int b = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        int ls = s.lastIndexOf('\n', Math.max(0, a - 1)) + 1;
        if (a == 0) ls = 0;
        int le = s.indexOf('\n', b);
        if (le < 0) le = s.length();
        String block = s.substring(ls, le);
        String out;
        if (cur.lang == Highlighter.XML) {
            String tr = block.trim();
            if (tr.startsWith("<!--") && tr.endsWith("-->")) out = block.replaceFirst("<!--\\s?", "").replaceFirst("\\s?-->(?!.*-->)", "");
            else out = "<!-- " + block + " -->";
        } else {
            String[] lines = block.split("\n", -1);
            boolean all = true;
            for (String l : lines) if (l.trim().length() > 0 && !l.trim().startsWith("//")) all = false;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i];
                if (all) l = l.replaceFirst("//\\s?", "");
                else if (l.trim().length() > 0) l = "//" + l;
                sb.append(l);
                if (i < lines.length - 1) sb.append('\n');
            }
            out = sb.toString();
        }
        editor.replaceRecorded(ls, le, out);
        Highlighter.apply(editor.getText(), cur.lang, t);
    }

    private void duplicateLine() {
        if (cur == null) return;
        String s = editor.getText().toString();
        int sel = Math.max(0, editor.getSelectionStart());
        int ls = s.lastIndexOf('\n', Math.max(0, sel - 1)) + 1;
        if (sel == 0) ls = 0;
        int le = s.indexOf('\n', sel);
        if (le < 0) le = s.length();
        editor.replaceRecorded(le, le, "\n" + s.substring(ls, le));
    }

    private void format() {
        if (cur == null) return;
        String s = editor.getText().toString();
        String out = cur.lang == Highlighter.XML ? formatXml(s) : cur.lang == Highlighter.JAVA || cur.lang == Highlighter.JSON ? formatBraces(s) : s;
        if (out.equals(s)) { Ui.toast(this, "Already formatted"); return; }
        int line = editor.currentLine();
        editor.replaceRecorded(0, s.length(), out);
        Highlighter.apply(editor.getText(), cur.lang, t);
        editor.goToLine(line);
    }

    private static String formatBraces(String s) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        boolean inBlockComment = false;
        String[] lines = s.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String tr = raw.trim();
            int lead = 0;
            if (inBlockComment) {
                out.append(Ui.rep("    ", depth)).append(tr.startsWith("*") ? " " : "").append(tr);
            } else {
                int closers = 0;
                while (lead < tr.length() && (tr.charAt(lead) == '}' || tr.charAt(lead) == ')' || tr.charAt(lead) == ']')) { closers++; lead++; }
                int d = Math.max(0, depth - closers);
                if (tr.length() == 0) { }
                else out.append(Ui.rep("    ", d)).append(tr);
            }
            // update depth from this line, ignoring strings & comments
            boolean str = false, chr = false;
            for (int k = 0; k < tr.length(); k++) {
                char c = tr.charAt(k);
                if (inBlockComment) { if (c == '*' && k + 1 < tr.length() && tr.charAt(k + 1) == '/') { inBlockComment = false; k++; } continue; }
                if (str) { if (c == '\\') k++; else if (c == '"') str = false; continue; }
                if (chr) { if (c == '\\') k++; else if (c == '\'') chr = false; continue; }
                if (c == '"') str = true;
                else if (c == '\'') chr = true;
                else if (c == '/' && k + 1 < tr.length() && tr.charAt(k + 1) == '/') break;
                else if (c == '/' && k + 1 < tr.length() && tr.charAt(k + 1) == '*') { inBlockComment = true; k++; }
                else if (c == '{' || c == '(' || c == '[') depth++;
                else if (c == '}' || c == ')' || c == ']') depth = Math.max(0, depth - 1);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    private static String formatXml(String s) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        boolean inTag = false;
        String[] lines = s.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String tr = lines[i].trim();
            int d = depth;
            if (inTag) d = depth + 1;
            else if (tr.startsWith("</")) d = Math.max(0, depth - 1);
            if (tr.length() > 0) out.append(Ui.rep("    ", d)).append(tr);
            if (!inTag && tr.startsWith("<") && !tr.startsWith("<?") && !tr.startsWith("<!--") && !tr.startsWith("</")) {
                if (!tr.contains(">")) { inTag = true; }
                else if (!tr.endsWith("/>") && !tr.matches(".*</[^>]+>\\s*$")) depth++;
            } else if (inTag) {
                if (tr.contains(">")) {
                    inTag = false;
                    if (!tr.endsWith("/>")) depth++;
                }
            } else if (tr.startsWith("</")) {
                depth = Math.max(0, depth - 1);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }
}
