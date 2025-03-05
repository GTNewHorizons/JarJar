package com.mitchej123.jarjar.fml.common.discovery.finder;

import com.google.common.collect.ImmutableList;
import com.mitchej123.jarjar.fml.common.DefaultLibraries;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.ModClassLoader;
import cpw.mods.fml.relauncher.CoreModManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class ClasspathModCandidateFinder implements ModCandidateFinder {

    public static final Logger LOGGER = LogManager.getLogger("ClasspathModCandidateFinder");
    private final ModClassLoader modClassLoader;

    public ClasspathModCandidateFinder(ModClassLoader modClassLoader) {
        this.modClassLoader = modClassLoader;
    }

    @Override
    public void findCandidates(ModCandidateConsumer out) {
        LOGGER.info("Searching for classpath mods");
        List<String> knownLibraries = ImmutableList.<String>builder()
            // skip default libs
            .addAll(modClassLoader.getDefaultLibraries())
            // skip loaded coremods
            .addAll(CoreModManager.getLoadedCoremods())
            // skip reparable coremods here
            .addAll(CoreModManager.getReparseableCoremods())
            .build();
        final File[] minecraftSources = modClassLoader.getParentSources();
        if (minecraftSources.length == 1 && minecraftSources[0].isFile()) {
            FMLLog.fine("Minecraft is a file at %s, loading", minecraftSources[0].getAbsolutePath());
            out.accept(Collections.singletonList(minecraftSources[0].toPath()), true, true, true);
        } else {
            for (int i = 0; i < minecraftSources.length; i++) {
                if (minecraftSources[i].isFile()) {
                    if (knownLibraries.contains(minecraftSources[i].getName()) || DefaultLibraries.isDefaultLibrary(minecraftSources[i])) {
                        FMLLog.finer("Skipping known library file %s", minecraftSources[i].getAbsolutePath());
                    } else {
                        FMLLog.fine("Found a minecraft related file at %s, examining for mod candidates", minecraftSources[i].getAbsolutePath());
                        out.accept(Collections.singletonList(minecraftSources[i].toPath()), true, i == 0, true);
                    }
                } else if (minecraftSources[i].isDirectory()) {
                    FMLLog.fine("Found a minecraft related directory at %s, ignoring", minecraftSources[i].getAbsolutePath());
                }
            }
        }
    }

}
