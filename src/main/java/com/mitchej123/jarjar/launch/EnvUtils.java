package com.mitchej123.jarjar.launch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared utilities for the JarJar launch system.
 */
public final class EnvUtils {

    private static Logger LOG;
    private static final String JAVA_VERSION = System.getProperty("java.version");

    private static Logger getLogger() {
        if (LOG == null) {
            LOG = LogManager.getLogger("JarJar");
        }
        return LOG;
    }
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase();
    private static final String USER_HOME = System.getProperty("user.home");

    /**
     * System property prefixes that are set by the JVM itself and shouldn't be forwarded
     * when relaunching.
     */
    public static final String[] SYSTEM_PROPERTY_PREFIXES = {
        "java.", "os.", "path.", "file.", "line.", "user.", "native.", "com.sun.", "sun.", "awt."
    };

    private EnvUtils() {}

    // ==================== Platform Detection ====================

    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    public static boolean isMacOS() {
        return OS_NAME.contains("mac");
    }

    public static boolean isLinux() {
        return OS_NAME.contains("linux") || OS_NAME.contains("nix") || OS_NAME.contains("nux");
    }

    /**
     * Check if running in a headless environment
     */
    public static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    // ==================== Cache/Data Directories ====================

    /**
     * Get the platform-specific cache directory for JarJar.
     * - Windows: %LOCALAPPDATA%/jarjar
     * - macOS: ~/Library/Caches/jarjar
     * - Linux: $XDG_CACHE_HOME/jarjar or ~/.cache/jarjar
     */
    public static Path getCacheDir() {
        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                return Paths.get(localAppData, "jarjar");
            }
            return Paths.get(USER_HOME, "AppData", "Local", "jarjar");
        } else if (isMacOS()) {
            return Paths.get(USER_HOME, "Library", "Caches", "jarjar");
        } else {
            String xdgCacheHome = System.getenv("XDG_CACHE_HOME");
            if (xdgCacheHome != null) {
                return Paths.get(xdgCacheHome, "jarjar");
            }
            return Paths.get(USER_HOME, ".cache", "jarjar");
        }
    }

    /**
     * Get the platform-specific Prism Launcher data directory.
     * Returns null if not found.
     */
    public static File getPrismDataDirectory() {
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                File dir = new File(appData, "PrismLauncher");
                if (dir.exists()) return dir;
            }
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                File dir = new File(localAppData, "PrismLauncher");
                if (dir.exists()) return dir;
            }
        } else if (isMacOS()) {
            File dir = new File(USER_HOME, "Library/Application Support/PrismLauncher");
            if (dir.exists()) return dir;
        } else {
            String xdgData = System.getenv("XDG_DATA_HOME");
            if (xdgData != null) {
                File dir = new File(xdgData, "PrismLauncher");
                if (dir.exists()) return dir;
            }
            File dir = new File(USER_HOME, ".local/share/PrismLauncher");
            if (dir.exists()) return dir;
        }
        return null;
    }

    /**
     * Get standard Prism/MultiMC root directories to search.
     */
    public static List<File> getStandardLauncherRoots() {
        List<File> roots = new ArrayList<>();

        if (isLinux()) {
            roots.add(new File(USER_HOME, ".local/share/PrismLauncher"));
            roots.add(new File(USER_HOME, ".local/share/multimc"));
            roots.add(new File(USER_HOME, ".local/share/polymc"));
            String xdgData = System.getenv("XDG_DATA_HOME");
            if (xdgData != null) {
                roots.add(new File(xdgData, "PrismLauncher"));
            }
            // Flatpak
            roots.add(new File(USER_HOME, ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"));
        } else if (isMacOS()) {
            roots.add(new File(USER_HOME, "Library/Application Support/PrismLauncher"));
            roots.add(new File(USER_HOME, "Library/Application Support/MultiMC"));
        } else if (isWindows()) {
            String appData = System.getenv("APPDATA");
            String localAppData = System.getenv("LOCALAPPDATA");
            if (appData != null) {
                roots.add(new File(appData, "PrismLauncher"));
            }
            if (localAppData != null) {
                roots.add(new File(localAppData, "PrismLauncher"));
            }
            // Scoop
            roots.add(new File(USER_HOME, "scoop/persist/prismlauncher"));
        }

        return roots;
    }

    /**
     * Check if running on Java 8.
     */
    public static boolean isJava8() {
        return JAVA_VERSION.startsWith("1.8");
    }

    /**
     * Get the Java version string.
     */
    public static String getJavaVersion() {
        return JAVA_VERSION;
    }

    /**
     * Check if RFB is already configured as the system classloader.
     */
    public static boolean isRfbActive() {
        String systemClassLoader = System.getProperty("java.system.class.loader");
        return systemClassLoader != null &&
               systemClassLoader.contains("retrofuturabootstrap");
    }

    /**
     * Check if we're running in a properly configured RFB environment.
     * On Java 8: true if RFB was set up (via Direct RFB install or tweaker relaunch).
     * On Java 9+: true (RFB is handled by lwjgl3ify).
     */
    public static boolean isRfbEnvironment() {
        if (!isJava8()) {
            return true; // Java 9+ uses RFB via lwjgl3ify
        }
        return isRfbActive();
    }

    /**
     * Check if a system property key is a JVM-internal property that shouldn't be forwarded.
     */
    public static boolean isSystemProperty(String key) {
        for (String prefix : SYSTEM_PROPERTY_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compute a relative path from a base directory to a file, if the file is inside the base.
     * Returns the original path if not inside the base or on error.
     *
     * @param filePath the file path (absolute or relative)
     * @param baseDir the base directory
     * @return relative path if file is inside baseDir, otherwise the original filePath
     */
    public static String computeRelativePath(String filePath, File baseDir) {
        if (filePath == null || baseDir == null) {
            return filePath;
        }
        try {
            File file = new File(filePath).getCanonicalFile();
            File baseDirCanonical = baseDir.getCanonicalFile();
            String filePathStr = file.getPath();
            String baseDirStr = baseDirCanonical.getPath();
            if (filePathStr.startsWith(baseDirStr + File.separator)) {
                return filePathStr.substring(baseDirStr.length() + 1);
            }
        } catch (IOException ignored) {
            // Fall through to return original
        }
        return filePath;
    }

    /**
     * Extract a relative path for display in manual instructions.
     * If the path contains a "mods" directory, returns from "mods/" onward.
     *
     * @param jarPath the jar path
     * @return a user-friendly relative path
     */
    public static String computeModsRelativePath(String jarPath) {
        if (jarPath == null) {
            return null;
        }
        // Check for mods directory in path
        int modsIdx = jarPath.lastIndexOf("mods" + File.separator);
        if (modsIdx < 0) {
            modsIdx = jarPath.lastIndexOf("mods/");
        }
        if (modsIdx >= 0) {
            return jarPath.substring(modsIdx);
        }
        return jarPath;
    }

    /**
     * Get the path to the Java executable for the current JVM.
     */
    public static String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        String executable = isWindows() ? "java.exe" : "java";
        return Paths.get(javaHome, "bin", executable).toString();
    }

    // ==================== JVM Args Utilities ====================

    /**
     * Result of filtering JVM arguments for relaunch.
     */
    public static class JvmArgsResult {
        public final List<String> args;
        public final boolean hasDockName;
        public final boolean hasDockIcon;

        public JvmArgsResult(List<String> args, boolean hasDockName, boolean hasDockIcon) {
            this.args = args;
            this.hasDockName = hasDockName;
            this.hasDockIcon = hasDockIcon;
        }
    }

    /**
     * Filter JVM arguments for relaunching, removing agent args and system classloader settings.
     * Also detects and warns about debug agents.
     *
     * @param inputArgs the original JVM input arguments
     * @param relaunchMarker the system property used to mark relaunch (to skip)
     * @return filtered args and dock arg presence flags
     */
    public static JvmArgsResult filterJvmArgsForRelaunch(List<String> inputArgs, String relaunchMarker) {
        List<String> filtered = new ArrayList<>();
        boolean hasDockName = false;
        boolean hasDockIcon = false;

        for (String arg : inputArgs) {
            // Skip agent arguments (we don't want to re-run the agent)
            if (arg.startsWith("-javaagent:")) {
                continue;
            }
            // Skip system classloader setting (we'll set our own)
            if (arg.startsWith("-Djava.system.class.loader=")) {
                continue;
            }
            // Skip relaunch marker if present
            if (relaunchMarker != null && arg.startsWith("-D" + relaunchMarker)) {
                continue;
            }
            // Detect debug agent - port will conflict since parent holds it during relaunch
            if (arg.startsWith("-agentlib:jdwp") || arg.startsWith("-Xrunjdwp:")) {
                getLogger().error("Debug agent detected: {}", arg);
                getLogger().error("The debug port will fail to bind because the parent process holds it during relaunch.");
                getLogger().error("To debug on Java 8, use one of these alternatives:");
                getLogger().error("  1. Install Direct RFB (run with -Djarjar.manage=true to configure)");
                getLogger().error("  2. Use Java 11+ (RFB is set up by lwjgl3ify, no relaunch needed)");
                continue;
            }
            if (arg.startsWith("-Xdock:name=")) {
                hasDockName = true;
            }
            if (arg.startsWith("-Xdock:icon=")) {
                hasDockIcon = true;
            }
            filtered.add(arg);
        }

        return new JvmArgsResult(filtered, hasDockName, hasDockIcon);
    }

    /**
     * Add macOS dock arguments if not already present.
     *
     * @param cmd the command list to add to
     * @param hasDockName whether dock name is already set
     * @param hasDockIcon whether dock icon is already set
     * @param gameDir the game directory to search for icon
     */
    public static void addMacOSDockArgs(List<String> cmd, boolean hasDockName, boolean hasDockIcon, File gameDir) {
        if (!isMacOS()) {
            return;
        }

        if (!hasDockName) {
            cmd.add("-Xdock:name=Minecraft 1.7.10");
        }
        if (!hasDockIcon && gameDir != null) {
            // Try to find an icon in common locations
            String[] iconPaths = {
                new File(gameDir, "icon.png").getAbsolutePath(),
                new File(gameDir.getParentFile(), "icon.png").getAbsolutePath()
            };
            for (String iconPath : iconPaths) {
                File iconFile = new File(iconPath);
                if (iconFile.exists()) {
                    cmd.add("-Xdock:icon=" + iconFile.getAbsolutePath());
                    cmd.add("-Dapple.awt.application.icon=" + iconFile.getAbsolutePath());
                    break;
                }
            }
        }
    }

    /**
     * Split a command string respecting quoted sections.
     * Handles double quotes but not escaped quotes or single quotes.
     * Preserves empty quoted strings (e.g., "" becomes an empty string argument).
     */
    public static String[] splitCommandString(String command) {
        if (command == null || command.isEmpty()) {
            return new String[0];
        }

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        boolean wasQuoted = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '"') {
                wasQuoted = true;
                inQuote = !inQuote;
            } else if (c == ' ' && !inQuote) {
                if (current.length() > 0 || wasQuoted) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                }
                wasQuoted = false;
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0 || wasQuoted) {
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }

    /**
     * Exit the JVM safely, bypassing shutdown hooks if possible.
     * Uses halt0 directly to avoid hooks that might block shutdown.
     */
    public static void safeExit(int code) {
        try {
            Class<?> shutdownClass = Class.forName("java.lang.Shutdown");
            java.lang.reflect.Method halt0 = shutdownClass.getDeclaredMethod("halt0", int.class);
            halt0.setAccessible(true);
            halt0.invoke(null, code);
        } catch (Exception e) {
            getLogger().warn("halt0 failed, trying Runtime.exit: {}", e.getMessage());
            try {
                Runtime.getRuntime().exit(code);
            } catch (Exception e2) {
                getLogger().error("All exit methods failed, attempting Runtime.halt: {}", e2.getMessage());
                Runtime.getRuntime().halt(code);
            }
        }
    }
}
