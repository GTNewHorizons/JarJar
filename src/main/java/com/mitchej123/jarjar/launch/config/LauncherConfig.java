package com.mitchej123.jarjar.launch.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Detects launcher type and modifies launcher configuration to add -javaagent.
 */
public class LauncherConfig {

    private static final Logger LOG = LogManager.getLogger("JarJar");

    public enum LauncherType {
        PRISM,      // Prism Launcher (or MultiMC, same format)
        MULTIMC,    // MultiMC (same format as Prism)
        UNKNOWN     // Unknown launcher
    }

    /**
     * Detect launcher type from gameDir structure.
     * gameDir is typically .minecraft, instance.cfg is in parent directory.
     */
    public static LauncherType detectLauncher(File gameDir) {
        if (gameDir == null) {
            return LauncherType.UNKNOWN;
        }

        File parentDir = gameDir.getParentFile();
        if (parentDir == null) {
            return LauncherType.UNKNOWN;
        }

        File instanceCfg = new File(parentDir, "instance.cfg");
        if (instanceCfg.exists()) {
            LOG.debug("Found instance.cfg at {}", instanceCfg.getAbsolutePath());
            return LauncherType.PRISM;
        }

        return LauncherType.UNKNOWN;
    }

    /**
     * Get human-readable launcher name.
     */
    public static String getLauncherName(LauncherType type) {
        switch (type) {
            case PRISM:
                return "Prism Launcher";
            case MULTIMC:
                return "MultiMC";
            default:
                return "Unknown";
        }
    }
}
