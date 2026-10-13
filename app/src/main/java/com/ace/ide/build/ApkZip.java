package com.ace.ide.build;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Zip writer for APKs: stored entries (resources.arsc, .so) are 4-byte aligned like zipalign does. */
final class ApkZip {

    private static final class Counting extends FilterOutputStream {
        long n;
        Counting(OutputStream o) { super(o); }
        @Override public void write(int b) throws IOException { out.write(b); n++; }
        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); n += len; }
    }

    private final Counting counting;
    private final ZipOutputStream zip;
    private final Set<String> names = new HashSet<String>();

    ApkZip(File out) throws IOException {
        counting = new Counting(new BufferedOutputStream(new FileOutputStream(out), 65536));
        zip = new ZipOutputStream(counting);
        zip.setLevel(6);
    }

    boolean has(String name) { return names.contains(name); }

    void addDeflated(String name, byte[] data) throws IOException {
        if (!names.add(name)) return;
        ZipEntry e = new ZipEntry(name);
        e.setTime(1230768000000L);
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }

    void addStored(String name, byte[] data, int align) throws IOException {
        if (!names.add(name)) return;
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(data.length);
        e.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        e.setCrc(crc.getValue());
        e.setTime(1230768000000L);
        int nameLen = name.getBytes("UTF-8").length;
        long dataStart = counting.n + 30 + nameLen;
        int pad = (int) ((align - (dataStart % align)) % align);
        if (pad > 0) {
            if (pad < 4) pad += align;
            byte[] extra = new byte[pad];
            extra[0] = 0x35; extra[1] = (byte) 0xD9;
            extra[2] = (byte) (pad - 4); extra[3] = 0;
            e.setExtra(extra);
        }
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }

    void addFile(String name, File f, boolean stored) throws IOException {
        if (names.contains(name)) return;
        byte[] data = readAll(f);
        if (stored) addStored(name, data, 4); else addDeflated(name, data);
    }

    void close() throws IOException { zip.close(); }

    static byte[] readAll(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[(int) Math.max(16, f.length())];
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            if (off == buf.length) return buf;
            byte[] r = new byte[off];
            System.arraycopy(buf, 0, r, 0, off);
            return r;
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }
}
