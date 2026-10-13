package com.ace.ide;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Java → .class (ECJ) → classes.dex (D8) — fully on-device */
public class Converter {

    public interface Callback {
        void onStep(String step);
        void onDone(boolean success, String log);
    }

    private static volatile boolean running = false;

    public static boolean isRunning() { return running; }

    public static void convert(final Context ctx, final Project p, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        final Handler h = new Handler(Looper.getMainLooper());
        final StringBuilder log = new StringBuilder();

        if (running) {
            h.post(() -> cb.onDone(false, "[ACE] Another conversion is already running.\n"));
            return;
        }
        running = true;

        new Thread(() -> {
            boolean ok;
            long t0 = System.currentTimeMillis();
            try {
                ok = run(app, p, log, cb, h);
            } catch (Throwable t) {
                log.append("\n[ACE] Unexpected error: ")
                   .append(t.getClass().getName()).append(": ")
                   .append(t.getMessage()).append("\n");
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                String st = sw.toString();
                if (st.length() > 3500) st = st.substring(0, 3500) + "\n…";
                log.append(st).append("\n");
                ok = false;
            }
            log.append("[ACE] Time: ").append(System.currentTimeMillis() - t0).append(" ms\n");

            final boolean fOk = ok;
            final String fLog = log.toString();

            p.status = fOk ? Project.ST_OK : Project.ST_ERROR;
            p.builtAt = System.currentTimeMillis();
            if (!fOk) p.dexSize = 0;
            Project.upsert(app, p);
            try { Ui.writeText(p.logFile(app), fLog); } catch (Throwable ignored) { }

            running = false;
            h.post(() -> cb.onDone(fOk, fLog));
        }, "j2d-convert").start();
    }

    private static boolean run(Context app, Project p, StringBuilder log,
                               Callback cb, Handler h) throws Exception {
        File classes = p.classesDir(app);
        File dexOut = p.dexTmpDir(app);
        Project.deleteDir(classes);
        Project.deleteDir(dexOut);
        classes.mkdirs();
        dexOut.mkdirs();

        log.append("[ACE] Project: ").append(p.name).append("\n");

        // 1. collect sources (.java only — notes, images, jars … are never fed to ECJ)
        h.post(() -> cb.onStep("Collecting source files…"));
        final List<File> sources = new ArrayList<>();
        collect(p.srcDir(app), sources, ".java");
        Collections.sort(sources);
        log.append("[ACE] Source files found: ").append(sources.size()).append("\n");
        if (sources.isEmpty()) {
            log.append("[ACE] ERROR: No .java files found in project sources.\n");
            return false;
        }

        // 2. classpath
        StringBuilder cp = new StringBuilder();
        File sysJar = ensureAndroidJar(app);
        if (sysJar != null) {
            cp.append(sysJar.getAbsolutePath());
            log.append("[ACE] Using android.jar for compilation.\n");
        } else {
            log.append("[ACE] WARNING: android.jar missing — Android APIs may not resolve.\n");
        }
        List<Path> libPaths = new ArrayList<>();
        File[] jars = p.libsDir(app).listFiles();
        if (jars != null) {
            java.util.Arrays.sort(jars);
            for (File j : jars) {
                String n = j.getName().toLowerCase();
                if (n.endsWith(".jar") || n.endsWith(".zip")) {
                    if (cp.length() > 0) cp.append(":");
                    cp.append(j.getAbsolutePath());
                    libPaths.add(j.toPath());
                    log.append("[ACE] Library: ").append(j.getName()).append("\n");
                }
            }
        }
        if (cp.length() == 0) cp.append(".");

        // 3. ECJ compile : .java → .class
        h.post(() -> cb.onStep("Compiling Java (" + sources.size() + " files)…"));
        log.append("\n[ECJ] Compiling with Eclipse Compiler for Java…\n");
        StringWriter outW = new StringWriter();
        StringWriter errW = new StringWriter();
        PrintWriter out = new PrintWriter(outW, true);
        PrintWriter err = new PrintWriter(errW, true);

        List<String> args = new ArrayList<>();
        args.add("-1.8");
        args.add("-g");
        args.add("-encoding");
        args.add("UTF-8");
        args.add("-proc:none");
        args.add("-cp");
        args.add(cp.toString());
        args.add("-d");
        args.add(classes.getAbsolutePath());
        for (File s : sources) args.add(s.getAbsolutePath());

        boolean ok = BatchCompiler.compile(args.toArray(new String[0]), out, err, null);
        out.flush();
        err.flush();
        if (outW.toString().length() > 0) log.append(outW).append("\n");
        if (errW.toString().length() > 0) log.append(errW).append("\n");

        if (!ok) {
            log.append("\n[ACE] Compilation FAILED. Fix the errors above and retry.\n");
            return false;
        }
        log.append("[ECJ] Compiled successfully.\n");

        // 4. D8 dex : .class → classes.dex (+ classes2.dex … when over the 64K limit)
        h.post(() -> cb.onStep("Converting to DEX (D8)…"));
        int minApi = Prefs.minApi(app);
        boolean bundle = Prefs.bundleLibs(app);
        log.append("\n[D8] Converting .class files to DEX (min API ").append(minApi)
           .append(bundle ? ", libraries bundled" : "").append(")…\n");

        List<File> classFiles = new ArrayList<>();
        collect(classes, classFiles, ".class");
        List<Path> prog = new ArrayList<>();
        for (File f : classFiles) prog.add(f.toPath());
        if (prog.isEmpty()) {
            log.append("[D8] ERROR: no .class files produced by compiler.\n");
            return false;
        }
        log.append("[D8] Class files: ").append(prog.size()).append("\n");

        final StringBuilder dlog = log;
        DiagnosticsHandler handler = new DiagnosticsHandler() {
            public void error(Diagnostic d) {
                dlog.append("[D8] ERROR: ").append(d.getDiagnosticMessage()).append("\n");
            }
            public void warning(Diagnostic d) {
                dlog.append("[D8] warning: ").append(d.getDiagnosticMessage()).append("\n");
            }
            public void info(Diagnostic d) { }
        };

        D8Command.Builder db = D8Command.builder(handler);
        db.addProgramFiles(prog.toArray(new Path[0]));
        if (bundle && !libPaths.isEmpty()) db.addProgramFiles(libPaths.toArray(new Path[0]));
        if (sysJar != null) db.addLibraryFiles(sysJar.toPath());
        if (!bundle && !libPaths.isEmpty()) db.addLibraryFiles(libPaths.toArray(new Path[0]));
        db.setMinApiLevel(minApi);
        db.setMode(CompilationMode.DEBUG);
        db.setOutput(dexOut.toPath(), OutputMode.DexIndexed);
        try {
            D8.run(db.build());
        } catch (CompilationFailedException e) {
            log.append("[D8] FAILED: ").append(String.valueOf(e.getMessage())).append("\n");
            Throwable c = e.getCause();
            if (c != null) log.append("[D8] cause: ").append(c).append("\n");
            return false;
        }
        log.append("[D8] DEX created.\n");

        // 5. save into JAVA2DEX folder
        h.post(() -> cb.onStep("Saving to output folder…"));
        File[] made = dexOut.listFiles();
        List<File> dexFiles = new ArrayList<>();
        if (made != null) for (File f : made) {
            if (f.getName().startsWith("classes") && f.getName().endsWith(".dex")) dexFiles.add(f);
        }
        if (dexFiles.isEmpty()) {
            log.append("[ACE] ERROR: classes.dex not found after dexing.\n");
            return false;
        }
        Collections.sort(dexFiles);

        File pub = p.publicDexDir(app);
        if (!pub.isDirectory() || !pub.canWrite()) {
            log.append("[ACE] ERROR: cannot write to output folder:\n  ")
               .append(pub.getAbsolutePath())
               .append("\n  Grant storage permission or pick another folder in Settings.\n");
            return false;
        }
        // remove the previous build only (never touch unrelated files in the folder)
        File[] old = pub.listFiles();
        if (old != null) for (File f : old) {
            String n = f.getName();
            if (n.equals("smali") || (n.startsWith("classes") && n.endsWith(".dex"))) {
                Project.deleteDir(f);
            }
        }
        long total = 0;
        for (File f : dexFiles) {
            Ui.copyFile(f, new File(pub, f.getName()));
            total += f.length();
        }
        p.dexSize = total;

        log.append("\n[ACE] SUCCESS\n");
        for (File f : dexFiles) {
            log.append("[ACE] Saved: ").append(new File(pub, f.getName()).getAbsolutePath())
               .append("  (").append(f.length()).append(" bytes)\n");
        }
        if (dexFiles.size() > 1) {
            log.append("[ACE] Multi-dex: ").append(dexFiles.size()).append(" files.\n");
        }
        return true;
    }

    // ------------------------------------------------------------ android.jar

    /**
     * android.jar bundled by CI into assets → extracted to internal storage.
     * Done atomically (temp file + rename) and re-done after every app update, so a
     * killed first run can never leave a corrupt jar behind. Safe to call from any thread.
     */
    public static synchronized File ensureAndroidJar(Context app) {
        File target = new File(app.getFilesDir(), "sys/android.jar");
        long stamp = 0L;
        try {
            PackageInfo pi = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            stamp = pi.lastUpdateTime;
        } catch (Throwable ignored) { }

        if (target.exists() && target.length() > 0 && Prefs.jarStamp(app) == stamp) return target;

        File tmp = new File(app.getFilesDir(), "sys/android.jar.tmp");
        try {
            InputStream in = app.getAssets().open("android.jar");
            Ui.copyStream(in, new java.io.FileOutputStream(prepare(tmp)));
            if (target.exists()) target.delete();
            if (!tmp.renameTo(target)) throw new java.io.IOException("rename failed");
            Prefs.jarStamp(app, stamp);
            return target;
        } catch (Throwable t) {
            tmp.delete();
            // asset missing (local build without CI step): fall back to an older copy if any
            return target.exists() && target.length() > 0 ? target : null;
        }
    }

    private static File prepare(File f) {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        return f;
    }

    public static File sysAndroidJar(Context app) {
        File f = new File(app.getFilesDir(), "sys/android.jar");
        return f.exists() ? f : null;
    }

    private static void collect(File dir, List<File> out, String ext) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out, ext);
            else if (f.getName().toLowerCase().endsWith(ext)) out.add(f);
        }
    }
}
