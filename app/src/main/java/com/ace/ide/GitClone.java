package com.ace.ide;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** git clone (JGit). Falls back to a zip download for public GitHub repositories. */
public final class GitClone {

    public interface Progress { void onStatus(String msg); }

    private GitClone() {}

    /** a sensible folder name for a repo URL */
    public static String nameFromUrl(String url) {
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith(".git")) u = u.substring(0, u.length() - 4);
        int s = Math.max(u.lastIndexOf('/'), u.lastIndexOf(':'));
        String n = s >= 0 ? u.substring(s + 1) : u;
        return n.length() == 0 ? "repo" : n;
    }

    /** @return null on success, otherwise an error message */
    public static String clone(String url, File dest, String branch, String user, String token, final Progress pr, File tmpDir) {
        String gitError = null;
        try {
            if (pr != null) pr.onStatus("Connecting …");
            Git.cloneRepository()
                    .setURI(url.trim())
                    .setDirectory(dest)
                    .setCredentialsProvider(token != null && token.length() > 0
                            ? new UsernamePasswordCredentialsProvider(user == null || user.length() == 0 ? "token" : user, token) : null)
                    .setProgressMonitor(new ProgressMonitor() {
                        private String task = "";
                        private int total, done;
                        @Override public void start(int totalTasks) { }
                        @Override public void beginTask(String title, int totalWork) {
                            task = title; total = totalWork; done = 0;
                            if (pr != null) pr.onStatus(title + " …");
                        }
                        @Override public void update(int completed) {
                            done += completed;
                            if (pr != null && total > 0 && (done % Math.max(1, total / 20) == 0)) pr.onStatus(task + "  " + (done * 100 / total) + "%");
                        }
                        @Override public void endTask() { }
                        @Override public boolean isCancelled() { return false; }
                    })
                    .call()
                    .close();
            return null;
        } catch (Throwable t) {
            gitError = t.getMessage() != null ? t.getMessage() : t.toString();
            FileUtil.deleteDir(dest);
        }
        // fallback: public GitHub zip
        Matcher m = Pattern.compile("github\\.com[/:]([^/]+)/([^/]+?)(?:\\.git)?/?$").matcher(url.trim());
        if (m.find() && (token == null || token.length() == 0)) {
            String[] branches = branch != null && branch.length() > 0 ? new String[]{branch} : new String[]{"main", "master"};
            for (String b : branches) {
                try {
                    if (pr != null) pr.onStatus("git failed — downloading zip (" + b + ") …");
                    File zip = new File(tmpDir, "clone.zip");
                    String zurl = "https://codeload.github.com/" + m.group(1) + "/" + m.group(2) + "/zip/refs/heads/" + b;
                    HttpURLConnection con = (HttpURLConnection) new URL(zurl).openConnection();
                    con.setConnectTimeout(15000);
                    con.setReadTimeout(30000);
                    if (con.getResponseCode() != 200) { con.disconnect(); continue; }
                    InputStream in = con.getInputStream();
                    FileOutputStream fo = new FileOutputStream(zip);
                    byte[] buf = new byte[16384];
                    int n;
                    try { while ((n = in.read(buf)) > 0) fo.write(buf, 0, n); } finally { fo.close(); in.close(); }
                    dest.mkdirs();
                    FileUtil.unzip(zip, dest, true);
                    zip.delete();
                    return null;
                } catch (Throwable ignored) {
                    FileUtil.deleteDir(dest);
                }
            }
        }
        return "Clone failed: " + gitError;
    }
}
