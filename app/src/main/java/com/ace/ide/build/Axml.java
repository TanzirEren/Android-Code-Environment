package com.ace.ide.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Android binary XML (AXML) encoder. */
final class Axml {

    /** an attribute after name/value resolution */
    static final class PAttr {
        String uri = "";
        String name;
        int resId;          // 0 = attribute has no resource id
        Val val;
        String raw;
    }

    interface Handler {
        /** @return null to drop the attribute */
        PAttr handle(XNode el, XNode.Attr a);
    }

    private final StringPool pool = new StringPool(false);
    private final Handler handler;
    private final Bin body = new Bin();
    private final Map<String, Integer> idStrings = new HashMap<String, Integer>();
    private final List<int[]> unused = new ArrayList<int[]>();

    private Axml(Handler h) { this.handler = h; }

    static byte[] encode(XNode root, Handler h) {
        Axml x = new Axml(h);
        Map<XNode, List<PAttr>> prepared = new HashMap<XNode, List<PAttr>>();
        x.prepare(root, prepared);

        // attribute names that carry a resource id go first, sorted by id
        List<PAttr> withIds = new ArrayList<PAttr>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (List<PAttr> l : prepared.values()) {
            for (PAttr a : l) {
                if (a.resId != 0 && seen.add(a.name + "#" + a.resId)) withIds.add(a);
            }
        }
        Collections.sort(withIds, new Comparator<PAttr>() {
            @Override public int compare(PAttr a, PAttr b) {
                long x1 = a.resId & 0xFFFFFFFFL, x2 = b.resId & 0xFFFFFFFFL;
                return x1 < x2 ? -1 : (x1 > x2 ? 1 : a.name.compareTo(b.name));
            }
        });
        Bin resMap = new Bin();
        for (PAttr a : withIds) {
            x.idStrings.put(a.name + "#" + a.resId, x.pool.addRaw(a.name));
            resMap.u32(a.resId);
        }

        x.node(root, prepared);

        Bin out = new Bin();
        byte[] sp = x.pool.build();
        int mapChunk = withIds.isEmpty() ? 0 : 8 + resMap.size();
        int total = 8 + sp.length + mapChunk + x.body.size();
        out.u16(0x0003).u16(8).u32(total);
        out.bytes(sp);
        if (!withIds.isEmpty()) {
            out.u16(0x0180).u16(8).u32(mapChunk);
            out.bytes(resMap.toByteArray());
        }
        out.bytes(x.body.toByteArray());
        return out.toByteArray();
    }

    private void prepare(XNode n, Map<XNode, List<PAttr>> prepared) {
        List<PAttr> list = new ArrayList<PAttr>();
        for (XNode.Attr a : n.attrs) {
            PAttr p = handler.handle(n, a);
            if (p != null) list.add(p);
        }
        // framework requires attributes ordered by resource id; id-less attributes follow in document order
        final List<PAttr> idd = new ArrayList<PAttr>();
        List<PAttr> none = new ArrayList<PAttr>();
        for (PAttr p : list) { if (p.resId != 0) idd.add(p); else none.add(p); }
        Collections.sort(idd, new Comparator<PAttr>() {
            @Override public int compare(PAttr a, PAttr b) {
                long x1 = a.resId & 0xFFFFFFFFL, x2 = b.resId & 0xFFFFFFFFL;
                return x1 < x2 ? -1 : (x1 > x2 ? 1 : 0);
            }
        });
        idd.addAll(none);
        prepared.put(n, idd);
        for (XNode c : n.children) prepare(c, prepared);
    }

    private void node(XNode n, Map<XNode, List<PAttr>> prepared) {
        for (String[] d : n.nsDecls) {
            body.u16(0x0100).u16(16).u32(24).u32(n.line).u32(-1);
            body.u32(pool.add(d[0])).u32(pool.add(d[1]));
        }
        List<PAttr> attrs = prepared.get(n);
        int nsIdx = n.uri.length() == 0 ? -1 : pool.add(n.uri);
        int nameIdx = pool.add(n.name);
        int idIdx = 0, classIdx = 0, styleIdx = 0;
        for (int i = 0; i < attrs.size(); i++) {
            PAttr a = attrs.get(i);
            if (a.uri.length() == 0 && a.resId == 0) {
                if (a.name.equals("id")) idIdx = i + 1;
                else if (a.name.equals("class")) classIdx = i + 1;
                else if (a.name.equals("style")) styleIdx = i + 1;
            }
        }
        body.u16(0x0102).u16(16).u32(36 + 20 * attrs.size()).u32(n.line).u32(-1);
        body.u32(nsIdx).u32(nameIdx).u16(20).u16(20).u16(attrs.size()).u16(idIdx).u16(classIdx).u16(styleIdx);
        for (PAttr a : attrs) {
            int aNs = a.uri.length() == 0 ? -1 : pool.add(a.uri);
            int aName = a.resId != 0 ? idStrings.get(a.name + "#" + a.resId) : pool.add(a.name);
            int raw = a.raw == null ? -1 : pool.add(a.raw);
            int type = a.val.type;
            int data = a.val.data;
            if (type == Val.STRING) {
                int si = pool.add(a.val.str);
                data = si;
                raw = si;
            }
            body.u32(aNs).u32(aName).u32(raw).u16(8).u8(0).u8(type).u32(data);
        }
        for (XNode c : n.children) node(c, prepared);
        body.u16(0x0103).u16(16).u32(24).u32(n.line).u32(-1).u32(nsIdx).u32(nameIdx);
        for (int i = n.nsDecls.size() - 1; i >= 0; i--) {
            String[] d = n.nsDecls.get(i);
            body.u16(0x0101).u16(16).u32(24).u32(n.line).u32(-1);
            body.u32(pool.add(d[0])).u32(pool.add(d[1]));
        }
    }
}
