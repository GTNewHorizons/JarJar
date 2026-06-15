package com.mitchej123.jarjar;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/*
 * Note: Don't use Forge's config as this needs to load early without all of Forge's classes loaded
 */
public class Config {

    private static final Logger LOGGER = LogManager.getLogger("JarJar");

    public static final int maxThreads;
    public static final boolean enableSortingIndexOverrides;
    public static final Object2IntMap<String> sortingIndexOverrides;

    static {
        Properties config = new Properties();
        File configLocation = new File(Launch.minecraftHome, "config/jarjar.properties");
        try (Reader r = new BufferedReader(new FileReader(configLocation))) {
            config.load(r);
        } catch (FileNotFoundException e) {
            LOGGER.debug("No existing configuration file. Will use defaults");
        } catch (IOException e) {
            LOGGER.error("Error reading configuration file. Will use defaults", e);
        }
        final int threads = Integer.parseInt(config.getProperty("maxThreads", "-1"));
        maxThreads = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();

        config.putIfAbsent("enableSortingIndexOverrides", "true");
        enableSortingIndexOverrides = Boolean.parseBoolean(config.getProperty("enableSortingIndexOverrides"));

        final Object2IntMap<String> overrides = new Object2IntOpenHashMap<>();
        final String sortingIndex = "SortingIndex.";
        final int subStrIdx = sortingIndex.length();
        for (String name : config.stringPropertyNames()) {
            if (name.startsWith(sortingIndex)) {
                try {
                    final String coremodClass = name.substring(subStrIdx);
                    final int sortOrderOverride = Integer.parseInt(config.getProperty(name).trim());
                    overrides.put(coremodClass, sortOrderOverride);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Ignoring non-integer SortingIndex override {}", name);
                }
            }
        }
        sortingIndexOverrides = overrides;

        try (Writer r = new BufferedWriter(new FileWriter(configLocation))) {
            config.store(r, "Coremod SortingIndex override: SortingIndex.<coremod class>=<number> sets a coremod's load order.\nExample: SortingIndex.com.mitchej123.hodgepodge.core.HodgepodgeCore=1");
        } catch (IOException e) {
            LOGGER.error("Error reading configuration file. Will use defaults", e);
        }
    }

    public static void ensureLoaded() {}

}
