package com.ace.ide;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Glass dialogs used everywhere in ACE. */
public final class Dialogs {

    private Dialogs() {}

    public interface Text { void onText(String value); }

    /** A rounded glass card in a floating window. Add views to {@link #body}. */
    public static final class Glass {
        public final Dialog dialog;
        public final LinearLayout body;
        public final Theme t;
        private final Activity a;

        public Glass(Activity a, String title) {
            this.a = a;
            this.t = Theme.get(a);
            dialog = new Dialog(a);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout root = new LinearLayout(a);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(Ui.outline(t.dialog, t.cardStroke, 22, 1, a));
            int p = Ui.dp(18);
            root.setPadding(p, p - Ui.dp(2), p, Ui.dp(12));
            if (title != null) {
                TextView tt = Ui.text(a, title, 17, t.text, true);
                root.addView(tt, new LinearLayout.LayoutParams(-1, -2));
            }
            body = new LinearLayout(a);
            body.setOrientation(LinearLayout.VERTICAL);
            root.addView(body, new LinearLayout.LayoutParams(-1, -2));
            dialog.setContentView(root);
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams wl = w.getAttributes();
                wl.width = Math.min(a.getResources().getDisplayMetrics().widthPixels - Ui.dp(32), Ui.dp(420));
                wl.height = WindowManager.LayoutParams.WRAP_CONTENT;
                wl.dimAmount = 0.45f;
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                        wl.setBlurBehindRadius(24);
                    } catch (Throwable ignored) { }
                }
                w.setAttributes(wl);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }

        public Glass message(String m) {
            TextView tv = Ui.text(a, m, 13, t.textSub, false);
            tv.setLineSpacing(0, 1.15f);
            body.addView(tv, Ui.lp(-1, -2, 0, 8, 0, 4));
            return this;
        }

        public Glass add(View v) { body.addView(v); return this; }

        public Glass add(View v, LinearLayout.LayoutParams lp) { body.addView(v, lp); return this; }

        /** a tappable row: icon + title + optional subtitle */
        public Glass row(String icon, String title, String sub, final Runnable r) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(10), Ui.dp(11), Ui.dp(10), Ui.dp(11));
            row.setBackground(Ui.ripple(a, Ui.fill(0x00000000, 14, a)));
            FrameLayout ib = new FrameLayout(a);
            ib.setBackground(Ui.fill(t.accentSoft, 12, a));
            ib.addView(Icons.view(a, icon, t.accent), new FrameLayout.LayoutParams(Ui.dp(22), Ui.dp(22), Gravity.CENTER));
            row.addView(ib, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(Ui.text(a, title, 14.5f, t.text, true));
            if (sub != null) col.addView(Ui.text(a, sub, 11.5f, t.textSub, false));
            row.addView(col, Ui.lp(0, -2, 12, 0, 0, 0));
            ((LinearLayout.LayoutParams) col.getLayoutParams()).weight = 1f;
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dialog.dismiss(); if (r != null) r.run(); }
            });
            body.addView(row, new LinearLayout.LayoutParams(-1, -2));
            return this;
        }

        /** right aligned button bar; first = primary */
        public Glass buttons(String primary, final Runnable onPrimary, String secondary, final Runnable onSecondary) {
            LinearLayout bar = new LinearLayout(a);
            bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            if (secondary != null) {
                TextView s = Ui.text(a, secondary, 14, t.textSub, true);
                s.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
                s.setBackground(Ui.ripple(a, Ui.fill(0x00000000, 12, a)));
                s.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { dialog.dismiss(); if (onSecondary != null) onSecondary.run(); }
                });
                bar.addView(s);
            }
            if (primary != null) {
                TextView b = Ui.button(a, primary, t.accent);
                b.setTextColor(t.onAccent);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (onPrimary != null) onPrimary.run();
                    }
                });
                bar.addView(b, Ui.lp(-2, -2, 6, 0, 0, 0));
            }
            body.addView(bar, Ui.lp(-1, -2, 0, 12, 0, 0));
            return this;
        }

        public void show() {
            if (!Ui.alive(a)) return;
            dialog.show();
        }

        public void dismiss() { try { dialog.dismiss(); } catch (Throwable ignored) { } }
    }

    public static void prompt(final Activity a, String title, String hint, String initial, String okLabel, final Text cb) {
        final Glass g = new Glass(a, title);
        final EditText e = Ui.input(a, hint, g.t);
        if (initial != null) { e.setText(initial); e.setSelection(initial.length()); }
        g.add(e, Ui.lp(-1, -2, 0, 12, 0, 0));
        g.buttons(okLabel == null ? "OK" : okLabel, new Runnable() {
            @Override public void run() {
                String v = e.getText().toString().trim();
                if (v.length() == 0) { Ui.shake(e); return; }
                g.dismiss();
                cb.onText(v);
            }
        }, "Cancel", null);
        g.show();
        e.requestFocus();
    }

    public static void confirm(Activity a, String title, String msg, String okLabel, final Runnable ok) {
        final Glass g = new Glass(a, title);
        g.message(msg);
        g.buttons(okLabel, new Runnable() {
            @Override public void run() { g.dismiss(); ok.run(); }
        }, "Cancel", null);
        g.show();
    }

    public static void info(Activity a, String title, String msg) {
        final Glass g = new Glass(a, title);
        TextView tv = Ui.text(a, msg, 12.5f, g.t.text, false);
        tv.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(a);
        sv.addView(tv);
        g.add(sv, Ui.lp(-1, -2, 0, 10, 0, 0));
        g.buttons("OK", new Runnable() { @Override public void run() { g.dismiss(); } }, null, null);
        g.show();
    }

    public static void choose(Activity a, String title, String[] labels, int selected, final IndexCb cb) {
        final Glass g = new Glass(a, title);
        ScrollView sv = new ScrollView(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView tv = Ui.text(a, (i == selected ? "●  " : "○  ") + labels[i], 14.5f, i == selected ? g.t.accent : g.t.text, i == selected);
            tv.setPadding(Ui.dp(10), Ui.dp(12), Ui.dp(10), Ui.dp(12));
            tv.setBackground(Ui.ripple(a, Ui.fill(0x00000000, 12, a)));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { g.dismiss(); cb.onIndex(idx); }
            });
            col.addView(tv);
        }
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2, 0, 8, 0, 0);
        int max = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.55f);
        lp.height = Math.min(max, Ui.dp(52) * labels.length + Ui.dp(8));
        g.add(sv, lp);
        g.buttons(null, null, "Close", null);
        g.show();
    }

    public interface IndexCb { void onIndex(int i); }

    /** scrollable monospace text with Copy + Close */
    public static void log(final Activity a, String title, final String text, boolean error) {
        final Glass g = new Glass(a, title);
        TextView tv = Ui.text(a, text, 11, error ? g.t.danger : g.t.text, false);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
        ScrollView sv = new ScrollView(a);
        sv.setBackground(Ui.outline(g.t.inputBg, g.t.cardStroke, 12, 1, a));
        sv.addView(tv);
        LinearLayout.LayoutParams lp = Ui.lp(-1, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.5f), 0, 10, 0, 0);
        g.add(sv, lp);
        g.buttons("Copy all", new Runnable() { @Override public void run() { Ui.copy(a, "ACE log", text); } }, "Close", null);
        g.show();
    }

    /** HTML page from assets in a dialog (developer profile etc.) */
    public static void html(final Activity a, String title, String assetFile) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int screenW = a.getResources().getDisplayMetrics().widthPixels;
        int screenH = a.getResources().getDisplayMetrics().heightPixels;
        Theme t = Theme.get(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.outline(t.card, t.cardStroke, 22, 1, a));
        int pad = Ui.dp(16);
        root.setPadding(pad, pad, pad, pad);
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(a, title, 17, t.text, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView x = Ui.text(a, "✕", 15, t.textSub, true);
        x.setGravity(Gravity.CENTER);
        x.setBackground(Ui.ripple(a, Ui.fill(t.chipBg, 20, a)));
        x.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(6));
        x.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        head.addView(x, new LinearLayout.LayoutParams(-2, -2));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));
        final FrameLayout webWrap = new FrameLayout(a);
        webWrap.setBackground(Ui.outline(t.inputBg, t.cardStroke, 14, 1, a));
        boolean has = false;
        try {
            for (String s : a.getAssets().list("")) if (s.equals(assetFile)) has = true;
        } catch (Throwable ignored) { }
        if (has) {
            final WebView wv = new WebView(a);
            wv.getSettings().setJavaScriptEnabled(true);
            wv.getSettings().setAllowContentAccess(false);
            wv.setBackgroundColor(Color.TRANSPARENT);
            wv.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (url != null && url.startsWith("file:///android_asset/")) return false;
                    try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (Throwable e) { Ui.toast(a, "No app can open this link"); }
                    return true;
                }
            });
            wv.loadUrl("file:///android_asset/" + assetFile);
            webWrap.addView(wv, new FrameLayout.LayoutParams(-1, -1));
            d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                @Override public void onDismiss(android.content.DialogInterface di) { webWrap.removeAllViews(); wv.destroy(); }
            });
        } else {
            TextView tv = Ui.text(a, assetFile + " not found in app assets.", 13, t.textSub, false);
            tv.setPadding(pad, pad, pad, pad);
            webWrap.addView(tv, new FrameLayout.LayoutParams(-1, -2));
        }
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, (int) (screenH * 0.68f));
        wlp.topMargin = Ui.dp(12);
        root.addView(webWrap, wlp);
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams wl = w.getAttributes();
            wl.width = screenW - Ui.dp(30);
            wl.height = WindowManager.LayoutParams.WRAP_CONTENT;
            w.setAttributes(wl);
        }
        if (!Ui.alive(a)) return;
        d.show();
        Ui.popIn(root, 40);
    }

    static int unused(Context c) { return InputType.TYPE_CLASS_TEXT; }
}
