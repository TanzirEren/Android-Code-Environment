package com.ace.ide;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Builds grouped settings cards. */
final class SettingsUi {

    interface Store {
        boolean getB(String k, boolean d);
        void putB(String k, boolean v);
        int getI(String k, int d);
        void putI(String k, int v);
        String getS(String k, String d);
        void putS(String k, String v);
    }

    static Store prefsStore(final Activity a) {
        return new Store() {
            public boolean getB(String k, boolean d) { return Prefs.b(a, k, d); }
            public void putB(String k, boolean v) { Prefs.putB(a, k, v); }
            public int getI(String k, int d) { return Prefs.i(a, k, d); }
            public void putI(String k, int v) { Prefs.putI(a, k, v); }
            public String getS(String k, String d) { return Prefs.s(a, k, d); }
            public void putS(String k, String v) { Prefs.putS(a, k, v); }
        };
    }

    interface OnChange { void changed(); }

    private final Activity a;
    private final Theme t;
    private final LinearLayout col;
    private final Store store;
    private LinearLayout card;
    private boolean first;

    SettingsUi(Activity a, LinearLayout col, Store store) {
        this.a = a;
        this.t = Theme.get(a);
        this.col = col;
        this.store = store;
    }

    void header(String title, String icon) {
        LinearLayout h = new LinearLayout(a);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(Icons.view(a, icon, t.accent), new LinearLayout.LayoutParams(Ui.dp(16), Ui.dp(16)));
        TextView tv = Ui.text(a, title.toUpperCase(), 11.5f, t.accent, true);
        tv.setLetterSpacing(0.08f);
        h.addView(tv, Ui.lp(-2, -2, 8, 0, 0, 0));
        col.addView(h, Ui.lp(-1, -2, 6, 18, 0, 8));
        card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.outline(t.card, t.cardStroke, 18, 1, a));
        col.addView(card, new LinearLayout.LayoutParams(-1, -2));
        first = true;
    }

    private LinearLayout row(String title, String sub) {
        if (!first) card.addView(Ui.divider(a, t.cardStroke), Ui.lp(-1, 1, 14, 0, 14, 0));
        first = false;
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(16), Ui.dp(13), Ui.dp(14), Ui.dp(13));
        LinearLayout tc = new LinearLayout(a);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.addView(Ui.text(a, title, 14.5f, t.text, false));
        if (sub != null && sub.length() > 0) {
            TextView s = Ui.text(a, sub, 11.5f, t.textSub, false);
            s.setLineSpacing(0, 1.1f);
            tc.addView(s);
        }
        r.addView(tc, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    TextView value(LinearLayout r, String v) {
        TextView tv = Ui.text(a, v, 12.5f, t.accent, true);
        r.addView(tv, Ui.lp(-2, -2, 8, 0, 0, 0));
        return tv;
    }

    void sw(final String key, String title, String sub, boolean def, final OnChange cb) {
        LinearLayout r = row(title, sub);
        Switch s = new Switch(a);
        s.setChecked(store.getB(key, def));
        s.setThumbTintList(ColorStateList.valueOf(t.accent));
        s.setTrackTintList(ColorStateList.valueOf(t.accentSoft | 0x66000000));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { store.putB(key, on); if (cb != null) cb.changed(); }
        });
        r.addView(s);
        final Switch fs = s;
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { fs.toggle(); } });
    }

    void choice(final String key, final String title, final String[] labels, final int[] values, int def, final OnChange cb) {
        int cur = store.getI(key, def);
        int idx = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == cur) idx = i;
        final LinearLayout r = row(title, null);
        final TextView v = value(r, labels[idx]);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                int c = store.getI(key, values[0]);
                int sel = 0;
                for (int i = 0; i < values.length; i++) if (values[i] == c) sel = i;
                Dialogs.choose(a, title, labels, sel, new Dialogs.IndexCb() {
                    @Override public void onIndex(int i) {
                        store.putI(key, values[i]);
                        v.setText(labels[i]);
                        if (cb != null) cb.changed();
                    }
                });
            }
        });
    }

    void seek(final String key, String title, String sub, final int min, final int max, int def, final String suffix, final OnChange cb) {
        LinearLayout r = row(title, sub);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.START);
        // restructure: title line + value, then the seek bar
        final TextView v = Ui.text(a, store.getI(key, def) + suffix, 12.5f, t.accent, true);
        ((LinearLayout) r.getChildAt(0)).addView(v);
        SeekBar sb = new SeekBar(a);
        sb.setMax(max - min);
        sb.setProgress(store.getI(key, def) - min);
        sb.setProgressTintList(ColorStateList.valueOf(t.accent));
        sb.setThumbTintList(ColorStateList.valueOf(t.accent));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { v.setText((p + min) + suffix); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { store.putI(key, s.getProgress() + min); if (cb != null) cb.changed(); }
        });
        r.addView(sb, new LinearLayout.LayoutParams(-1, -2));
    }

    void action(String title, String sub, final Runnable r) {
        LinearLayout row = row(title, sub);
        row.addView(Icons.view(a, Icons.CHEV_R, t.textSub), new LinearLayout.LayoutParams(Ui.dp(14), Ui.dp(14)));
        row.setBackground(Ui.ripple(a, null));
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
    }

    TextView actionValue(String title, String sub, String value, final Runnable r) {
        LinearLayout row = row(title, sub);
        TextView v = value(row, value);
        row.setBackground(Ui.ripple(a, null));
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { r.run(); } });
        return v;
    }

    void text(String key, String title, String hint, String def) { text(key, title, hint, def, false); }

    void text(final String key, final String title, final String hint, String def, final boolean secret) {
        String cur = store.getS(key, def);
        final LinearLayout r = row(title, null);
        final TextView v = value(r, display(cur, secret));
        v.setSingleLine(true);
        v.setMaxWidth(Ui.dp(170));
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.setBackground(Ui.ripple(a, null));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                final Dialogs.Glass g = new Dialogs.Glass(a, title);
                final EditText e = Ui.input(a, hint, t);
                String c = store.getS(key, "");
                e.setText(c);
                if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                g.add(e, Ui.lp(-1, -2, 0, 10, 0, 0));
                g.buttons("Save", new Runnable() {
                    @Override public void run() {
                        String val = e.getText().toString().trim();
                        store.putS(key, val);
                        v.setText(display(val, secret));
                        g.dismiss();
                    }
                }, "Cancel", null);
                g.show();
            }
        });
    }

    private static String display(String v, boolean secret) {
        if (v == null || v.length() == 0) return "not set";
        return secret ? "••••••" : v;
    }

    void info(String text) {
        if (!first) card.addView(Ui.divider(a, t.cardStroke), Ui.lp(-1, 1, 14, 0, 14, 0));
        first = false;
        TextView tv = Ui.text(a, text, 12, t.textSub, false);
        tv.setLineSpacing(0, 1.15f);
        tv.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        card.addView(tv);
    }
}
