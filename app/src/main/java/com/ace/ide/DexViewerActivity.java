package com.ace.ide;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** DEX Explorer — classes, methods, strings, real smali, export. Multi-dex aware. */
public class DexViewerActivity extends Activity {

    private static final int TAB_CLASSES = 0, TAB_METHODS = 1, TAB_STRINGS = 2;
    private static final int MAX_SMALI_CHARS = 150000;

    private Theme t;
    private Project p;
    private final List<SmaliGen.DexFile> dexes = new ArrayList<>();
    private final Map<String, Integer> classDex = new HashMap<>();
    private List<String> classes = new ArrayList<>();
    private List<String> methods = new ArrayList<>();
    private List<String> strings = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private int tab = TAB_CLASSES;

    private ListView listView;
    private TextView infoText, emptyText;
    private EditText search;
    private LinearLayout loadingBox;
    private final TextView[] tabs = new TextView[3];
    private final Handler h = new Handler();
    private Runnable filterRun;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        t = Theme.get(this);
        getWindow().setStatusBarColor(Ui.HEAD_TOP);
        String id = getIntent() != null ? getIntent().getStringExtra("id") : null;
        p = id != null ? Project.byId(this, id) : null;
        if (p == null) { finish(); return; }
        buildUi();
        load();
    }

    private LinearLayout.LayoutParams rowWeight() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private void buildUi() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(t.bg);
        setContentView(frame);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        // ---- header ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(Ui.HEAD_TOP, Ui.ACCENT, 0, this));
        header.setPadding(Ui.dp(16), Ui.dp(24), Ui.dp(16), Ui.dp(16));

        LinearLayout hr = new LinearLayout(this);
        hr.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "←", 20, Color.WHITE, true);
        back.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        back.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        back.setOnClickListener(v -> finish());
        hr.addView(back, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout hc = new LinearLayout(this);
        hc.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hclp = new LinearLayout.LayoutParams(0, -2, 1f);
        hclp.leftMargin = Ui.dp(10);
        hc.addView(Ui.text(this, "DEX Explorer", 17, Color.WHITE, true));
        hc.addView(Ui.text(this, p.name, 11, 0xB3FFFFFF, false));
        hr.addView(hc, hclp);
        header.addView(hr, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView saveAll = miniBtn("💾 Save All Smali");
        saveAll.setOnClickListener(v -> saveAllSmali());
        btnRow.addView(saveAll);
        TextView reload = miniBtn("↺ Reload");
        reload.setOnClickListener(v -> {
            loadingBox.setVisibility(View.VISIBLE);
            load();
        });
        btnRow.addView(reload);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(-1, -2);
        brp.topMargin = Ui.dp(12);
        header.addView(btnRow, brp);

        infoText = Ui.text(this, "loading…", 11, 0xB3FFFFFF, false);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.topMargin = Ui.dp(8);
        header.addView(infoText, ilp);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // ---- body ----
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = Ui.dp(10);
        root.addView(body, blp);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(Ui.dp(16), 0, Ui.dp(16), Ui.dp(8));
        String[] names = {"🏫 Classes", "⚙ Methods", "🔤 Strings"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tabs[i] = Ui.button(this, names[i], t.textSub);
            tabs[i].setTextSize(13);
            tabs[i].setOnClickListener(v -> { tab = idx; applyTabs(); filter(); });
            LinearLayout.LayoutParams lp = rowWeight();
            if (i > 0) lp.leftMargin = Ui.dp(8);
            tabRow.addView(tabs[i], lp);
        }
        body.addView(tabRow, new LinearLayout.LayoutParams(-1, -2));

        search = Ui.input(this, "🔍 Filter…", t);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(8));
        search.setLayoutParams(sp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { scheduleFilter(); }
        });
        body.addView(search);

        FrameLayout listWrap = new FrameLayout(this);
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(Ui.dp(8));
        listView.setPadding(Ui.dp(16), 0, Ui.dp(16), Ui.dp(20));
        listView.setClipToPadding(false);
        listView.setAdapter(new ListAdapter());
        listView.setOnItemClickListener((parent, v, pos, id2) -> onItemTap(shown.get(pos)));
        listView.setOnItemLongClickListener((parent, v, pos, id2) -> {
            Ui.copy(this, "item", shown.get(pos));
            return true;
        });
        listWrap.addView(listView, new FrameLayout.LayoutParams(-1, -1));
        emptyText = Ui.text(this, "Nothing matches", 13, t.textSub, false);
        emptyText.setVisibility(View.GONE);
        listWrap.addView(emptyText, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        body.addView(listWrap, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- loading overlay ----
        loadingBox = new LinearLayout(this);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setGravity(Gravity.CENTER);
        loadingBox.setBackgroundColor(t.bg);
        loadingBox.setClickable(true);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        loadingBox.addView(pb, new LinearLayout.LayoutParams(-2, -2));
        loadingBox.addView(Ui.text(this, "Parsing DEX…", 13, t.text, true));
        frame.addView(loadingBox, new FrameLayout.LayoutParams(-1, -1));
    }

    /** bug fix: the old version cast getLayoutParams() (null at this point) → NPE on open */
    private TextView miniBtn(String label) {
        TextView b = Ui.text(this, label, 12, Color.WHITE, true);
        b.setBackground(Ui.ripple(this, Ui.fill(0x40FFFFFF, 10, this)));
        b.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(8);
        b.setLayoutParams(lp);
        Ui.pressScale(b, 0.94f);
        return b;
    }

    private void applyTabs() {
        for (int i = 0; i < 3; i++) {
            tabs[i].setBackground(Ui.ripple(this, Ui.fill(i == tab ? Ui.ACCENT : t.textSub, 14, this)));
        }
        search.setHint(tab == TAB_CLASSES ? "🔍 Filter classes…"
                : tab == TAB_METHODS ? "🔍 Filter methods…" : "🔍 Filter strings…");
    }

    // ------------------------------------------------------------ loading

    private void load() {
        new Thread(() -> {
            try {
                List<File> files = p.publicDexFiles(this);
                if (files.isEmpty()) {
                    File internal = p.internalDexFile(this);
                    if (internal.exists()) files.add(internal);
                }
                if (files.isEmpty()) {
                    throw new Exception("classes.dex not found — convert the project first");
                }
                final List<SmaliGen.DexFile> parsed = new ArrayList<>();
                final Map<String, Integer> cmap = new HashMap<>();
                final List<String> cl = new ArrayList<>();
                final List<String> ml = new ArrayList<>();
                TreeSet<String> st = new TreeSet<>();
                long bytes = 0;
                for (File f : files) {
                    SmaliGen.DexFile d = SmaliGen.open(f);
                    int idx = parsed.size();
                    parsed.add(d);
                    bytes += f.length();
                    for (String n : SmaliGen.classNames(d)) { cl.add(n); cmap.put(n, idx); }
                    ml.addAll(SmaliGen.methodSignatures(d));
                    st.addAll(SmaliGen.stringList(d));
                }
                java.util.Collections.sort(cl);
                final List<String> sl = new ArrayList<>(st);
                final long totalBytes = bytes;
                final String ver = parsed.get(0).version;
                runOnUiThread(() -> {
                    if (!Ui.alive(this)) return;
                    dexes.clear();
                    dexes.addAll(parsed);
                    classDex.clear();
                    classDex.putAll(cmap);
                    classes = cl;
                    methods = ml;
                    strings = sl;
                    loadingBox.setVisibility(View.GONE);
                    infoText.setText("DEX v" + ver + "  •  " + Ui.size(totalBytes)
                            + (parsed.size() > 1 ? "  •  " + parsed.size() + " files" : "")
                            + "  •  " + classes.size() + " classes  •  "
                            + methods.size() + " methods  •  " + strings.size() + " strings");
                    applyTabs();
                    filter();
                });
            } catch (Throwable e) {
                final String msg = e.getMessage();
                runOnUiThread(() -> {
                    if (!Ui.alive(this)) return;
                    loadingBox.setVisibility(View.GONE);
                    Ui.dialog(this)
                            .setTitle("No DEX found")
                            .setMessage("Convert the project first, then open the DEX Explorer.\n\n" + msg)
                            .setPositiveButton("OK", (d, w) -> finish())
                            .setCancelable(false)
                            .show();
                });
            }
        }, "dex-load").start();
    }

    private void scheduleFilter() {
        if (filterRun != null) h.removeCallbacks(filterRun);
        filterRun = new Runnable() { @Override public void run() { filter(); } };
        h.postDelayed(filterRun, 150);
    }

    private void filter() {
        shown.clear();
        String q = search.getText().toString().trim().toLowerCase(Locale.US);
        List<String> src = tab == TAB_CLASSES ? classes : tab == TAB_METHODS ? methods : strings;
        int cap = 4000;   // keep list rendering instant on huge dex files
        int total = 0;
        for (String s : src) {
            if (q.isEmpty() || s.toLowerCase(Locale.US).contains(q)) {
                total++;
                if (shown.size() < cap) shown.add(s);
            }
        }
        ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
        listView.setSelection(0);
        emptyText.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        if (total > cap) Ui.toast(this, "Showing first " + cap + " of " + total + " — refine the filter");
    }

    // ------------------------------------------------------------ actions

    private void onItemTap(String item) {
        if (tab == TAB_STRINGS) {
            Ui.copy(this, "string", item);
        } else if (tab == TAB_METHODS) {
            int i = item.indexOf("->");
            String cls = i > 0 ? item.substring(0, i) : item;
            if (classDex.containsKey(cls)) classDialog(cls); else Ui.copy(this, "method", item);
        } else {
            classDialog(item);
        }
    }

    /** class options: view / copy / save / share smali */
    private void classDialog(final String name) {
        Ui.dialog(this)
                .setTitle(name)
                .setItems(new String[]{"📜 View Smali", "📋 Copy Smali",
                        "💾 Save .smali", "↗ Share Smali", "📋 Copy class name"},
                        (d, w) -> {
                            if (w == 0) showSmali(name);
                            else if (w == 1) smaliBg(name, code -> Ui.copy(this, "smali", code));
                            else if (w == 2) smaliBg(name, code -> saveOne(name, code));
                            else if (w == 3) smaliBg(name, code -> Ui.shareText(this, name + ".smali", code));
                            else Ui.copy(this, "class", name);
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private interface SmaliReady { void ready(String code); }

    private SmaliGen.DexClass lookup(String name) {
        Integer di = classDex.get(name);
        if (di == null || di >= dexes.size()) return null;
        return SmaliGen.findClass(dexes.get(di), name);
    }

    /** generates smali on a background thread — catches Throwable, never crashes */
    private void smaliBg(final String name, final SmaliReady action) {
        new Thread(() -> {
            String code;
            try {
                SmaliGen.DexClass c = lookup(name);
                code = (c == null) ? "# class not found"
                        : SmaliGen.renderClass(dexes.get(classDex.get(name)), c);
            } catch (Throwable e) {
                code = "# Failed to generate smali: " + e.getMessage();
            }
            final String out = code;
            runOnUiThread(() -> { if (Ui.alive(this)) action.ready(out); });
        }, "smali-gen").start();
    }

    private CharSequence colorize(String code) {
        boolean dk = Prefs.dark(this);
        int dir = dk ? 0xFFC084FC : 0xFF7C3AED;
        int com = dk ? 0xFF64748B : 0xFF94A3B8;
        int op = dk ? 0xFF60A5FA : 0xFF2563EB;
        int str = dk ? 0xFF34D399 : 0xFF059669;
        SpannableStringBuilder sb = new SpannableStringBuilder(code);
        int pos = 0, n = code.length();
        while (pos < n) {
            int end = code.indexOf('\n', pos);
            if (end < 0) end = n;
            int s = pos;
            while (s < end && code.charAt(s) == ' ') s++;
            if (s < end) {
                char c = code.charAt(s);
                if (c == '#') {
                    sb.setSpan(new ForegroundColorSpan(com), s, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (c == '.') {
                    sb.setSpan(new ForegroundColorSpan(dir), s, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    int e2 = s;
                    while (e2 < end && code.charAt(e2) != ' ') e2++;
                    sb.setSpan(new ForegroundColorSpan(op), s, e2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    int q = code.indexOf('"', e2);
                    if (q >= 0 && q < end) {
                        sb.setSpan(new ForegroundColorSpan(str), q, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            pos = end + 1;
        }
        return sb;
    }

    private void showSmali(final String name) {
        smaliBg(name, code -> {
            final String full = code;
            String shownCode = code;
            boolean cut = false;
            if (shownCode.length() > MAX_SMALI_CHARS) {
                shownCode = shownCode.substring(0, MAX_SMALI_CHARS) + "\n# … truncated — use Copy / Save for the full class";
                cut = true;
            }
            ScrollView sc = new ScrollView(this);
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
            TextView tv = Ui.text(this, "", 11, t.text, false);
            tv.setText(colorize(shownCode));
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextIsSelectable(true);
            tv.setHorizontallyScrolling(true);
            int pd = Ui.dp(14);
            tv.setPadding(pd, pd, pd, pd);
            hs.addView(tv, new FrameLayout.LayoutParams(-2, -2));
            sc.addView(hs, new FrameLayout.LayoutParams(-1, -2));
            Ui.dialog(this)
                    .setTitle(name + (cut ? "  (truncated)" : ""))
                    .setView(sc)
                    .setPositiveButton("📋 Copy", (d, w) -> Ui.copy(this, "smali", full))
                    .setNeutralButton("↗ Share", (d, w) -> Ui.shareText(this, name + ".smali", full))
                    .setNegativeButton("Close", null)
                    .show();
        });
    }

    /** resolves smali/<pkg>/<Class>.smali and refuses anything escaping the smali folder */
    private File smaliTarget(String name) throws java.io.IOException {
        File dir = p.smaliDir(this);
        File out = new File(dir, name.replace('.', '/') + ".smali");
        String base = dir.getCanonicalPath() + File.separator;
        if (!out.getCanonicalPath().startsWith(base)) throw new java.io.IOException("bad class name");
        return out;
    }

    private void saveOne(String name, String code) {
        try {
            File out = smaliTarget(name);
            Ui.writeText(out, code);
            Ui.toast(this, "Saved: " + out.getAbsolutePath());
        } catch (Throwable e) {
            Ui.toast(this, "Save failed: " + e.getMessage());
        }
    }

    private void saveAllSmali() {
        if (dexes.isEmpty()) { Ui.toast(this, "Load a dex first"); return; }
        Ui.toast(this, "Exporting smali…");
        final List<String> names = new ArrayList<>(classes);
        new Thread(() -> {
            int count = 0, failed = 0;
            try {
                for (String name : names) {
                    try {
                        SmaliGen.DexClass c = lookup(name);
                        if (c == null) { failed++; continue; }
                        Ui.writeText(smaliTarget(name),
                                SmaliGen.renderClass(dexes.get(classDex.get(name)), c));
                        count++;
                    } catch (Throwable ignored) { failed++; }
                }
                final int n = count, f = failed;
                runOnUiThread(() -> Ui.toast(this, n + " smali files saved"
                        + (f > 0 ? " (" + f + " failed)" : "") + "\n"
                        + p.smaliDir(this).getAbsolutePath()));
            } catch (Throwable e) {
                runOnUiThread(() -> Ui.toast(this, "Save failed: " + e.getMessage()));
            }
        }, "smali-save").start();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------ list

    private class ListAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout card;
            TextView title, sub;
            if (convertView == null) {
                card = new LinearLayout(DexViewerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(Ui.ripple(DexViewerActivity.this,
                        Ui.outline(t.card, t.cardStroke, 14, 1, DexViewerActivity.this)));
                card.setElevation(Ui.dp(2));
                int pd = Ui.dp(12);
                card.setPadding(pd, pd, pd, pd);
                title = Ui.text(DexViewerActivity.this, "", 12.5f, t.text, false);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                card.addView(title);
                sub = Ui.text(DexViewerActivity.this, "", 10.5f, t.textSub, false);
                sub.setSingleLine(true);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                card.addView(sub);
                card.setTag(new TextView[]{title, sub});
            } else {
                card = (LinearLayout) convertView;
                TextView[] tv = (TextView[]) card.getTag();
                title = tv[0];
                sub = tv[1];
            }
            String s = shown.get(position);
            if (tab == TAB_CLASSES) {
                int dot = s.lastIndexOf('.');
                String simple = dot >= 0 ? s.substring(dot + 1) : s;
                String pkg = dot >= 0 ? s.substring(0, dot) : "(default package)";
                String kind = "";
                Integer di = classDex.get(s);
                if (di != null && di < dexes.size()) {
                    SmaliGen.DexClass c = SmaliGen.findClass(dexes.get(di), s);
                    if (c != null) kind = SmaliGen.kind(c) + "  •  " + c.methodCount() + " methods  •  "
                            + c.fieldCount() + " fields";
                }
                title.setText("🏫 " + simple);
                title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                sub.setText(pkg + (kind.length() > 0 ? "\n" + kind : ""));
                sub.setSingleLine(false);
                sub.setMaxLines(2);
            } else {
                title.setText(tab == TAB_METHODS ? "⚙ " + s : "🔤 " + s);
                title.setTypeface(Typeface.MONOSPACE);
                sub.setText("");
                sub.setSingleLine(true);
            }
            sub.setVisibility(sub.getText().length() == 0 ? View.GONE : View.VISIBLE);
            return card;
        }
    }
}
