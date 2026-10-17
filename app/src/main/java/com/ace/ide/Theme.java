package com.ace.ide;

import android.content.Context;

/** ACE palette: background #385F8C, icons and other UI #E9E9E9. */
public class Theme {

    public static final int BG = 0xFF385F8C;
    public static final int FG = 0xFFE9E9E9;

    public final boolean dark = true;       // single blue theme, always "dark" (light text on dark background)
    public final int bg = BG;
    public final int card = 0xFF30517B;
    public final int cardStroke = 0x40E9E9E9;
    public final int text = FG;
    public final int textSub = 0xFFB6C3D3;
    public final int accent = FG;
    public final int accentDark = 0xFFC9D1DB;
    public final int accentSoft = 0x30E9E9E9;
    public final int danger = 0xFFFF8A80;
    public final int dangerSoft = 0x33FF8A80;
    public final int chipBg = 0xFF2C4B74;
    public final int inputBg = 0xFF2A4870;
    public final int headerTop = 0xFF2F5179;
    public final int headerBottom = 0xFF4A729D;
    public final int onAccent = BG;
    // glass + overlays
    public final int glassFill = 0x33E9E9E9;
    public final int glassStroke = 0x55E9E9E9;
    public final int glassScrim = 0x99000000;
    public final int dialog = 0xF2294A73;
    public final int panelTint = 0xCC2A4A72;
    public final int navFill = 0xF02A4A72;
    // editor
    public final int edBg = 0xFF2F4F7A;
    public final int edText = FG;
    public final int edGutter = 0xFF2A4870;
    public final int edGutterText = 0xFF8FA6C2;
    public final int edLine = 0x22FFFFFF;
    public final int edKw = 0xFFFFC66D;
    public final int edStr = 0xFFA5D6A7;
    public final int edCom = 0xFF8FA6C2;
    public final int edNum = 0xFFFFAB91;
    public final int edAnn = 0xFFE6D27A;
    public final int edTyp = 0xFF9ADCF5;
    public final int edTag = 0xFFFFC66D;
    public final int edAttr = 0xFF80DEEA;
    public final int edErr = 0xFFFF8A80;
    public final int edSel = 0x55E9E9E9;

    public Theme() { }

    public static Theme get(Context c) { return new Theme(); }
}
