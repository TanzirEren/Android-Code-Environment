package com.ace.ide.build;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Google D8 based dexer (runs on the phone). */
public final class D8Dexer implements Dexer {

    @Override
    public String dex(List<File> classDirs, List<File> libJars, File androidJar, int minApi, boolean debug,
                      File outDir, final StringBuilder log) {
        List<Path> prog = new ArrayList<Path>();
        for (File d : classDirs) collect(d, prog);
        if (prog.isEmpty()) return "no .class files were produced by the compiler";
        for (File j : libJars) prog.add(j.toPath());

        DiagnosticsHandler handler = new DiagnosticsHandler() {
            public void error(Diagnostic d) { log.append("[D8] ERROR: ").append(d.getDiagnosticMessage()).append("\n"); }
            public void warning(Diagnostic d) {
                String m = d.getDiagnosticMessage();
                if (m != null && m.length() > 300) m = m.substring(0, 300) + "…";
                log.append("[D8] warning: ").append(m).append("\n");
            }
            public void info(Diagnostic d) { }
        };
        try {
            D8Command.Builder b = D8Command.builder(handler);
            b.addProgramFiles(prog.toArray(new Path[0]));
            if (androidJar != null) b.addLibraryFiles(androidJar.toPath());
            b.setMinApiLevel(Math.max(21, Math.min(30, minApi)));
            b.setMode(debug ? CompilationMode.DEBUG : CompilationMode.RELEASE);
            b.setOutput(outDir.toPath(), OutputMode.DexIndexed);
            D8.run(b.build());
        } catch (CompilationFailedException e) {
            String m = String.valueOf(e.getMessage());
            Throwable c = e.getCause();
            return "D8 failed: " + m + (c != null ? "\ncause: " + c : "");
        } catch (Throwable t) {
            return "D8 crashed: " + t;
        }
        return null;
    }

    private static void collect(File f, List<Path> out) {
        if (f.isFile()) {
            if (f.getName().endsWith(".class")) out.add(f.toPath());
            return;
        }
        File[] fs = f.listFiles();
        if (fs == null) return;
        for (File c : fs) collect(c, out);
    }
}
