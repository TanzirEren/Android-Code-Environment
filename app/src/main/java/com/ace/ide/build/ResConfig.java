package com.ace.ide.build;

/** A resource configuration parsed from a directory qualifier like "hdpi", "night-v29", "sw600dp-land". */
final class ResConfig implements Comparable<ResConfig> {

    int mcc, mnc;
    char lang0, lang1, cc0, cc1;
    int orientation, touch, density;
    int sdk;
    int screenLayout, uiMode, smallestSw, screenW, screenH;
    int layoutDirection;   // folded into screenLayout bits
    String key = "";

    static final ResConfig DEFAULT = new ResConfig();

    boolean isDefault() { return key.length() == 0; }

    /** @return null and sets err[0] when a qualifier is not understood */
    static ResConfig parse(String qualifiers, String[] err) {
        ResConfig c = new ResConfig();
        if (qualifiers == null || qualifiers.length() == 0) return c;
        String[] parts = qualifiers.split("-");
        for (int i = 0; i < parts.length; i++) {
            String q = parts[i];
            if (q.length() == 0) continue;
            String lq = q.toLowerCase();
            if (lq.equals("ldpi")) c.density = 120;
            else if (lq.equals("mdpi")) c.density = 160;
            else if (lq.equals("tvdpi")) c.density = 213;
            else if (lq.equals("hdpi")) c.density = 240;
            else if (lq.equals("xhdpi")) c.density = 320;
            else if (lq.equals("xxhdpi")) c.density = 480;
            else if (lq.equals("xxxhdpi")) c.density = 640;
            else if (lq.equals("nodpi")) c.density = 0xFFFF;
            else if (lq.equals("anydpi")) c.density = 0xFFFE;
            else if (lq.matches("\\d+dpi")) c.density = Integer.parseInt(lq.substring(0, lq.length() - 3));
            else if (lq.equals("night")) c.uiMode = (c.uiMode & ~0x30) | 0x20;
            else if (lq.equals("notnight")) c.uiMode = (c.uiMode & ~0x30) | 0x10;
            else if (lq.equals("port")) c.orientation = 1;
            else if (lq.equals("land")) c.orientation = 2;
            else if (lq.equals("small")) c.screenLayout = (c.screenLayout & ~0x0F) | 1;
            else if (lq.equals("normal")) c.screenLayout = (c.screenLayout & ~0x0F) | 2;
            else if (lq.equals("large")) c.screenLayout = (c.screenLayout & ~0x0F) | 3;
            else if (lq.equals("xlarge")) c.screenLayout = (c.screenLayout & ~0x0F) | 4;
            else if (lq.equals("long")) c.screenLayout = (c.screenLayout & ~0x30) | 0x20;
            else if (lq.equals("notlong")) c.screenLayout = (c.screenLayout & ~0x30) | 0x10;
            else if (lq.equals("ldltr")) c.screenLayout = (c.screenLayout & ~0xC0) | 0x40;
            else if (lq.equals("ldrtl")) c.screenLayout = (c.screenLayout & ~0xC0) | 0x80;
            else if (lq.equals("desk")) c.uiMode = (c.uiMode & ~0x0F) | 2;
            else if (lq.equals("car")) c.uiMode = (c.uiMode & ~0x0F) | 3;
            else if (lq.equals("television")) c.uiMode = (c.uiMode & ~0x0F) | 4;
            else if (lq.equals("appliance")) c.uiMode = (c.uiMode & ~0x0F) | 5;
            else if (lq.equals("watch")) c.uiMode = (c.uiMode & ~0x0F) | 6;
            else if (lq.matches("v\\d+")) c.sdk = Integer.parseInt(lq.substring(1));
            else if (lq.matches("sw\\d+dp")) c.smallestSw = Integer.parseInt(lq.substring(2, lq.length() - 2));
            else if (lq.matches("w\\d+dp")) c.screenW = Integer.parseInt(lq.substring(1, lq.length() - 2));
            else if (lq.matches("h\\d+dp")) c.screenH = Integer.parseInt(lq.substring(1, lq.length() - 2));
            else if (lq.matches("mcc\\d+")) c.mcc = Integer.parseInt(lq.substring(3));
            else if (lq.matches("mnc\\d+")) c.mnc = Integer.parseInt(lq.substring(3));
            else if (q.matches("[a-z]{2}") && c.lang0 == 0) { c.lang0 = q.charAt(0); c.lang1 = q.charAt(1); }
            else if (q.matches("r[A-Z]{2}") && c.lang0 != 0) { c.cc0 = q.charAt(1); c.cc1 = q.charAt(2); }
            else {
                if (err != null) err[0] = "unsupported resource qualifier '" + q + "'";
                return null;
            }
        }
        c.key = qualifiers;
        return c;
    }

    /** ResTable_config, 52 bytes */
    byte[] toBytes() {
        Bin b = new Bin();
        b.u32(52);
        b.u16(mcc).u16(mnc);
        b.u8(lang0).u8(lang1).u8(cc0).u8(cc1);
        b.u8(orientation).u8(touch).u16(density);
        b.u8(0).u8(0).u8(0).u8(0);
        b.u16(0).u16(0);
        b.u16(sdk).u16(0);
        b.u8(screenLayout).u8(uiMode).u16(smallestSw);
        b.u16(screenW).u16(screenH);
        b.zeros(4);
        b.zeros(8);
        b.u8(0).u8(0).u16(0);
        return b.toByteArray();
    }

    /** which configuration dimensions differ from the default (ResTable_typeSpec flags) */
    int specFlags() {
        int f = 0;
        if (mcc != 0) f |= 0x0001;
        if (mnc != 0) f |= 0x0002;
        if (lang0 != 0) f |= 0x0004;
        if (orientation != 0) f |= 0x0080;
        if ((screenLayout & 0x3F) != 0) f |= 0x0100;
        if ((screenLayout & 0xC0) != 0) f |= 0x2000;
        if (uiMode != 0) f |= 0x0200;
        if (screenW != 0 || screenH != 0) f |= 0x0400;
        if (smallestSw != 0) f |= 0x0800;
        if (density != 0) f |= 0x1000;
        if (sdk != 0) f |= 0x10000000;
        return f;
    }

    @Override public int compareTo(ResConfig o) { return key.compareTo(o.key); }
}
