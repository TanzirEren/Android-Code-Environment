package com.ace.ide.build;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing of literal values: colors, dimensions, numbers, booleans and string escapes. */
final class Values {

    static final int F_REF = 1, F_STRING = 1 << 1, F_INT = 1 << 2, F_BOOL = 1 << 3, F_COLOR = 1 << 4,
            F_FLOAT = 1 << 5, F_DIMEN = 1 << 6, F_FRACTION = 1 << 7, F_ENUM = 1 << 16, F_FLAGS = 1 << 17,
            F_ANY = 0xFFFF;

    private static final Pattern DIM = Pattern.compile("^([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)(px|dip|dp|sp|pt|in|mm)$");
    private static final Pattern FRAC = Pattern.compile("^([+-]?(?:\\d+\\.?\\d*|\\.\\d+))(%p?)$");
    private static final Pattern DEC = Pattern.compile("^[+-]?\\d+$");
    private static final Pattern HEX = Pattern.compile("^0[xX][0-9a-fA-F]+$");
    private static final Pattern FLT = Pattern.compile("^[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?[fF]?$");

    private Values() {}

    static Val bool(String s) {
        if (s.equals("true") || s.equals("TRUE") || s.equals("True")) return new Val(Val.BOOL, -1);
        if (s.equals("false") || s.equals("FALSE") || s.equals("False")) return new Val(Val.BOOL, 0);
        return null;
    }

    static Val color(String s) {
        if (!s.startsWith("#")) return null;
        String h = s.substring(1);
        for (int i = 0; i < h.length(); i++) if (Character.digit(h.charAt(i), 16) < 0) return null;
        long v;
        try {
            switch (h.length()) {
                case 3: {
                    int r = Character.digit(h.charAt(0), 16), g = Character.digit(h.charAt(1), 16), b = Character.digit(h.charAt(2), 16);
                    v = 0xFF000000L | ((r * 17L) << 16) | ((g * 17L) << 8) | (b * 17L);
                    return new Val(Val.COLOR_RGB4, (int) v);
                }
                case 4: {
                    int a = Character.digit(h.charAt(0), 16), r = Character.digit(h.charAt(1), 16),
                            g = Character.digit(h.charAt(2), 16), b = Character.digit(h.charAt(3), 16);
                    v = ((a * 17L) << 24) | ((r * 17L) << 16) | ((g * 17L) << 8) | (b * 17L);
                    return new Val(Val.COLOR_ARGB4, (int) v);
                }
                case 6:
                    v = 0xFF000000L | Long.parseLong(h, 16);
                    return new Val(Val.COLOR_RGB8, (int) v);
                case 8:
                    v = Long.parseLong(h, 16);
                    return new Val(Val.COLOR_ARGB8, (int) v);
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Val integer(String s) {
        try {
            if (HEX.matcher(s).matches()) {
                long v = Long.parseLong(s.substring(2), 16);
                if (v > 0xFFFFFFFFL) return null;
                return new Val(Val.INT_HEX, (int) v);
            }
            if (DEC.matcher(s).matches()) {
                String t = s.startsWith("+") ? s.substring(1) : s;
                long v = Long.parseLong(t);
                if (v > 0xFFFFFFFFL || v < Integer.MIN_VALUE) return null;
                return new Val(Val.INT_DEC, (int) v);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    static Val flt(String s) {
        if (!FLT.matcher(s).matches()) return null;
        try {
            String t = s.endsWith("f") || s.endsWith("F") ? s.substring(0, s.length() - 1) : s;
            return new Val(Val.FLOAT, Float.floatToIntBits(Float.parseFloat(t)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Val dimension(String s) {
        Matcher m = DIM.matcher(s);
        if (!m.matches()) return null;
        int unit;
        String u = m.group(2);
        if (u.equals("px")) unit = 0;
        else if (u.equals("dip") || u.equals("dp")) unit = 1;
        else if (u.equals("sp")) unit = 2;
        else if (u.equals("pt")) unit = 3;
        else if (u.equals("in")) unit = 4;
        else unit = 5;
        try {
            return new Val(Val.DIMEN, complex(Float.parseFloat(m.group(1)), unit));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Val fraction(String s) {
        Matcher m = FRAC.matcher(s);
        if (!m.matches()) return null;
        try {
            float f = Float.parseFloat(m.group(1)) / 100f;
            return new Val(Val.FRACTION, complex(f, m.group(2).equals("%") ? 0 : 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** android ResTable::stringToFloat complex encoding */
    static int complex(float f, int unit) {
        boolean neg = f < 0;
        if (neg) f = -f;
        long bits = (long) (f * (1 << 23) + 0.5f);
        int radix;
        long mantissa;
        if ((bits & 0x7FFFFFL) == 0) { mantissa = bits >> 23; radix = 0; }
        else if ((bits & 0xFFFFFFFFFF800000L) == 0) { mantissa = bits; radix = 3; }
        else if ((bits & 0xFFFFFFFF80000000L) == 0) { mantissa = bits >> 8; radix = 2; }
        else if ((bits & 0xFFFFFF8000000000L) == 0) { mantissa = bits >> 16; radix = 1; }
        else { mantissa = bits >> 23; radix = 0; }
        mantissa &= 0xFFFFFF;
        if (neg) mantissa = (-mantissa) & 0xFFFFFF;
        return (int) ((mantissa << 8) | (radix << 4) | unit);
    }

    /**
     * Android string resource rules: \n \t \\ \' \" \@ \? \#, \\uXXXX; unquoted text has its
     * whitespace collapsed; "quoted text" keeps its whitespace.
     */
    static String unescape(String raw, boolean collapse) {
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        boolean lastSpace = false;
        int n = raw.length();
        for (int i = 0; i < n; i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char d = raw.charAt(++i);
                lastSpace = false;
                switch (d) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'u': {
                        int e = Math.min(n, i + 5);
                        try {
                            sb.append((char) Integer.parseInt(raw.substring(i + 1, e), 16));
                            i = e - 1;
                        } catch (NumberFormatException ex) {
                            sb.append('u');
                        }
                        break;
                    }
                    default: sb.append(d);
                }
                continue;
            }
            if (c == '"') { quoted = !quoted; continue; }
            if (collapse && !quoted && Character.isWhitespace(c)) {
                if (!lastSpace && sb.length() > 0) sb.append(' ');
                lastSpace = true;
                continue;
            }
            lastSpace = false;
            sb.append(c);
        }
        if (collapse && !quoted) {
            int e = sb.length();
            while (e > 0 && sb.charAt(e - 1) == ' ') e--;
            sb.setLength(e);
        }
        return sb.toString();
    }
}
