package com.ace.ide.build;

import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;

/** Quick XML well-formedness check with a line number (used for live error marks). */
public final class XmlCheck {

    public static final class Error extends Exception {
        public final int line;
        public Error(int line, String msg) { super(msg); this.line = line; }
    }

    private XmlCheck() {}

    public static void check(String text) throws Error {
        try {
            XNode.parse(new ByteArrayInputStream(text.getBytes("UTF-8")));
        } catch (SAXParseException e) {
            String m = e.getMessage() == null ? "XML error" : e.getMessage();
            throw new Error(e.getLineNumber(), m);
        } catch (Exception e) {
            throw new Error(1, e.getMessage() == null ? "XML error" : e.getMessage());
        }
    }
}
