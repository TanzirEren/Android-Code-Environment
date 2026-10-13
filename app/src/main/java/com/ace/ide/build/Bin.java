package com.ace.ide.build;

import java.io.ByteArrayOutputStream;

/** Tiny little-endian byte writer used by the binary XML / resources.arsc writers. */
final class Bin {
    private byte[] buf = new byte[256];
    private int len = 0;

    int size() { return len; }

    private void ensure(int n) {
        if (len + n > buf.length) {
            byte[] nb = new byte[Math.max(buf.length * 2, len + n)];
            System.arraycopy(buf, 0, nb, 0, len);
            buf = nb;
        }
    }

    Bin u8(int v) { ensure(1); buf[len++] = (byte) v; return this; }

    Bin u16(int v) { ensure(2); buf[len++] = (byte) v; buf[len++] = (byte) (v >>> 8); return this; }

    Bin u32(int v) {
        ensure(4);
        buf[len++] = (byte) v; buf[len++] = (byte) (v >>> 8);
        buf[len++] = (byte) (v >>> 16); buf[len++] = (byte) (v >>> 24);
        return this;
    }

    Bin bytes(byte[] b) { return bytes(b, 0, b.length); }

    Bin bytes(byte[] b, int off, int n) {
        ensure(n);
        System.arraycopy(b, off, buf, len, n);
        len += n;
        return this;
    }

    Bin zeros(int n) { ensure(n); for (int i = 0; i < n; i++) buf[len++] = 0; return this; }

    Bin pad4() { while ((len & 3) != 0) u8(0); return this; }

    /** overwrite a u32 that was written earlier */
    void patch32(int at, int v) {
        buf[at] = (byte) v; buf[at + 1] = (byte) (v >>> 8);
        buf[at + 2] = (byte) (v >>> 16); buf[at + 3] = (byte) (v >>> 24);
    }

    void patch16(int at, int v) { buf[at] = (byte) v; buf[at + 1] = (byte) (v >>> 8); }

    byte[] toByteArray() {
        byte[] r = new byte[len];
        System.arraycopy(buf, 0, r, 0, len);
        return r;
    }

    static ByteArrayOutputStream unused() { return null; }
}
