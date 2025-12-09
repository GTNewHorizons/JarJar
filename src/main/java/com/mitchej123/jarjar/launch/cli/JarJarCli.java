package com.mitchej123.jarjar.launch.cli;

import com.mitchej123.jarjar.launch.EnvUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * CLI entry point for JarJar setup utility.
 * Handles classpath setup (finding/downloading dependencies) before delegating to SetupDialog.
 *
 * Java 8 only - uses URLClassLoader hack to add JARs at runtime.
 */
public class JarJarCli {

    private static File prismRoot = null;
    private static File instanceDir = null;

    public static void main(String[] args) {
        // Check Java version
        String javaVersion = System.getProperty("java.version", "");
        if (!javaVersion.startsWith("1.8")) {
            System.err.println("JarJar CLI is for Java 8 only.");
            System.err.println("On Java 9+, RFB handles things directly.");
            System.exit(1);
        }

        // Parse instance dir from CLI args early (needed for library detection)
        parseInstanceDirFromArgs(args);

        // Determine locations from JAR path and standard locations
        detectLocations();

        // Load required dependencies
        loadDependencies();

        // Delegate to SetupDialog
        try {
            Class<?> setupDialog = Class.forName("com.mitchej123.jarjar.launch.ui.SetupDialog");
            Method main = setupDialog.getMethod("main", String[].class);
            main.invoke(null, (Object) args);
        } catch (Exception e) {
            System.err.println("Failed to launch SetupDialog: " + e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            } else {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private static void parseInstanceDirFromArgs(String[] args) {
        // Look for instance dir in CLI args: --install <dir>, --uninstall <dir>, etc.
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];
            if (arg.startsWith("--") && !arg.equals("--help")) {
                File candidate = new File(args[i + 1]);
                if (candidate.isDirectory()) {
                    instanceDir = candidate;
                    System.out.println("Using instance dir from args: " + instanceDir);
                    break;
                }
            }
        }
        // Also check for bare directory argument (no flag)
        if (instanceDir == null && args.length > 0 && !args[0].startsWith("--")) {
            File candidate = new File(args[0]);
            if (candidate.isDirectory()) {
                instanceDir = candidate;
                System.out.println("Using instance dir from args: " + instanceDir);
            }
        }
    }

    private static void detectLocations() {
        // Try to detect from JAR location first
        File jarLocation = getJarLocation();
        if (jarLocation != null) {
            // JAR is likely in mods/: <instance>/.minecraft/mods/jarjar.jar
            File modsDir = jarLocation.getParentFile();
            if (modsDir != null && "mods".equals(modsDir.getName())) {
                File minecraftDir = modsDir.getParentFile();
                if (minecraftDir != null) {
                    instanceDir = minecraftDir.getParentFile();
                    // Prism root is typically <prism_root>/instances/<instance>
                    File instancesDir = instanceDir.getParentFile();
                    if (instancesDir != null && "instances".equals(instancesDir.getName())) {
                        prismRoot = instancesDir.getParentFile();
                    }
                }
            }
        }

        // If prismRoot not found, check standard Prism/MultiMC locations
        if (prismRoot == null) {
            for (File candidate : EnvUtils.getStandardLauncherRoots()) {
                if (candidate != null && candidate.isDirectory() && new File(candidate, "libraries").isDirectory()) {
                    prismRoot = candidate;
                    System.out.println("Found Prism root: " + prismRoot);
                    break;
                }
            }
        }
    }

    private static void loadDependencies() {
        System.out.println("Looking for dependencies...");
        System.out.println("  instanceDir: " + instanceDir);
        System.out.println("  prismRoot: " + prismRoot);

        // Gson is required
        if (!isClassAvailable("com.google.gson.Gson")) {
            File gsonJar = findLibrary("com/google/code/gson/gson", "gson");
            if (gsonJar == null) {
                System.err.println("Could not find Gson in Prism/Minecraft libraries.");
                System.err.println("Searched in:");
                if (prismRoot != null) {
                    System.err.println("  - " + new File(prismRoot, "libraries/com/google/code/gson/gson"));
                }
                if (instanceDir != null) {
                    System.err.println("  - " + new File(instanceDir, "libraries/com/google/code/gson/gson"));
                }
                System.exit(1);
            }
            if (!addToClasspath(gsonJar)) {
                System.err.println("Failed to add Gson to classpath.");
                System.exit(1);
            }
        }

        // Log4j is required for our logging
        if (!isClassAvailable("org.apache.logging.log4j.LogManager")) {
            File log4jApi = findLibrary("org/apache/logging/log4j/log4j-api", "log4j-api");
            File log4jCore = findLibrary("org/apache/logging/log4j/log4j-core", "log4j-core");

            if (log4jApi == null || log4jCore == null) {
                System.err.println("Could not find Log4j in Prism/Minecraft libraries.");
                System.err.println("Make sure you're running from a Prism/MultiMC instance mods folder.");
                System.exit(1);
            }
            addToClasspath(log4jApi);
            addToClasspath(log4jCore);
        }
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static File findLibrary(String mavenPath, String jarPrefix) {
        // Try global Prism libraries
        if (prismRoot != null) {
            File libDir = new File(prismRoot, "libraries/" + mavenPath);
            File found = findLibraryInDir(libDir, jarPrefix);
            if (found != null) return found;
        }

        // Try per-instance libraries
        if (instanceDir != null) {
            File libDir = new File(instanceDir, "libraries/" + mavenPath);
            File found = findLibraryInDir(libDir, jarPrefix);
            if (found != null) return found;
        }

        // Try standard library locations directly (fallback)
        for (File launcherRoot : EnvUtils.getStandardLauncherRoots()) {
            if (launcherRoot != null && launcherRoot.isDirectory()) {
                File libDir = new File(new File(launcherRoot, "libraries"), mavenPath);
                File found = findLibraryInDir(libDir, jarPrefix);
                if (found != null) return found;
            }
        }

        return null;
    }

    private static File findLibraryInDir(File dir, String jarPrefix) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }

        // Look for matching JAR directly
        File[] jars = dir.listFiles((d, name) ->
            name.startsWith(jarPrefix + "-") && name.endsWith(".jar") && !name.contains("-sources"));
        if (jars != null && jars.length > 0) {
            System.out.println("Found " + jarPrefix + ": " + jars[0].getAbsolutePath());
            return jars[0];
        }

        // Recurse into subdirectories (Maven structure)
        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                File found = findLibraryInDir(subdir, jarPrefix);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private static File getJarLocation() {
        try {
            String path = JarJarCli.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath();
            if (path != null && path.endsWith(".jar")) {
                return new File(path);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private static boolean addToClasspath(File jar) {
        try {
            URLClassLoader sysLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            addURL.invoke(sysLoader, jar.toURI().toURL());
            return true;
        } catch (Exception e) {
            System.err.println("Failed to add to classpath: " + e.getMessage());
            return false;
        }
    }
}
