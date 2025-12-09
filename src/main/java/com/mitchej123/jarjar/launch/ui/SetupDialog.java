package com.mitchej123.jarjar.launch.ui;

import com.mitchej123.jarjar.launch.EnvUtils;
import com.mitchej123.jarjar.launch.config.LauncherConfig;
import com.mitchej123.jarjar.launch.config.UserConfig;
import com.mitchej123.jarjar.launch.prism.PrismRfbInstaller;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.UIManager;

/**
 * Swing dialog for JarJar setup and configuration.
 *
 * Can be run standalone: java -cp jarjar.jar com.mitchej123.jarjar.launch.ui.SetupDialog
 */
public class SetupDialog {

    public static class DialogResult {
        public final UserConfig.Choice choice;
        public final boolean rememberChoice;

        public DialogResult(UserConfig.Choice choice, boolean rememberChoice) {
            this.choice = choice;
            this.rememberChoice = rememberChoice;
        }
    }

    /**
     * Standalone entry point for running the setup dialog.
     *
     * Usage:
     *   java -cp jarjar.jar com.mitchej123.jarjar.launch.ui.SetupDialog [options] [instanceDir]
     *
     * Options:
     *   --install <instanceDir>   Install Direct RFB to instance
     *   --uninstall <instanceDir> Uninstall Direct RFB from instance
     *   --status <instanceDir>    Check installation status
     *   --clear <instanceDir>     Clear saved choice (jarjar.cfg)
     *   --help                    Show this help
     *
     * If no options, launches the GUI.
     */
    public static void main(String[] args) {
        // Handle CLI options
        if (args.length > 0 && args[0].startsWith("--")) {
            handleCliCommand(args);
            return;
        }

        File instanceDir = null;

        // Check for command-line argument (instance directory)
        if (args.length > 0) {
            instanceDir = new File(args[0]);
            if (!instanceDir.isDirectory()) {
                System.err.println("Not a valid directory: " + args[0]);
                System.exit(1);
            }
        }

        // If no argument, try to detect or prompt
        if (instanceDir == null) {
            instanceDir = promptForInstanceDirectory();
            if (instanceDir == null) {
                System.exit(0); // User cancelled
            }
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        // Check if Direct RFB is already installed
        PrismRfbInstaller installer = new PrismRfbInstaller(
            instanceDir, PrismRfbInstaller.getJarJarVersion());
        if (installer.isInstalled()) {
            showManagementDialog(instanceDir, installer);
            return;
        }

        LauncherConfig.LauncherType launcher = LauncherConfig.detectLauncher(
            new File(instanceDir, ".minecraft"));
        String launcherName = LauncherConfig.getLauncherName(launcher);
        boolean canAutoUpdate = launcher != LauncherConfig.LauncherType.UNKNOWN;

        DialogResult result = showStandalone(instanceDir, canAutoUpdate, launcherName);
        if (result != null) {
            handleStandaloneResult(result, instanceDir, launcherName);
        }
    }

    private static void handleCliCommand(String[] args) {
        String command = args[0];

        if ("--help".equals(command) || "-h".equals(command)) {
            printHelp();
            return;
        }

        if (args.length < 2) {
            System.err.println("Error: Instance directory required");
            System.err.println("Use --help for usage information");
            System.exit(1);
        }

        File instanceDir = new File(args[1]);
        if (!instanceDir.isDirectory()) {
            System.err.println("Error: Not a valid directory: " + args[1]);
            System.exit(1);
        }

        PrismRfbInstaller installer = new PrismRfbInstaller(
            instanceDir, PrismRfbInstaller.getJarJarVersion());

        switch (command) {
            case "--install":
                if (installer.isInstalled()) {
                    System.out.println("Direct RFB is already installed.");
                    System.out.println("Use --uninstall first to reinstall.");
                    System.exit(0);
                }
                if (installer.install()) {
                    System.out.println("Direct RFB installed successfully.");
                    System.out.println("Restart your launcher and game for changes to take effect.");
                } else {
                    System.err.println("Failed to install Direct RFB.");
                    System.exit(1);
                }
                break;

            case "--uninstall":
                if (!installer.isInstalled()) {
                    System.out.println("Direct RFB is not installed.");
                    System.exit(0);
                }
                if (installer.uninstall()) {
                    System.out.println("Direct RFB uninstalled successfully.");
                } else {
                    System.err.println("Failed to uninstall Direct RFB.");
                    System.exit(1);
                }
                break;

            case "--status":
                System.out.println("Instance: " + instanceDir.getAbsolutePath());
                System.out.println("Direct RFB installed: " + installer.isInstalled());

                // Check for saved choice
                File gameDir = new File(instanceDir, ".minecraft");
                if (gameDir.isDirectory()) {
                    UserConfig config = UserConfig.load(gameDir);
                    UserConfig.Choice choice = config.getSavedChoice();
                    System.out.println("Saved choice: " + (choice != null ? choice.name() : "none"));
                }
                break;

            case "--clear":
                File gameDirClear = new File(instanceDir, ".minecraft");
                if (gameDirClear.isDirectory()) {
                    UserConfig config = UserConfig.load(gameDirClear);
                    config.clear();
                    System.out.println("Saved choice cleared.");
                } else {
                    System.err.println("No .minecraft directory found in instance.");
                    System.exit(1);
                }
                break;

            default:
                System.err.println("Unknown command: " + command);
                printHelp();
                System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("JarJar Setup Utility");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -cp jarjar.jar com.mitchej123.jarjar.launch.ui.SetupDialog [options] [instanceDir]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --install <instanceDir>   Install Direct RFB to instance");
        System.out.println("  --uninstall <instanceDir> Uninstall Direct RFB from instance");
        System.out.println("  --status <instanceDir>    Check installation status");
        System.out.println("  --clear <instanceDir>     Clear saved choice (jarjar.cfg)");
        System.out.println("  --help                    Show this help");
        System.out.println();
        System.out.println("If no options provided, launches the GUI.");
    }

    private static void showManagementDialog(File instanceDir, PrismRfbInstaller installer) {
        String version = PrismRfbInstaller.getJarJarVersion();

        String[] options = {"Update", "Uninstall", "Clear Saved Choice", "Close"};
        int choice = JOptionPane.showOptionDialog(null,
            "Direct RFB is already installed (version: " + version + ").\n\n" +
            "What would you like to do?",
            "JarJar - Manage Installation",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[3]);

        switch (choice) {
            case 0: // Update
                if (installer.uninstall() && installer.install()) {
                    showInfo("Success", "Direct RFB updated to version " + version + ".");
                } else {
                    showInfo("Error", "Failed to update Direct RFB.");
                }
                break;

            case 1: // Uninstall
                if (installer.uninstall()) {
                    showInfo("Success", "Direct RFB uninstalled.\n\nRestart your launcher for changes to take effect.");
                } else {
                    showInfo("Error", "Failed to uninstall Direct RFB.");
                }
                break;

            case 2: // Clear saved choice
                File gameDir = new File(instanceDir, ".minecraft");
                if (gameDir.isDirectory()) {
                    UserConfig config = UserConfig.load(gameDir);
                    config.clear();
                    showInfo("Success", "Saved choice cleared.\n\nThe setup dialog will appear next time you launch.");
                } else {
                    showInfo("Error", "Could not find .minecraft directory.");
                }
                break;

            default: // Close
                break;
        }
    }

    private static File promptForInstanceDirectory() {
        int choice = JOptionPane.showConfirmDialog(null,
            "Would you like to select a Prism/MultiMC instance directory?",
            "JarJar Setup",
            JOptionPane.YES_NO_OPTION);

        if (choice != JOptionPane.YES_OPTION) {
            return null;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Instance Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private static void handleStandaloneResult(DialogResult result, File instanceDir, String launcherName) {
        switch (result.choice) {
            case DIRECT_RFB:
                PrismRfbInstaller installer = new PrismRfbInstaller(
                    instanceDir, PrismRfbInstaller.getJarJarVersion());
                if (installer.install()) {
                    showInfo("Success",
                        "Direct RFB installation complete.\n\n" +
                        "Close your launcher and restart the game for changes to take effect.");
                } else {
                    showInfo("Error", "Failed to install direct RFB. Check the logs for details.");
                }
                break;

            default:
                break;
        }
    }

    /**
     * Show the agent setup dialog (standalone mode with all options).
     */
    public static DialogResult showStandalone(File instanceDir, boolean canAutoUpdate, String launcherName) {
        return showInternal(instanceDir, canAutoUpdate, launcherName, null, true);
    }

    /**
     * Show the agent setup dialog (in-game mode).
     *
     * @param canAutoUpdate true if we detected a supported launcher and can modify its config
     * @param launcherName name of detected launcher (e.g., "Prism Launcher") for display
     * @param jarPath path to the JarJar JAR file for display in manual instructions
     * @return user's choice, or null if dialog was closed
     */
    public static DialogResult show(boolean canAutoUpdate, String launcherName, String jarPath) {
        return showInternal(null, canAutoUpdate, launcherName, jarPath, false);
    }

    /**
     * Show the agent setup dialog with instance directory (in-game mode with direct RFB option).
     */
    public static DialogResult show(File instanceDir, boolean canAutoUpdate, String launcherName, String jarPath) {
        return showInternal(instanceDir, canAutoUpdate, launcherName, jarPath, false);
    }

    private static DialogResult showInternal(File instanceDir, boolean canAutoUpdate,
                                             String launcherName, String jarPath, boolean standalone) {
        // Skip dialog in headless environments
        if (EnvUtils.isHeadless()) {
            System.err.println("[JarJar] Running in headless mode - cannot show setup dialog.");
            System.err.println("[JarJar] Use -javaagent:" + (jarPath != null ? jarPath : "jarjar.jar") + " to enable JarJar.");
            return null;
        }

        final UserConfig.Choice[] result = {null};
        final boolean[] remember = {false};

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        String title = standalone ? "JarJar Setup" : "JarJar - Java 8 Setup Required";
        JDialog dialog = new JDialog((Frame) null, title, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        String message = standalone
            ? "<html><body style='width: 280px'>" +
                "<b>JarJar Setup</b><br><br>" +
                "Choose how to configure JarJar for this instance." +
                "</body></html>"
            : "<html><body style='width: 280px'>" +
                "<b>JarJar needs setup to work on Java 8.</b><br><br>" +
                "It either needs to relaunch or install RetroFuturaBootstrap, " +
                "otherwise JarJar features will be disabled." +
                "</body></html>";

        JLabel messageLabel = new JLabel(message);
        mainPanel.add(messageLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        if (!standalone) {
            // In-game relaunch options
            JPanel relaunchPanel = new JPanel();
            relaunchPanel.setLayout(new BoxLayout(relaunchPanel, BoxLayout.X_AXIS));
            relaunchPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JButton relaunchOnceBtn = new JButton("Relaunch (One Time)");
            relaunchOnceBtn.setToolTipText("Restart - will ask again next time");
            relaunchOnceBtn.addActionListener((ActionEvent e) -> {
                result[0] = UserConfig.Choice.RELAUNCH_NOW;
                remember[0] = false;
                dialog.dispose();
            });
            relaunchPanel.add(relaunchOnceBtn);
            relaunchPanel.add(Box.createHorizontalGlue());

            JButton relaunchAlwaysBtn = new JButton("Relaunch (Every Time)");
            relaunchAlwaysBtn.setToolTipText("Restart - automatic on future launches");
            relaunchAlwaysBtn.addActionListener((ActionEvent e) -> {
                result[0] = UserConfig.Choice.RELAUNCH_NOW;
                remember[0] = true;
                dialog.dispose();
            });
            relaunchPanel.add(relaunchAlwaysBtn);

            buttonPanel.add(relaunchPanel);
            buttonPanel.add(Box.createVerticalStrut(10));

            // Separator
            JSeparator sep = new JSeparator();
            sep.setAlignmentX(Component.LEFT_ALIGNMENT);
            buttonPanel.add(sep);
            buttonPanel.add(Box.createVerticalStrut(5));

            JLabel advLabel = new JLabel("<html><b>Advanced Options:</b></html>");
            advLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            buttonPanel.add(advLabel);
            buttonPanel.add(Box.createVerticalStrut(5));
        }

        // Direct RFB option (if we have instance directory and can auto-update)
        if (canAutoUpdate && instanceDir != null) {
            JButton directRfbBtn = new JButton("Install Direct RFB (Recommended)");
            directRfbBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            directRfbBtn.setToolTipText(
                "<html>Installs RFB as a launcher component.<br>" +
                "Faster startup - no relaunch needed.<br>" +
                "Requires closing launcher after install.</html>");
            directRfbBtn.addActionListener((ActionEvent e) -> {
                result[0] = UserConfig.Choice.DIRECT_RFB;
                dialog.dispose();
            });
            buttonPanel.add(directRfbBtn);
        }

        // Bottom row with Continue/Cancel on left, Exit on right
        if (!standalone) {
            buttonPanel.add(Box.createVerticalStrut(10));
            JSeparator sep2 = new JSeparator();
            sep2.setAlignmentX(Component.LEFT_ALIGNMENT);
            buttonPanel.add(sep2);
            buttonPanel.add(Box.createVerticalStrut(5));
            JPanel bottomRow = new JPanel();
            bottomRow.setLayout(new BoxLayout(bottomRow, BoxLayout.X_AXIS));
            bottomRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JButton continueBtn = new JButton("Continue Without JarJar");
            continueBtn.setToolTipText("Continue launching the game with JarJar features disabled");
            continueBtn.addActionListener((ActionEvent e) -> {
                result[0] = UserConfig.Choice.CONTINUE_WITHOUT;
                dialog.dispose();
            });
            bottomRow.add(continueBtn);
            bottomRow.add(Box.createHorizontalGlue());

            JButton exitBtn = new JButton("Exit Now");
            exitBtn.setToolTipText("Exit the game immediately");
            exitBtn.addActionListener((ActionEvent e) -> {
                dialog.dispose();
                EnvUtils.safeExit(0);
            });
            bottomRow.add(exitBtn);

            buttonPanel.add(bottomRow);
        } else {
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            cancelBtn.addActionListener((ActionEvent e) -> {
                dialog.dispose();
            });
            buttonPanel.add(cancelBtn);
        }

        mainPanel.add(buttonPanel, BorderLayout.CENTER);

        dialog.add(mainPanel);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        if (result[0] == null) {
            return null;
        }

        return new DialogResult(result[0], remember[0]);
    }

    /**
     * Show a simple info dialog.
     * @deprecated Use {@link DialogHelper#showInfo(String, String)} instead
     */
    @Deprecated
    public static void showInfo(String title, String message) {
        DialogHelper.showInfo(title, message);
    }
}
