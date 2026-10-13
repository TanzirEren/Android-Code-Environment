package com.ace.ide.build;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Name / id / attribute-format table of the Android framework, read from the
 * resources.arsc inside android.jar. Cached on disk because the table is large.
 */
public final class FrameworkTable {

    public static final class AttrInfo {
        public int format;
        /** symbol name -> {dataType, data} (enum values and flag bits) */
        public final LinkedHashMap<String, int[]> symbols = new LinkedHashMap<String, int[]>();
    }

    private static final int CACHE_MAGIC = 0xACE00001;

    public final HashMap<String, Integer> ids = new HashMap<String, Integer>(32768);
    public final HashMap<Integer, AttrInfo> attrs = new HashMap<Integer, AttrInfo>(2048);

    private static FrameworkTable memory;
    private static String memoryKey;

    public int id(String type, String name) {
        Integer i = ids.get(type + "/" + name);
        return i == null ? 0 : i;
    }

    public AttrInfo attr(int id) { return attrs.get(id); }

    // ------------------------------------------------------------------ load

    public static synchronized FrameworkTable load(File androidJar, File cache) throws IOException {
        String key = androidJar.getAbsolutePath() + "|" + androidJar.length() + "|" + androidJar.lastModified();
        if (memory != null && key.equals(memoryKey)) return memory;
        FrameworkTable t = null;
        if (cache != null && cache.exists()) {
            try { t = readCache(cache, androidJar); } catch (Throwable ignored) { t = null; }
        }
        if (t == null) {
            t = parseJar(androidJar);
            if (cache != null) {
                try { writeCache(t, cache, androidJar); } catch (Throwable ignored) { }
            }
        }
        memory = t;
        memoryKey = key;
        return t;
    }

    private static FrameworkTable parseJar(File jar) throws IOException {
        ZipFile zf = new ZipFile(jar);
        try {
            ZipEntry e = zf.getEntry("resources.arsc");
            if (e == null) throw new IOException("android.jar has no resources.arsc");
            InputStream in = zf.getInputStream(e);
            ByteArrayOutputStream bo = new ByteArrayOutputStream((int) Math.max(1024, e.getSize()));
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return parse(bo.toByteArray());
        } finally {
            zf.close();
        }
    }

    // ------------------------------------------------------------------ arsc parser

    private static final class R {
        final ByteBuffer b;
        R(byte[] d) { b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN); }
        int u8(int p) { return b.get(p) & 0xFF; }
        int u16(int p) { return b.getShort(p) & 0xFFFF; }
        int s32(int p) { return b.getInt(p); }
    }

    static FrameworkTable parse(byte[] data) throws IOException {
        R r = new R(data);
        if (r.u16(0) != 0x0002) throw new IOException("not a resource table");
        FrameworkTable t = new FrameworkTable();
        int total = Math.min(r.s32(4), data.length);
        int pos = r.u16(2);
        while (pos + 8 <= total) {
            int ct = r.u16(pos);
            int cs = r.s32(pos + 4);
            if (cs <= 0) break;
            if (ct == 0x0200) parsePackage(r, pos, t);
            pos += cs;
        }
        return t;
    }

    private static void parsePackage(R r, int pos, FrameworkTable t) {
        int pkgId = r.s32(pos + 8);
        int headerSize = r.u16(pos + 2);
        int size = r.s32(pos + 4);
        int typeStrings = r.s32(pos + 268);
        int keyStrings = r.s32(pos + 276);
        String[] typeNames = readPool(r, pos + typeStrings);
        String[] keys = readPool(r, pos + keyStrings);

        BitSet[] seen = new BitSet[typeNames.length + 2];
        Map<Integer, String> idNames = new HashMap<Integer, String>();
        List<int[]> pendingSyms = new ArrayList<int[]>();   // {attrId, symId, vType, vData}

        int p = pos + headerSize;
        int end = pos + size;
        while (p + 8 <= end) {
            int ct = r.u16(p);
            int cs = r.s32(p + 4);
            if (cs <= 0) break;
            if (ct == 0x0201) {
                int typeId = r.u8(p + 8);
                int flags = r.u8(p + 9);
                int entryCount = r.s32(p + 12);
                int entriesStart = r.s32(p + 16);
                int hs = r.u16(p + 2);
                if (typeId >= 1 && typeId <= typeNames.length) {
                    String tname = typeNames[typeId - 1];
                    BitSet bs = seen[typeId];
                    if (bs == null) { bs = new BitSet(); seen[typeId] = bs; }
                    int offs = p + hs;
                    boolean sparse = (flags & 1) != 0;
                    boolean off16 = (flags & 2) != 0;
                    for (int i = 0; i < entryCount; i++) {
                        int idx;
                        long off;
                        if (sparse) {
                            int v = r.s32(offs + 4 * i);
                            idx = v & 0xFFFF;
                            off = ((v >>> 16) & 0xFFFF) * 4L;
                        } else if (off16) {
                            int o = r.u16(offs + 2 * i);
                            if (o == 0xFFFF) continue;
                            idx = i;
                            off = o * 4L;
                        } else {
                            int o = r.s32(offs + 4 * i);
                            if (o == -1) continue;
                            idx = i;
                            off = o & 0xFFFFFFFFL;
                        }
                        if (bs.get(idx)) continue;
                        bs.set(idx);
                        int e = (int) (p + entriesStart + off);
                        int eflags = r.u16(e + 2);
                        int key = r.s32(e + 4);
                        if (key < 0 || key >= keys.length) continue;
                        String name = keys[key];
                        int id = (pkgId << 24) | (typeId << 16) | idx;
                        t.ids.put(tname + "/" + name, id);
                        if (tname.equals("id")) idNames.put(id, name);
                        if (tname.equals("attr") && (eflags & 1) != 0) {
                            AttrInfo ai = new AttrInfo();
                            int count = r.s32(e + 12);
                            int ip = e + 16;
                            for (int k = 0; k < count; k++, ip += 12) {
                                int nm = r.s32(ip);
                                int vt = r.u8(ip + 7);
                                int vd = r.s32(ip + 8);
                                if (nm == 0x01000000) ai.format = vd;
                                else if ((nm & 0xFFFF0000) != 0x01000000) {
                                    pendingSyms.add(new int[]{id, nm, vt, vd});
                                }
                            }
                            t.attrs.put(id, ai);
                        }
                    }
                }
            }
            p += cs;
        }
        for (int[] s : pendingSyms) {
            AttrInfo ai = t.attrs.get(s[0]);
            String nm = idNames.get(s[1]);
            if (ai != null && nm != null) ai.symbols.put(nm, new int[]{s[2], s[3]});
        }
    }

    private static String[] readPool(R r, int off) {
        int count = r.s32(off + 8);
        int flags = r.s32(off + 16);
        int start = r.s32(off + 20);
        boolean utf8 = (flags & 0x100) != 0;
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            int so = off + start + r.s32(off + 28 + 4 * i);
            if (utf8) {
                int l = r.u8(so);
                so += (l & 0x80) != 0 ? 2 : 1;
                int bl = r.u8(so);
                int bo = 1;
                if ((bl & 0x80) != 0) { bl = ((bl & 0x7F) << 8) | r.u8(so + 1); bo = 2; }
                so += bo;
                byte[] bytes = new byte[bl];
                for (int k = 0; k < bl; k++) bytes[k] = r.b.get(so + k);
                try { out[i] = new String(bytes, "UTF-8"); } catch (Exception e) { out[i] = ""; }
            } else {
                int l = r.u16(so);
                so += 2;
                if ((l & 0x8000) != 0) { l = ((l & 0x7FFF) << 16) | r.u16(so); so += 2; }
                char[] cs = new char[l];
                for (int k = 0; k < l; k++) cs[k] = (char) r.u16(so + 2 * k);
                out[i] = new String(cs);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ cache

    private static void writeCache(FrameworkTable t, File cache, File jar) throws IOException {
        File parent = cache.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(cache.getPath() + ".tmp");
        DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp), 65536));
        try {
            o.writeInt(CACHE_MAGIC);
            o.writeLong(jar.length());
            o.writeLong(jar.lastModified());
            o.writeInt(t.ids.size());
            for (Map.Entry<String, Integer> e : t.ids.entrySet()) {
                o.writeUTF(e.getKey());
                o.writeInt(e.getValue());
            }
            o.writeInt(t.attrs.size());
            for (Map.Entry<Integer, AttrInfo> e : t.attrs.entrySet()) {
                o.writeInt(e.getKey());
                o.writeInt(e.getValue().format);
                o.writeInt(e.getValue().symbols.size());
                for (Map.Entry<String, int[]> s : e.getValue().symbols.entrySet()) {
                    o.writeUTF(s.getKey());
                    o.writeInt(s.getValue()[0]);
                    o.writeInt(s.getValue()[1]);
                }
            }
        } finally {
            o.close();
        }
        if (cache.exists()) cache.delete();
        if (!tmp.renameTo(cache)) throw new IOException("cache rename failed");
    }

    private static FrameworkTable readCache(File cache, File jar) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cache), 65536));
        try {
            if (in.readInt() != CACHE_MAGIC) throw new IOException("bad cache");
            if (in.readLong() != jar.length() || in.readLong() != jar.lastModified()) throw new IOException("stale");
            FrameworkTable t = new FrameworkTable();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                String k = in.readUTF();
                t.ids.put(k, in.readInt());
            }
            int m = in.readInt();
            for (int i = 0; i < m; i++) {
                int id = in.readInt();
                AttrInfo ai = new AttrInfo();
                ai.format = in.readInt();
                int sc = in.readInt();
                for (int k = 0; k < sc; k++) {
                    String nm = in.readUTF();
                    int a = in.readInt();
                    int b = in.readInt();
                    ai.symbols.put(nm, new int[]{a, b});
                }
                t.attrs.put(id, ai);
            }
            return t;
        } finally {
            in.close();
        }
    }
}
