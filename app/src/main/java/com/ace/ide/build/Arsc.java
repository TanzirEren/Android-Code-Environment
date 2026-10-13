package com.ace.ide.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** resources.arsc writer for a single application package. */
final class Arsc {

    static final class EntryDef {
        String name;
        final Map<ResConfig, Res> byCfg = new TreeMap<ResConfig, Res>();
    }

    static final class TypeDef {
        String name;
        final List<EntryDef> entries = new ArrayList<EntryDef>();   // index = entry id
    }

    private Arsc() {}

    static byte[] write(String pkgName, int pkgId, List<TypeDef> types) {
        StringPool global = new StringPool(true);
        StringPool typePool = new StringPool(true);
        StringPool keyPool = new StringPool(true);
        for (TypeDef t : types) typePool.addRaw(t.name);
        for (TypeDef t : types) for (EntryDef e : t.entries) keyPool.add(e.name);

        byte[] typeBytes = typePool.build();
        byte[] keyBytes = keyPool.build();

        Bin pkg = new Bin();
        int headerSize = 288;
        pkg.u16(0x0200).u16(headerSize).u32(0).u32(pkgId);
        for (int i = 0; i < 128; i++) pkg.u16(i < pkgName.length() && i < 127 ? pkgName.charAt(i) : 0);
        pkg.u32(headerSize);                       // typeStrings
        pkg.u32(types.size());                     // lastPublicType
        pkg.u32(headerSize + typeBytes.length);    // keyStrings
        pkg.u32(0);                                // lastPublicKey
        pkg.u32(0);                                // typeIdOffset
        pkg.bytes(typeBytes);
        pkg.bytes(keyBytes);

        for (int ti = 0; ti < types.size(); ti++) {
            TypeDef t = types.get(ti);
            int typeId = ti + 1;
            int n = t.entries.size();

            // all configurations used by this type
            TreeMap<ResConfig, Boolean> cfgs = new TreeMap<ResConfig, Boolean>();
            for (EntryDef e : t.entries) for (ResConfig c : e.byCfg.keySet()) cfgs.put(c, Boolean.TRUE);

            // type spec
            pkg.u16(0x0202).u16(16).u32(16 + 4 * n).u8(typeId).u8(0).u16(0).u32(n);
            for (EntryDef e : t.entries) {
                int flags = 0;
                for (ResConfig c : e.byCfg.keySet()) flags |= c.specFlags();
                pkg.u32(flags);
            }

            for (ResConfig cfg : cfgs.keySet()) {
                Bin entries = new Bin();
                int[] offs = new int[n];
                for (int i = 0; i < n; i++) {
                    EntryDef e = t.entries.get(i);
                    Res r = e.byCfg.get(cfg);
                    if (r == null) { offs[i] = -1; continue; }
                    offs[i] = entries.size();
                    int key = keyPool.add(e.name);
                    if (r.complex) {
                        entries.u16(16).u16(1).u32(key).u32(r.parent).u32(r.items.size());
                        for (Res.Item it : r.items) {
                            entries.u32(it.nameId);
                            value(entries, it.v, global);
                        }
                    } else {
                        entries.u16(8).u16(0).u32(key);
                        value(entries, r.val, global);
                    }
                }
                int hs = 20 + 52;
                int entriesStart = hs + 4 * n;
                pkg.u16(0x0201).u16(hs).u32(entriesStart + entries.size());
                pkg.u8(typeId).u8(0).u16(0).u32(n).u32(entriesStart);
                pkg.bytes(cfg.toBytes());
                for (int i = 0; i < n; i++) pkg.u32(offs[i]);
                pkg.bytes(entries.toByteArray());
            }
        }
        pkg.patch32(4, pkg.size());

        byte[] gp = global.build();
        byte[] pk = pkg.toByteArray();
        Bin out = new Bin();
        out.u16(0x0002).u16(12).u32(12 + gp.length + pk.length).u32(1);
        out.bytes(gp);
        out.bytes(pk);
        return out.toByteArray();
    }

    private static void value(Bin b, Val v, StringPool global) {
        int data = v.data;
        if (v.type == Val.STRING) data = global.add(v.str);
        b.u16(8).u8(0).u8(v.type).u32(data);
    }

    static List<String> unusedSort(List<String> l) { Collections.sort(l); return l; }
}
