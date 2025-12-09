package com.mitchej123.jarjar.launch;

import com.mitchej123.jarjar.launch.bootstrap.RfbExtractor;
import com.mitchej123.jarjar.launch.config.LauncherConfig;
import com.mitchej123.jarjar.launch.config.UserConfig;
import com.mitchej123.jarjar.launch.prism.PrismRfbInstaller;
import com.mitchej123.jarjar.launch.ui.DialogHelper;
import com.mitchej123.jarjar.launch.ui.SetupDialog;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * LaunchWrapper tweaker that ensures RFB is set up on Java 8.
 * If RFB isn't active, this tweaker will show a dialog with options
 * for the user to resolve the issue (Direct RFB install or Relaunch).
 */
public class JarJarTweaker implements ITweaker {

    private static final Logger LOG = LogManager.getLogger("JarJar");

    private static boolean skipJarJar = false;

    private List<String> launchArgs;
    private File gameDir;
    private File assetsDir;
    private String profile;

    public JarJarTweaker() {
        LOG.info("Tweaker init, Java {}", EnvUtils.getJavaVersion());
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        this.launchArgs = new ArrayList<>(args);
        this.gameDir = gameDir;
        this.assetsDir = assetsDir;
        this.profile = profile;

        if (!EnvUtils.isJava8()) {
            LOG.info("Java 9+ detected, deferring to RFB");
            return;
        }

        File effectiveGameDir = gameDir != null ? gameDir : Launch.minecraftHome;
        if (effectiveGameDir == null) {
            LOG.error("No game directory available, cannot proceed with setup");
            return;
        }

        File instanceDir = effectiveGameDir.getParentFile();

        // Check for management mode (system property)
        if (Boolean.getBoolean("jarjar.manage") && instanceDir != null) {
            LOG.info("Management mode requested via system property");
            showManagementDialog(instanceDir);
            return;
        }

        // Check for Direct RFB version mismatch
        if (instanceDir != null) {
            PrismRfbInstaller installer = new PrismRfbInstaller(
                instanceDir, PrismRfbInstaller.getJarJarVersion());
            if (installer.isInstalled() && !installer.isVersionCurrent()) {
                LOG.info("Direct RFB version mismatch detected");
                handleVersionMismatch(installer, instanceDir);
                return;
            }
        }

        if (EnvUtils.isRfbEnvironment()) {
            LOG.info("RFB environment active, ready");
            return;
        }

        UserConfig config = UserConfig.load(effectiveGameDir);
        UserConfig.Choice savedChoice = config.getSavedChoice();

        if (savedChoice != null) {
            LOG.debug("Using saved choice: {}", savedChoice);
            LauncherConfig.LauncherType launcher = LauncherConfig.detectLauncher(effectiveGameDir);
            String launcherName = LauncherConfig.getLauncherName(launcher);
            handleChoice(savedChoice, config, effectiveGameDir, launcherName);
            return;
        }

        handleMissing(config, effectiveGameDir);
    }

    private void showManagementDialog(File instanceDir) {
        PrismRfbInstaller installer = new PrismRfbInstaller(instanceDir, PrismRfbInstaller.getJarJarVersion());

        // Check for saved relaunch choice
        File gameDir = new File(instanceDir, ".minecraft");
        UserConfig config = UserConfig.load(gameDir);
        UserConfig.Choice savedChoice = config.getSavedChoice();
        boolean hasRelaunchSetting = (savedChoice == UserConfig.Choice.RELAUNCH_NOW);

        String version = PrismRfbInstaller.getJarJarVersion();
        DialogHelper.ManagementAction action = DialogHelper.showManagementDialog(
            installer.isInstalled(),
            installer.isInstalled() && !installer.isVersionCurrent(),
            hasRelaunchSetting,
            version
        );

        switch (action) {
            case INSTALL:
                if (installer.install()) {
                    if (hasRelaunchSetting) {
                        config.clear();
                    }
                    DialogHelper.showInfo("Installed", "Direct RFB installed. Exit and relaunch for changes to take effect.");
                } else {
                    DialogHelper.showInfo("Error", "Failed to install. Check logs.");
                }
                break;

            case UPDATE:
                if (installer.update()) {
                    DialogHelper.showInfo("Updated", "Direct RFB updated. Exit and relaunch for changes to take effect.");
                } else {
                    DialogHelper.showInfo("Error", "Failed to update. Check logs.");
                }
                break;

            case UNINSTALL:
                if (installer.uninstall()) {
                    DialogHelper.showInfo("Uninstalled", "Direct RFB uninstalled. Exit and relaunch for changes to take effect.");
                } else {
                    DialogHelper.showInfo("Error", "Failed to uninstall. Check logs.");
                }
                break;

            case DISABLE_RELAUNCH:
                config.clear();
                DialogHelper.showInfo("Disabled", "Relaunch mode disabled.\nYou'll see the setup dialog next time you launch on Java 8.");
                break;

            case CANCEL:
            default:
                // Continue normally
                break;
        }
    }

    private void handleVersionMismatch(PrismRfbInstaller installer, File instanceDir) {
        String version = PrismRfbInstaller.getJarJarVersion();
        DialogHelper.VersionMismatchAction action = DialogHelper.showVersionMismatchDialog(version);

        switch (action) {
            case UPDATE:
                if (installer.update()) {
                    DialogHelper.showInfo("Updated", "Direct RFB updated.\n\nThe game will now exit. Relaunch to continue.");
                    EnvUtils.safeExit(0);
                } else {
                    DialogHelper.showInfo("Error", "Failed to update. Check logs.");
                }
                break;

            case UNINSTALL:
                if (installer.uninstall()) {
                    DialogHelper.showInfo("Uninstalled", "Direct RFB uninstalled.\n\nThe game will now exit. Relaunch to continue.");
                    EnvUtils.safeExit(0);
                } else {
                    DialogHelper.showInfo("Error", "Failed to uninstall. Check logs.");
                }
                break;

            case CONTINUE:
            default:
                // Just continue with outdated RFB
                break;
        }
    }

    private void handleMissing(UserConfig config, File effectiveGameDir) {
        String jarPath = findJarPath();
        if (jarPath == null) {
            LOG.error("Could not find JarJar JAR path");
            DialogHelper.showInfo("JarJar Error", "Could not locate the JarJar JAR file.\nJarJar features will be disabled.");
            skipJarJar = true;
            return;
        }

        LauncherConfig.LauncherType launcher = LauncherConfig.detectLauncher(effectiveGameDir);
        boolean canAutoUpdate = (launcher != LauncherConfig.LauncherType.UNKNOWN);
        String launcherName = LauncherConfig.getLauncherName(launcher);

        LOG.debug("Detected launcher: {} (can auto-update: {})", launcherName, canAutoUpdate);

        // Instance directory is parent of gameDir (which is .minecraft)
        File instanceDir = effectiveGameDir.getParentFile();

        SetupDialog.DialogResult result = SetupDialog.show(instanceDir, canAutoUpdate, launcherName, jarPath);

        if (result == null) {
            LOG.info("Dialog closed, continuing without JarJar");
            skipJarJar = true;
            return;
        }

        if (result.rememberChoice) {
            config.setSavedChoice(result.choice);
            config.save();
        }

        handleChoice(result.choice, config, effectiveGameDir, launcherName);
    }

    private void handleChoice(UserConfig.Choice choice, UserConfig config, File effectiveGameDir, String launcherName) {
        String jarPath = findJarPath();
        File instanceDir = effectiveGameDir.getParentFile();

        switch (choice) {
            case DIRECT_RFB:
                if (instanceDir != null) {
                    PrismRfbInstaller installer = new PrismRfbInstaller(instanceDir, PrismRfbInstaller.getJarJarVersion());
                    if (installer.install()) {
                        DialogHelper.showInfo("Direct RFB Installed", "RFB has been installed as a launcher component.\n\nThe game will now exit.\nRelaunch to use JarJar with RFB.");
                        LOG.info("Direct RFB installed, exiting");
                        EnvUtils.safeExit(0);
                    } else {
                        DialogHelper.showInfo("Installation Failed", "Failed to install direct RFB.\nCheck the game log for details.");
                        skipJarJar = true;
                    }
                } else {
                    DialogHelper.showInfo("Installation Failed", "Could not determine instance directory.");
                    skipJarJar = true;
                }
                break;

            case RELAUNCH_NOW:
                relaunchWithRfb(jarPath);
                break;

            case CONTINUE_WITHOUT:
                LOG.warn("Continuing without JarJar agent - features disabled");
                skipJarJar = true;
                break;
        }
    }

    private void relaunchWithRfb(String jarPath) {
        try {
            // Extract RFB bundle
            Path rfbJar = RfbExtractor.extractRfb();

            List<String> command = buildRfbCommand(jarPath, rfbJar);
            LOG.info("Relaunching with RFB");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            if (gameDir != null) {
                pb.directory(gameDir);
            }

            Process p = pb.start();

            // Clean up parent process - we're just waiting now
            this.launchArgs = null;
            this.gameDir = null;
            this.assetsDir = null;
            this.profile = null;

            // Clear LaunchWrapper state we no longer need
            try {
                Launch.classLoader = null;
                Launch.blackboard = null;
            } catch (Throwable ignored) {}

            // Hint to GC that we've freed up references
            System.gc();

            // Must wait for child - if we exit, Prism cleans up the natives directory
            int exitCode = p.waitFor();
            LOG.debug("Game process exited with code: {}", exitCode);

            EnvUtils.safeExit(exitCode);

        } catch (Exception e) {
            LOG.error("Failed to relaunch with RFB: {}", e.getMessage(), e);
            DialogHelper.showInfo("Relaunch Failed", "Failed to relaunch the game with RFB.\n\nError: " + e.getMessage() + "\n\nConsider installing Direct RFB instead.");
            skipJarJar = true;
        }
    }

    /**
     * Build command for direct RFB relaunch (single relaunch, no agent step).
     */
    private List<String> buildRfbCommand(String jarPath, Path rfbJar) {
        List<String> command = new ArrayList<>();

        command.add(EnvUtils.getJavaExecutable());

        // JVM arguments like -Xmx, -XX:, etc. - filter out agent args and debug agents
        List<String> inputArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        EnvUtils.JvmArgsResult argsResult = EnvUtils.filterJvmArgsForRelaunch(inputArgs, null);
        command.addAll(argsResult.args);

        // Construct natives path from gameDir
        File effectiveGameDir = gameDir != null ? gameDir : Launch.minecraftHome;
        if (effectiveGameDir != null) {
            File nativesDir = new File(effectiveGameDir.getParentFile(), "natives");
            if (nativesDir.isDirectory()) {
                command.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
                LOG.info("Using natives directory: {}", nativesDir.getAbsolutePath());
            } else {
                LOG.warn("Natives directory not found at {}, falling back to system property", nativesDir);
                String libraryPath = System.getProperty("java.library.path");
                if (libraryPath != null) {
                    command.add("-Djava.library.path=" + libraryPath);
                }
            }
        }

        // RFB system classloader
        command.add("-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader");

        // macOS dock settings
        EnvUtils.addMacOSDockArgs(command, argsResult.hasDockName, argsResult.hasDockIcon, effectiveGameDir);

        // Forward custom system properties
        for (String key : System.getProperties().stringPropertyNames()) {
            if (!EnvUtils.isSystemProperty(key)) {
                String value = System.getProperty(key);
                if (value != null) {
                    command.add("-D" + key + "=" + value);
                }
            }
        }

        // Classpath: RFB bundle first, then original classpath, then jarjar
        command.add("-cp");
        String originalCp = System.getProperty("java.class.path");
        command.add(rfbJar.toAbsolutePath() + File.pathSeparator + originalCp + File.pathSeparator + jarPath);

        // Use RFB's Main class which delegates to LaunchWrapper
        command.add("com.gtnewhorizons.retrofuturabootstrap.Main");

        // Program arguments
        command.add("--tweakClass");
        command.add("cpw.mods.fml.common.launcher.FMLTweaker");

        command.add("--version");
        command.add(profile != null ? profile : "1.7.10");

        command.add("--gameDir");
        command.add((gameDir != null ? gameDir : Launch.minecraftHome).getAbsolutePath());

        command.add("--assetsDir");
        command.add((assetsDir != null ? assetsDir : Launch.assetsDir).getAbsolutePath());

        // Remaining args (auth tokens, etc.)
        if (launchArgs != null && !launchArgs.isEmpty()) {
            command.addAll(launchArgs);
        }

        return command;
    }


    private String findJarPath() {
        try {
            String path = JarJarTweaker.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();

            if (path != null && path.endsWith(".jar")) {
                return path;
            }
        } catch (Exception e) {
            LOG.debug("Could not get JAR path from CodeSource: {}", e.getMessage());
        }

        String classpath = System.getProperty("java.class.path");
        if (classpath != null) {
            for (String entry : classpath.split(File.pathSeparator)) {
                if (entry.toLowerCase().contains("jarjar") && entry.endsWith(".jar")) {
                    return entry;
                }
            }
        }

        return null;
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        if (EnvUtils.isJava8()) {
            LOG.info("injectIntoClassLoader - RFB active: {}, skip: {}",
                EnvUtils.isRfbActive(), skipJarJar);
        }
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    /**
     * Check if JarJar is disabled (user chose to continue without, or RFB missing on Java 8).
     */
    public static boolean isDisabled() {
        return skipJarJar || (EnvUtils.isJava8() && !EnvUtils.isRfbEnvironment());
    }
}
