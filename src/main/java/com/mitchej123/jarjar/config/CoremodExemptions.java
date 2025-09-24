package com.mitchej123.jarjar.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the list of coremod class names that should be exempt from duplicate detection.
 */
public class CoremodExemptions {
    private static final Logger LOGGER = LogManager.getLogger("CoremodExemptions");
    private static final String CONFIG_PATH = "/META-INF/jarjar_coremod_exemptions.cfg";

    private static Set<String> exemptedCoremods = null;

    /**
     * Check if a coremod class name is exempt from duplicate detection
     * @param coremodClassName The fully qualified class name of the coremod
     * @return true if this coremod should be exempt from duplicate detection
     */
    public static boolean isExempt(String coremodClassName) {
        if (exemptedCoremods == null) {
            loadExemptions();
        }
        return exemptedCoremods.contains(coremodClassName);
    }

    /**
     * Load the exemption list from the configuration file
     */
    private static void loadExemptions() {
        exemptedCoremods = new HashSet<>();

        try (InputStream is = CoremodExemptions.class.getResourceAsStream(CONFIG_PATH)) {
            if (is == null) {
                LOGGER.warn("Could not find coremod exemptions config file: {}", CONFIG_PATH);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    // Skip empty lines and comments
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    exemptedCoremods.add(line);
                    LOGGER.debug("Added coremod exemption: {}", line);
                }

                LOGGER.info("Loaded {} coremod exemptions from {}", exemptedCoremods.size(), CONFIG_PATH);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load coremod exemptions from {}", CONFIG_PATH, e);
        }
    }

}
