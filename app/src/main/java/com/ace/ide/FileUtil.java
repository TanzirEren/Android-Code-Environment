package com.ace.ide;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** File helpers: recursive copy / delete / zip. */
public final class FileUtil {

    private FileUtil() {}

    public static void deleteDir(File f) {
        if (f == null || !f.exists()) return;
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) deleteDir(x);
        f.delete();
    }

    public static void copyFile(File src, File dst) throws IOException {
        File p = dst.getParentFile();
        if (p != null) p.mkdirs();
        copyStream(new FileInputStream(src), new FileOutputStream(dst));
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            try { in.close(); } catch (IOException ignored) { }
            try { out.close(); } catch (IOException ignored) { }
        }
    }

    public static void copyTree(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            dst.mkdirs();
            File[] fs = src.listFiles();
            if (fs != null) for (File f : fs) copyTree(f, new File(dst, f.getName()));
        } else {
            copyFile(src, dst);
        }
    }

    public static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long t = 0;
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) t += size(x);
        return t;
    }

    public static boolean isInside(File child, File parent) {
        try {
            String c = child.getCanonicalPath(), p = parent.getCanonicalPath();
            return c.equals(p) || c.startsWith(p + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    /** "name", "name (2)", … that does not exist in dir yet */
    public static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && f.isFile()) { base = name.substring(0, dot); ext = name.substring(dot); }
        int i = 2;
        while (true) {
            f = new File(dir, base + " (" + i++ + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    public static String safeName(String s) {
        String r = s == null ? "" : s.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return r.length() == 0 ? "project" : r;
    }

    public static List<File> sorted(File dir, boolean showHidden) {
        File[] fs = dir.listFiles();
        List<File> out = new ArrayList<File>();
        if (fs == null) return out;
        for (File f : fs) {
            if (!showHidden && f.getName().startsWith(".")) continue;
            out.add(f);
        }
        java.util.Collections.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return out;
    }

    public static void zipDir(File dir, File zipFile, String... skipNames) throws IOException {
        ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            addZip(zo, dir, "", Arrays.asList(skipNames));
        } finally {
            zo.close();
        }
    }

    private static void addZip(ZipOutputStream zo, File f, String path, List<String> skip) throws IOException {
        File[] fs = f.listFiles();
        if (fs == null) return;
        for (File c : fs) {
            if (skip.contains(c.getName())) continue;
            String name = path + c.getName();
            if (c.isDirectory()) addZip(zo, c, name + "/", skip);
            else {
                zo.putNextEntry(new ZipEntry(name));
                FileInputStream in = new FileInputStream(c);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) zo.write(buf, 0, n);
                in.close();
                zo.closeEntry();
            }
        }
    }

    /** zip-slip safe extraction; strips a single top-level folder when {@code stripRoot} */
    public static void unzip(File zip, File dest, boolean stripRoot) throws IOException {
        String root = null;
        if (stripRoot) {
            ZipInputStream z0 = new ZipInputStream(new FileInputStream(zip));
            try {
                ZipEntry e;
                boolean single = true;
                while ((e = z0.getNextEntry()) != null) {
                    String n = e.getName().replace('\\', '/');
                    int s = n.indexOf('/');
                    if (s < 0) { single = false; break; }
                    String top = n.substring(0, s + 1);
                    if (root == null) root = top; else if (!root.equals(top)) { single = false; break; }
                }
                if (!single) root = null;
            } finally {
                z0.close();
            }
        }
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zip));
        try {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (root != null && name.startsWith(root)) name = name.substring(root.length());
                if (name.length() == 0 || name.contains("..")) continue;
                File out = new File(dest, name);
                if (e.isDirectory()) { out.mkdirs(); continue; }
                File p = out.getParentFile();
                if (p != null) p.mkdirs();
                FileOutputStream fo = new FileOutputStream(out);
                byte[] buf = new byte[16384];
                int n;
                while ((n = zis.read(buf)) > 0) fo.write(buf, 0, n);
                fo.close();
            }
        } finally {
            zis.close();
        }
    }

    public static String readText(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream((int) Math.max(32, f.length()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }

    public static void writeText(File f, String text) throws IOException {
        File p = f.getParentFile();
        if (p != null) p.mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } finally {
            try { out.close(); } catch (IOException ignored) { }
        }
    }

    public static String ext(String name) {
        int d = name.lastIndexOf('.');
        return d < 0 ? "" : name.substring(d + 1).toLowerCase();
    }

    public static boolean isText(String name) {
        String e = ext(name);
        return e.equals("java") || e.equals("xml") || e.equals("txt") || e.equals("md") || e.equals("json")
                || e.equals("gradle") || e.equals("properties") || e.equals("html") || e.equals("css") || e.equals("js")
                || e.equals("kt") || e.equals("smali") || e.equals("yml") || e.equals("yaml") || e.equals("pro")
                || e.equals("cfg") || e.equals("ini") || e.equals("csv") || e.equals("log") || e.equals("gitignore")
                || e.equals("sh") || e.equals("bat") || e.equals("c") || e.equals("h") || e.equals("cpp")
                || e.length() == 0;
    }
}
