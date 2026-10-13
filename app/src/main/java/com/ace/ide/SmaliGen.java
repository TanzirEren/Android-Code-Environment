package com.ace.ide;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Self-contained DEX parser + smali renderer.
 * Pure Java — no dexlib2, no guava. Crash-proof by design.
 */
public final class SmaliGen {

    private SmaliGen() {}

    // ================= data model =================

    public static class DexFile {
        public byte[] d;
        public String version = "?";
        int stringIdsSize, stringIdsOff;
        int typeIdsSize, typeIdsOff;
        int protoIdsSize, protoIdsOff;
        int fieldIdsSize, fieldIdsOff;
        int methodIdsSize, methodIdsOff;
        int classDefsSize, classDefsOff;
        public final List<DexClass> classes = new ArrayList<DexClass>();
        public final List<String> strings = new ArrayList<String>();
    }

    public static class DexField {
        public int access;
        public String name;
        public String type;
    }

    public static class DexMethod {
        public int access;
        public String name;
        public String params;
        public String ret;
        public int codeOff;
        public int registers;
        public int ins;          // incoming argument registers (p0…)
        public int[] insns; // null = no code (abstract/native)
    }

    public static class DexClass {
        public int access;
        public String type;
        public String superType;
        public List<String> interfaces = new ArrayList<String>();
        public List<DexField> staticFields = new ArrayList<DexField>();
        public List<DexField> instanceFields = new ArrayList<DexField>();
        public List<DexMethod> directMethods = new ArrayList<DexMethod>();
        public List<DexMethod> virtualMethods = new ArrayList<DexMethod>();

        public String pretty() { return SmaliGen.pretty(type); }
        public int methodCount() { return directMethods.size() + virtualMethods.size(); }
        public int fieldCount() { return staticFields.size() + instanceFields.size(); }
    }

    // ================= primitives =================

    private static int u2(byte[] d, int p) {
        if (p < 0 || p + 1 >= d.length) return 0;
        return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8);
    }

    private static int u4(byte[] d, int p) {
        if (p < 0 || p + 3 >= d.length) return 0;
        return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8)
                | ((d[p + 2] & 0xFF) << 16) | ((d[p + 3] & 0xFF) << 24);
    }

    private static int uleb(byte[] d, int[] pos) {
        int r = 0, sh = 0, p = pos[0];
        while (p < d.length) {
            int b = d[p++] & 0xFF;
            r |= (b & 0x7F) << sh;
            if ((b & 0x80) == 0) break;
            sh += 7;
            if (sh > 35) break;
        }
        pos[0] = p;
        return r;
    }

    // ================= strings / types =================

    private static String getString(DexFile f, int idx) {
        if (idx < 0 || idx >= f.stringIdsSize) return "?";
        int off = u4(f.d, f.stringIdsOff + idx * 4);
        if (off < 0 || off >= f.d.length) return "?";
        int[] pos = { off };
        uleb(f.d, pos); // utf16 size
        StringBuilder sb = new StringBuilder();
        int p = pos[0];
        int end = f.d.length;
        while (p < end) {
            int b = f.d[p++] & 0xFF;
            if (b == 0) break;
            if (b < 0x80) {
                sb.append((char) b);
            } else if ((b & 0xE0) == 0xC0 && p < end) {
                int b2 = f.d[p++] & 0xFF;
                sb.append((char) (((b & 0x1F) << 6) | (b2 & 0x3F)));
            } else if ((b & 0xF0) == 0xE0 && p + 1 < end) {
                int b2 = f.d[p++] & 0xFF;
                int b3 = f.d[p++] & 0xFF;
                sb.append((char) (((b & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F)));
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String getType(DexFile f, int idx) {
        if (idx < 0 || idx >= f.typeIdsSize) return "?";
        return getString(f, u4(f.d, f.typeIdsOff + idx * 4));
    }

    private static String paramsOf(DexFile f, int protoIdx) {
        if (protoIdx < 0 || protoIdx >= f.protoIdsSize) return "";
        int paramsOff = u4(f.d, f.protoIdsOff + protoIdx * 12 + 8);
        if (paramsOff == 0 || paramsOff + 4 > f.d.length) return "";
        int n = u4(f.d, paramsOff);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(getType(f, u2(f.d, paramsOff + 4 + i * 2)));
        }
        return sb.toString();
    }

    private static String retOf(DexFile f, int protoIdx) {
        if (protoIdx < 0 || protoIdx >= f.protoIdsSize) return "?";
        return getType(f, u4(f.d, f.protoIdsOff + protoIdx * 12 + 4));
    }

    private static String methodRef(DexFile f, int idx) {
        if (idx < 0 || idx >= f.methodIdsSize) return "?->?()?";
        int base = f.methodIdsOff + idx * 8;
        String cls = getType(f, u2(f.d, base));
        int proto = u2(f.d, base + 2);
        String name = getString(f, u4(f.d, base + 4));
        return cls + "->" + name + "(" + paramsOf(f, proto) + ")" + retOf(f, proto);
    }

    private static String fieldRef(DexFile f, int idx) {
        if (idx < 0 || idx >= f.fieldIdsSize) return "?->?:?";
        int base = f.fieldIdsOff + idx * 8;
        String cls = getType(f, u2(f.d, base));
        String typ = getType(f, u2(f.d, base + 2));
        String name = getString(f, u4(f.d, base + 4));
        return cls + "->" + name + ":" + typ;
    }

    // ================= parsing =================

    public static DexFile open(File file) throws Exception {
        byte[] d = readAll(file);
        if (d.length < 0x70 || d[0] != 'd' || d[1] != 'e' || d[2] != 'x') {
            throw new IllegalArgumentException("Not a valid DEX file");
        }
        DexFile f = new DexFile();
        f.d = d;
        f.version = new String(d, 4, 3);
        f.stringIdsSize = u4(d, 0x38); f.stringIdsOff = u4(d, 0x3C);
        f.typeIdsSize = u4(d, 0x40);   f.typeIdsOff = u4(d, 0x44);
        f.protoIdsSize = u4(d, 0x48);  f.protoIdsOff = u4(d, 0x4C);
        f.fieldIdsSize = u4(d, 0x50);  f.fieldIdsOff = u4(d, 0x54);
        f.methodIdsSize = u4(d, 0x58); f.methodIdsOff = u4(d, 0x5C);
        f.classDefsSize = u4(d, 0x60); f.classDefsOff = u4(d, 0x64);

        TreeSet<String> set = new TreeSet<String>();
        for (int i = 0; i < f.stringIdsSize; i++) {
            try { set.add(getString(f, i)); } catch (Throwable ignored) { }
        }
        f.strings.addAll(set);

        for (int i = 0; i < f.classDefsSize; i++) {
            try { f.classes.add(parseClass(f, i)); } catch (Throwable ignored) { }
        }
        Collections.sort(f.classes, (a, b) -> a.type.compareTo(b.type));
        return f;
    }

    private static byte[] readAll(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            in.close();
        }
    }

    private static DexClass parseClass(DexFile f, int i) {
        int base = f.classDefsOff + i * 32;
        DexClass c = new DexClass();
        c.access = u4(f.d, base + 4);
        c.type = getType(f, u4(f.d, base));
        int sup = u4(f.d, base + 8);
        c.superType = (sup == 0xFFFFFFFF) ? null : getType(f, sup);

        int ifOff = u4(f.d, base + 12);
        if (ifOff != 0 && ifOff + 4 <= f.d.length) {
            int n = u4(f.d, ifOff);
            for (int k = 0; k < n; k++) {
                c.interfaces.add(getType(f, u2(f.d, ifOff + 4 + k * 2)));
            }
        }

        int cdo = u4(f.d, base + 24);
        if (cdo != 0 && cdo < f.d.length) {
            int[] pos = { cdo };
            int sf = uleb(f.d, pos);
            int inf = uleb(f.d, pos);
            int dm = uleb(f.d, pos);
            int vm = uleb(f.d, pos);

            int idx = 0, acc;
            for (int k = 0; k < sf; k++) {
                idx += uleb(f.d, pos);
                acc = uleb(f.d, pos);
                DexField fl = new DexField();
                fl.access = acc;
                fl.name = getString(f, u4(f.d, f.fieldIdsOff + idx * 8 + 4));
                fl.type = getType(f, u2(f.d, f.fieldIdsOff + idx * 8 + 2));
                c.staticFields.add(fl);
            }
            idx = 0;
            for (int k = 0; k < inf; k++) {
                idx += uleb(f.d, pos);
                acc = uleb(f.d, pos);
                DexField fl = new DexField();
                fl.access = acc;
                fl.name = getString(f, u4(f.d, f.fieldIdsOff + idx * 8 + 4));
                fl.type = getType(f, u2(f.d, f.fieldIdsOff + idx * 8 + 2));
                c.instanceFields.add(fl);
            }
            idx = 0;
            for (int k = 0; k < dm; k++) {
                idx += uleb(f.d, pos);
                acc = uleb(f.d, pos);
                int code = uleb(f.d, pos);
                c.directMethods.add(makeMethod(f, idx, acc, code));
            }
            idx = 0;
            for (int k = 0; k < vm; k++) {
                idx += uleb(f.d, pos);
                acc = uleb(f.d, pos);
                int code = uleb(f.d, pos);
                c.virtualMethods.add(makeMethod(f, idx, acc, code));
            }
        }
        return c;
    }

    private static DexMethod makeMethod(DexFile f, int idx, int acc, int codeOff) {
        DexMethod m = new DexMethod();
        m.access = acc;
        m.codeOff = codeOff;
        int base = f.methodIdsOff + idx * 8;
        m.name = getString(f, u4(f.d, base + 4));
        int proto = u2(f.d, base + 2);
        m.params = paramsOf(f, proto);
        m.ret = retOf(f, proto);

        if (codeOff != 0 && codeOff + 16 <= f.d.length) {
            m.registers = u2(f.d, codeOff);
            m.ins = u2(f.d, codeOff + 2);
            int insnsSize = u4(f.d, codeOff + 12);
            if (insnsSize > 0 && codeOff + 16 + insnsSize * 2 <= f.d.length) {
                m.insns = new int[insnsSize];
                for (int k = 0; k < insnsSize; k++) {
                    m.insns[k] = u2(f.d, codeOff + 16 + k * 2);
                }
            }
        }
        return m;
    }

    // ================= public helpers =================

    public static List<String> classNames(DexFile f) {
        List<String> out = new ArrayList<String>();
        for (DexClass c : f.classes) out.add(pretty(c.type));
        return out;
    }

    public static List<String> stringList(DexFile f) {
        return f.strings;
    }

    public static DexClass findClass(DexFile f, String prettyName) {
        String raw = "L" + prettyName.replace('.', '/') + ";";
        for (DexClass c : f.classes) {
            if (c.type.equals(raw)) return c;
        }
        return null;
    }

    /** "class", "interface", "enum", "annotation" or "abstract class" */
    public static String kind(DexClass c) {
        if ((c.access & 0x2000) != 0) return "annotation";
        if ((c.access & 0x200) != 0) return "interface";
        if ((c.access & 0x4000) != 0) return "enum";
        if ((c.access & 0x400) != 0) return "abstract class";
        return "class";
    }

    /** every method as "pkg.Class->name(params)ret" for the Methods tab */
    public static List<String> methodSignatures(DexFile f) {
        List<String> out = new ArrayList<String>();
        for (DexClass c : f.classes) {
            String cn = pretty(c.type);
            for (DexMethod m : c.directMethods) out.add(cn + "->" + m.name + "(" + m.params + ")" + m.ret);
            for (DexMethod m : c.virtualMethods) out.add(cn + "->" + m.name + "(" + m.params + ")" + m.ret);
        }
        return out;
    }

    public static String pretty(String t) {
        if (t != null && t.length() > 2 && t.charAt(0) == 'L'
                && t.charAt(t.length() - 1) == ';') {
            return t.substring(1, t.length() - 1).replace('/', '.');
        }
        return t == null ? "?" : t;
    }

    // ================= access flags =================

    private static void add(StringBuilder b, int flags, int bit, String s) {
        if ((flags & bit) != 0) b.append(s).append(' ');
    }

    private static String acc(int flags, int kind) {
        // kind: 0=class 1=field 2=method
        StringBuilder b = new StringBuilder();
        add(b, flags, 0x1, "public");
        add(b, flags, 0x2, "private");
        add(b, flags, 0x4, "protected");
        add(b, flags, 0x8, "static");
        add(b, flags, 0x10, "final");
        if (kind == 2) {
            add(b, flags, 0x20, "synchronized");
            add(b, flags, 0x40, "bridge");
            add(b, flags, 0x80, "varargs");
            add(b, flags, 0x100, "native");
            add(b, flags, 0x400, "abstract");
            add(b, flags, 0x800, "strictfp");
            add(b, flags, 0x1000, "synthetic");
            add(b, flags, 0x10000, "constructor");
        } else if (kind == 1) {
            add(b, flags, 0x40, "volatile");
            add(b, flags, 0x80, "transient");
            add(b, flags, 0x1000, "synthetic");
            add(b, flags, 0x4000, "enum");
        } else {
            add(b, flags, 0x200, "interface");
            add(b, flags, 0x400, "abstract");
            add(b, flags, 0x1000, "synthetic");
            add(b, flags, 0x2000, "annotation");
            add(b, flags, 0x4000, "enum");
        }
        return b.toString();
    }

    // ================= smali rendering =================

    public static String renderClass(DexClass c) {
        StringBuilder b = new StringBuilder();
        b.append(".class ").append(acc(c.access, 0)).append(c.type).append('\n');
        if (c.superType != null) b.append(".super ").append(c.superType).append('\n');
        for (String i : c.interfaces) b.append(".implements ").append(i).append('\n');
        b.append('\n');

        if (!c.staticFields.isEmpty()) {
            b.append("# static fields\n");
            for (DexField fl : c.staticFields) {
                b.append(".field ").append(acc(fl.access, 1))
                        .append(fl.name).append(':').append(fl.type).append('\n');
            }
            b.append('\n');
        }
        if (!c.instanceFields.isEmpty()) {
            b.append("# instance fields\n");
            for (DexField fl : c.instanceFields) {
                b.append(".field ").append(acc(fl.access, 1))
                        .append(fl.name).append(':').append(fl.type).append('\n');
            }
            b.append('\n');
        }
        if (!c.directMethods.isEmpty()) {
            b.append("# direct methods\n");
            for (DexMethod m : c.directMethods) b.append(renderMethod(m));
        }
        if (!c.virtualMethods.isEmpty()) {
            b.append("# virtual methods\n");
            for (DexMethod m : c.virtualMethods) b.append(renderMethod(m));
        }
        return b.toString();
    }

    private static String renderMethod(DexMethod m) {
        StringBuilder b = new StringBuilder();
        b.append(".method ").append(acc(m.access, 2)).append(m.name)
                .append('(').append(m.params).append(')').append(m.ret).append('\n');
        if (m.insns == null || m.insns.length == 0) {
            b.append(".end method\n\n");
            return b.toString();
        }
        b.append("    .registers ").append(m.registers).append('\n');

        final int[] ins = m.insns;
        regTotal.set(m.registers);
        regIns.set(m.ins);

        // ---- pass 1: instruction boundaries, branch targets, payload owners ----
        java.util.TreeSet<Integer> labels = new java.util.TreeSet<Integer>();
        java.util.HashMap<Integer, Integer> payloadOwner = new java.util.HashMap<Integer, Integer>();
        List<int[]> map = new ArrayList<int[]>();           // {offset, width}
        int p = 0;
        while (p < ins.length) {
            int w;
            try { w = insnWidth(ins, p); } catch (Throwable t) { w = 0; }
            if (w <= 0 || p + w > ins.length) break;
            map.add(new int[]{p, w});
            int op = ins[p] & 0xFF;
            if (op == 0x26 || op == 0x2B || op == 0x2C) {
                int po = p + s32(ins[p + 1], ins[p + 2]);
                labels.add(po);
                payloadOwner.put(po, p);
            } else {
                int tg = branchTarget(ins, p);
                if (tg != Integer.MIN_VALUE) labels.add(tg);
            }
            p += w;
        }
        for (int[] e : map) {                                // switch case targets
            int off = e[0];
            if ((ins[off] & 0xFF) != 0 || e[1] <= 1) continue;
            Integer owner = payloadOwner.get(off);
            if (owner == null) continue;
            int hi = (ins[off] >> 8) & 0xFF;
            try {
                if (hi == 0x01) {
                    int size = ins[off + 1];
                    for (int i = 0; i < size; i++)
                        labels.add(owner + s32(ins[off + 4 + i * 2], ins[off + 5 + i * 2]));
                } else if (hi == 0x02) {
                    int size = ins[off + 1];
                    for (int i = 0; i < size; i++)
                        labels.add(owner + s32(ins[off + 2 + size * 2 + i * 2], ins[off + 3 + size * 2 + i * 2]));
                }
            } catch (Throwable ignored) { }
        }

        // ---- pass 2: emit ----
        for (int[] e : map) {
            int off = e[0], w = e[1];
            try {
                if (labels.contains(off)) b.append("    ").append(lbl(off)).append('\n');
                int u = ins[off];
                if ((u & 0xFF) == 0 && w > 1) {
                    appendPayload(b, ins, off, w, payloadOwner.get(off));
                } else {
                    String line = decode(ins, off, w);
                    if (line != null) b.append("    ").append(line).append('\n');
                }
            } catch (Throwable t) {
                b.append("    # decode error at ").append(hex(off)).append('\n');
            }
        }
        if (!map.isEmpty()) {
            int[] last = map.get(map.size() - 1);
            if (last[0] + last[1] < ins.length) b.append("    # truncated / undecodable tail\n");
        } else {
            b.append("    # undecodable code\n");
        }
        b.append(".end method\n\n");
        return b.toString();
    }

    private static void appendPayload(StringBuilder b, int[] ins, int off, int w, Integer owner) {
        int hi = (ins[off] >> 8) & 0xFF;
        int base = owner == null ? 0 : owner;
        if (hi == 0x01) {
            int size = ins[off + 1];
            b.append("    .packed-switch ").append(lit(s32(ins[off + 2], ins[off + 3]))).append('\n');
            for (int i = 0; i < size; i++)
                b.append("        ").append(lbl(base + s32(ins[off + 4 + i * 2], ins[off + 5 + i * 2]))).append('\n');
            b.append("    .end packed-switch\n");
        } else if (hi == 0x02) {
            int size = ins[off + 1];
            b.append("    .sparse-switch\n");
            for (int i = 0; i < size; i++) {
                int key = s32(ins[off + 2 + i * 2], ins[off + 3 + i * 2]);
                int tgt = s32(ins[off + 2 + size * 2 + i * 2], ins[off + 3 + size * 2 + i * 2]);
                b.append("        ").append(lit(key)).append(" -> ").append(lbl(base + tgt)).append('\n');
            }
            b.append("    .end sparse-switch\n");
        } else if (hi == 0x03) {
            int ew = ins[off + 1];
            int size = (ins[off + 2] & 0xFFFF) | (ins[off + 3] << 16);
            b.append("    .array-data ").append(ew).append('\n');
            int bytePos = 0;
            for (int i = 0; i < size; i++) {
                long v = 0;
                for (int k = 0; k < ew; k++) {
                    int unit = ins[off + 4 + (bytePos >> 1)];
                    int by = (bytePos & 1) == 0 ? (unit & 0xFF) : ((unit >> 8) & 0xFF);
                    v |= ((long) by) << (8 * k);
                    bytePos++;
                }
                if (ew == 1) v = (byte) v; else if (ew == 2) v = (short) v; else if (ew == 4) v = (int) v;
                b.append("        ").append(lit(v)).append(ew == 1 ? "t" : ew == 2 ? "s" : ew == 8 ? "L" : "").append('\n');
            }
            b.append("    .end array-data\n");
        } else {
            b.append("    # payload (").append(w).append(" units)\n");
        }
    }

    // ================= instruction engine =================

    private static final String[] OPNAMES = new String[256];
    private static final byte[] WTABLE = new byte[256];

    static {
        String[] head = {
                "nop", "move", "move/from16", "move/16", "move-wide", "move-wide/from16",
                "move-wide/16", "move-object", "move-object/from16", "move-object/16",
                "move-result", "move-result-wide", "move-result-object", "move-exception",
                "return-void", "return", "return-wide", "return-object",
                "const/4", "const/16", "const", "const/high16",
                "const-wide/16", "const-wide/32", "const-wide", "const-wide/high16",
                "const-string", "const-string/jumbo", "const-class",
                "monitor-enter", "monitor-exit", "check-cast", "instance-of",
                "array-length", "new-instance", "new-array",
                "filled-new-array", "filled-new-array/range", "fill-array-data",
                "throw", "goto", "goto/16", "goto/32",
                "packed-switch", "sparse-switch",
                "cmpl-float", "cmpg-float", "cmpl-double", "cmpg-double", "cmp-long",
                "if-eq", "if-ne", "if-lt", "if-ge", "if-gt", "if-le",
                "if-eqz", "if-nez", "if-ltz", "if-gez", "if-gtz", "if-lez"
        };
        for (int i = 0; i < head.length; i++) OPNAMES[i] = head[i]; // 0x00..0x3D

        String[] arr = {"aget", "aget-wide", "aget-object", "aget-boolean", "aget-byte",
                "aget-char", "aget-short", "aput", "aput-wide", "aput-object",
                "aput-boolean", "aput-byte", "aput-char", "aput-short"};
        for (int i = 0; i < arr.length; i++) OPNAMES[0x44 + i] = arr[i];

        String[] ifld = {"iget", "iget-wide", "iget-object", "iget-boolean", "iget-byte",
                "iget-char", "iget-short", "iput", "iput-wide", "iput-object",
                "iput-boolean", "iput-byte", "iput-char", "iput-short"};
        for (int i = 0; i < ifld.length; i++) OPNAMES[0x52 + i] = ifld[i];

        String[] sfld = {"sget", "sget-wide", "sget-object", "sget-boolean", "sget-byte",
                "sget-char", "sget-short", "sput", "sput-wide", "sput-object",
                "sput-boolean", "sput-byte", "sput-char", "sput-short"};
        for (int i = 0; i < sfld.length; i++) OPNAMES[0x60 + i] = sfld[i];

        String[] inv = {"invoke-virtual", "invoke-super", "invoke-direct",
                "invoke-static", "invoke-interface"};
        for (int i = 0; i < inv.length; i++) OPNAMES[0x6E + i] = inv[i];

        String[] invr = {"invoke-virtual/range", "invoke-super/range",
                "invoke-direct/range", "invoke-static/range", "invoke-interface/range"};
        for (int i = 0; i < invr.length; i++) OPNAMES[0x74 + i] = invr[i];

        String[] unop = {"neg-int", "not-int", "neg-long", "not-long", "neg-float",
                "neg-double", "int-to-long", "int-to-float", "int-to-double",
                "long-to-int", "long-to-float", "long-to-double",
                "float-to-int", "float-to-long", "float-to-double",
                "double-to-int", "double-to-long", "double-to-float",
                "int-to-byte", "int-to-char", "int-to-short"};
        for (int i = 0; i < unop.length; i++) OPNAMES[0x7B + i] = unop[i];

        String[] binop = {"add-int", "sub-int", "mul-int", "div-int", "rem-int",
                "and-int", "or-int", "xor-int", "shl-int", "shr-int", "ushr-int",
                "add-long", "sub-long", "mul-long", "div-long", "rem-long",
                "and-long", "or-long", "xor-long", "shl-long", "shr-long", "ushr-long",
                "add-float", "sub-float", "mul-float", "div-float", "rem-float",
                "add-double", "sub-double", "mul-double", "div-double", "rem-double"};
        for (int i = 0; i < binop.length; i++) OPNAMES[0x90 + i] = binop[i];
        for (int i = 0; i < binop.length; i++) OPNAMES[0xB0 + i] = binop[i] + "/2addr";

        String[] lit16 = {"add-int/lit16", "rsub-int", "mul-int/lit16", "div-int/lit16",
                "rem-int/lit16", "and-int/lit16", "or-int/lit16", "xor-int/lit16"};
        for (int i = 0; i < lit16.length; i++) OPNAMES[0xD0 + i] = lit16[i];

        String[] lit8 = {"add-int/lit8", "rsub-int/lit8", "mul-int/lit8", "div-int/lit8",
                "rem-int/lit8", "and-int/lit8", "or-int/lit8", "xor-int/lit8",
                "shl-int/lit8", "shr-int/lit8", "ushr-int/lit8"};
        for (int i = 0; i < lit8.length; i++) OPNAMES[0xD8 + i] = lit8[i];

        String[] tail = {"invoke-polymorphic", "invoke-polymorphic/range",
                "invoke-custom", "invoke-custom/range",
                "const-method-handle", "const-method-type"};
        for (int i = 0; i < tail.length; i++) OPNAMES[0xFA + i] = tail[i];

        // ---- widths (in 16-bit code units) ----
        java.util.Arrays.fill(WTABLE, (byte) 1);
        w(0x02, 2); w(0x05, 2); w(0x08, 2);
        w(0x03, 3); w(0x06, 3); w(0x09, 3);
        w(0x13, 2); w(0x15, 2); w(0x16, 2); w(0x19, 2);
        w(0x14, 3); w(0x17, 3);
        w(0x18, 5);
        w(0x1A, 2); w(0x1B, 3); w(0x1C, 2); w(0x1F, 2); w(0x20, 2);
        w(0x22, 2); w(0x23, 2);
        w(0x24, 3); w(0x25, 3); w(0x26, 3);
        w(0x29, 2); w(0x2A, 3); w(0x2B, 3); w(0x2C, 3);
        for (int i = 0x2D; i <= 0x31; i++) w(i, 2);   // cmpl/cmpg/cmp-long (23x)
        for (int i = 0x32; i <= 0x37; i++) w(i, 2);   // if-eq … if-le   (22t)
        for (int i = 0x38; i <= 0x3D; i++) w(i, 2);   // if-eqz … if-lez (21t)
        for (int i = 0x44; i <= 0x51; i++) w(i, 2);
        for (int i = 0x52; i <= 0x5F; i++) w(i, 2);
        for (int i = 0x60; i <= 0x6D; i++) w(i, 2);
        for (int i = 0x6E; i <= 0x72; i++) w(i, 3);
        for (int i = 0x74; i <= 0x78; i++) w(i, 3);
        for (int i = 0x90; i <= 0xAF; i++) w(i, 2);
        for (int i = 0xD0; i <= 0xD7; i++) w(i, 2);
        for (int i = 0xD8; i <= 0xE2; i++) w(i, 2);
        w(0xFA, 4); w(0xFB, 4); w(0xFC, 3); w(0xFD, 3); w(0xFE, 2); w(0xFF, 2);
    }

    private static void w(int op, int units) { WTABLE[op] = (byte) units; }

    private static int insnWidth(int[] ins, int p) {
        int u = ins[p];
        int op = u & 0xFF;
        if (op == 0x00) {
            int hi = (u >> 8) & 0xFF;
            if (hi == 0x01 && p + 1 < ins.length) { // packed-switch-payload
                int size = ins[p + 1];
                return 4 + 2 * size;
            }
            if (hi == 0x02 && p + 1 < ins.length) { // sparse-switch-payload
                int size = ins[p + 1];
                return 2 + 4 * size;
            }
            if (hi == 0x03 && p + 3 < ins.length) { // fill-array-data-payload
                int ew = ins[p + 1];
                int size = ins[p + 2] | (ins[p + 3] << 16);
                return 4 + (size * ew + 1) / 2;
            }
            return 1; // nop
        }
        return WTABLE[op];
    }

    private static final ThreadLocal<Integer> regTotal = new ThreadLocal<Integer>();
    private static final ThreadLocal<Integer> regIns = new ThreadLocal<Integer>();

    /** v-register or, for incoming arguments, the smali p-register */
    private static String reg(int r) {
        Integer total = regTotal.get(), ins = regIns.get();
        if (total != null && ins != null && ins > 0 && r >= total - ins) return "p" + (r - (total - ins));
        return "v" + r;
    }

    private static String lit(long v) {
        return v < 0 ? "-0x" + Long.toHexString(-v) : "0x" + Long.toHexString(v);
    }

    private static String lbl(int off) { return ":label_" + Integer.toHexString(off); }

    /** absolute code-unit target of a branch instruction, or MIN_VALUE if it is not a plain branch */
    private static int branchTarget(int[] ins, int p) {
        int u = ins[p];
        int op = u & 0xFF;
        if (op == 0x28) return p + s8((u >> 8) & 0xFF);
        if (op == 0x29) return p + s16(ins[p + 1]);
        if (op == 0x2A) return p + s32(ins[p + 1], ins[p + 2]);
        if (op >= 0x32 && op <= 0x3D) return p + s16(ins[p + 1]);
        return Integer.MIN_VALUE;
    }

    private static int s4(int v) { v &= 0xF; return (v >= 8) ? v - 16 : v; }
    private static int s8(int v) { v &= 0xFF; return (v >= 128) ? v - 256 : v; }
    private static int s16(int v) { v &= 0xFFFF; return (v >= 0x8000) ? v - 0x10000 : v; }
    private static int s32(int lo, int hi) { return lo | (hi << 16); }
    private static String hex(int v) { return "0x" + Integer.toHexString(v); }

    /** format 35c: first unit = A|G|op  (A = count, G = 5th register), third unit = F|E|D|C */
    private static String inv35c(String ref, int firstUnit, int regsUnit) {
        int count = (firstUnit >> 12) & 0xF;
        int G = (firstUnit >> 8) & 0xF;
        int C = regsUnit & 0xF, D = (regsUnit >> 4) & 0xF;
        int E = (regsUnit >> 8) & 0xF, F = (regsUnit >> 12) & 0xF;
        int[] r = {C, D, E, F, G};
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < count && i < 5; i++) {
            if (i > 0) b.append(", ");
            b.append(reg(r[i]));
        }
        b.append("}, ").append(ref);
        return b.toString();
    }

    private static String inv3rc(String ref, int count, int start) {
        if (count <= 0) return "{}" + ", " + ref;
        if (count == 1) return "{" + reg(start) + "}, " + ref;
        return "{" + reg(start) + " .. " + reg(start + count - 1) + "}, " + ref;
    }

    /** decodes one instruction to smali text */
    private static String decode(int[] ins, int p, int width) {
        int u = ins[p];
        int op = u & 0xFF;
        if (op == 0x00) return OPNAMES[0];
        String name = OPNAMES[op] != null ? OPNAMES[op] : ("op_" + hex(op));
        int AA = (u >> 8) & 0xFF;
        int A = (u >> 8) & 0x0F;
        int B = (u >> 12) & 0x0F;
        int u2 = width >= 2 ? ins[p + 1] : 0;
        int u3 = width >= 3 ? ins[p + 2] : 0;
        int u4 = width >= 4 ? ins[p + 3] : 0;

        // 12x: move / unary / binop-2addr / array-length
        if (op == 0x01 || op == 0x04 || op == 0x07 || op == 0x21
                || (op >= 0x7B && op <= 0x8F) || (op >= 0xB0 && op <= 0xCF)) {
            return name + " " + reg(A) + ", " + reg(B);
        }
        if (op == 0x02 || op == 0x05 || op == 0x08) return name + " " + reg(AA) + ", " + reg(u2);
        if (op == 0x03 || op == 0x06 || op == 0x09)
            return name + " " + reg(u2) + ", " + reg(u3);   // 32x: vAAAA, vBBBB
        // 11x: move-result / return / monitor / throw
        if ((op >= 0x0A && op <= 0x0D) || (op >= 0x0F && op <= 0x11)
                || op == 0x1D || op == 0x1E || op == 0x27) {
            return name + " " + reg(AA);
        }
        if (op == 0x0E) return name;
        // constants
        if (op == 0x12) return name + " " + reg(A) + ", " + lit(s4(B));
        if (op == 0x13 || op == 0x16) return name + " " + reg(AA) + ", " + lit(s16(u2));
        if (op == 0x14 || op == 0x17) return name + " " + reg(AA) + ", " + lit(s32(u2, u3));
        if (op == 0x15) return name + " " + reg(AA) + ", 0x" + Integer.toHexString((u2 & 0xFFFF) << 16);
        if (op == 0x19) return name + " " + reg(AA) + ", 0x" + Long.toHexString((u2 & 0xFFFFL) << 48) + "L";
        if (op == 0x18) {
            long val = (u2 & 0xFFFFL) | ((u3 & 0xFFFFL) << 16)
                    | ((u4 & 0xFFFFL) << 32) | ((ins[p + 4] & 0xFFFFL) << 48);
            return name + " " + reg(AA) + ", " + lit(val) + "L";
        }
        if (op == 0x1A) return name + " " + reg(AA) + ", " + quote(smaliString(smaliStringIdx(u2)));
        if (op == 0x1B) return name + " " + reg(AA) + ", " + quote(smaliString(smaliStringIdx((u2 & 0xFFFF) | (u3 << 16))));
        if (op == 0x1C || op == 0x1F || op == 0x22)
            return name + " " + reg(AA) + ", " + smaliType(u2);
        if (op == 0x20 || op == 0x23)
            return name + " " + reg(A) + ", " + reg(B) + ", " + smaliType(u2);
        if (op == 0x24) return name + " " + inv35c(smaliType(u2), u, u3);
        if (op == 0x25) return name + " " + inv3rc(smaliType(u2), AA, u3);
        if (op >= 0x74 && op <= 0x78) return name + " " + inv3rc(smaliMethod(u2), AA, u3);
        // payload users: target label is the payload offset
        if (op == 0x26 || op == 0x2B || op == 0x2C)
            return name + " " + reg(AA) + ", " + lbl(p + s32(u2, u3));
        // branches
        if (op == 0x28 || op == 0x29 || op == 0x2A) return name + " " + lbl(branchTarget(ins, p));
        if (op >= 0x32 && op <= 0x37)
            return name + " " + reg(A) + ", " + reg(B) + ", " + lbl(branchTarget(ins, p));
        if (op >= 0x38 && op <= 0x3D)
            return name + " " + reg(AA) + ", " + lbl(branchTarget(ins, p));
        // 23x: vAA, vBB, vCC  (BB = low byte of the 2nd unit)
        if ((op >= 0x2D && op <= 0x31) || (op >= 0x44 && op <= 0x51) || (op >= 0x90 && op <= 0xAF)) {
            return name + " " + reg(AA) + ", " + reg(u2 & 0xFF) + ", " + reg((u2 >> 8) & 0xFF);
        }
        // 22c / 21c field access
        if (op >= 0x52 && op <= 0x5F)
            return name + " " + reg(A) + ", " + reg(B) + ", " + smaliField(u2);
        if (op >= 0x60 && op <= 0x6D)
            return name + " " + reg(AA) + ", " + smaliField(u2);
        if (op >= 0x6E && op <= 0x72) return name + " " + inv35c(smaliMethod(u2), u, u3);
        // literals
        if (op >= 0xD0 && op <= 0xD7)
            return name + " " + reg(A) + ", " + reg(B) + ", " + lit(s16(u2));
        if (op >= 0xD8 && op <= 0xE2)
            return name + " " + reg(AA) + ", " + reg(u2 & 0xFF) + ", " + lit(s8(u2 >> 8));
        if (op == 0xFA) return name + " " + inv35c(smaliMethod(u2), u, u3) + ", proto@" + u4;
        if (op == 0xFB) return name + " " + inv3rc(smaliMethod(u2), AA, u3) + ", proto@" + u4;
        if (op == 0xFC) return name + " " + inv35c("callsite@" + u2, u, u3);
        if (op == 0xFD) return name + " " + inv3rc("callsite@" + u2, AA, u3);
        if (op == 0xFE) return name + " " + reg(AA) + ", mh@" + u2;
        if (op == 0xFF) return name + " " + reg(AA) + ", proto@" + u2;
        return name;
    }

    // per-dex-file resolvers (static context bound to current parse)
    private static final ThreadLocal<DexFile> CUR = new ThreadLocal<DexFile>();
    private static int smaliStringIdx(int idx) { return idx; }
    private static String smaliString(int idx) { return getString(CUR.get(), idx); }
    private static String smaliType(int idx) { return getType(CUR.get(), idx); }
    private static String smaliMethod(int idx) { return methodRef(CUR.get(), idx); }
    private static String smaliField(int idx) { return fieldRef(CUR.get(), idx); }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n') b.append("\\n");
            else if (ch == '\t') b.append("\\t");
            else if (ch == '\r') b.append("\\r");
            else if (ch == '"') b.append("\\\"");
            else if (ch == '\\') b.append("\\\\");
            else b.append(ch);
        }
        return b.append('"').toString();
    }

    /** renders with the dex bound for reference resolution */
    public static String renderClass(DexFile f, DexClass c) {
        CUR.set(f);
        try {
            return renderClass(c);
        } finally {
            CUR.remove();
        }
    }
}
