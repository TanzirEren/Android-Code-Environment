package com.ace.ide.build;

/** A typed resource value (Res_value). {@code str} is set for string values; data is assigned at flatten time. */
final class Val {
    static final int NULL = 0x00, REF = 0x01, ATTR = 0x02, STRING = 0x03, FLOAT = 0x04, DIMEN = 0x05,
            FRACTION = 0x06, INT_DEC = 0x10, INT_HEX = 0x11, BOOL = 0x12,
            COLOR_ARGB8 = 0x1C, COLOR_RGB8 = 0x1D, COLOR_ARGB4 = 0x1E, COLOR_RGB4 = 0x1F;

    int type;
    int data;
    String str;

    Val(int type, int data) { this.type = type; this.data = data; }

    static Val string(String s) { Val v = new Val(STRING, 0); v.str = s; return v; }
    static Val ref(int id) { return new Val(REF, id); }
}
