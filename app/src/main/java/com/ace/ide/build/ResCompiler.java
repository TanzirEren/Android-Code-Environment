package com.ace.ide.build;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A small, pure-Java replacement for "aapt2 compile + link": compiles res/ and AndroidManifest.xml
 * into binary XML + resources.arsc and writes R.java. No native tools involved.
 */
public final class ResCompiler {

    // ------------------------------------------------------------------ API

    public static final class Input {
        public File manifest;
        public final List<File> resDirs = new ArrayList<File>();
        public String pkg;
        public int minSdk = 21, targetSdk = 34, versionCode = 1;
        public String versionName = "1.0";
        public boolean debuggable = true;
        public FrameworkTable fw;
    }

    public static final class Output {
        public byte[] manifest;
        public byte[] arsc;
        /** apk path -> compiled binary xml */
        public final Map<String, byte[]> compiled = new LinkedHashMap<String, byte[]>();
        /** apk path -> file copied as-is (png, jpg, raw, fonts …) */
        public final Map<String, File> copied = new LinkedHashMap<String, File>();
        public String rJava;
        public String pkg;
        public final List<String> errors = new ArrayList<String>();
        public final List<String> warnings = new ArrayList<String>();
        public boolean ok() { return errors.isEmpty(); }
    }

    public static Output compile(Input in) {
        ResCompiler c = new ResCompiler(in);
        try {
            c.run();
        } catch (Throwable t) {
            c.out.errors.add("resource compiler crashed: " + t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(6, st.length); i++) c.out.errors.add("    at " + st[i]);
        }
        return c.out;
    }

    // ------------------------------------------------------------------ internals

    private static final String[] TYPE_ORDER = {"attr", "id", "style", "string", "dimen", "color", "array", "plurals",
            "drawable", "layout", "anim", "animator", "interpolator", "mipmap", "menu", "xml", "raw",
            "integer", "bool", "fraction", "font", "transition", "navigation"};
    private static final Set<String> FILE_TYPES = new HashSet<String>(Arrays.asList(
            "drawable", "mipmap", "layout", "menu", "xml", "anim", "animator", "interpolator", "transition",
            "raw", "font", "color", "navigation"));

    private static final class Def {
        static final int SIMPLE = 0, STYLE = 1, ARRAY = 2, PLURALS = 3, ATTR = 4, ID = 5, FILE = 6, STYLEABLE = 7;
        int kind;
        String type, name, file;
        ResConfig cfg = ResConfig.DEFAULT;
        int line;
        String raw, fmt, parent, arrayKind, attrFormat;
        boolean flags;
        final List<String[]> items = new ArrayList<String[]>();
        File src;
        boolean xml;
        XNode tree;
    }

    private static final class AttrRes {
        int id;
        int fmt;
        FrameworkTable.AttrInfo info;
    }

    private final Input in;
    private final Output out = new Output();
    private final FrameworkTable fw;
    private String pkg;

    private final List<Def> defs = new ArrayList<Def>();
    private final Map<String, Def> defIndex = new HashMap<String, Def>();
    private final Map<String, Integer> appIds = new HashMap<String, Integer>();
    private final Map<String, FrameworkTable.AttrInfo> appAttrs = new HashMap<String, FrameworkTable.AttrInfo>();
    private final Map<String, List<String>> styleables = new TreeMap<String, List<String>>();
    private final Map<String, TreeSet<String>> namesByType = new HashMap<String, TreeSet<String>>();

    private ResCompiler(Input in) {
        this.in = in;
        this.fw = in.fw;
    }

    private void err(String file, int line, String msg) {
        out.errors.add((file == null ? "" : file + (line > 0 ? ":" + line : "") + ": ") + msg);
    }

    private void warn(String file, int line, String msg) {
        out.warnings.add((file == null ? "" : file + (line > 0 ? ":" + line : "") + ": ") + msg);
    }

    // ------------------------------------------------------------------ driver

    private void run() throws Exception {
        // manifest first: we need the package name
        XNode manifest = null;
        if (in.manifest != null && in.manifest.isFile()) {
            try {
                manifest = XNode.parse(in.manifest);
            } catch (Exception e) {
                err("AndroidManifest.xml", 0, "XML error: " + e.getMessage());
            }
        } else {
            err("AndroidManifest.xml", 0, "AndroidManifest.xml not found");
        }
        pkg = in.pkg;
        if (pkg == null || pkg.length() == 0) {
            if (manifest != null) pkg = manifest.attr("", "package");
        }
        if (pkg == null || pkg.length() == 0) { err("AndroidManifest.xml", 0, "package name is missing"); return; }
        out.pkg = pkg;

        for (File rd : in.resDirs) scanResDir(rd);
        if (!out.errors.isEmpty()) return;

        // ids declared inline (@+id/x) in layouts etc.
        for (Def d : new ArrayList<Def>(defs)) {
            if (d.tree != null) collectIds(d.tree);
        }
        if (manifest != null) collectIds(manifest);

        registerNames();
        assignIds();

        List<Arsc.TypeDef> types = resolveAll();
        if (!out.errors.isEmpty()) return;

        // manifest
        if (manifest != null) {
            prepareManifest(manifest);
            final String mf = "AndroidManifest.xml";
            out.manifest = Axml.encode(manifest, new Axml.Handler() {
                @Override public Axml.PAttr handle(XNode el, XNode.Attr a) { return attr(mf, el, a); }
            });
        }
        if (!out.errors.isEmpty()) return;

        out.arsc = Arsc.write(pkg, 0x7f, types);
        out.rJava = rJava(types);
    }

    // ------------------------------------------------------------------ scanning res/

    private void scanResDir(File resDir) {
        File[] dirs = resDir.listFiles();
        if (dirs == null) return;
        Arrays.sort(dirs);
        for (File d : dirs) {
            if (!d.isDirectory()) continue;
            String dn = d.getName();
            if (dn.startsWith(".")) continue;
            int dash = dn.indexOf('-');
            String type = dash < 0 ? dn : dn.substring(0, dash);
            String qual = dash < 0 ? "" : dn.substring(dash + 1);
            String[] e = new String[1];
            ResConfig cfg = ResConfig.parse(qual, e);
            if (cfg == null) { warn("res/" + dn, 0, e[0] + " — folder skipped"); continue; }
            boolean values = type.equals("values");
            if (!values && !FILE_TYPES.contains(type)) { warn("res/" + dn, 0, "unknown resource folder — skipped"); continue; }
            File[] files = d.listFiles();
            if (files == null) continue;
            Arrays.sort(files);
            for (File f : files) {
                if (!f.isFile() || f.getName().startsWith(".")) continue;
                String rel = "res/" + dn + "/" + f.getName();
                if (values) {
                    if (f.getName().toLowerCase().endsWith(".xml")) parseValues(f, rel, cfg);
                    continue;
                }
                String fname = f.getName();
                int dot = fname.indexOf('.');
                String name = dot < 0 ? fname : fname.substring(0, dot);
                if (name.length() == 0) continue;
                Def def = new Def();
                def.kind = Def.FILE;
                def.type = type;
                def.name = name;
                def.cfg = cfg;
                def.file = rel;
                def.src = f;
                def.xml = fname.toLowerCase().endsWith(".xml") && !type.equals("raw");
                if (def.xml) {
                    try {
                        def.tree = XNode.parse(f);
                    } catch (Exception ex) {
                        err(rel, 0, "XML error: " + ex.getMessage());
                        continue;
                    }
                }
                if (fname.toLowerCase().endsWith(".9.png")) warn(rel, 0, "9-patch images are copied as-is (not processed)");
                add(def);
            }
        }
    }

    private void add(Def d) {
        String key = d.type + "/" + d.name + "/" + d.cfg.key;
        Def old = defIndex.get(key);
        if (old != null) defs.remove(old);
        defIndex.put(key, d);
        defs.add(d);
    }

    private void parseValues(File f, String rel, ResConfig cfg) {
        XNode root;
        try {
            root = XNode.parse(f);
        } catch (Exception e) {
            err(rel, 0, "XML error: " + e.getMessage());
            return;
        }
        if (!root.name.equals("resources")) { warn(rel, root.line, "root element is not <resources> — ignored"); return; }
        for (XNode n : root.children) {
            String tag = n.name;
            String name = n.attr("", "name");
            Def d = new Def();
            d.cfg = cfg;
            d.file = rel;
            d.line = n.line;
            d.name = name;
            if (tag.equals("string") || tag.equals("color") || tag.equals("dimen") || tag.equals("integer")
                    || tag.equals("bool") || tag.equals("fraction") || tag.equals("drawable")) {
                if (name == null) { err(rel, n.line, "<" + tag + "> without a name"); continue; }
                d.kind = Def.SIMPLE; d.type = tag; d.raw = n.deepText(); d.fmt = n.attr("", "format");
                if (tag.equals("string") && "false".equals(n.attr("", "translatable")) == false) { /* keep */ }
                add(d);
            } else if (tag.equals("item")) {
                String type = n.attr("", "type");
                if (name == null || type == null) { err(rel, n.line, "<item> needs name and type"); continue; }
                if (type.equals("id")) { d.kind = Def.ID; d.type = "id"; add(d); continue; }
                d.kind = Def.SIMPLE; d.type = type; d.raw = n.deepText(); d.fmt = n.attr("", "format");
                add(d);
            } else if (tag.equals("id")) {
                if (name == null) continue;
                d.kind = Def.ID; d.type = "id"; add(d);
            } else if (tag.equals("string-array") || tag.equals("integer-array") || tag.equals("array")) {
                if (name == null) { err(rel, n.line, "<" + tag + "> without a name"); continue; }
                d.kind = Def.ARRAY; d.type = "array"; d.arrayKind = tag;
                for (XNode it : n.children) if (it.name.equals("item")) d.items.add(new String[]{null, it.deepText()});
                add(d);
            } else if (tag.equals("plurals")) {
                if (name == null) { err(rel, n.line, "<plurals> without a name"); continue; }
                d.kind = Def.PLURALS; d.type = "plurals";
                for (XNode it : n.children) {
                    if (it.name.equals("item")) d.items.add(new String[]{it.attr("", "quantity"), it.deepText()});
                }
                add(d);
            } else if (tag.equals("style")) {
                if (name == null) { err(rel, n.line, "<style> without a name"); continue; }
                d.kind = Def.STYLE; d.type = "style"; d.parent = n.attr("", "parent");
                for (XNode it : n.children) {
                    if (!it.name.equals("item")) continue;
                    String an = it.attr("", "name");
                    if (an == null) { err(rel, it.line, "style <item> without a name"); continue; }
                    d.items.add(new String[]{an, it.deepText(), String.valueOf(it.line)});
                }
                add(d);
            } else if (tag.equals("attr")) {
                if (name == null) continue;
                parseAttr(n, rel, cfg);
            } else if (tag.equals("declare-styleable")) {
                if (name == null) continue;
                List<String> list = new ArrayList<String>();
                for (XNode a : n.children) {
                    if (!a.name.equals("attr")) continue;
                    String an = a.attr("", "name");
                    if (an == null) continue;
                    list.add(an);
                    if (!an.startsWith("android:") && (a.attr("", "format") != null || !a.children.isEmpty())) parseAttr(a, rel, cfg);
                    else if (!an.startsWith("android:") && !appAttrs.containsKey(an)) {
                        // referenced only: declare as "any"
                        Def ad = new Def();
                        ad.kind = Def.ATTR; ad.type = "attr"; ad.name = an; ad.file = rel; ad.line = a.line; ad.attrFormat = null;
                        if (!defIndex.containsKey("attr/" + an + "/")) add(ad);
                    }
                }
                styleables.put(name, list);
            } else if (tag.equals("eat-comment") || tag.equals("skip") || tag.equals("public") || tag.equals("java-symbol")
                    || tag.equals("private-symbol") || tag.equals("overlayable") || tag.equals("public-group")) {
                // ignored
            } else {
                warn(rel, n.line, "unsupported element <" + tag + "> ignored");
            }
        }
    }

    private void parseAttr(XNode n, String rel, ResConfig cfg) {
        Def d = new Def();
        d.kind = Def.ATTR; d.type = "attr"; d.name = n.attr("", "name"); d.cfg = ResConfig.DEFAULT;
        d.file = rel; d.line = n.line; d.attrFormat = n.attr("", "format");
        for (XNode c : n.children) {
            boolean en = c.name.equals("enum"), fl = c.name.equals("flag");
            if (!en && !fl) continue;
            d.flags = fl;
            d.items.add(new String[]{c.attr("", "name"), c.attr("", "value")});
        }
        Def old = defIndex.get("attr/" + d.name + "/");
        if (old != null) {
            // an explicit definition (format / enum) wins over a bare reference
            if (old.attrFormat == null && old.items.isEmpty()) defs.remove(old);
            else if (d.attrFormat == null && d.items.isEmpty()) return;
        }
        add(d);
    }

    private void collectIds(XNode n) {
        for (XNode.Attr a : n.attrs) {
            String v = a.value == null ? null : a.value.trim();
            if (v != null && v.startsWith("@+id/")) {
                String nm = v.substring(5);
                if (nm.length() > 0) addName("id", nm);
            } else if (v != null && v.startsWith("@+")) {
                int sl = v.indexOf('/');
                if (sl > 2) addName(v.substring(2, sl), v.substring(sl + 1));
            }
        }
        for (XNode c : n.children) collectIds(c);
    }

    private void addName(String type, String name) {
        TreeSet<String> s = namesByType.get(type);
        if (s == null) { s = new TreeSet<String>(); namesByType.put(type, s); }
        s.add(name);
    }

    private void registerNames() {
        for (Def d : defs) {
            if (d.kind == Def.ATTR) {
                FrameworkTable.AttrInfo ai = new FrameworkTable.AttrInfo();
                ai.format = formatMask(d.attrFormat, !d.items.isEmpty(), d.flags);
                appAttrs.put(d.name, ai);
                addName("attr", d.name);
                int auto = 0;
                for (String[] s : d.items) {
                    if (s[0] == null) continue;
                    addName("id", s[0]);
                    Val v = s[1] == null ? null : Values.integer(s[1].trim());
                    if (v == null) v = new Val(Val.INT_DEC, d.flags ? 1 << auto : auto);
                    auto++;
                    ai.symbols.put(s[0], new int[]{v.type, v.data});
                }
            } else if (d.kind == Def.STYLEABLE) {
                continue;
            } else {
                addName(d.type, d.name);
            }
        }
        for (Map.Entry<String, List<String>> e : styleables.entrySet()) {
            for (String an : e.getValue()) {
                if (an.startsWith("android:")) continue;
                if (!appAttrs.containsKey(an)) {
                    FrameworkTable.AttrInfo ai = new FrameworkTable.AttrInfo();
                    ai.format = Values.F_ANY;
                    appAttrs.put(an, ai);
                    addName("attr", an);
                }
            }
        }
    }

    private static int formatMask(String f, boolean hasSymbols, boolean flags) {
        int m = 0;
        if (f != null) {
            for (String p : f.split("\\|")) {
                p = p.trim();
                if (p.equals("reference")) m |= Values.F_REF;
                else if (p.equals("string")) m |= Values.F_STRING;
                else if (p.equals("integer")) m |= Values.F_INT;
                else if (p.equals("boolean")) m |= Values.F_BOOL;
                else if (p.equals("color")) m |= Values.F_COLOR;
                else if (p.equals("float")) m |= Values.F_FLOAT;
                else if (p.equals("dimension")) m |= Values.F_DIMEN;
                else if (p.equals("fraction")) m |= Values.F_FRACTION;
                else if (p.equals("enum")) m |= Values.F_ENUM;
                else if (p.equals("flags")) m |= Values.F_FLAGS;
            }
        }
        if (hasSymbols) m |= flags ? Values.F_FLAGS : Values.F_ENUM;
        if (m == 0) m = Values.F_ANY;
        return m;
    }

    // ------------------------------------------------------------------ ids

    private List<String> typeOrder = new ArrayList<String>();

    private void assignIds() {
        typeOrder.clear();
        for (String t : TYPE_ORDER) if (namesByType.containsKey(t)) typeOrder.add(t);
        List<String> rest = new ArrayList<String>();
        for (String t : namesByType.keySet()) if (!typeOrder.contains(t)) rest.add(t);
        Collections.sort(rest);
        typeOrder.addAll(rest);
        for (int ti = 0; ti < typeOrder.size(); ti++) {
            String t = typeOrder.get(ti);
            int e = 0;
            for (String name : namesByType.get(t)) {
                appIds.put(t + "/" + name, 0x7f000000 | ((ti + 1) << 16) | e++);
            }
        }
    }

    // ------------------------------------------------------------------ references / typed values

    private Val ref(String s, String file, int line) {
        if (s.length() == 0) return null;
        char c0 = s.charAt(0);
        if (c0 == '@') {
            String r = s.substring(1);
            if (r.equals("null")) return new Val(Val.REF, 0);
            if (r.equals("empty")) return new Val(Val.NULL, 1);
            if (r.equals("undefined")) return new Val(Val.NULL, 0);
            if (r.startsWith("+")) r = r.substring(1);
            String p = null;
            int colon = r.indexOf(':');
            if (colon >= 0) { p = r.substring(0, colon); r = r.substring(colon + 1); }
            int sl = r.indexOf('/');
            if (sl <= 0 || sl == r.length() - 1) {
                err(file, line, "invalid resource reference '" + s + "'");
                return Val.ref(0);
            }
            String type = r.substring(0, sl), name = r.substring(sl + 1);
            int id = lookup(p, type, name);
            if (id == 0) {
                err(file, line, "resource not found: @" + (p != null ? p + ":" : "") + type + "/" + name
                        + (p == null ? hintFor(type, name) : ""));
                return Val.ref(0);
            }
            return Val.ref(id);
        }
        if (c0 == '?') {
            String r = s.substring(1);
            String p = null;
            int colon = r.indexOf(':');
            if (colon >= 0) { p = r.substring(0, colon); r = r.substring(colon + 1); }
            if (r.startsWith("attr/")) r = r.substring(5);
            int id = lookup(p, "attr", r);
            if (id == 0) {
                err(file, line, "attribute not found: ?" + (p != null ? p + ":" : "") + r
                        + (p == null ? " (theme attributes from AppCompat/Material libraries are not available)" : ""));
                return new Val(Val.ATTR, 0);
            }
            return new Val(Val.ATTR, id);
        }
        return null;
    }

    private String hintFor(String type, String name) {
        TreeSet<String> names = namesByType.get(type);
        if (names == null || names.isEmpty()) return " (there are no '" + type + "' resources in this project)";
        String low = name.toLowerCase();
        for (String n : names) if (n.toLowerCase().equals(low)) return " (did you mean '" + n + "'?)";
        return "";
    }

    private int lookup(String p, String type, String name) {
        if (p != null && p.startsWith("*")) p = p.substring(1);
        if ("android".equals(p)) return fw.id(type, name);
        if (p == null || p.equals(pkg)) {
            Integer i = appIds.get(type + "/" + name);
            return i == null ? 0 : i;
        }
        return 0;
    }

    private static String describe(int fmt) {
        List<String> l = new ArrayList<String>();
        if ((fmt & Values.F_REF) != 0) l.add("reference");
        if ((fmt & Values.F_STRING) != 0) l.add("string");
        if ((fmt & Values.F_INT) != 0) l.add("integer");
        if ((fmt & Values.F_BOOL) != 0) l.add("boolean");
        if ((fmt & Values.F_COLOR) != 0) l.add("color");
        if ((fmt & Values.F_FLOAT) != 0) l.add("float");
        if ((fmt & Values.F_DIMEN) != 0) l.add("dimension");
        if ((fmt & Values.F_FRACTION) != 0) l.add("fraction");
        if ((fmt & Values.F_ENUM) != 0) l.add("enum");
        if ((fmt & Values.F_FLAGS) != 0) l.add("flags");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append('|'); sb.append(l.get(i)); }
        return sb.toString();
    }

    private Val typed(String raw, int fmt, FrameworkTable.AttrInfo info, String file, int line, String what) {
        String s = raw.trim();
        if (s.length() > 0 && (s.charAt(0) == '@' || s.charAt(0) == '?')) {
            Val r = ref(s, file, line);
            if (r != null) return r;
        }
        boolean any = fmt == 0 || (fmt & Values.F_ANY) == Values.F_ANY;
        if (info != null && !info.symbols.isEmpty()) {
            if ((fmt & Values.F_ENUM) != 0) {
                int[] sv = info.symbols.get(s);
                if (sv != null) return new Val(sv[0], sv[1]);
            }
            if ((fmt & Values.F_FLAGS) != 0 && s.length() > 0) {
                String[] toks = s.split("\\|");
                int acc = 0;
                boolean ok = true;
                for (String t : toks) {
                    int[] sv = info.symbols.get(t.trim());
                    if (sv == null) { ok = false; break; }
                    acc |= sv[1];
                }
                if (ok) return new Val(Val.INT_HEX, acc);
            }
        }
        int f = any ? (Values.F_BOOL | Values.F_COLOR | Values.F_INT | Values.F_DIMEN | Values.F_FRACTION | Values.F_FLOAT) : fmt;
        Val v;
        if ((f & Values.F_COLOR) != 0 && (v = Values.color(s)) != null) return v;
        if ((f & Values.F_BOOL) != 0 && (v = Values.bool(s)) != null) return v;
        if ((f & Values.F_INT) != 0 && (v = Values.integer(s)) != null) return v;
        if ((f & Values.F_FLOAT) != 0 && (f & Values.F_DIMEN) == 0 && (v = Values.flt(s)) != null) return v;
        if ((f & Values.F_DIMEN) != 0 && (v = Values.dimension(s)) != null) return v;
        if ((f & Values.F_FRACTION) != 0 && (v = Values.fraction(s)) != null) return v;
        if ((f & Values.F_FLOAT) != 0 && (v = Values.flt(s)) != null) return v;
        if (any || (fmt & Values.F_STRING) != 0) return Val.string(Values.unescape(raw, true));
        err(file, line, "'" + raw + "' is not valid for " + what + " (expects " + describe(fmt) + ")");
        return Val.string(raw);
    }

    private AttrRes findAttr(String uri, String local, String file, int line) {
        if (uri.equals(XNode.Ns.ANDROID)) {
            int id = fw.id("attr", local);
            if (id == 0) { err(file, line, "attribute android:" + local + " not found in this Android SDK"); return null; }
            AttrRes r = new AttrRes();
            r.id = id;
            r.info = fw.attr(id);
            r.fmt = r.info == null ? Values.F_ANY : r.info.format;
            return r;
        }
        if (uri.equals(XNode.Ns.AUTO) || uri.equals("http://schemas.android.com/apk/res/" + pkg)) {
            Integer id = appIds.get("attr/" + local);
            if (id == null) {
                err(file, line, "attribute '" + local + "' not found (custom attributes must be declared in values/attrs.xml;"
                        + " AppCompat / Material attributes are not available)");
                return null;
            }
            AttrRes r = new AttrRes();
            r.id = id;
            r.info = appAttrs.get(local);
            r.fmt = r.info == null ? Values.F_ANY : r.info.format;
            return r;
        }
        return null;
    }

    private Axml.PAttr attr(String file, XNode el, XNode.Attr a) {
        if (a.uri.equals(XNode.Ns.TOOLS)) return null;
        Axml.PAttr p = new Axml.PAttr();
        p.uri = a.uri;
        p.name = a.name;
        p.raw = a.value;
        AttrRes ar = findAttr(a.uri, a.name, file, el.line);
        if (ar == null) {
            String s = a.value.trim();
            Val r = (s.startsWith("@") || s.startsWith("?")) ? ref(s, file, el.line) : null;
            p.val = r != null ? r : Val.string(Values.unescape(a.value, true));
            if (a.uri.equals(XNode.Ns.ANDROID) || a.uri.equals(XNode.Ns.AUTO)) p.uri = "";
            return p;
        }
        p.resId = ar.id;
        p.val = typed(a.value, ar.fmt, ar.info, file, el.line, "attribute '" + a.name + "'");
        return p;
    }

    // ------------------------------------------------------------------ resolving definitions

    private List<Arsc.TypeDef> resolveAll() {
        Map<String, TreeMap<String, Arsc.EntryDef>> table = new HashMap<String, TreeMap<String, Arsc.EntryDef>>();
        for (String t : typeOrder) table.put(t, new TreeMap<String, Arsc.EntryDef>());

        // every named resource gets an entry (ids, enum symbols, undefined attrs …)
        for (String t : typeOrder) {
            for (String n : namesByType.get(t)) {
                Arsc.EntryDef e = new Arsc.EntryDef();
                e.name = n;
                table.get(t).put(n, e);
            }
        }
        Set<String> filled = new HashSet<String>();

        for (Def d : defs) {
            Res r = null;
            switch (d.kind) {
                case Def.FILE: r = resolveFile(d); break;
                case Def.SIMPLE: r = resolveSimple(d); break;
                case Def.ID: r = null; break;
                case Def.STYLE: r = resolveStyle(d); break;
                case Def.ARRAY: r = resolveArray(d); break;
                case Def.PLURALS: r = resolvePlurals(d); break;
                case Def.ATTR: r = resolveAttr(d); break;
                default: break;
            }
            if (r == null) continue;
            Arsc.EntryDef e = table.get(d.type).get(d.name);
            e.byCfg.put(d.cfg, r);
            filled.add(d.type + "/" + d.name);
        }
        // ids: every id entry is a boolean "false"
        for (Map.Entry<String, Arsc.EntryDef> en : table.get("id") == null
                ? new TreeMap<String, Arsc.EntryDef>().entrySet() : table.get("id").entrySet()) {
            Res r = new Res();
            r.val = new Val(Val.BOOL, 0);
            en.getValue().byCfg.put(ResConfig.DEFAULT, r);
            filled.add("id/" + en.getKey());
        }
        // entries that were only referenced (e.g. undefined attr) still need a value
        for (String t : typeOrder) {
            for (Arsc.EntryDef e : table.get(t).values()) {
                if (e.byCfg.isEmpty()) {
                    Res r = new Res();
                    if (t.equals("attr")) {
                        r.complex = true;
                        r.items.add(new Res.Item(0x01000000, new Val(Val.INT_DEC, Values.F_ANY)));
                    } else {
                        r.val = new Val(Val.NULL, 0);
                    }
                    e.byCfg.put(ResConfig.DEFAULT, r);
                }
            }
        }
        List<Arsc.TypeDef> types = new ArrayList<Arsc.TypeDef>();
        for (String t : typeOrder) {
            Arsc.TypeDef td = new Arsc.TypeDef();
            td.name = t;
            td.entries.addAll(table.get(t).values());
            types.add(td);
        }
        return types;
    }

    private Res resolveFile(Def d) {
        Res r = new Res();
        r.val = Val.string(d.file);
        final String file = d.file;
        if (d.xml) {
            byte[] bin = Axml.encode(d.tree, new Axml.Handler() {
                @Override public Axml.PAttr handle(XNode el, XNode.Attr a) { return attr(file, el, a); }
            });
            out.compiled.put(d.file, bin);
        } else {
            out.copied.put(d.file, d.src);
        }
        return r;
    }

    private Res resolveSimple(Def d) {
        Res r = new Res();
        String raw = d.raw == null ? "" : d.raw;
        String s = raw.trim();
        String t = d.type;
        Val v;
        if (s.startsWith("@") || s.startsWith("?")) {
            v = ref(s, d.file, d.line);
            if (v != null) { r.val = v; return r; }
        }
        if (t.equals("string")) {
            v = Val.string(Values.unescape(raw, true));
        } else if (t.equals("color") || t.equals("drawable")) {
            v = Values.color(s);
            if (v == null) { err(d.file, d.line, "'" + s + "' is not a valid color for <" + t + " name=\"" + d.name + "\">"); v = new Val(Val.NULL, 0); }
        } else if (t.equals("dimen")) {
            v = Values.dimension(s);
            if (v == null) v = Values.fraction(s);
            if (v == null && "float".equals(d.fmt)) v = Values.flt(s);
            if (v == null) { err(d.file, d.line, "'" + s + "' is not a valid dimension for <dimen name=\"" + d.name + "\"> (add dp / sp / px)"); v = new Val(Val.NULL, 0); }
        } else if (t.equals("integer")) {
            v = Values.integer(s);
            if (v == null) { err(d.file, d.line, "'" + s + "' is not a valid integer for <integer name=\"" + d.name + "\">"); v = new Val(Val.NULL, 0); }
        } else if (t.equals("bool")) {
            v = Values.bool(s);
            if (v == null) { err(d.file, d.line, "'" + s + "' is not a valid boolean for <bool name=\"" + d.name + "\">"); v = new Val(Val.NULL, 0); }
        } else if (t.equals("fraction")) {
            v = Values.fraction(s);
            if (v == null) v = Values.dimension(s);
            if (v == null) { err(d.file, d.line, "'" + s + "' is not a valid fraction"); v = new Val(Val.NULL, 0); }
        } else {
            v = typed(raw, Values.F_ANY, null, d.file, d.line, "<item type=\"" + t + "\">");
        }
        r.val = v;
        return r;
    }

    private int attrIdByName(String name, String file, int line) {
        int id;
        if (name.startsWith("android:")) {
            id = fw.id("attr", name.substring(8));
            if (id == 0) err(file, line, "attribute '" + name + "' not found in this Android SDK");
        } else {
            Integer i = appIds.get("attr/" + name);
            id = i == null ? 0 : i;
            if (id == 0) {
                // be forgiving: a bare name that only exists in the framework
                id = fw.id("attr", name);
                if (id == 0) err(file, line, "attribute '" + name + "' not found (custom attributes need values/attrs.xml;"
                        + " AppCompat / Material attributes are not available)");
            }
        }
        return id;
    }

    private AttrRes attrInfoById(int id) {
        AttrRes r = new AttrRes();
        r.id = id;
        if ((id >>> 24) == 0x01) {
            r.info = fw.attr(id);
        } else {
            for (Map.Entry<String, Integer> e : appIds.entrySet()) {
                if (e.getValue() == id && e.getKey().startsWith("attr/")) { r.info = appAttrs.get(e.getKey().substring(5)); break; }
            }
        }
        r.fmt = r.info == null ? Values.F_ANY : r.info.format;
        return r;
    }

    private Res resolveStyle(Def d) {
        Res r = new Res();
        r.complex = true;
        String p = d.parent;
        if (p == null) {
            int dot = d.name.lastIndexOf('.');
            if (dot > 0) p = "@style/" + d.name.substring(0, dot);
        }
        if (p != null && p.length() > 0) {
            String ps = p.trim();
            if (!ps.startsWith("@") && !ps.startsWith("?")) {
                int colon = ps.indexOf(':');
                ps = colon >= 0 ? "@" + ps.substring(0, colon) + ":style/" + ps.substring(colon + 1) : "@style/" + ps;
            }
            Val pv = ref(ps, d.file, d.line);
            r.parent = pv == null ? 0 : pv.data;
        }
        Map<Integer, Res.Item> byId = new TreeMap<Integer, Res.Item>(new java.util.Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                long x = a & 0xFFFFFFFFL, y = b & 0xFFFFFFFFL;
                return x < y ? -1 : (x > y ? 1 : 0);
            }
        });
        for (String[] it : d.items) {
            int line = Integer.parseInt(it[2]);
            int id = attrIdByName(it[0], d.file, line);
            if (id == 0) continue;
            AttrRes ar = attrInfoById(id);
            Val v = typed(it[1], ar.fmt, ar.info, d.file, line, "style attribute '" + it[0] + "'");
            byId.put(id, new Res.Item(id, v));
        }
        r.items.addAll(byId.values());
        return r;
    }

    private Res resolveArray(Def d) {
        Res r = new Res();
        r.complex = true;
        int idx = 0;
        for (String[] it : d.items) {
            String raw = it[1];
            String s = raw.trim();
            Val v = null;
            if (s.startsWith("@") || s.startsWith("?")) v = ref(s, d.file, d.line);
            if (v == null) {
                if (d.arrayKind.equals("string-array")) v = Val.string(Values.unescape(raw, true));
                else if (d.arrayKind.equals("integer-array")) {
                    v = Values.integer(s);
                    if (v == null) { err(d.file, d.line, "'" + s + "' is not an integer in <integer-array name=\"" + d.name + "\">"); v = new Val(Val.NULL, 0); }
                } else v = typed(raw, Values.F_ANY, null, d.file, d.line, "array item");
            }
            r.items.add(new Res.Item(0x02000000 | (idx++ & 0xFFFF), v));
        }
        return r;
    }

    private Res resolvePlurals(Def d) {
        Res r = new Res();
        r.complex = true;
        String[] q = {"other", "zero", "one", "two", "few", "many"};
        Map<Integer, Res.Item> byId = new TreeMap<Integer, Res.Item>();
        for (String[] it : d.items) {
            int id = -1;
            for (int i = 0; i < q.length; i++) if (q[i].equals(it[0])) id = 0x01000004 + i;
            if (id < 0) { err(d.file, d.line, "invalid plurals quantity '" + it[0] + "'"); continue; }
            byId.put(id, new Res.Item(id, Val.string(Values.unescape(it[1], true))));
        }
        r.items.addAll(byId.values());
        return r;
    }

    private Res resolveAttr(Def d) {
        Res r = new Res();
        r.complex = true;
        int fmt = formatMask(d.attrFormat, !d.items.isEmpty(), d.flags);
        r.items.add(new Res.Item(0x01000000, new Val(Val.INT_DEC, fmt)));
        FrameworkTable.AttrInfo ai = appAttrs.get(d.name);
        int auto = 0;
        for (String[] s : d.items) {
            if (s[0] == null) continue;
            Integer sid = appIds.get("id/" + s[0]);
            Val v;
            if (s[1] == null) { v = new Val(Val.INT_DEC, d.flags ? 1 << auto : auto); auto++; }
            else {
                v = Values.integer(s[1].trim());
                if (v == null) { err(d.file, d.line, "invalid enum/flag value '" + s[1] + "' for '" + s[0] + "'"); v = new Val(Val.INT_DEC, 0); }
            }
            if (sid != null) r.items.add(new Res.Item(sid, v));
        }
        return r;
    }

    // ------------------------------------------------------------------ manifest

    private void prepareManifest(XNode root) {
        root.setAttr("", "package", pkg);
        root.setAttr(XNode.Ns.ANDROID, "versionCode", String.valueOf(in.versionCode));
        root.setAttr(XNode.Ns.ANDROID, "versionName", in.versionName == null ? "1.0" : in.versionName);
        XNode sdk = null;
        for (XNode c : root.children) if (c.name.equals("uses-sdk")) sdk = c;
        if (sdk == null) {
            sdk = new XNode();
            sdk.name = "uses-sdk";
            sdk.line = root.line;
            sdk.parent = root;
            root.children.add(0, sdk);
        }
        sdk.setAttr(XNode.Ns.ANDROID, "minSdkVersion", String.valueOf(in.minSdk));
        sdk.setAttr(XNode.Ns.ANDROID, "targetSdkVersion", String.valueOf(in.targetSdk));
        for (XNode c : root.children) {
            if (!c.name.equals("application")) continue;
            if (in.debuggable) c.setAttr(XNode.Ns.ANDROID, "debuggable", "true");
            for (XNode comp : c.children) {
                String cn = comp.name;
                boolean component = cn.equals("activity") || cn.equals("service") || cn.equals("receiver") || cn.equals("activity-alias");
                if (!component) continue;
                boolean hasFilter = false;
                for (XNode k : comp.children) if (k.name.equals("intent-filter")) hasFilter = true;
                if (hasFilter && comp.attr(XNode.Ns.ANDROID, "exported") == null && in.targetSdk >= 31) {
                    comp.setAttr(XNode.Ns.ANDROID, "exported", "true");
                    warn("AndroidManifest.xml", comp.line, "<" + cn + "> has an intent-filter but no android:exported — added android:exported=\"true\"");
                }
            }
        }
    }

    // ------------------------------------------------------------------ R.java

    private static final Set<String> JAVA_KW = new HashSet<String>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"));

    static String ident(String n) {
        String s = n.replace('.', '_').replace('-', '_');
        if (s.length() == 0 || !Character.isJavaIdentifierStart(s.charAt(0)) || JAVA_KW.contains(s)) return null;
        for (int i = 1; i < s.length(); i++) if (!Character.isJavaIdentifierPart(s.charAt(i))) return null;
        return s;
    }

    private String rJava(List<Arsc.TypeDef> types) {
        StringBuilder sb = new StringBuilder();
        sb.append("/* AUTO-GENERATED by ACE (Android Code Environment) — do not edit */\n");
        sb.append("package ").append(pkg).append(";\n\npublic final class R {\n");
        for (int ti = 0; ti < types.size(); ti++) {
            Arsc.TypeDef t = types.get(ti);
            sb.append("    public static final class ").append(t.name).append(" {\n");
            Set<String> used = new HashSet<String>();
            for (int ei = 0; ei < t.entries.size(); ei++) {
                String id = ident(t.entries.get(ei).name);
                if (id == null || !used.add(id)) continue;
                sb.append("        public static final int ").append(id).append(" = 0x")
                  .append(Integer.toHexString(0x7f000000 | ((ti + 1) << 16) | ei)).append(";\n");
            }
            sb.append("    }\n");
        }
        if (!styleables.isEmpty()) {
            sb.append("    public static final class styleable {\n");
            for (Map.Entry<String, List<String>> e : styleables.entrySet()) {
                String sn = ident(e.getKey());
                if (sn == null) continue;
                List<Integer> ids = new ArrayList<Integer>();
                Map<Integer, String> names = new HashMap<Integer, String>();
                for (String an : e.getValue()) {
                    int id;
                    if (an.startsWith("android:")) id = fw.id("attr", an.substring(8));
                    else { Integer i = appIds.get("attr/" + an); id = i == null ? 0 : i; }
                    if (id == 0) continue;
                    ids.add(id);
                    names.put(id, an.replace(':', '_'));
                }
                Collections.sort(ids, new java.util.Comparator<Integer>() {
                    @Override public int compare(Integer a, Integer b) {
                        long x = a & 0xFFFFFFFFL, y = b & 0xFFFFFFFFL;
                        return x < y ? -1 : (x > y ? 1 : 0);
                    }
                });
                sb.append("        public static final int[] ").append(sn).append(" = {");
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("0x").append(Integer.toHexString(ids.get(i)));
                }
                sb.append("};\n");
                for (int i = 0; i < ids.size(); i++) {
                    String an = ident(names.get(ids.get(i)));
                    if (an == null) continue;
                    sb.append("        public static final int ").append(sn).append('_').append(an).append(" = ").append(i).append(";\n");
                }
            }
            sb.append("    }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
