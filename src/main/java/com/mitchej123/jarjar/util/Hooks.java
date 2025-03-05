package com.mitchej123.jarjar.util;

import com.mitchej123.jarjar.fml.common.DefaultLibraries;
import com.mitchej123.jarjar.fml.common.discovery.finder.DirectoryModCandidateFinder;

import java.io.File;

@SuppressWarnings("unused")
public class Hooks {
    /**
     * Check if the jar file should be ignored.
     *  - It's included in the default libraries
     *  - It's something that has been disabled by LoaderV2
     */
    public static boolean shouldIgnore(File file) {
        return DefaultLibraries.isDefaultLibrary(file) || DirectoryModCandidateFinder.getDisabledFiles().contains(file.getName());
    }
}
