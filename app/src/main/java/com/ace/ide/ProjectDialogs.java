package com.ace.ide;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** New project / import project / clone repository dialogs of the App Builder tab. */
final class ProjectDialogs {

    interface Done { void onProject(Project p); }

    private ProjectDialogs() {}

    // ------------------------------------------------------------------ FAB menu

    static void fabMenu(final Activity a, final Done done) {
        Dialogs.Glass g = new Dialogs.Glass(a, "Add a project");
        g.row(Icons.PLUS, "New project", "Start from a template with a buildable default app", new Runnable() {
            @Override public void run() { newProject(a, done); }
        });
        g.row(Icons.IMPORT, "Import project", "Pick any project folder from your storage", new Runnable() {
            @Override public void run() { importProject(a, done); }
        });
        g.row(Icons.GIT, "Clone repository", "Download a project from a Git URL", new Runnable() {
            @Override public void run() { cloneRepo(a, done); }
        });
        g.show();
    }

    // ------------------------------------------------------------------ new project

    private static Bitmap pickedIcon;
    private static int radius = 22;

    static void newProject(final Activity a, final Done done) {
        final Theme t = Theme.get(a);
        pickedIcon = null;
        radius = Prefs.i(a, "defIconRadius", 22);
        final Dialogs.Glass g = new Dialogs.Glass(a, "New project");
        ScrollView sv = new ScrollView(a);
        LinearLayout in = new LinearLayout(a);
        in.setOrientation(LinearLayout.VERTICAL);
        sv.addView(in);
        g.body.addView(sv, new LinearLayout.LayoutParams(-1, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.60f)));

        final EditText name = field(a, t, in, "Project name", "MyApp");
        final EditText appName = field(a, t, in, "App name (shown on the phone)", "My App");
        final EditText pkg = field(a, t, in, "Package id", Prefs.s(a, "defPkgPrefix", "com.example") + ".myapp");
        final boolean[] edited = {false, false};
        name.addTextChangedListener(new SimpleWatcher() {
            @Override public void changed(String s) {
                if (!edited[0]) appName.setText(s);
                if (!edited[1]) pkg.setText(Prefs.s(a, "defPkgPrefix", "com.example") + "." + s.toLowerCase().replaceAll("[^a-z0-9]", ""));
            }
        });
        appName.setOnFocusChangeListener(new View.OnFocusChangeListener() { @Override public void onFocusChange(View v, boolean f) { if (f) edited[0] = true; } });
        pkg.setOnFocusChangeListener(new View.OnFocusChangeListener() { @Override public void onFocusChange(View v, boolean f) { if (f) edited[1] = true; } });
        name.setText("");

        // icon
        in.addView(label(a, t, "App icon"), Ui.lp(-1, -2, 0, 14, 0, 0));
        LinearLayout iconRow = new LinearLayout(a);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        final ImageView iv = new ImageView(a);
        iconRow.addView(iv, new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(64)));
        LinearLayout ic = new LinearLayout(a);
        ic.setOrientation(LinearLayout.VERTICAL);
        final TextView rl = Ui.text(a, "Icon radius: " + radius + " %", 12, t.textSub, false);
        ic.addView(rl);
        SeekBar sb = new SeekBar(a);
        sb.setMax(50);
        sb.setProgress(radius);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(t.accent));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(t.accent));
        ic.addView(sb);
        TextView pick = Ui.text(a, "Choose image  ·  default: ACE logo", 12, t.accent, true);
        ic.addView(pick, Ui.lp(-2, -2, 0, 4, 0, 0));
        iconRow.addView(ic, Ui.lp(0, -2, 14, 0, 0, 0));
        ((LinearLayout.LayoutParams) ic.getLayoutParams()).weight = 1f;
        in.addView(iconRow, Ui.lp(-1, -2, 0, 6, 0, 0));
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                Bitmap src = pickedIcon != null ? pickedIcon : IconGen.defaultIcon(a);
                iv.setImageBitmap(IconGen.render(src, 128, radius));
            }
        };
        refresh.run();
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) { radius = v; rl.setText("Icon radius: " + v + " %"); refresh.run(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                StoragePicker.pickFiles(a, "Choose an icon image", null, new String[]{".png", ".jpg", ".jpeg", ".webp"}, false, new StoragePicker.FilesCb() {
                    @Override public void onFiles(List<File> files) {
                        Bitmap b = IconGen.load(files.get(0));
                        if (b == null) Ui.toast(a, "Cannot read that image"); else { pickedIcon = b; refresh.run(); }
                    }
                });
            }
        });

        // API
        in.addView(label(a, t, "API level (auto)"), Ui.lp(-1, -2, 0, 14, 0, 0));
        final int[] mins = {21, 23, 26, 28, 29, 30};
        final int[] tars = {30, 31, 32, 33, 34};
        final int[] minSel = {Prefs.i(a, "defMinSdk", 21)};
        final int[] tarSel = {Prefs.i(a, "defTargetSdk", 34)};
        final TextView apiView = Ui.text(a, "", 13, t.text, true);
        apiView.setBackground(Ui.outline(t.inputBg, t.cardStroke, 12, 1, a));
        apiView.setPadding(Ui.dp(12), Ui.dp(11), Ui.dp(12), Ui.dp(11));
        final Runnable api = new Runnable() {
            @Override public void run() { apiView.setText("Min SDK " + minSel[0] + "   ·   Target SDK " + tarSel[0] + "      (tap to change)"); }
        };
        api.run();
        apiView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String[] l = new String[mins.length];
                for (int i = 0; i < mins.length; i++) l[i] = "Min SDK " + mins[i];
                Dialogs.choose(a, "Minimum SDK", l, -1, new Dialogs.IndexCb() {
                    @Override public void onIndex(int i) { minSel[0] = mins[i]; if (tarSel[0] < minSel[0]) tarSel[0] = minSel[0]; api.run(); }
                });
            }
        });
        in.addView(apiView, Ui.lp(-1, -2, 0, 6, 0, 0));

        // other settings
        in.addView(label(a, t, "Template"), Ui.lp(-1, -2, 0, 14, 0, 0));
        final int[] tpl = {Prefs.i(a, "defTemplate", 1)};
        final TextView tplView = Ui.text(a, "", 13, t.text, true);
        tplView.setBackground(Ui.outline(t.inputBg, t.cardStroke, 12, 1, a));
        tplView.setPadding(Ui.dp(12), Ui.dp(11), Ui.dp(12), Ui.dp(11));
        final Runnable tplShow = new Runnable() { @Override public void run() { tplView.setText(Templates.NAMES[tpl[0]] + "\n" + Templates.DESCS[tpl[0]]); } };
        tplShow.run();
        tplView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Dialogs.choose(a, "Template", Templates.NAMES, tpl[0], new Dialogs.IndexCb() {
                    @Override public void onIndex(int i) { tpl[0] = i; tplShow.run(); }
                });
            }
        });
        in.addView(tplView, Ui.lp(-1, -2, 0, 6, 0, 0));
        final boolean[] internet = {false};
        final TextView net = Ui.text(a, "☐  Internet permission", 13.5f, t.text, false);
        net.setPadding(Ui.dp(4), Ui.dp(12), Ui.dp(4), Ui.dp(4));
        net.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { internet[0] = !internet[0]; ((TextView) v).setText((internet[0] ? "☑" : "☐") + "  Internet permission"); }
        });
        in.addView(net);

        g.buttons("Create project", new Runnable() {
            @Override public void run() {
                String n = name.getText().toString().trim();
                String an = appName.getText().toString().trim();
                String pk = pkg.getText().toString().trim();
                if (n.length() == 0) { Ui.shake(name); Ui.toast(a, "Enter a project name"); return; }
                if (n.equalsIgnoreCase("Java2Dex")) { Ui.toast(a, "\"Java2Dex\" is reserved — pick another name"); return; }
                if (an.length() == 0) an = n;
                if (!pk.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) {
                    Ui.shake(pkg);
                    Ui.toast(a, "Package id must look like com.example.app (lowercase)");
                    return;
                }
                File target = new File(Project.aceRoot(a), FileUtil.safeName(n));
                if (target.exists()) { Ui.shake(name); Ui.toast(a, "A project folder named \"" + target.getName() + "\" already exists"); return; }
                final Templates.Spec s = new Templates.Spec();
                s.name = n; s.appName = an; s.pkg = pk; s.minSdk = minSel[0]; s.targetSdk = tarSel[0];
                s.template = tpl[0]; s.icon = pickedIcon; s.radiusPct = radius; s.internet = internet[0];
                g.dismiss();
                createAsync(a, s, done);
            }
        }, "Cancel", null);
        g.show();
    }

    private static void createAsync(final Activity a, final Templates.Spec s, final Done done) {
        Ui.toast(a, "Creating project …");
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                Project p = null;
                try {
                    File root = Templates.create(a, s);
                    p = new Project();
                    p.id = String.valueOf(System.currentTimeMillis());
                    p.name = root.getName();
                    p.kind = Project.KIND_APP;
                    p.createdAt = System.currentTimeMillis();
                    p.path = root.getAbsolutePath();
                    p.appName = s.appName;
                    p.pkg = s.pkg;
                    p.minSdk = s.minSdk;
                    p.targetSdk = s.targetSdk;
                    Project.upsert(a, p);
                } catch (Exception e) {
                    err = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                final String fe = err;
                final Project fp = p;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (fe != null) Dialogs.info(a, "Could not create the project", fe + "\n\nIs storage permission granted?");
                        else done.onProject(fp);
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ import

    static void importProject(final Activity a, final Done done) {
        StoragePicker.pickFolder(a, "Select the project folder", Project.aceRootNoCreate(a), new StoragePicker.FolderCb() {
            @Override public void onFolder(final File dir) {
                final ProjectLayout l = ProjectLayout.detect(dir);
                if (l.valid()) { register(a, dir, done); return; }
                Dialogs.confirm(a, "No Android project found",
                        "There is no AndroidManifest.xml in \"" + dir.getName() + "\". You can still open it in the editor, but it cannot be built until you add one.",
                        "Open anyway", new Runnable() { @Override public void run() { register(a, dir, done); } });
            }
        });
    }

    /** add an existing folder to the project list (copied into the ACE folder when it lives elsewhere) */
    private static void register(final Activity a, final File dir, final Done done) {
        final File aceRoot = Project.aceRoot(a);
        for (Project x : Project.ofKind(a, Project.KIND_APP)) {
            if (x.path != null && new File(x.path).equals(dir)) { done.onProject(x); return; }
        }
        if (FileUtil.isInside(dir, aceRoot)) { finishRegister(a, dir, done); return; }
        Ui.toast(a, "Copying project into the ACE folder …");
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                final File dest = FileUtil.unique(aceRoot, dir.getName());
                try {
                    FileUtil.copyTree(dir, dest);
                } catch (Exception e) { err = e.getMessage(); FileUtil.deleteDir(dest); }
                final String fe = err;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (fe != null) Dialogs.info(a, "Import failed", fe);
                        else finishRegister(a, dest, done);
                    }
                });
            }
        }).start();
    }

    private static void finishRegister(Activity a, File dir, Done done) {
        Project p = new Project();
        p.id = String.valueOf(System.currentTimeMillis());
        p.name = dir.getName();
        p.kind = Project.KIND_APP;
        p.createdAt = System.currentTimeMillis();
        p.path = dir.getAbsolutePath();
        ProjectLayout.guessConfig(p, ProjectLayout.detect(dir));
        if (p.appName == null || p.appName.length() == 0) p.appName = p.name;
        Project.upsert(a, p);
        done.onProject(p);
    }

    // ------------------------------------------------------------------ clone

    static void cloneRepo(final Activity a, final Done done) {
        final Theme t = Theme.get(a);
        final Dialogs.Glass g = new Dialogs.Glass(a, "Clone repository");
        final EditText url = field(a, t, g.body, "Repository URL", "https://github.com/user/repo.git");
        final EditText name = field(a, t, g.body, "Project folder name", "repo");
        final EditText branch = field(a, t, g.body, "Branch (optional)", "");
        final EditText user = field(a, t, g.body, "Username (private repos)", "");
        user.setText(Prefs.s(a, "gitUser", ""));
        final EditText token = field(a, t, g.body, "Access token (private repos)", "");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setText(Prefs.s(a, "gitToken", ""));
        final boolean[] renamed = {false};
        name.setOnFocusChangeListener(new View.OnFocusChangeListener() { @Override public void onFocusChange(View v, boolean f) { if (f) renamed[0] = true; } });
        url.addTextChangedListener(new SimpleWatcher() {
            @Override public void changed(String s) { if (!renamed[0]) name.setText(GitClone.nameFromUrl(s)); }
        });
        g.buttons("Clone", new Runnable() {
            @Override public void run() {
                final String u = url.getText().toString().trim();
                String n = FileUtil.safeName(name.getText().toString().trim());
                if (u.length() == 0) { Ui.shake(url); return; }
                if (name.getText().toString().trim().length() == 0) n = FileUtil.safeName(GitClone.nameFromUrl(u));
                final File dest = new File(Project.aceRoot(a), n);
                if (dest.exists()) { Ui.shake(name); Ui.toast(a, "Folder \"" + n + "\" already exists — rename it"); return; }
                g.dismiss();
                doClone(a, u, dest, branch.getText().toString().trim(), user.getText().toString().trim(), token.getText().toString().trim(), done);
            }
        }, "Cancel", null);
        g.show();
    }

    private static void doClone(final Activity a, final String url, final File dest, final String branch, final String user, final String token, final Done done) {
        Theme t = Theme.get(a);
        final Dialogs.Glass g = new Dialogs.Glass(a, "Cloning …");
        g.dialog.setCancelable(false);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(new ProgressBar(a), new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
        final TextView st = Ui.text(a, "Starting …", 13, t.text, false);
        row.addView(st, Ui.lp(0, -2, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) st.getLayoutParams()).weight = 1f;
        g.add(row, Ui.lp(-1, -2, 0, 14, 0, 8));
        g.show();
        new Thread(new Runnable() {
            @Override public void run() {
                final String err = GitClone.clone(url, dest, branch, user, token, new GitClone.Progress() {
                    @Override public void onStatus(final String m) { a.runOnUiThread(new Runnable() { @Override public void run() { st.setText(m); } }); }
                }, a.getCacheDir());
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        g.dismiss();
                        if (err != null) Dialogs.log(a, "Clone failed", err, true);
                        else finishRegister(a, dest, done);
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ small helpers

    private static TextView label(Activity a, Theme t, String s) { return Ui.text(a, s, 12, t.textSub, true); }

    private static EditText field(Activity a, Theme t, LinearLayout parent, String hint, String def) {
        EditText e = Ui.input(a, hint, t);
        parent.addView(e, Ui.lp(-1, -2, 0, 10, 0, 0));
        return e;
    }

    abstract static class SimpleWatcher implements TextWatcher {
        abstract void changed(String s);
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void afterTextChanged(Editable e) { changed(e.toString()); }
    }

    static Intent unused() { return null; }
}
