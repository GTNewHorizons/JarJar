package com.mitchej123.jarjar.launch.prism;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mitchej123.jarjar.Tags;
import com.mitchej123.jarjar.launch.EnvUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Installs JarJar's RFB bundle as a Prism Launcher component.
 * This allows RFB to load directly without the agent relaunch.
 */
public class PrismRfbInstaller {

    private static final Logger LOG = LogManager.getLogger("JarJar");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Override net.minecraftforge to set our mainClass - Prism only respects mainClass
    // from patches that override known component UIDs
    private static final String COMPONENT_UID = "net.minecraftforge";
    private static final String COMPONENT_NAME = "Forge";
    private static final String RFB_BUNDLE_RESOURCE = "/com/mitchej123/jarjar/launch/rfb-bundle.jar";
    private static final String FORGE_LIBRARIES_FALLBACK = "/com/mitchej123/jarjar/launch/prism/forge-libraries.json";

    private static final String FORGE_VERSION = "10.13.4.1614";

    private final File instanceDir;
    private final String version;

    public PrismRfbInstaller(File instanceDir, String version) {
        this.instanceDir = instanceDir;
        this.version = version;
    }

    /**
     * Check if direct RFB is already installed.
     */
    public boolean isInstalled() {
        File patchFile = getPatchFile();
        // Also check for old patch file from previous versions
        File oldPatchFile = new File(instanceDir, "patches/com.mitchej123.jarjar.rfb.json");
        return patchFile.exists() || oldPatchFile.exists();
    }

    /**
     * Check if the installed version matches the current JarJar version.
     * @return true if versions match or not installed, false if update needed
     */
    public boolean isVersionCurrent() {
        if (!isInstalled()) {
            return true; // Not installed, no mismatch
        }

        // Check library file exists with current version
        File librariesDir = new File(instanceDir, "libraries");
        File expectedJar = new File(librariesDir, "jarjar-rfb-bundle-" + version + ".jar");
        return expectedJar.exists();
    }

    /**
     * Update the installation to the current version.
     * @return true if successful
     */
    public boolean update() {
        LOG.info("Updating Direct RFB from installed version to {}", version);
        return uninstall() && install();
    }

    /**
     * Install direct RFB setup.
     * @return true if successful
     */
    public boolean install() {
        try {
            // Clean up old patch file from previous versions
            File oldPatchFile = new File(instanceDir, "patches/com.mitchej123.jarjar.rfb.json");
            if (oldPatchFile.exists()) {
                LOG.info("Removing old patch file: {}", oldPatchFile);
                oldPatchFile.delete();
            }

            // Create directories
            File patchesDir = new File(instanceDir, "patches");
            File librariesDir = new File(instanceDir, "libraries");
            if (!patchesDir.exists() && !patchesDir.mkdirs()) {
                LOG.error("Failed to create patches directory");
                return false;
            }
            if (!librariesDir.exists() && !librariesDir.mkdirs()) {
                LOG.error("Failed to create libraries directory");
                return false;
            }

            // Extract RFB bundle to libraries
            File rfbJar = new File(librariesDir, "jarjar-rfb-bundle-" + version + ".jar");
            if (!extractRfbBundle(rfbJar)) {
                return false;
            }

            // Create patch JSON (single consolidated patch for Forge + RFB)
            if (!createPatchJson()) {
                return false;
            }

            // Update mmc-pack.json
            if (!updateMmcPack()) {
                return false;
            }

            LOG.info("Direct RFB installation complete");
            return true;

        } catch (Exception e) {
            LOG.error("Failed to install direct RFB: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Uninstall direct RFB setup.
     * @return true if successful
     */
    public boolean uninstall() {
        try {
            boolean success = true;

            // Remove patch file
            File patchFile = getPatchFile();
            if (patchFile.exists() && !patchFile.delete()) {
                LOG.warn("Failed to delete patch file: {}", patchFile);
                success = false;
            }

            // Clean up old patch files from previous versions
            File oldPatchFile = new File(instanceDir, "patches/com.mitchej123.jarjar.rfb.json");
            if (oldPatchFile.exists() && !oldPatchFile.delete()) {
                LOG.warn("Failed to delete old patch file: {}", oldPatchFile);
            }
            File oldLaunchArgsPatch = new File(instanceDir, "patches/com.mitchej123.jarjar.json");
            if (oldLaunchArgsPatch.exists() && !oldLaunchArgsPatch.delete()) {
                LOG.warn("Failed to delete old launch args patch file: {}", oldLaunchArgsPatch);
            }

            // Remove RFB bundle from libraries
            File librariesDir = new File(instanceDir, "libraries");
            File[] rfbJars = librariesDir.listFiles((dir, name) ->
                name.startsWith("jarjar-rfb-bundle-") && name.endsWith(".jar"));
            if (rfbJars != null) {
                for (File jar : rfbJars) {
                    if (!jar.delete()) {
                        LOG.warn("Failed to delete RFB jar: {}", jar);
                        success = false;
                    }
                }
            }

            // Remove component from mmc-pack.json
            if (!removeFromMmcPack()) {
                success = false;
            }

            return success;

        } catch (Exception e) {
            LOG.error("Failed to uninstall direct RFB: {}", e.getMessage(), e);
            return false;
        }
    }

    private File getPatchFile() {
        return new File(instanceDir, "patches/" + COMPONENT_UID + ".json");
    }

    private boolean extractRfbBundle(File targetFile) {
        try (InputStream is = getClass().getResourceAsStream(RFB_BUNDLE_RESOURCE)) {
            if (is == null) {
                LOG.error("RFB bundle not found in resources");
                return false;
            }
            try (OutputStream os = Files.newOutputStream(targetFile.toPath())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            LOG.info("Extracted RFB bundle to {}", targetFile);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to extract RFB bundle: {}", e.getMessage());
            return false;
        }
    }

    private boolean createPatchJson() {
        JsonObject patch = new JsonObject();
        patch.addProperty("formatVersion", 1);
        patch.addProperty("name", COMPONENT_NAME);
        patch.addProperty("uid", COMPONENT_UID);
        // Component version must reflect Forge, not JarJar
        patch.addProperty("version", FORGE_VERSION);
        // Keep low order so dedicated launchargs patch can override mainClass later
        patch.addProperty("order", 5);

        patch.addProperty("mainClass", "com.gtnewhorizons.retrofuturabootstrap.Main");

        // Ensure Forge launches with FMLTweaker under RFB (prevents VanillaTweaker fallback)
        JsonArray tweakers = new JsonArray();
        tweakers.add(new JsonPrimitive("cpw.mods.fml.common.launcher.FMLTweaker"));
        patch.add("+tweakers", tweakers);

        // JVM args: RFB classloader + FML/terminal compatibility
        // NOTE: Xdock args are not needed - Prism adds them automatically
        JsonArray jvmArgs = new JsonArray();
        jvmArgs.add(new JsonPrimitive("-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader"));
        jvmArgs.add(new JsonPrimitive("-Djline.terminal=jline.UnsupportedTerminal"));
        jvmArgs.add(new JsonPrimitive("-Dfml.terminal.Disabled=true"));
        jvmArgs.add(new JsonPrimitive("-Dorg.lwjgl.opengl.Display.enableHighDPI=false"));
        patch.add("+jvmArgs", jvmArgs);

        // Ensure this component requires Minecraft 1.7.10
        JsonArray requires = new JsonArray();
        JsonObject req = new JsonObject();
        req.addProperty("equals", "1.7.10");
        req.addProperty("uid", "net.minecraft");
        requires.add(req);
        patch.add("requires", requires);

        // Read Forge libraries from Prism metadata or fallback
        JsonArray forgeLibraries = loadForgeLibraries();
        if (forgeLibraries != null && forgeLibraries.size() > 0) {
            patch.add("libraries", forgeLibraries);
        } else {
            LOG.warn("No Forge libraries found - patch may be incomplete");
        }

        // RFB bundle library
        JsonArray rfbLibraries = new JsonArray();
        JsonObject rfbLib = new JsonObject();
        rfbLib.addProperty("name", "com.mitchej123:jarjar-rfb-bundle:" + version);
        rfbLib.addProperty("MMC-hint", "local");
        rfbLibraries.add(rfbLib);
        patch.add("+libraries", rfbLibraries);

        File patchFile = getPatchFile();
        try (FileWriter writer = new FileWriter(patchFile)) {
            GSON.toJson(patch, writer);
            LOG.info("Created patch file: {}", patchFile);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to write patch file: {}", e.getMessage());
            return false;
        }
    }

    // Libraries we provide via RFB bundle - exclude from Forge libraries
    private static final Set<String> EXCLUDED_LIBRARY_PREFIXES = new HashSet<>(Arrays.asList(
        "net.minecraft:launchwrapper:",
        "org.ow2.asm:"
    ));

    /**
     * Load Forge libraries from Prism metadata or bundled fallback.
     * Tries in order:
     * 1. Prism global metadata: ~/.local/share/PrismLauncher/meta/net.minecraftforge/{version}.json
     * 2. Bundled fallback resource
     *
     * Filters out libraries we override (launchwrapper, asm).
     */
    private JsonArray loadForgeLibraries() {
        String forgeVersion = FORGE_VERSION;

        // Try Prism global metadata first
        File prismMeta = findPrismForgeMetadata(forgeVersion);
        if (prismMeta != null && prismMeta.exists()) {
            JsonArray libs = readLibrariesFromJson(prismMeta);
            if (libs != null) {
                libs = filterExcludedLibraries(libs);
                LOG.info("Loaded {} Forge libraries from Prism metadata: {}", libs.size(), prismMeta);
                return libs;
            }
        }

        // Fall back to bundled resource (already filtered)
        LOG.info("Prism metadata not found, using bundled Forge libraries");
        try (InputStream is = getClass().getResourceAsStream(FORGE_LIBRARIES_FALLBACK)) {
            if (is == null) {
                LOG.error("Bundled Forge libraries fallback not found: {}", FORGE_LIBRARIES_FALLBACK);
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonElement el = GSON.fromJson(reader, JsonElement.class);
                if (el != null && el.isJsonArray()) {
                    JsonArray libs = el.getAsJsonArray();
                    LOG.info("Loaded {} Forge libraries from bundled fallback", libs.size());
                    return libs;
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to read bundled Forge libraries: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Filter out libraries that RFB provides (launchwrapper, asm).
     */
    private JsonArray filterExcludedLibraries(JsonArray libs) {
        JsonArray filtered = new JsonArray();
        for (JsonElement el : libs) {
            if (!el.isJsonObject()) continue;
            JsonObject lib = el.getAsJsonObject();
            if (!lib.has("name")) continue;
            String name = lib.get("name").getAsString();
            boolean excluded = false;
            for (String prefix : EXCLUDED_LIBRARY_PREFIXES) {
                if (name.startsWith(prefix)) {
                    excluded = true;
                    LOG.debug("Excluding library {} (provided by RFB)", name);
                    break;
                }
            }
            if (!excluded) {
                filtered.add(lib);
            }
        }
        return filtered;
    }

    /**
     * Find Prism's global metadata file for the given Forge version.
     */
    private File findPrismForgeMetadata(String forgeVersion) {
        // Prism data directory varies by platform
        File prismData = getPrismDataDirectory();
        if (prismData == null) {
            return null;
        }
        return new File(prismData, "meta/net.minecraftforge/" + forgeVersion + ".json");
    }

    /**
     * Get Prism Launcher's data directory based on platform.
     * Delegates to AgentUtils for platform-specific detection.
     */
    private File getPrismDataDirectory() {
        return EnvUtils.getPrismDataDirectory();
    }

    /**
     * Read libraries array from a Prism component JSON file.
     */
    private JsonArray readLibrariesFromJson(File jsonFile) {
        try (FileReader reader = new FileReader(jsonFile)) {
            JsonElement el = GSON.fromJson(reader, JsonElement.class);
            if (el != null && el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("libraries") && obj.get("libraries").isJsonArray()) {
                    return obj.getAsJsonArray("libraries");
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to read {}: {}", jsonFile, e.getMessage());
        }
        return null;
    }

    private boolean updateMmcPack() {
        File mmcPack = new File(instanceDir, "mmc-pack.json");
        if (!mmcPack.exists()) {
            LOG.warn("mmc-pack.json not found at {}, skipping component insertion", mmcPack.getAbsolutePath());
            return true; // Non-fatal
        }
        try (FileReader r = new FileReader(mmcPack)) {
            com.google.gson.JsonElement rootEl = GSON.fromJson(r, com.google.gson.JsonElement.class);
            if (rootEl == null || !rootEl.isJsonObject()) {
                LOG.warn("mmc-pack.json malformed, skipping modification");
                return true;
            }
            JsonObject root = rootEl.getAsJsonObject();
            JsonArray components = root.has("components") && root.get("components").isJsonArray()
                ? root.getAsJsonArray("components") : new JsonArray();

            boolean hasForge = false;
            for (int i = 0; i < components.size(); i++) {
                if (!components.get(i).isJsonObject()) continue;
                JsonObject comp = components.get(i).getAsJsonObject();
                if (COMPONENT_UID.equals(comp.has("uid") ? comp.get("uid").getAsString() : null)) {
                    hasForge = true;
                    break;
                }
            }
            if (!hasForge) {
                JsonObject forgeComp = new JsonObject();
                forgeComp.addProperty("uid", COMPONENT_UID);
                forgeComp.addProperty("version", FORGE_VERSION);
                forgeComp.addProperty("cachedName", "Forge");
                components.add(forgeComp);
                root.add("components", components);

                try (FileWriter w = new FileWriter(mmcPack)) {
                    GSON.toJson(root, w);
                }
                LOG.info("Added forge component to mmc-pack.json");
            } else {
                LOG.info("Forge component already present in mmc-pack.json");
            }
            return true;
        } catch (IOException e) {
            LOG.error("Failed to update mmc-pack.json: {}", e.getMessage());
            return false;
        }
    }

    private boolean removeFromMmcPack() {
        // We override net.minecraftforge, so we should NOT remove it from mmc-pack.json
        // The patch file removal is sufficient - Prism will fall back to the default forge behavior
        LOG.info("Skipping mmc-pack.json modification (net.minecraftforge component must remain)");
        return true;
    }

    /**
     * Get the current JarJar version.
     */
    public static String getJarJarVersion() {
        return Tags.VERSION;
    }
}
