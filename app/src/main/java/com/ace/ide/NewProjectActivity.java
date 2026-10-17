package com.ace.ide;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class NewProjectActivity extends Activity {

    private static final int REQ_SRC = 41;
    private static final int REQ_LIBS = 42;

    private static final String SAMPLE_JAVA =
            "public class HelloMod {\n"
            + "    public static String TAG = \"Java2Dex\";\n\n"
            + "    public static String hello(String who) {\n"
            + "        return \"Hello \" + who + \" from \" + TAG + \"!\";\n"
            + "    }\n\n"
            + "    public static int add(int a, int b) {\n"
            + "        return a + b;\n"
            + "    }\n}\n";

    private Theme t;
    private EditText nameInput;
    private TextView srcSub, libSub, statusText;
    private ScrollView formScroll;
    private LinearLayout progressBox, srcCard;
    private FrameLayout resultBox;
    private Project currentProject;
    private final List<Uri> sources = new ArrayList<>();
    private final List<Uri> libs = new ArrayList<>();
    private boolean useSample = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        t = Theme.get(this);
        getWindow().setStatusBarColor(Ui.HEAD_TOP);
        buildUi();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(t.bg);
        setContentView(root);

        FrameLayout stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        formScroll = new ScrollView(this);
        formScroll.setFillViewport(true);
        stage.addView(formScroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        formScroll.addView(col, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(Ui.HEAD_TOP, Ui.HEAD_BOTTOM, 0, this));
        header.setPadding(Ui.dp(18), Ui.dp(26), Ui.dp(18), Ui.dp(22));

        TextView back = Ui.text(this, "←  Back", 15, Color.WHITE, true);
        back.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        back.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(-2, -2));

        TextView h1 = Ui.text(this, "New Project", 22, Color.WHITE, true);
        LinearLayout.LayoutParams h1p = new LinearLayout.LayoutParams(-2, -2);
        h1p.topMargin = Ui.dp(14);
        header.addView(h1, h1p);
        header.addView(Ui.text(this, "Compile Java sources into classes.dex", 12, 0xB3FFFFFF, false));
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(28));
        col.addView(body, new LinearLayout.LayoutParams(-1, -2));

        body.addView(Ui.text(this, "PROJECT NAME", 11, t.textSub, true));
        nameInput = Ui.input(this, "e.g. MyModPatch", t);
        LinearLayout.LayoutParams nip = new LinearLayout.LayoutParams(-1, -2);
        nip.topMargin = Ui.dp(7);
        body.addView(nameInput, nip);

        srcCard = new LinearLayout(this);
        srcCard.setOrientation(LinearLayout.VERTICAL);
        srcCard.setBackground(Ui.outline(t.card, t.cardStroke, 14, 1.2f, this));
        int pd = Ui.dp(14);
        srcCard.setPadding(pd, pd, pd, pd);
        srcCard.addView(Ui.text(this, "📄  Java sources", 14.5f, t.text, true));
        srcSub = Ui.text(this, "Tap to choose .java files or a .zip archive", 11.5f, t.textSub, false);
        LinearLayout.LayoutParams ssp = new LinearLayout.LayoutParams(-2, -2);
        ssp.topMargin = Ui.dp(3);
        srcCard.addView(srcSub, ssp);
        srcCard.setOnClickListener(v -> pick(REQ_SRC));
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(-1, -2);
        scp.topMargin = Ui.dp(16);
        body.addView(srcCard, scp);

        LinearLayout libCard = new LinearLayout(this);
        libCard.setOrientation(LinearLayout.VERTICAL);
        libCard.setBackground(Ui.outline(t.card, t.cardStroke, 14, 1.2f, this));
        libCard.setPadding(pd, pd, pd, pd);
        libCard.addView(Ui.text(this, "📚  Library jars (optional)", 14.5f, t.text, true));
        libSub = Ui.text(this, "Tap to add .jar / .zip to classpath", 11.5f, t.textSub, false);
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(-2, -2);
        lsp.topMargin = Ui.dp(3);
        libCard.addView(libSub, lsp);
        libCard.setOnClickListener(v -> pick(REQ_LIBS));
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(-1, -2);
        lcp.topMargin = Ui.dp(10);
        body.addView(libCard, lcp);

        // IDE card
        LinearLayout ideCard = new LinearLayout(this);
        ideCard.setOrientation(LinearLayout.VERTICAL);
        ideCard.setBackground(Ui.ripple(this, Ui.outline(t.card, t.accent, 14, 1.4f, this)));
        ideCard.setPadding(pd, pd, pd, pd);
        ideCard.addView(Ui.text(this, "🧠  Or open in Code IDE", 14.5f, t.accentDark, true));
        ideCard.addView(Ui.text(this,
                "Create files & folders, import, edit with\nsyntax highlight — then convert right there",
                11.5f, t.textSub, false));
        ideCard.setOnClickListener(v -> openInIde());
        LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(-1, -2);
        icp.topMargin = Ui.dp(10);
        body.addView(ideCard, icp);

        TextView sample = Ui.text(this, "✨  Load sample project", 13, t.accentDark, true);
        sample.setPadding(0, Ui.dp(14), 0, 0);
        sample.setOnClickListener(v -> {
            useSample = true;
            srcSub.setText("Sample project loaded (HelloMod.java)");
            Ui.toast(this, "Sample loaded — name it & convert!");
        });
        body.addView(sample, new LinearLayout.LayoutParams(-2, -2));

        TextView convertBtn = Ui.button(this, "⚡  CONVERT TO DEX", Ui.ACCENT);
        convertBtn.setOnClickListener(v -> startConvert());
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-1, -2);
        cbp.topMargin = Ui.dp(22);
        body.addView(convertBtn, cbp);

        progressBox = new LinearLayout(this);
        progressBox.setOrientation(LinearLayout.VERTICAL);
        progressBox.setGravity(Gravity.CENTER);
        progressBox.setBackgroundColor(t.bg);
        progressBox.setVisibility(View.GONE);
        progressBox.setClickable(true);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        progressBox.addView(pb, new LinearLayout.LayoutParams(-2, -2));
        statusText = Ui.text(this, "Preparing…", 14.5f, t.text, true);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-2, -2);
        stp.topMargin = Ui.dp(16);
        progressBox.addView(statusText, stp);
        progressBox.addView(Ui.text(this, "Large projects may take a while", 12, t.textSub, false));
        stage.addView(progressBox, new FrameLayout.LayoutParams(-1, -1));

        resultBox = new FrameLayout(this);
        resultBox.setVisibility(View.GONE);
        stage.addView(resultBox, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override
    public void onBackPressed() {
        if (progressBox != null && progressBox.getVisibility() == View.VISIBLE) {
            Ui.toast(this, "Conversion in progress…");
            return;
        }
        if (resultBox != null && resultBox.getVisibility() == View.VISIBLE
                && formScroll.getVisibility() != View.VISIBLE && currentProject != null
                && currentProject.status == Project.ST_ERROR) {
            resetForm();
            return;
        }
        super.onBackPressed();
    }

    private void openInIde() {
        String name = nameInput.getText().toString().trim();
        if (name.length() == 0) { Ui.shake(nameInput); Ui.toast(this, "Enter a project name first"); return; }
        Project p = new Project();
        p.id = String.valueOf(System.currentTimeMillis());
        p.name = Project.uniqueName(this, name);
        p.createdAt = System.currentTimeMillis();
        p.srcDir(this).mkdirs();
        if (useSample) {
            try { Ui.writeText(new File(p.srcDir(this), "HelloMod.java"), SAMPLE_JAVA); }
            catch (Exception ignored) { }
        }
        Project.upsert(this, p);
        Intent i = new Intent(this, IdeActivity.class);
        i.putExtra("id", p.id);
        startActivity(i);
    }

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(i,
                req == REQ_SRC ? "Select .java / .zip" : "Select library jars"), req);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++)
                uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        if (uris.isEmpty()) return;
        if (requestCode == REQ_SRC) {
            sources.addAll(uris);
            useSample = false;
            srcSub.setText(describe(sources) + " — tap to change");
        } else {
            libs.addAll(uris);
            libSub.setText(describe(libs) + " — tap to change");
        }
    }

    private void startConvert() {
        String name = nameInput.getText().toString().trim();
        if (name.length() == 0) { Ui.shake(nameInput); Ui.toast(this, "Enter a project name"); return; }
        if (sources.isEmpty() && !useSample) { Ui.shake(srcCard); Ui.toast(this, "Select .java files or a .zip"); return; }
        if (Converter.isRunning()) { Ui.toast(this, "A conversion is already running"); return; }
        Project p = new Project();
        p.id = String.valueOf(System.currentTimeMillis());
        p.name = Project.uniqueName(this, name);
        p.createdAt = System.currentTimeMillis();
        Project.upsert(this, p);
        currentProject = p;

        showProgress();
        new Thread(() -> {
            final boolean staged = stageFiles(p);
            runOnUiThread(() -> {
                if (!staged) { showError("[ACE] Could not import files."); return; }
                runConvert(p);
            });
        }).start();
    }

    private void runConvert(final Project p) {
        showProgress();
        Converter.convert(this, p, new Converter.Callback() {
            @Override public void onStep(String s) { statusText.setText(s); }
            @Override public void onDone(boolean ok, String log) {
                if (!Ui.alive(NewProjectActivity.this)) return;
                if (ok) showSuccess(p); else showError(log);
            }
        });
    }

    private boolean stageFiles(Project p) {
        try {
            File src = p.srcDir(this);
            src.mkdirs();
            if (useSample) {
                Ui.writeText(new File(src, "HelloMod.java"), SAMPLE_JAVA);
            }
            int i = 0;
            File libDir0 = p.libsDir(this);
            for (Uri u : sources) {
                String n = displayName(u);
                if (n == null || n.length() == 0) n = "file_" + (i++) + ".java";
                String low = n.toLowerCase();
                InputStream in = getContentResolver().openInputStream(u);
                if (in == null) continue;
                if (low.endsWith(".jar")) {          // a jar picked as "source" is a library
                    libDir0.mkdirs();
                    copyStream(in, new FileOutputStream(new File(libDir0, sanitize(n))));
                } else if (low.endsWith(".zip")) {
                    File dst = new File(getCacheDir(), "stage_" + System.currentTimeMillis() + ".zip");
                    copyStream(in, new FileOutputStream(dst));
                    try { Ui.unzipJava(dst, src); } finally { dst.delete(); }
                } else if (low.endsWith(".java")) {
                    copyStream(in, new FileOutputStream(new File(src, sanitize(n))));
                } else {
                    in.close();   // notes, images … are not Java sources
                }
            }
            if (!libs.isEmpty()) {
                File libDir = p.libsDir(this);
                libDir.mkdirs();
                int j = 0;
                for (Uri u : libs) {
                    String n = displayName(u);
                    if (n == null || n.length() == 0) n = "lib_" + (j++) + ".jar";
                    copyStream(getContentResolver().openInputStream(u),
                            new FileOutputStream(new File(libDir, sanitize(n))));
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String describe(List<Uri> list) {
        if (list.isEmpty()) return "Nothing";
        return list.size() + (list.size() == 1 ? " file selected" : " files selected");
    }

    private static String sanitize(String n) {
        String s = n == null ? "" : n.replace("..", "_").replace("/", "_").replace("\\", "_");
        return s.length() == 0 ? "file" : s;
    }

    private String displayName(Uri u) {
        try {
            Cursor c = getContentResolver().query(u, null, null, null, null);
            if (c != null) {
                try {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0 && c.moveToFirst()) {
                        String s = c.getString(idx);
                        if (s != null && s.length() > 0) return s;
                    }
                } finally { c.close(); }
            }
        } catch (Exception ignored) { }
        String seg = u.getLastPathSegment();
        if (seg == null) return null;
        int i = seg.lastIndexOf('/');
        return i >= 0 ? seg.substring(i + 1) : seg;
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush(); out.close(); in.close();
    }

    private LinearLayout.LayoutParams rowWeight() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private void showProgress() {
        formScroll.setVisibility(View.GONE);
        resultBox.setVisibility(View.GONE);
        resultBox.removeAllViews();
        progressBox.setVisibility(View.VISIBLE);
    }

    private void resetForm() {
        resultBox.setVisibility(View.GONE);
        resultBox.removeAllViews();
        formScroll.setVisibility(View.VISIBLE);
    }

    private void showSuccess(final Project p) {
        progressBox.setVisibility(View.GONE);
        resultBox.removeAllViews();
        resultBox.setVisibility(View.VISIBLE);

        ScrollView sc = new ScrollView(this);
        resultBox.addView(sc, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(22), Ui.dp(40), Ui.dp(22), Ui.dp(28));
        sc.addView(col, new ScrollView.LayoutParams(-1, -2));

        Ui.SuccessView sv = new Ui.SuccessView(this);
        col.addView(sv, new LinearLayout.LayoutParams(Ui.dp(110), Ui.dp(110)));
        sv.start();

        TextView t1 = Ui.text(this, "Conversion Successful!", 19, t.text, true);
        t1.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams t1p = new LinearLayout.LayoutParams(-2, -2);
        t1p.topMargin = Ui.dp(18);
        col.addView(t1, t1p);

        TextView t2 = Ui.text(this, "Saved to " + Project.java2dexRootNoCreate(this).getAbsolutePath() + "/",
                13, t.textSub, false);
        t2.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
        t2.setGravity(Gravity.CENTER);
        col.addView(t2);

        LinearLayout pathCard = new LinearLayout(this);
        pathCard.setOrientation(LinearLayout.VERTICAL);
        pathCard.setBackground(Ui.outline(t.accentSoft, t.accent, 12, 1, this));
        int pd2 = Ui.dp(12);
        pathCard.setPadding(pd2, pd2, pd2, pd2);
        List<File> made = p.publicDexFiles(this);
        StringBuilder pathTxt = new StringBuilder();
        for (File mf : made) {
            if (pathTxt.length() > 0) pathTxt.append('\n');
            pathTxt.append(mf.getAbsolutePath()).append("  (").append(Ui.size(mf.length())).append(')');
        }
        TextView pt = Ui.text(this, pathTxt.toString(), 12, t.accentDark, false);
        pt.setTypeface(Typeface.MONOSPACE);
        pt.setTextIsSelectable(true);
        pathCard.addView(pt);
        LinearLayout.LayoutParams pcp = new LinearLayout.LayoutParams(-1, -2);
        pcp.topMargin = Ui.dp(18);
        col.addView(pathCard, pcp);

        LinearLayout row1 = new LinearLayout(this);
        TextView copyBtn = Ui.button(this, "📋  Copy Path", Ui.ACCENT_DARK);
        row1.addView(copyBtn, rowWeight());
        TextView smaliBtn = Ui.button(this, "🧬  Smali", Ui.ACCENT);
        LinearLayout.LayoutParams slp = rowWeight();
        slp.leftMargin = Ui.dp(10);
        row1.addView(smaliBtn, slp);
        LinearLayout.LayoutParams r1p = new LinearLayout.LayoutParams(-1, -2);
        r1p.topMargin = Ui.dp(20);
        col.addView(row1, r1p);

        LinearLayout row2 = new LinearLayout(this);
        TextView shareBtn = Ui.button(this, "↗  Share", t.textSub);
        row2.addView(shareBtn, rowWeight());
        TextView doneBtn = Ui.button(this, "Done", Ui.ACCENT);
        LinearLayout.LayoutParams dlp = rowWeight();
        dlp.leftMargin = Ui.dp(10);
        row2.addView(doneBtn, dlp);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, -2);
        r2p.topMargin = Ui.dp(10);
        col.addView(row2, r2p);

        copyBtn.setOnClickListener(v ->
                Ui.copy(this, "dex-path", p.publicDexFile(this).getAbsolutePath()));
        smaliBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, DexViewerActivity.class);
            i.putExtra("id", p.id);
            startActivity(i);
        });
        shareBtn.setOnClickListener(v ->
                Ui.shareFiles(this, p.publicDexFiles(this), p.safeName()));
        doneBtn.setOnClickListener(v -> finish());
    }

    private void showError(String log) {
        progressBox.setVisibility(View.GONE);
        resultBox.removeAllViews();
        resultBox.setVisibility(View.VISIBLE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        resultBox.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.gradient(0xFF8E2B2B, Ui.RED, 0, this));
        header.setPadding(Ui.dp(18), Ui.dp(26), Ui.dp(18), Ui.dp(20));
        TextView back = Ui.text(this, "←  Back", 15, Color.WHITE, true);
        back.setBackground(Ui.ripple(this, Ui.fill(0x33FFFFFF, 12, this)));
        back.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        back.setOnClickListener(v -> resetForm());
        header.addView(back, new LinearLayout.LayoutParams(-2, -2));
        TextView h1 = Ui.text(this, "✖  Build Failed", 20, Color.WHITE, true);
        LinearLayout.LayoutParams h1p = new LinearLayout.LayoutParams(-2, -2);
        h1p.topMargin = Ui.dp(12);
        header.addView(h1, h1p);
        header.addView(Ui.text(this, "Tap Copy Error to grab the full log", 12, 0xCCFFFFFF, false));
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(16));
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        ScrollView logScroll = new ScrollView(this);
        logScroll.setBackground(Ui.fill(t.chipBg, 12, this));
        TextView logTv = Ui.text(this, log, 11.5f, t.danger, false);
        logTv.setTypeface(Typeface.MONOSPACE);
        logTv.setTextIsSelectable(true);
        int pd3 = Ui.dp(12);
        logTv.setPadding(pd3, pd3, pd3, pd3);
        logScroll.addView(logTv, new ScrollView.LayoutParams(-1, -2));
        body.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        TextView copyBtn = Ui.button(this, "📋  Copy Error", Ui.RED);
        row.addView(copyBtn, rowWeight());
        TextView retryBtn = Ui.button(this, "↺  Retry", Ui.ACCENT_DARK);
        LinearLayout.LayoutParams rlp = rowWeight();
        rlp.leftMargin = Ui.dp(10);
        row.addView(retryBtn, rlp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = Ui.dp(12);
        body.addView(row, rp);

        copyBtn.setOnClickListener(v -> Ui.copy(this, "build-error", log));
        retryBtn.setOnClickListener(v -> { if (currentProject != null) runConvert(currentProject); });
        Ui.shake(body);
    }
}
