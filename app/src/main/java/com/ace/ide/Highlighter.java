package com.ace.ide;

import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Fast single-pass syntax highlighter for Java, XML, JSON and plain config files. */
public final class Highlighter {

    public static final int PLAIN = 0, JAVA = 1, XML = 2, JSON = 3, PROPS = 4;
    public static final int MAX_LEN = 220000;

    static final class Span extends ForegroundColorSpan {
        Span(int c) { super(c); }
    }

    private static final Set<String> KW = new HashSet<String>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var"));

    private Highlighter() {}

    public static int langOf(String fileName) {
        String e = FileUtil.ext(fileName);
        if (e.equals("java")) return JAVA;
        if (e.equals("xml") || e.equals("html") || e.equals("svg")) return XML;
        if (e.equals("json")) return JSON;
        if (e.equals("properties") || e.equals("gradle") || e.equals("pro") || e.equals("md") || e.equals("yml")
                || e.equals("yaml") || e.equals("txt") || e.equals("gitignore") || e.equals("cfg") || e.equals("ini")) return PROPS;
        return PLAIN;
    }

    public static void apply(Editable e, int lang, Theme t) {
        Span[] old = e.getSpans(0, e.length(), Span.class);
        for (Span s : old) e.removeSpan(s);
        if (lang == PLAIN || e.length() > MAX_LEN) return;
        String s = e.toString();
        switch (lang) {
            case JAVA: java(e, s, t); break;
            case XML: xml(e, s, t); break;
            case JSON: json(e, s, t); break;
            case PROPS: props(e, s, t); break;
            default: break;
        }
    }

    private static void mark(Editable e, int from, int to, int color) {
        if (to > from) e.setSpan(new Span(color), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void java(Editable e, String s, Theme t) {
        int n = s.length(), i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                int j = s.indexOf('\n', i);
                if (j < 0) j = n;
                mark(e, i, j, t.edCom);
                i = j;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int j = s.indexOf("*/", i + 2);
                j = j < 0 ? n : j + 2;
                mark(e, i, j, t.edCom);
                i = j;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n) {
                    char d = s.charAt(j);
                    if (d == '\\') { j += 2; continue; }
                    if (d == c || d == '\n') break;
                    j++;
                }
                j = Math.min(n, j + 1);
                mark(e, i, j, t.edStr);
                i = j;
            } else if (c == '@' && i + 1 < n && Character.isJavaIdentifierStart(s.charAt(i + 1))) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                mark(e, i, j, t.edAnn);
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '.' || s.charAt(j) == '_')) j++;
                mark(e, i, j, t.edNum);
                i = j;
            } else if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                String w = s.substring(i, j);
                if (KW.contains(w)) mark(e, i, j, t.edKw);
                else if (Character.isUpperCase(c) && (j - i > 1 || !isConstant(w))) mark(e, i, j, t.edTyp);
                i = j;
            } else {
                i++;
            }
        }
    }

    private static boolean isConstant(String w) { return w.length() > 1 && w.equals(w.toUpperCase()); }

    private static void xml(Editable e, String s, Theme t) {
        int n = s.length(), i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c != '<') { i++; continue; }
            if (s.startsWith("<!--", i)) {
                int j = s.indexOf("-->", i + 4);
                j = j < 0 ? n : j + 3;
                mark(e, i, j, t.edCom);
                i = j;
                continue;
            }
            if (s.startsWith("<![CDATA[", i)) {
                int j = s.indexOf("]]>", i);
                j = j < 0 ? n : j + 3;
                mark(e, i, j, t.edStr);
                i = j;
                continue;
            }
            int j = i + 1;
            if (j < n && (s.charAt(j) == '/' || s.charAt(j) == '?' || s.charAt(j) == '!')) j++;
            int ns = j;
            while (j < n && !Character.isWhitespace(s.charAt(j)) && s.charAt(j) != '>' && s.charAt(j) != '/') j++;
            mark(e, i, j, t.edTag);
            i = j;
            while (i < n && s.charAt(i) != '>') {
                char d = s.charAt(i);
                if (d == '"' || d == '\'') {
                    int k = s.indexOf(d, i + 1);
                    k = k < 0 ? n : k + 1;
                    mark(e, i, k, t.edStr);
                    i = k;
                } else if (Character.isWhitespace(d) || d == '/' || d == '?' || d == '=') {
                    i++;
                } else {
                    int k = i;
                    while (k < n && !Character.isWhitespace(s.charAt(k)) && s.charAt(k) != '=' && s.charAt(k) != '>' && s.charAt(k) != '/') k++;
                    mark(e, i, k, t.edAttr);
                    i = Math.max(k, i + 1);
                }
            }
            if (i < n) { mark(e, i, i + 1, t.edTag); i++; }
        }
    }

    private static void json(Editable e, String s, Theme t) {
        int n = s.length(), i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"') {
                int j = i + 1;
                while (j < n) {
                    char d = s.charAt(j);
                    if (d == '\\') { j += 2; continue; }
                    if (d == '"' || d == '\n') break;
                    j++;
                }
                j = Math.min(n, j + 1);
                int k = j;
                while (k < n && Character.isWhitespace(s.charAt(k))) k++;
                mark(e, i, j, k < n && s.charAt(k) == ':' ? t.edAttr : t.edStr);
                i = j;
            } else if (Character.isDigit(c) || (c == '-' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '.' || s.charAt(j) == '-' || s.charAt(j) == '+')) j++;
                mark(e, i, j, t.edNum);
                i = j;
            } else if (Character.isLetter(c)) {
                int j = i + 1;
                while (j < n && Character.isLetter(s.charAt(j))) j++;
                String w = s.substring(i, j);
                if (w.equals("true") || w.equals("false") || w.equals("null")) mark(e, i, j, t.edKw);
                i = j;
            } else {
                i++;
            }
        }
    }

    private static void props(Editable e, String s, Theme t) {
        int n = s.length(), i = 0;
        while (i < n) {
            int eol = s.indexOf('\n', i);
            if (eol < 0) eol = n;
            int k = i;
            while (k < eol && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) k++;
            if (k < eol && (s.charAt(k) == '#' || s.startsWith("//", k))) {
                mark(e, k, eol, t.edCom);
            } else {
                int eq = s.indexOf('=', k);
                if (eq > 0 && eq < eol) mark(e, k, eq, t.edAttr);
                int q = s.indexOf('"', k);
                while (q >= 0 && q < eol) {
                    int q2 = s.indexOf('"', q + 1);
                    if (q2 < 0 || q2 > eol) break;
                    mark(e, q, q2 + 1, t.edStr);
                    q = s.indexOf('"', q2 + 1);
                }
            }
            i = eol + 1;
        }
    }
}
