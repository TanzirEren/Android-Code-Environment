package com.ace.ide;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** About ACE + the feature list. */
public class InfoActivity extends Activity {

    private Theme t;
    private LinearLayout col;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        t = Theme.get(this);
        getWindow().setStatusBarColor(t.headerTop);
        getWindow().setNavigationBarColor(t.bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.bg);
        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t.headerTop, t.headerBottom}));
        head.setPadding(Ui.dp(20), Ui.dp(40), Ui.dp(20), Ui.dp(26));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        head.addView(logo, new LinearLayout.LayoutParams(Ui.dp(84), Ui.dp(84)));
        TextView n = Ui.text(this, "ACE", 28, Theme.FG, true);
        n.setLetterSpacing(0.2f);
        head.addView(n, Ui.lp(-2, -2, 0, 10, 0, 0));
        head.addView(Ui.text(this, "Android Code Environment", 13, 0xFFC9D1DB, false));
        head.addView(Ui.text(this, "Version " + Ui.VERSION, 11.5f, 0xFFB6C3D3, false), Ui.lp(-2, -2, 0, 4, 0, 0));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(40));
        col.addView(body);

        section(body, "Java → DEX",
                "Convert Java source to classes.dex on the phone (ECJ + D8)",
                "Import .java files, folders and ZIP archives",
                "Bundle library .jar files into the DEX",
                "Multi-dex output and a Smali explorer",
                "Download or share the DEX from Downloads/ACE");
        section(body, "App Builder",
                "Create apps from 4 ready-to-build Java + XML templates",
                "Import any project folder (ACE, Android Studio, AIDE layouts)",
                "Clone a Git repository (private repos with a token)",
                "Complete APK pipeline on-device — no Gradle, no aapt2",
                "Own resource compiler: binary XML, resources.arsc and R.java",
                "Styles, themes, arrays, plurals, night / land / density / version folders",
                "Debug and release builds, signed with APK Signature Scheme v1 + v2 + v3",
                "Generate or import signing keys (.jks, .p12, .bks)",
                "Install, download or share the finished APK",
                "Custom app icon with adjustable corner radius",
                "30 popular libraries, Maven coordinates, your own .jar / .aar",
                "Project settings: name, version, SDK levels, permissions");
        section(body, "Code editor",
                "Syntax highlighting for Java, XML and JSON",
                "Live red error marks while you type",
                "Two-finger pinch zoom (10–14 sp default)",
                "Line numbers, current-line highlight, undo / redo",
                "Find & replace with regex, go to line",
                "Auto indent, auto-close brackets, symbol bar",
                "Word and Android API suggestions",
                "Snippets, format code, comment toggle, duplicate line",
                "File tabs and automatic saving");
        section(body, "Files",
                "Glass file explorer with a real tree",
                "Create, rename, duplicate, move, delete, export, share",
                "Import files or whole folders from anywhere on storage",
                "Projects saved in /storage/emulated/0/ACE/<project>",
                "Light and dark themes in the ACE colors");

        TextView dev = Ui.button(this, "Developer", t.accent);
        dev.setTextColor(t.onAccent);
        dev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Dialogs.html(InfoActivity.this, "Developer", "developer.html"); }
        });
        body.addView(dev, Ui.lp(-1, -2, 0, 20, 0, 0));
        TextView note = Ui.text(this, "Not supported: Kotlin, AndroidX / AppCompat / Material resources (library resources are not merged), annotation processors, 9-patch processing.", 11.5f, t.textSub, false);
        note.setLineSpacing(0, 1.15f);
        body.addView(note, Ui.lp(-1, -2, 0, 14, 0, 0));
    }

    private void section(LinearLayout body, String title, String... items) {
        TextView h = Ui.text(this, title.toUpperCase(), 11.5f, t.accent, true);
        h.setLetterSpacing(0.1f);
        body.addView(h, Ui.lp(-2, -2, 4, 20, 0, 8));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.outline(t.card, t.cardStroke, 18, 1, this));
        card.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        for (String it : items) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
            r.addView(Icons.view(this, Icons.CHECK, t.accent), new LinearLayout.LayoutParams(Ui.dp(16), Ui.dp(16)));
            TextView tv = Ui.text(this, it, 13, t.text, false);
            r.addView(tv, Ui.lp(0, -2, 10, 0, 0, 0));
            ((LinearLayout.LayoutParams) tv.getLayoutParams()).weight = 1f;
            card.addView(r);
        }
        body.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    static int unused() { return Build.VERSION.SDK_INT; }
}
