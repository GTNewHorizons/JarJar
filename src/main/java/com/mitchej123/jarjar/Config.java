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

/*
 * Note: Don't use Forge's config as this needs to load early without all of Forge's classes loaded
 */
public class Config {

    private static final Logger LOGGER = LogManager.getLogger("JarJar");

    public static final int maxThreads;

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

        try (Writer r = new BufferedWriter(new FileWriter(configLocation))) {
            config.store(r, "Configuration file for early hodgepodge class transformers");
        } catch (IOException e) {
            LOGGER.error("Error reading configuration file. Will use defaults", e);
        }
    }

    public static void ensureLoaded() {}

}
