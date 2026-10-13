package com.ace.ide.build;

import java.io.File;
import java.util.List;

/** .class -> .dex step (D8 in the app, anything else in tests). */
public interface Dexer {
    /**
     * @param classDirs directories with the app's compiled classes
     * @param libJars   library jars that must be merged into the dex
     * @param androidJar framework classes (library, not merged)
     * @return error text or null when everything went fine; dex files end up in outDir
     */
    String dex(List<File> classDirs, List<File> libJars, File androidJar, int minApi, boolean debug,
               File outDir, StringBuilder log);
}
