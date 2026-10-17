package com.ace.ide;

import android.app.Activity;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Looks at the project (imports in the code, build.gradle, chosen libraries), adds what is missing and
 * downloads everything in parallel with live progress.
 */
final class LibFetcher {

    interface Listener {
        void onStart(String message);
        void onProgress(float fraction, String message);
        void onDone(String message, boolean error, boolean projectChanged);
    }

    private static volatile boolean running;

    static boolean isRunning() { return running; }

    private LibFetcher() {}

    static void run(final Activity a, final Project p, final ProjectLayout layout, final boolean detect, final Listener l) {
        if (running) return;
        running = true;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean changed = false;
                final List<String> added = new ArrayList<String>();
                try {
                    if (detect && Prefs.b(a, "autoLibs", true) && layout != null) {
                        for (String id : Libs.detectIds(layout.javaDirs)) {
                            if (!p.libs.contains(id) && Libs.byId(id) != null) { p.libs.add(id); added.add(Libs.byId(id).title); changed = true; }
                        }
                        Set<String> known = new LinkedHashSet<String>();
                        for (String c : Libs.coordsOf(p)) known.add(ga(c));
                        for (String c : Libs.gradleCoords(layout)) {
                            if (known.add(ga(c))) { p.customLibs.add(c); added.add(c); changed = true; }
                        }
                        if (changed) Project.upsert(a, p);
                    }
                    final Libs.Plan plan = Libs.plan(a, p, true);
                    final List<String> missing = new ArrayList<String>();
                    for (String c : plan.all()) if (!Libs.isCached(a, c)) missing.add(c);
                    if (missing.isEmpty()) {
                        finish(a, l, added.isEmpty() ? null : "Added " + join(added) + " ✔", false, changed);
                        return;
                    }
                    final int n = missing.size();
                    post(a, new Runnable() { @Override public void run() {
                        l.onStart((added.isEmpty() ? "" : "Detected " + join(added) + " · ") + "downloading " + n + (n == 1 ? " library" : " libraries"));
                    } });
                    final float[] frac = new float[n];
                    final int[] doneFiles = {0};
                    final long[] lastPost = {0};
                    ExecutorService ex = Executors.newFixedThreadPool(Math.min(4, n));
                    List<Future<String>> fs = new ArrayList<Future<String>>();
                    for (int i = 0; i < n; i++) {
                        final int idx = i;
                        final String coord = missing.get(i);
                        final boolean req = plan.required.contains(coord);
                        fs.add(ex.submit(new java.util.concurrent.Callable<String>() {
                            @Override public String call() {
                                try {
                                    Libs.ensure(a, coord, null, new Libs.ByteProgress() {
                                        @Override public void onBytes(long done, long total) {
                                            frac[idx] = total > 0 ? Math.min(1f, done / (float) total) : 0.5f;
                                            report(a, l, frac, doneFiles, n, coord, lastPost, false);
                                        }
                                    });
                                    frac[idx] = 1f;
                                    synchronized (doneFiles) { doneFiles[0]++; }
                                    report(a, l, frac, doneFiles, n, coord, lastPost, true);
                                    return null;
                                } catch (Exception e) {
                                    frac[idx] = 1f;
                                    synchronized (doneFiles) { doneFiles[0]++; }
                                    return req ? e.getMessage() : null;
                                }
                            }
                        }));
                    }
                    String err = null;
                    for (Future<String> f : fs) {
                        try { String r = f.get(); if (r != null && err == null) err = r; }
                        catch (Exception e) { if (err == null) err = String.valueOf(e.getMessage()); }
                    }
                    ex.shutdown();
                    if (err != null) finish(a, l, "Offline or not found — will retry on build", true, changed);
                    else finish(a, l, n + (n == 1 ? " library" : " libraries") + " ready ✔", false, changed);
                } catch (Throwable t) {
                    finish(a, l, "Library check failed: " + t.getMessage(), true, changed);
                } finally {
                    running = false;
                }
            }
        }).start();
    }

    private static void report(Activity a, final Listener l, float[] frac, int[] done, final int n, String coord, long[] last, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - last[0] < 80) return;
        last[0] = now;
        float sum = 0;
        for (float f : frac) sum += f;
        final float overall = sum / n;
        final int d;
        synchronized (done) { d = done[0]; }
        String[] pc = Libs.parse(coord);
        final String name = pc == null ? coord : pc[1] + "-" + pc[2] + "." + pc[3];
        post(a, new Runnable() { @Override public void run() { l.onProgress(overall, name + "   " + d + "/" + n); } });
    }

    private static void finish(Activity a, final Listener l, final String msg, final boolean error, final boolean changed) {
        post(a, new Runnable() { @Override public void run() { l.onDone(msg, error, changed); } });
    }

    private static void post(Activity a, Runnable r) {
        if (Ui.alive(a)) a.runOnUiThread(r);
    }

    private static String ga(String coord) {
        String[] p = Libs.parse(coord);
        return p == null ? coord : p[0] + ":" + p[1];
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(", ");
            if (i == 3) { sb.append("+").append(l.size() - 3).append(" more"); break; }
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    static File unused() { return null; }
}
