package com.ace.ide.build;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Android ResStringPool writer (no style spans). */
final class StringPool {

    private final boolean utf8;
    private final List<String> strings = new ArrayList<String>();
    private final Map<String, Integer> index = new HashMap<String, Integer>();

    StringPool(boolean utf8) { this.utf8 = utf8; }

    int size() { return strings.size(); }

    /** deduplicated add */
    int add(String s) {
        Integer i = index.get(s);
        if (i != null) return i;
        int n = strings.size();
        strings.add(s);
        index.put(s, n);
        return n;
    }

    /** always appends (used for the attribute-name block that must match the resource id map) */
    int addRaw(String s) {
        int n = strings.size();
        strings.add(s);
        return n;
    }

    byte[] build() {
        int count = strings.size();
        Bin data = new Bin();
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = data.size();
            String s = strings.get(i);
            if (utf8) writeUtf8(data, s); else writeUtf16(data, s);
        }
        data.pad4();
        int headerSize = 28;
        int stringsStart = headerSize + 4 * count;
        Bin out = new Bin();
        out.u16(0x0001).u16(headerSize).u32(stringsStart + data.size());
        out.u32(count).u32(0).u32(utf8 ? 0x100 : 0).u32(count == 0 ? 0 : stringsStart).u32(0);
        for (int i = 0; i < count; i++) out.u32(offsets[i]);
        out.bytes(data.toByteArray());
        return out.toByteArray();
    }

    private static void writeUtf16(Bin d, String s) {
        int n = s.length();
        if (n > 0x7FFF) {
            d.u16(0x8000 | (n >>> 16)).u16(n & 0xFFFF);
        } else {
            d.u16(n);
        }
        for (int i = 0; i < n; i++) d.u16(s.charAt(i));
        d.u16(0);
    }

    private static void writeUtf8(Bin d, String s) {
        byte[] b;
        try { b = s.getBytes("UTF-8"); } catch (UnsupportedEncodingException e) { b = s.getBytes(); }
        int chars = s.length();
        len8(d, chars);
        len8(d, b.length);
        d.bytes(b);
        d.u8(0);
    }

    private static void len8(Bin d, int n) {
        if (n > 0x7F) {
            d.u8(0x80 | (n >>> 8));
            d.u8(n & 0xFF);
        } else {
            d.u8(n);
        }
    }
}
