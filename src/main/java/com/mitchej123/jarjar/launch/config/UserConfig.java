package com.mitchej123.jarjar.launch.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

/**
 * Configuration for JarJar agent preferences.
 * Saves user's choice for handling missing agent on Java 8.
 */
public class UserConfig {

    private static final Logger LOG = LogManager.getLogger("JarJar");
    private static final String CONFIG_FILE = "jarjar.cfg";

    public enum Choice {
        DIRECT_RFB,       // Install RFB as launcher component (no relaunch needed)
        RELAUNCH_NOW,     // One-time relaunch with -javaagent
        CONTINUE_WITHOUT  // JarJar becomes NOOP, continue launch
    }

    private Choice savedChoice;
    private File configFile;

    private UserConfig(File configFile) {
        this.configFile = configFile;
    }

    public Choice getSavedChoice() {
        return savedChoice;
    }

    public void setSavedChoice(Choice choice) {
        this.savedChoice = choice;
    }

    /**
     * Load config from gameDir/jarjar.cfg
     */
    public static UserConfig load(File gameDir) {
        File configFile = new File(gameDir, CONFIG_FILE);
        UserConfig config = new UserConfig(configFile);

        if (!configFile.exists()) {
            return config;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            Properties props = new Properties();
            props.load(reader);

            String choiceStr = props.getProperty("agentChoice");
            if (choiceStr != null) {
                try {
                    config.savedChoice = Choice.valueOf(choiceStr);
                    LOG.debug("Loaded saved choice: {}", config.savedChoice);
                } catch (IllegalArgumentException e) {
                    LOG.warn("Invalid saved choice '{}', ignoring", choiceStr);
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to load config: {}", e.getMessage());
        }

        return config;
    }

    /**
     * Save config to gameDir/jarjar.cfg
     */
    public void save() {
        if (savedChoice == null) {
            return;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            Properties props = new Properties();
            props.setProperty("agentChoice", savedChoice.name());
            props.store(writer, "JarJar configuration");
            LOG.info("Saved choice: {}", savedChoice);
        } catch (IOException e) {
            LOG.error("Failed to save config: {}", e.getMessage());
        }
    }

    /**
     * Clear saved choice (delete config file)
     */
    public void clear() {
        savedChoice = null;
        if (configFile.exists()) {
            if (configFile.delete()) {
                LOG.info("Cleared saved config");
            } else {
                LOG.warn("Failed to delete config file");
            }
        }
    }
}
