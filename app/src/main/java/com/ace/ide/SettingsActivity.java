package com.ace.ide;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/** App-wide settings. */
public class SettingsActivity extends Activity {

    private Theme t;
    private LinearLayout col;

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
        root.addView(topBar("Settings"));
        root.addView(Ui.divider(this, t.cardStroke));
        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(14), Ui.dp(4), Ui.dp(14), Ui.dp(40));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        build();
    }

    private View topBar(String title) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(t.card);
        top.setPadding(Ui.dp(6), 0, Ui.dp(10), 0);
        top.addView(Ui.iconButton(this, Icons.ARROW_L, t.text, 20, 12, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        }));
        top.addView(Ui.text(this, title, 17, t.text, true), Ui.lp(-2, -2, 4, 0, 0, 0));
        top.setMinimumHeight(Ui.dp(52));
        return top;
    }

    private void build() {
        col.removeAllViews();
        SettingsUi s = new SettingsUi(this, col, SettingsUi.prefsStore(this));

        s.header("Appearance", Icons.IMAGE);
        s.info("ACE uses its blue theme: background #385F8C, icons and text #E9E9E9.");
        s.sw("showHidden", "Show hidden files", "Show .git, .gitignore and other dot-files in the explorer", false, null);

        s.header("Editor", Icons.EDIT);
        s.seek("fontSize", "Font size", "10–14 is comfortable on a phone. You can also pinch with two fingers in the editor.", 8, 24, 12, " sp", null);
        s.sw("pinchZoom", "Pinch to zoom", "Two-finger zoom like MT Manager", true, null);
        s.sw("wrap", "Word wrap", "Wrap long lines instead of scrolling sideways", false, null);
        s.sw("lineNumbers", "Line numbers", null, true, null);
        s.sw("hlLine", "Highlight current line", null, true, null);
        s.sw("autoIndent", "Auto indent", "Keep indentation on new lines, add a level after {", true, null);
        s.sw("autoClose", "Auto-close brackets and quotes", null, true, null);
        s.sw("symbolBar", "Symbol bar", "Quick keys for { } ( ) ; \" and more", true, null);
        s.sw("suggestions", "Code suggestions", "Word and Android API completion above the keyboard", true, null);
        s.sw("liveCheck", "Live error check", "Mark compile errors in red while you type (Java and XML)", true, null);
        s.sw("autosave", "Auto-save", "Save when you leave the editor or run", true, null);
        s.sw("keepScreenOn", "Keep screen on", "While the code editor is open", false, null);

        s.header("New app defaults", Icons.PACKAGE);
        s.text("defPkgPrefix", "Package prefix", "e.g. com.yourname", "com.example");
        s.choice("defMinSdk", "Minimum SDK", new String[]{"21 (Android 5.0)", "23 (Android 6)", "26 (Android 8)", "28 (Android 9)", "29 (Android 10)", "30 (Android 11)"},
                new int[]{21, 23, 26, 28, 29, 30}, 21, null);
        s.choice("defTargetSdk", "Target SDK", new String[]{"30", "31", "32", "33", "34"}, new int[]{30, 31, 32, 33, 34}, 34, null);
        s.choice("defTemplate", "Default template", Templates.NAMES, new int[]{0, 1, 2, 3}, 1, null);
        s.seek("defIconRadius", "Default icon corner radius", "0 = square, 50 = circle", 0, 50, 22, " %", null);

        s.header("Libraries", Icons.LIB);
        s.sw("autoLibs", "Auto-detect libraries", "Read imports and build.gradle, add the libraries a project needs and download them automatically", true, null);

        s.header("Java → DEX", Icons.CONVERT);
        s.choice("minApi", "Min API level for DEX", new String[]{"21", "23", "24", "26", "28", "29", "30"}, new int[]{21, 23, 24, 26, 28, 29, 30}, 21, null);
        s.sw("bundleLibs", "Bundle libraries into the DEX", "Merge imported .jar files into classes.dex", false, null);

        s.header("Signing", Icons.KEY);
        s.action("Signing keys", "Create, generate or import a key to sign release apps", new Runnable() {
            @Override public void run() { startActivity(new Intent(SettingsActivity.this, SigningActivity.class)); }
        });
        s.info("Debug builds are signed with ACE's built-in debug key. Release builds use the key chosen in Project settings (or your default key).");

        s.header("Git", Icons.GIT);
        s.text("gitUser", "Username", "GitHub / GitLab username", "");
        s.text("gitToken", "Access token", "Personal access token (for private repositories)", "", true);

        s.header("Storage", Icons.FOLDER);
        final TextView[] folderVal = new TextView[1];
        folderVal[0] = s.actionValue("ACE folder", "Where projects and APKs are saved", Project.aceRootNoCreate(this).getAbsolutePath(), new Runnable() {
            @Override public void run() { chooseFolder(); }
        });
        s.action("Reset ACE folder", "Use /storage/emulated/0/ACE again", new Runnable() {
            @Override public void run() { Prefs.folder(SettingsActivity.this, null); build(); Ui.toast(SettingsActivity.this, "Reset"); }
        });
        s.action("Clear library cache", Ui.size(FileUtil.size(Libs.cacheDir(this))) + " downloaded jars", new Runnable() {
            @Override public void run() {
                FileUtil.deleteDir(Libs.cacheDir(SettingsActivity.this));
                Ui.toast(SettingsActivity.this, "Library cache cleared");
                build();
            }
        });
        s.action("Rebuild framework cache", "Re-read resources from android.jar on the next build", new Runnable() {
            @Override public void run() { new File(getFilesDir(), "sys/fw.cache").delete(); Ui.toast(SettingsActivity.this, "Will be rebuilt on next run"); }
        });
        s.action("Clear Java → DEX outputs", "Delete converted .dex / smali files", new Runnable() {
            @Override public void run() {
                Dialogs.confirm(SettingsActivity.this, "Clear DEX outputs?", "Removes classes.dex and smali files in ACE/Java2Dex. Projects stay.", "Clear", new Runnable() {
                    @Override public void run() { Ui.toast(SettingsActivity.this, Project.cleanOutputs(SettingsActivity.this) + " item(s) removed"); }
                });
            }
        });

        s.header("About", Icons.INFO);
        s.action("Features", "Everything ACE can do", new Runnable() {
            @Override public void run() { startActivity(new Intent(SettingsActivity.this, InfoActivity.class)); }
        });
        s.action("Developer", "About the developer", new Runnable() {
            @Override public void run() { Dialogs.html(SettingsActivity.this, "Developer", "developer.html"); }
        });
        s.action("Reset all settings", "Restore every option to its default", new Runnable() {
            @Override public void run() {
                Dialogs.confirm(SettingsActivity.this, "Reset settings?", "Editor, build and appearance options go back to defaults. Projects and keys are not touched.", "Reset", new Runnable() {
                    @Override public void run() {
                        getSharedPreferences("ace_prefs", MODE_PRIVATE).edit().clear().apply();
                        recreate();
                    }
                });
            }
        });
        s.info("ACE " + Ui.VERSION + "  ·  Android Code Environment\nJava + XML app builder with an on-device compiler");
    }

    private void chooseFolder() {
        StoragePicker.pickFolder(this, "Choose the ACE folder", null, new StoragePicker.FolderCb() {
            @Override public void onFolder(File dir) {
                Prefs.folder(SettingsActivity.this, dir.getAbsolutePath());
                build();
                Ui.toast(SettingsActivity.this, "New projects are saved in " + dir.getAbsolutePath());
            }
        });
    }

    static int unused() { return Color.TRANSPARENT; }
}
