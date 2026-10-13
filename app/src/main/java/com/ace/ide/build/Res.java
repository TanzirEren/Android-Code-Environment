package com.ace.ide.build;

import java.util.ArrayList;
import java.util.List;

/** A fully resolved resource definition (one entry in one configuration). */
final class Res {
    static final class Item {
        int nameId;
        Val v;
        Item(int nameId, Val v) { this.nameId = nameId; this.v = v; }
    }

    boolean complex;
    Val val;                 // simple entries (also file references)
    int parent;              // complex entries
    final List<Item> items = new ArrayList<Item>();
}
