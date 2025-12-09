package com.mitchej123.jarjar.launch.prism;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for Prism Launcher configuration.
 */
public final class PrismUtils {

    private static final Logger LOG = LogManager.getLogger("JarJar");

    private PrismUtils() {}

    /**
     * Updates Prism Launcher's instance.cfg to include the JarJar agent.
     * Handles JvmArgs and OverrideJavaArgs settings.
     *
     * @param instanceCfg the instance.cfg file
     * @param agentPath the path to add as -javaagent (can be relative or absolute)
     * @return true if update was successful
     */
    public static boolean updateConfig(File instanceCfg, String agentPath) {
        if (instanceCfg == null || !instanceCfg.exists() || agentPath == null) {
            LOG.error("Invalid parameters for updateConfig");
            return false;
        }

        String javaAgentArg = "-javaagent:" + agentPath;

        try {
            List<String> lines = new ArrayList<>();
            boolean foundJvmArgs = false;
            boolean foundOverrideJavaArgs = false;

            // Read and modify
            try (BufferedReader reader = new BufferedReader(new FileReader(instanceCfg))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("JvmArgs=")) {
                        foundJvmArgs = true;
                        String existingArgs = line.substring("JvmArgs=".length());
                        if (existingArgs.contains("-javaagent:") && existingArgs.toLowerCase().contains("jarjar")) {
                            LOG.info("JarJar agent already in JvmArgs");
                            return true;
                        }
                        if (existingArgs.isEmpty()) {
                            line = "JvmArgs=" + javaAgentArg;
                        } else {
                            line = "JvmArgs=" + javaAgentArg + " " + existingArgs;
                        }
                        LOG.info("Updated JvmArgs");
                    } else if (line.startsWith("OverrideJavaArgs=")) {
                        foundOverrideJavaArgs = true;
                        if (!line.equals("OverrideJavaArgs=true")) {
                            line = "OverrideJavaArgs=true";
                            LOG.info("Set OverrideJavaArgs=true");
                        }
                    }
                    lines.add(line);
                }
            }

            // Add missing entries after [General]
            if (!foundJvmArgs || !foundOverrideJavaArgs) {
                int insertIndex = -1;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).equals("[General]")) {
                        insertIndex = i + 1;
                        break;
                    }
                }
                if (insertIndex >= 0) {
                    if (!foundOverrideJavaArgs) {
                        lines.add(insertIndex, "OverrideJavaArgs=true");
                        LOG.info("Added OverrideJavaArgs=true");
                        insertIndex++;
                    }
                    if (!foundJvmArgs) {
                        lines.add(insertIndex, "JvmArgs=" + javaAgentArg);
                        LOG.info("Added JvmArgs={}", javaAgentArg);
                    }
                } else {
                    // No [General] section, append
                    if (!foundJvmArgs) lines.add("JvmArgs=" + javaAgentArg);
                    if (!foundOverrideJavaArgs) lines.add("OverrideJavaArgs=true");
                }
            }

            // Write to temp file first for atomic update
            File tempFile = new File(instanceCfg.getParentFile(), "instance.cfg.tmp");
            try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
                for (String line : lines) {
                    writer.println(line);
                }
            }

            // Rename temp to original
            if (instanceCfg.exists() && !instanceCfg.delete()) {
                LOG.error("Failed to delete original instance.cfg");
                tempFile.delete();
                return false;
            }
            if (!tempFile.renameTo(instanceCfg)) {
                LOG.error("Failed to rename temp file to instance.cfg");
                return false;
            }

            // Verify
            try (BufferedReader reader = new BufferedReader(new FileReader(instanceCfg))) {
                String line;
                boolean hasOverride = false;
                boolean hasAgent = false;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("OverrideJavaArgs=true")) hasOverride = true;
                    if (line.contains("-javaagent:") && line.toLowerCase().contains("jarjar")) hasAgent = true;
                }
                if (hasOverride && hasAgent) {
                    LOG.info("Config update verified");
                    return true;
                } else {
                    LOG.error("Verification failed: override={}, agent={}", hasOverride, hasAgent);
                    return false;
                }
            }

        } catch (IOException e) {
            LOG.error("Failed to update instance.cfg: {}", e.getMessage());
            return false;
        }
    }
}
