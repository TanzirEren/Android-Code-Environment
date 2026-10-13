package com.ace.ide;

import android.content.Context;

/** ACE palette — taken from the logo (warm sand, khaki and dark olive). */
public class Theme {

    public final boolean dark;
    public final int bg, card, cardStroke, text, textSub, accent, accentDark, accentSoft, danger, dangerSoft,
            chipBg, inputBg, headerTop, headerBottom, onAccent;
    // glass
    public final int glassFill, glassStroke, glassScrim;
    // editor
    public final int edBg, edText, edGutter, edGutterText, edLine, edKw, edStr, edCom, edNum, edAnn, edTyp, edTag, edAttr, edErr, edSel;

    public Theme(boolean dark) {
        this.dark = dark;
        if (dark) {
            bg = 0xFF15140F;
            card = 0xFF211F18;
            cardStroke = 0xFF37342A;
            text = 0xFFECE7D9;
            textSub = 0xFFA8A28C;
            accent = 0xFFB9B28D;
            accentDark = 0xFF928C6F;
            accentSoft = 0x33B9B28D;
            danger = 0xFFF0746C;
            dangerSoft = 0x33F0746C;
            chipBg = 0xFF2B2920;
            inputBg = 0xFF1A1913;
            headerTop = 0xFF2A281F;
            headerBottom = 0xFF4A4735;
            onAccent = 0xFF1F1E16;
            glassFill = 0x66241F18 | 0x00000000;
            glassStroke = 0x33FFFFFF;
            glassScrim = 0x99000000;
            edBg = 0xFF1B1A14;
            edText = 0xFFE6E1D2;
            edGutter = 0xFF211F18;
            edGutterText = 0xFF7E7967;
            edLine = 0x22FFFFFF;
            edKw = 0xFFD8A657;
            edStr = 0xFF9FC27C;
            edCom = 0xFF7D7966;
            edNum = 0xFFD98B6E;
            edAnn = 0xFFC9B26B;
            edTyp = 0xFF86B7C9;
            edTag = 0xFFD8A657;
            edAttr = 0xFF86B7C9;
            edErr = 0xFFF0746C;
            edSel = 0x55B9B28D;
        } else {
            bg = 0xFFF3EEEA;
            card = 0xFFFBF8F5;
            cardStroke = 0xFFE0D7D0;
            text = 0xFF2B2A22;
            textSub = 0xFF6F6A5A;
            accent = 0xFF6B6753;
            accentDark = 0xFF55523F;
            accentSoft = 0x226B6753;
            danger = 0xFFC0392B;
            dangerSoft = 0x22C0392B;
            chipBg = 0xFFEAE3DD;
            inputBg = 0xFFFFFFFF;
            headerTop = 0xFF55523F;
            headerBottom = 0xFF928C6F;
            onAccent = 0xFFFFFFFF;
            glassFill = 0x99FFFFFF;
            glassStroke = 0x99FFFFFF;
            glassScrim = 0x66000000;
            edBg = 0xFFFBF8F5;
            edText = 0xFF2B2A22;
            edGutter = 0xFFF0EAE4;
            edGutterText = 0xFF9A9482;
            edLine = 0x14000000;
            edKw = 0xFF8A5A12;
            edStr = 0xFF4E7A2C;
            edCom = 0xFF9A9482;
            edNum = 0xFFB0522F;
            edAnn = 0xFF8A7415;
            edTyp = 0xFF2B6A86;
            edTag = 0xFF8A5A12;
            edAttr = 0xFF2B6A86;
            edErr = 0xFFC0392B;
            edSel = 0x446B6753;
        }
    }

    public static Theme get(Context c) { return new Theme(Prefs.dark(c)); }
}
