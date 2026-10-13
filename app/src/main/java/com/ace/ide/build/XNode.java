package com.ace.ide.build;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/** Minimal namespace-aware XML tree with source line numbers. */
final class XNode {

    static final class Attr {
        String uri;       // "" when the attribute has no namespace
        String name;      // local name
        String value;
        Attr(String uri, String name, String value) { this.uri = uri; this.name = name; this.value = value; }
    }

    String uri = "";
    String name;
    int line;
    final List<Attr> attrs = new ArrayList<Attr>();
    final List<XNode> children = new ArrayList<XNode>();
    /** namespace declarations made on this element: {prefix, uri} */
    final List<String[]> nsDecls = new ArrayList<String[]>();
    final StringBuilder text = new StringBuilder();
    XNode parent;

    String attr(String uriWanted, String local) {
        for (Attr a : attrs) if (a.name.equals(local) && a.uri.equals(uriWanted)) return a.value;
        return null;
    }

    /** attribute without namespace (or android: when not found) */
    String attr(String local) {
        String v = attr("", local);
        if (v == null) v = attr(Ns.ANDROID, local);
        return v;
    }

    void setAttr(String uriWanted, String local, String value) {
        for (Attr a : attrs) if (a.name.equals(local) && a.uri.equals(uriWanted)) { a.value = value; return; }
        attrs.add(new Attr(uriWanted, local, value));
    }

    /** all text below this element, tags removed */
    String deepText() {
        StringBuilder sb = new StringBuilder();
        collect(this, sb);
        return sb.toString();
    }

    private static void collect(XNode n, StringBuilder sb) {
        // text and child text are not interleaved in the tree, children text goes after own text
        sb.append(n.text);
        for (XNode c : n.children) collect(c, sb);
    }

    static final class Ns {
        static final String ANDROID = "http://schemas.android.com/apk/res/android";
        static final String AUTO = "http://schemas.android.com/apk/res-auto";
        static final String TOOLS = "http://schemas.android.com/tools";
    }

    static XNode parse(File f) throws IOException, SAXException {
        InputStream in = new FileInputStream(f);
        try {
            return parse(in);
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }

    static XNode parse(InputStream in) throws IOException, SAXException {
        try {
            SAXParserFactory fac = SAXParserFactory.newInstance();
            fac.setNamespaceAware(true);
            SAXParser sp = fac.newSAXParser();
            final XNode[] root = new XNode[1];
            final XNode[] cur = new XNode[1];
            final List<String[]> pending = new ArrayList<String[]>();
            final Locator[] loc = new Locator[1];
            DefaultHandler h = new DefaultHandler() {
                @Override public void setDocumentLocator(Locator l) { loc[0] = l; }

                @Override public void startPrefixMapping(String prefix, String uri) {
                    pending.add(new String[]{prefix == null ? "" : prefix, uri});
                }

                @Override public void startElement(String uri, String local, String qName, Attributes a) {
                    XNode n = new XNode();
                    n.uri = uri == null ? "" : uri;
                    n.name = local != null && local.length() > 0 ? local : qName;
                    n.line = loc[0] != null ? loc[0].getLineNumber() : 0;
                    n.nsDecls.addAll(pending);
                    pending.clear();
                    for (int i = 0; i < a.getLength(); i++) {
                        String qn = a.getQName(i);
                        if (qn != null && (qn.equals("xmlns") || qn.startsWith("xmlns:"))) continue;
                        String ln = a.getLocalName(i);
                        if (ln == null || ln.length() == 0) ln = qn;
                        String au = a.getURI(i);
                        n.attrs.add(new Attr(au == null ? "" : au, ln, a.getValue(i)));
                    }
                    if (cur[0] == null) root[0] = n; else { n.parent = cur[0]; cur[0].children.add(n); }
                    cur[0] = n;
                }

                @Override public void endElement(String uri, String local, String qName) {
                    if (cur[0] != null) cur[0] = cur[0].parent;
                }

                @Override public void characters(char[] ch, int start, int length) {
                    if (cur[0] != null) cur[0].text.append(ch, start, length);
                }
            };
            sp.parse(new InputSource(in), h);
            if (root[0] == null) throw new SAXException("empty document");
            return root[0];
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new SAXException(e);
        }
    }
}
