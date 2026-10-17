package com.ace.ide;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Ui {

    public static final int WHITE = 0xFFE9E9E9;   // ACE "white" = #E9E9E9
    public static final int ACCENT = 0xFFE9E9E9;
    public static final int ACCENT_DARK = 0xFFC9D1DB;
    public static final int ACCENT_LIGHT = 0xFF4A729D;
    public static final int HEAD_BOTTOM = 0xFF4A729D;
    public static final int BG = 0xFF385F8C;
    public static final int HEAD_TOP = 0xFF2F5179;
    public static final int RED = 0xFFD9534F;
    public static final int RED_LIGHT = 0x33D9534F;

    public static final String VERSION = "1.1.0";

    private static Context appContext;

    private Ui() {}

    /** called once from App (Application class) */
    public static void init(Context c) {
        if (appContext == null) appContext = c.getApplicationContext();
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** short dp without context — uses app context */
    public static int dp(float v) {
        return Math.round(v * appContext.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable fill(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable outline(int fillColor, int strokeColor,
                                           float radiusDp, float strokeDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fillColor);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, strokeDp)), strokeColor);
        return g;
    }

    public static GradientDrawable gradient(int from, int to, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{from, to});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static Drawable ripple(Context c, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x33000000), content, null);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    public static EditText input(Context c, String hint, Theme t) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(t.text);
        e.setHintTextColor(t.textSub);
        e.setBackground(outline(t.inputBg, t.cardStroke, 12, 1.2f, c));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        e.setSingleLine(true);
        return e;
    }

    public static TextView button(Context c, String label, int bg) {
        TextView t = text(c, label, 14.5f, luma(bg) > 0.55f ? BG : WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(ripple(c, fill(bg, 14, c)));
        t.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        pressScale(t, 0.97f);
        return t;
    }

    public static float luma(int c) {
        return (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f;
    }

    public static void pressScale(final View v, final float down) {
        v.setClickable(true);
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(down).scaleY(down).setDuration(80).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                        break;
                }
                return false;
            }
        });
    }

    public static LinearLayout header(final Activity a, String title, String sub, boolean withBack) {
        LinearLayout h = new LinearLayout(a);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setBackground(gradient(HEAD_TOP, HEAD_BOTTOM, 0, a));
        h.setPadding(dp(a, 18), dp(a, 26), dp(a, 18), dp(a, 22));
        if (withBack) {
            TextView back = text(a, "←  Back", 15, WHITE, true);
            back.setBackground(ripple(a, fill(0x33FFFFFF, 12, a)));
            back.setPadding(dp(a, 14), dp(a, 8), dp(a, 14), dp(a, 8));
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { a.finish(); }
            });
            h.addView(back, new LinearLayout.LayoutParams(-2, -2));
        }
        TextView t1 = text(a, title, 22, WHITE, true);
        t1.setSingleLine(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.topMargin = dp(a, withBack ? 14 : 0);
        h.addView(t1, p);
        if (sub != null) h.addView(text(a, sub, 12, 0xB3FFFFFF, false));
        return h;
    }

    public static void copy(Context c, String label, String value) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            toast(c, "Copied to clipboard");
        }
    }

    public static void toast(Context c, String m) {
        Toast.makeText(c, m, Toast.LENGTH_SHORT).show();
    }

    public static void shareText(Activity a, String subject, String text) {
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType("text/plain");
        s.putExtra(Intent.EXTRA_SUBJECT, subject);
        s.putExtra(Intent.EXTRA_TEXT, text);
        a.startActivity(Intent.createChooser(s, "Share"));
    }

    // ---------------------------------------------------------------- dialogs

    /** AlertDialog builder that follows the in-app dark / light theme */
    public static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, Prefs.dark(c)
                ? AlertDialog.THEME_DEVICE_DEFAULT_DARK
                : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
    }

    /** false once the activity is finishing — never show a dialog on a dead window */
    public static boolean alive(Activity a) {
        if (a == null || a.isFinishing()) return false;
        return Build.VERSION.SDK_INT < 17 || !a.isDestroyed();
    }

    // ---------------------------------------------------------------- file io (UTF-8, no leaks)

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static String readText(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream((int) Math.max(32, f.length()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), UTF8);
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }

    public static void writeText(File f, String text) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(text.getBytes(UTF8));
            out.flush();
        } finally {
            try { out.close(); } catch (IOException ignored) { }
        }
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            try { in.close(); } catch (IOException ignored) { }
            try { out.close(); } catch (IOException ignored) { }
        }
    }

    public static void copyFile(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null) parent.mkdirs();
        copyStream(new FileInputStream(src), new FileOutputStream(dst));
    }

    // ---------------------------------------------------------------- export / share

    /**
     * Copies a file into Downloads/ACE. Works on Android 8+ (MediaStore on 10+,
     * plain file copy below). An older export with the same name is replaced instead
     * of piling up "name (1).dex" copies. Returns a Uri when MediaStore was used.
     */
    public static Uri exportToDownloads(Context c, File file, String name) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            String rel = Environment.DIRECTORY_DOWNLOADS + "/ACE";
            try {
                Cursor q = c.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.MediaColumns._ID},
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                        new String[]{name, rel + "%"}, null);
                if (q != null) {
                    try {
                        while (q.moveToNext()) {
                            Uri old = android.content.ContentUris.withAppendedId(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, q.getLong(0));
                            try { c.getContentResolver().delete(old, null, null); }
                            catch (Throwable ignored) { }
                        }
                    } finally { q.close(); }
                }
            } catch (Throwable ignored) { }
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(name));
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
            Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IOException("MediaStore insert failed");
            OutputStream out = c.getContentResolver().openOutputStream(uri);
            if (out == null) throw new IOException("Cannot open output stream");
            copyStream(new FileInputStream(file), out);
            return uri;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "ACE");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir.getAbsolutePath()
                    + " — grant storage permission first");
        }
        copyFile(file, new File(dir, name));
        return null;
    }

    public static void shareFile(Activity act, File file, String name) {
        if (act == null || file == null || !file.exists()) {
            if (act != null) toast(act, "File not found — convert first");
            return;
        }
        try {
            Uri uri = exportToDownloads(act, file, name);
            if (uri == null) {
                // Android 8/9: the exported copy is a plain file
                try {
                    StrictMode.class.getMethod("disableDeathOnFileUriExposure").invoke(null);
                } catch (Exception ignored) { }
                uri = Uri.fromFile(new File(new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "ACE"), name));
            }
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType(mimeFor(name));
            s.putExtra(Intent.EXTRA_STREAM, uri);
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            act.startActivity(Intent.createChooser(s, "Share"));
        } catch (Exception e) {
            toast(act, "Share failed: " + e.getMessage());
        }
    }

    /** exports every file to Downloads/ACE and opens the share sheet for all of them */
    public static void shareFiles(Activity act, List<File> files, String prefix) {
        if (act == null) return;
        if (files == null || files.isEmpty()) { toast(act, "No DEX yet — convert first"); return; }
        if (files.size() == 1) { shareFile(act, files.get(0), prefix + "_" + files.get(0).getName()); return; }
        try {
            ArrayList<Uri> uris = new ArrayList<Uri>();
            for (File f : files) {
                String name = prefix + "_" + f.getName();
                Uri u = exportToDownloads(act, f, name);
                if (u == null) {
                    try { StrictMode.class.getMethod("disableDeathOnFileUriExposure").invoke(null); }
                    catch (Exception ignored) { }
                    u = Uri.fromFile(new File(new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), "ACE"), name));
                }
                uris.add(u);
            }
            Intent s = new Intent(Intent.ACTION_SEND_MULTIPLE);
            s.setType("application/octet-stream");
            s.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            act.startActivity(Intent.createChooser(s, "Share DEX files"));
        } catch (Exception e) {
            toast(act, "Share failed: " + e.getMessage());
        }
    }

    /** extracts only .java files from a zip (zip-slip protected) */
    public static void unzipJava(File zip, File destDir) throws IOException {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zip));
        try {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName().replace("\\", "/");
                if (name.contains("..")) continue;
                while (name.startsWith("/")) name = name.substring(1);
                if (!name.toLowerCase().endsWith(".java")) continue;
                File out = new File(destDir, name);
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                FileOutputStream fo = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) > 0) fo.write(buf, 0, n);
                fo.close();
            }
        } finally {
            zis.close();
        }
    }

    public static void popIn(View v, long delay) {
        v.setAlpha(0f);
        v.setScaleX(0.85f);
        v.setScaleY(0.85f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(delay).setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.15f)).start();
    }

    public static void riseIn(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 24));
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay).setDuration(360)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    public static void shake(View v) {
        float d = dp(v.getContext(), 10);
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, d, -d, d * 0.6f, -d * 0.4f, 0f)
                .setDuration(420).start();
    }

    public static void countUp(final TextView t, int to) {
        ValueAnimator a = ValueAnimator.ofInt(0, to);
        a.setDuration(650);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator anim) {
                t.setText(String.valueOf((Integer) anim.getAnimatedValue()));
            }
        });
        a.start();
    }

    public static String size(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        return String.format(Locale.US, "%.2f MB", bytes / 1048576f);
    }


    // ---------------------------------------------------------------- ACE additions

    public static String rep(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    public static String mimeFor(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".txt") || n.endsWith(".log")) return "text/plain";
        return "application/octet-stream";
    }

    /** translucent "frosted glass" panel */
    public static GradientDrawable glass(Context c, Theme t, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40E9E9E9, t.glassFill});
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, dp(c, 1)), t.glassStroke);
        return g;
    }

    /** small round / rect icon button with ripple */
    public static FrameLayout iconButton(Context c, String icon, int color, float sizeDp, float padDp, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        int p = dp(c, padDp);
        Icons.IconView iv = Icons.view(c, icon, color);
        f.addView(iv, new FrameLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp), Gravity.CENTER));
        f.setPadding(p, p, p, p);
        f.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), null, null));
        if (l != null) f.setOnClickListener(l);
        return f;
    }

    public static TextView chip(Context c, String s, int fg, int bg) {
        TextView t = text(c, s, 10f, fg, true);
        t.setBackground(fill(bg, 20, c));
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        return t;
    }

    public static View divider(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1))));
        return v;
    }

    public static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    public static class SuccessView extends View {

        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path checkPath = new Path();
        private final PathMeasure pm = new PathMeasure();
        private final float box;
        private float progress = 0f;

        public SuccessView(Context c) {
            super(c);
            halo.setColor(ACCENT_LIGHT);
            circle.setColor(ACCENT);
            check.setColor(WHITE);
            check.setStyle(Paint.Style.STROKE);
            check.setStrokeWidth(dp(c, 5));
            check.setStrokeCap(Paint.Cap.ROUND);
            check.setStrokeJoin(Paint.Join.ROUND);
            box = dp(c, 52);
            checkPath.moveTo(box * 0.30f, box * 0.52f);
            checkPath.lineTo(box * 0.44f, box * 0.66f);
            checkPath.lineTo(box * 0.72f, box * 0.36f);
            pm.setPath(checkPath, false);
        }

        public void start() {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(950);
            a.setStartDelay(120);
            a.setInterpolator(new AccelerateDecelerateInterpolator());
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator anim) {
                    progress = (Float) anim.getAnimatedValue();
                    invalidate();
                }
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float maxR = Math.min(getWidth(), getHeight()) / 2f - dp(getContext(), 2);
            canvas.drawCircle(cx, cy, maxR, halo);
            float grow = Math.min(1f, progress / 0.45f);
            canvas.drawCircle(cx, cy, maxR * (0.25f + 0.75f * grow), circle);
            float cp = (progress - 0.5f) / 0.5f;
            if (cp > 0f) {
                Path dst = new Path();
                dst.rLineTo(0, 0);
                pm.getSegment(0, pm.getLength() * Math.min(1f, cp), dst, true);
                canvas.save();
                canvas.translate(cx - box / 2f, cy - box / 2f);
                canvas.drawPath(dst, check);
                canvas.restore();
            }
        }
    }
}
