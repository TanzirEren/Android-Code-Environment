package com.ace.ide;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ace.ide.build.Keys;

import java.io.File;
import java.util.List;

/** Signing keys: create / generate / import, default key, details. */
public class SigningActivity extends Activity {

    private Theme t;
    private LinearLayout list;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        t = Theme.get(this);
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
        top.addView(Ui.text(this, "Signing keys", 17, t.text, true), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(top);
        root.addView(Ui.divider(this, t.cardStroke));

        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(40));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView note = Ui.text(this, "Release apps must be signed with the same key every time, or Android will refuse to update the app. "
                + "Keep a backup of your key and passwords. Debug builds use ACE's built-in debug key.", 12, t.textSub, false);
        note.setLineSpacing(0, 1.15f);
        col.addView(note, Ui.lp(-1, -2, 4, 0, 4, 12));

        LinearLayout actions = new LinearLayout(this);
        TextView gen = Ui.button(this, "Generate key", t.accent);
        gen.setTextColor(t.onAccent);
        gen.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { generateDialog(); } });
        TextView imp = Ui.button(this, "Import key", t.accentDark);
        imp.setTextColor(0xFFFFFFFF);
        imp.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { importDialog(); } });
        actions.addView(gen, new LinearLayout.LayoutParams(0, -2, 1f));
        actions.addView(imp, Ui.lp(0, -2, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) imp.getLayoutParams()).weight = 1f;
        col.addView(actions, Ui.lp(-1, -2, 0, 0, 0, 14));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        List<Signing.Entry> all = Signing.all(this);
        // built-in debug key
        list.addView(card("ACE debug key", "Built in · used for debug builds", false, null));
        for (final Signing.Entry e : all) {
            boolean def = e.id.equals(Signing.defaultId(this));
            list.addView(card(e.name, "alias " + e.alias + (def ? "  ·  default release key" : ""), true, e));
        }
        if (all.isEmpty()) {
            TextView none = Ui.text(this, "No release keys yet — generate or import one.", 13, t.textSub, false);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, Ui.dp(24), 0, 0);
            list.addView(none);
        }
    }

    private View card(String title, String sub, boolean real, final Signing.Entry e) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(Ui.ripple(this, Ui.outline(t.card, t.cardStroke, 16, 1, this)));
        c.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        c.addView(Icons.view(this, Icons.KEY, real ? t.accent : t.textSub), new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.addView(Ui.text(this, title, 15, t.text, true));
        tc.addView(Ui.text(this, sub, 11.5f, t.textSub, false));
        c.addView(tc, Ui.lp(0, -2, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) tc.getLayoutParams()).weight = 1f;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(10);
        c.setLayoutParams(lp);
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { details(e); }
        });
        return c;
    }

    private void details(final Signing.Entry e) {
        final Dialogs.Glass g = new Dialogs.Glass(this, e == null ? "ACE debug key" : e.name);
        String info;
        try {
            Keys.KeyInfo ki = e == null ? Signing.debugKey(this) : Signing.load(e);
            info = Keys.describe(ki);
        } catch (Exception ex) {
            info = "Cannot open this key: " + ex.getMessage();
        }
        TextView tv = Ui.text(this, info, 11.5f, t.text, false);
        tv.setTextIsSelectable(true);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setLineSpacing(0, 1.15f);
        g.add(tv, Ui.lp(-1, -2, 0, 10, 0, 0));
        if (e != null) {
            g.row(Icons.CHECK, "Use as default release key", null, new Runnable() {
                @Override public void run() { Signing.setDefault(SigningActivity.this, e.id); refresh(); }
            });
            g.row(Icons.EXPORT, "Export key file", "Copy to Downloads/ACE", new Runnable() {
                @Override public void run() {
                    try {
                        File f = new File(e.file);
                        Ui.exportToDownloads(SigningActivity.this, f, e.name.replaceAll("[^A-Za-z0-9._-]", "_") + "." + FileUtil.ext(f.getName()));
                        Ui.toast(SigningActivity.this, "Saved to Downloads/ACE");
                    } catch (Exception ex) { Ui.toast(SigningActivity.this, "Export failed: " + ex.getMessage()); }
                }
            });
            g.row(Icons.TRASH, "Delete key", "Cannot be undone — export first", new Runnable() {
                @Override public void run() {
                    Dialogs.confirm(SigningActivity.this, "Delete key?", "Apps signed with it can no longer be updated unless you have a backup.", "Delete", new Runnable() {
                        @Override public void run() { Signing.remove(SigningActivity.this, e.id); refresh(); }
                    });
                }
            });
        }
        g.buttons(null, null, "Close", null);
        g.show();
    }

    private EditText field(Dialogs.Glass g, String hint, String def, boolean pw) {
        EditText e = Ui.input(this, hint, t);
        if (def != null) e.setText(def);
        if (pw) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        g.add(e, Ui.lp(-1, -2, 0, 8, 0, 0));
        return e;
    }

    private void generateDialog() {
        final Dialogs.Glass g = new Dialogs.Glass(this, "Generate a new key");
        ScrollView sv = new ScrollView(this);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        sv.addView(inner);
        final Dialogs.Glass holder = g;
        g.body.addView(sv, new LinearLayout.LayoutParams(-1, (int) (getResources().getDisplayMetrics().heightPixels * 0.52f)));
        final EditText name = add(inner, "Key name (for you)", "My release key", false);
        final EditText alias = add(inner, "Alias", "key0", false);
        final EditText pass = add(inner, "Password (min 6 characters)", "", true);
        final EditText cn = add(inner, "Your name / CN", "", false);
        final EditText org = add(inner, "Organization (optional)", "", false);
        final EditText city = add(inner, "City (optional)", "", false);
        final EditText country = add(inner, "Country code, e.g. BD", "", false);
        final EditText years = add(inner, "Validity in years", "25", false);
        years.setInputType(InputType.TYPE_CLASS_NUMBER);
        g.buttons("Generate", new Runnable() {
            @Override public void run() {
                final String p = pass.getText().toString();
                if (p.length() < 6) { Ui.toast(SigningActivity.this, "Password needs 6+ characters"); return; }
                if (cn.getText().toString().trim().length() == 0) { Ui.toast(SigningActivity.this, "Enter your name"); return; }
                int y = 25;
                try { y = Integer.parseInt(years.getText().toString().trim()); } catch (Exception ignored) { }
                final int yy = Math.max(1, Math.min(100, y));
                final String nm = name.getText().toString().trim().length() == 0 ? "Release key" : name.getText().toString().trim();
                final String al = alias.getText().toString().trim().length() == 0 ? "key0" : alias.getText().toString().trim();
                holder.dismiss();
                Ui.toast(SigningActivity.this, "Generating key …");
                new Thread(new Runnable() {
                    @Override public void run() {
                        String msg;
                        try {
                            Signing.generate(SigningActivity.this, nm, al, p, cn.getText().toString().trim(), "", org.getText().toString().trim(),
                                    city.getText().toString().trim(), "", country.getText().toString().trim().toUpperCase(), yy, 2048);
                            msg = "Key created ✔";
                        } catch (Exception ex) { msg = "Failed: " + ex.getMessage(); }
                        final String m = msg;
                        runOnUiThread(new Runnable() { @Override public void run() { Ui.toast(SigningActivity.this, m); refresh(); } });
                    }
                }).start();
            }
        }, "Cancel", null);
        g.show();
    }

    private EditText add(LinearLayout parent, String hint, String def, boolean pw) {
        EditText e = Ui.input(this, hint, t);
        if (def != null && def.length() > 0) e.setText(def);
        if (pw) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        parent.addView(e, Ui.lp(-1, -2, 0, 8, 0, 0));
        return e;
    }

    private void importDialog() {
        StoragePicker.pickFiles(this, "Choose a keystore file", null,
                new String[]{".jks", ".keystore", ".p12", ".pfx", ".bks", ".ks"}, false, new StoragePicker.FilesCb() {
                    @Override public void onFiles(List<File> files) { importFile(files.get(0)); }
                });
    }

    private void importFile(final File f) {
        final Dialogs.Glass g = new Dialogs.Glass(this, "Import " + f.getName());
        final EditText name = field(g, "Key name (for you)", f.getName(), false);
        final EditText sp = field(g, "Keystore password", "", true);
        final EditText alias = field(g, "Key alias (leave empty = first key)", "", false);
        final EditText kp = field(g, "Key password (empty = same as keystore)", "", true);
        g.buttons("Import", new Runnable() {
            @Override public void run() {
                final String pw = sp.getText().toString();
                if (pw.length() == 0) { Ui.toast(SigningActivity.this, "Enter the keystore password"); return; }
                g.dismiss();
                new Thread(new Runnable() {
                    @Override public void run() {
                        String msg;
                        try {
                            Signing.importKey(SigningActivity.this, f, name.getText().toString().trim(), alias.getText().toString().trim(),
                                    pw, kp.getText().toString());
                            msg = "Key imported ✔";
                        } catch (Exception ex) {
                            msg = "Could not open the key: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
                        }
                        final String m = msg;
                        runOnUiThread(new Runnable() { @Override public void run() { Ui.toast(SigningActivity.this, m); refresh(); } });
                    }
                }).start();
            }
        }, "Cancel", null);
        g.show();
    }
}
