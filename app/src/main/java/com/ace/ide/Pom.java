package com.ace.ide;

import android.content.Context;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/** Minimal Maven POM reader: compile / runtime dependencies with explicit versions. */
final class Pom {

    private Pom() {}

    private static final String[] REPOS = {"https://repo1.maven.org/maven2/", "https://dl.google.com/dl/android/maven2/"};

    static List<String> dependencies(Context c, String coord) {
        List<String> out = new ArrayList<String>();
        String[] p = Libs.parse(coord);
        if (p == null) return out;
        try {
            File pom = new File(new File(Libs.cacheDir(c), p[0]), p[1] + "-" + p[2] + ".pom");
            if (!pom.isFile() || pom.length() == 0) {
                pom.getParentFile().mkdirs();
                String path = p[0].replace('.', '/') + "/" + p[1] + "/" + p[2] + "/" + p[1] + "-" + p[2] + ".pom";
                boolean ok = false;
                for (String repo : REPOS) { if (fetch(repo + path, pom)) { ok = true; break; } }
                if (!ok) return out;
            }
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            Document d = f.newDocumentBuilder().parse(pom);
            Element root = d.getDocumentElement();
            Map<String, String> props = new HashMap<String, String>();
            props.put("project.version", p[2]);
            props.put("project.groupId", p[0]);
            props.put("pom.version", p[2]);
            Element pr = child(root, "properties");
            if (pr != null) {
                NodeList nl = pr.getChildNodes();
                for (int i = 0; i < nl.getLength(); i++) {
                    Node n = nl.item(i);
                    if (n instanceof Element) props.put(n.getNodeName(), n.getTextContent().trim());
                }
            }
            Element deps = child(root, "dependencies");
            if (deps == null) return out;
            NodeList nl = deps.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (!(n instanceof Element) || !n.getNodeName().equals("dependency")) continue;
                Element e = (Element) n;
                String g = sub(text(e, "groupId"), props), a = sub(text(e, "artifactId"), props), v = sub(text(e, "version"), props);
                String scope = text(e, "scope"), opt = text(e, "optional"), type = text(e, "type");
                if (g == null || a == null || v == null || v.contains("${") || v.startsWith("[") || v.startsWith("(")) continue;
                if (scope != null && (scope.equals("test") || scope.equals("provided") || scope.equals("system") || scope.equals("import"))) continue;
                if ("true".equals(opt)) continue;
                if (type != null && type.equals("pom")) continue;
                out.add(g + ":" + a + ":" + v + (type != null && type.equals("aar") ? "@aar" : ""));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static Element child(Element parent, String name) {
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element && n.getNodeName().equals(name)) return (Element) n;
        }
        return null;
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        return e == null ? null : e.getTextContent().trim();
    }

    private static String sub(String s, Map<String, String> props) {
        if (s == null) return null;
        for (int guard = 0; guard < 5 && s.contains("${"); guard++) {
            int a = s.indexOf("${"), b = s.indexOf('}', a);
            if (b < 0) break;
            String v = props.get(s.substring(a + 2, b));
            if (v == null) break;
            s = s.substring(0, a) + v + s.substring(b + 1);
        }
        return s;
    }

    private static boolean fetch(String url, File out) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            if (con.getResponseCode() != 200) return false;
            InputStream in = con.getInputStream();
            FileOutputStream fo = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            try { while ((n = in.read(buf)) > 0) fo.write(buf, 0, n); } finally { fo.close(); in.close(); }
            return true;
        } catch (Throwable t) {
            out.delete();
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
