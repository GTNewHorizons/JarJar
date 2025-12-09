package com.mitchej123.jarjar.launch.ui;

import com.mitchej123.jarjar.launch.EnvUtils;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * Helper for showing dialogs in both GUI and TUI modes.
 * Automatically detects headless environments and falls back to text-based UI.
 */
public class DialogHelper {

    private static final int TUI_TIMEOUT_SECONDS = 30;

    /** Result of an option dialog */
    public static class OptionResult {
        public final int selectedIndex;
        public final boolean timedOut;

        public OptionResult(int selectedIndex, boolean timedOut) {
            this.selectedIndex = selectedIndex;
            this.timedOut = timedOut;
        }
    }

    /**
     * Check if we have an interactive stdin available.
     */
    public static boolean hasInteractiveStdin() {
        if (System.console() != null) {
            return true;
        }
        // Fallback: check if stdin has data available or is a terminal
        try {
            return System.in.available() >= 0 && !isStdinRedirectedFromEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isStdinRedirectedFromEmpty() {
        // If stdin is /dev/null or empty pipe, available() returns 0 and read would block forever
        // We can't easily detect this without attempting a read, so we're conservative
        return false;
    }

    /**
     * Show an informational message.
     */
    public static void showInfo(String title, String message) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println();
            System.out.println("=== " + title + " ===");
            System.out.println(message);
            System.out.println();
        } else {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Show a warning message.
     */
    public static void showWarning(String title, String message) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println();
            System.out.println("=== " + title + " (WARNING) ===");
            System.out.println(message);
            System.out.println();
        } else {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Show an option dialog with multiple choices.
     *
     * @param title dialog title
     * @param message message to display
     * @param options array of option labels
     * @param defaultOption index of the default option (used for TUI timeout)
     * @return OptionResult with selected index (-1 if cancelled) and timeout flag
     */
    public static OptionResult showOptions(String title, String message, String[] options, int defaultOption) {
        if (GraphicsEnvironment.isHeadless()) {
            if (hasInteractiveStdin()) {
                return showTuiOptions(title, message, options, defaultOption);
            } else {
                // Non-interactive headless - use default
                System.out.println("[JarJar] Headless non-interactive mode, using default: " + options[defaultOption]);
                return new OptionResult(defaultOption, true);
            }
        } else {
            return showGuiOptions(title, message, options, defaultOption);
        }
    }

    /**
     * Show a yes/no confirmation dialog.
     *
     * @param title dialog title
     * @param message message to display
     * @param defaultYes true if default should be yes (for TUI timeout)
     * @return true for yes, false for no
     */
    public static boolean showConfirm(String title, String message, boolean defaultYes) {
        String[] options = {"Yes", "No"};
        int defaultOption = defaultYes ? 0 : 1;
        OptionResult result = showOptions(title, message, options, defaultOption);
        return result.selectedIndex == 0;
    }

    private static OptionResult showGuiOptions(String title, String message, String[] options, int defaultOption) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        int choice = JOptionPane.showOptionDialog(
            null,
            message,
            title,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[defaultOption]
        );

        return new OptionResult(choice, false);
    }

    private static OptionResult showTuiOptions(String title, String message, String[] options, int defaultOption) {
        System.out.println();
        System.out.println("=== " + title + " ===");
        System.out.println(message);
        System.out.println();
        System.out.println("Options:");
        for (int i = 0; i < options.length; i++) {
            String marker = (i == defaultOption) ? "*" : " ";
            System.out.println("  " + marker + (i + 1) + ". " + options[i]);
        }
        System.out.println();
        System.out.println("Enter choice [1-" + options.length + "] (default: " + (defaultOption + 1) + ", timeout: " + TUI_TIMEOUT_SECONDS + "s): ");

        Integer choice = readIntWithTimeout(1, options.length, defaultOption + 1);
        if (choice == null) {
            System.out.println("Timed out, using default: " + options[defaultOption]);
            return new OptionResult(defaultOption, true);
        }

        return new OptionResult(choice - 1, false);
    }

    // ==================== Specific Dialogs ====================

    /** Actions returned from the management dialog */
    public enum ManagementAction {
        INSTALL, UPDATE, UNINSTALL, DISABLE_RELAUNCH, CANCEL
    }

    /** Actions returned from the version mismatch dialog */
    public enum VersionMismatchAction {
        UPDATE, CONTINUE, UNINSTALL
    }

    /**
     * Show the management dialog for -Djarjar.manage=true mode.
     *
     * @param isInstalled whether Direct RFB is currently installed
     * @param isOutdated whether installed version is outdated
     * @param hasRelaunchSetting whether relaunch mode is enabled
     * @param version current JarJar version
     * @return the selected action
     */
    public static ManagementAction showManagementDialog(boolean isInstalled, boolean isOutdated,
                                                        boolean hasRelaunchSetting, String version) {
        StringBuilder message = new StringBuilder();
        message.append("JarJar Management\n\n");

        java.util.List<String> optionsList = new java.util.ArrayList<>();

        // Direct RFB status
        if (isInstalled) {
            if (isOutdated) {
                message.append("Direct RFB: Installed (outdated)\n");
                optionsList.add("Update Direct RFB");
                optionsList.add("Uninstall Direct RFB");
            } else {
                message.append("Direct RFB: Installed (v").append(version).append(")\n");
                optionsList.add("Uninstall Direct RFB");
            }
        } else {
            message.append("Direct RFB: Not installed\n");
            optionsList.add("Install Direct RFB");
        }

        // Relaunch setting status
        if (hasRelaunchSetting) {
            message.append("Relaunch Mode: Enabled\n");
            optionsList.add("Disable Relaunch Mode");
        } else {
            message.append("Relaunch Mode: Not set\n");
        }

        optionsList.add("Cancel");
        String[] options = optionsList.toArray(new String[0]);
        int cancelIndex = options.length - 1;

        OptionResult result = showOptions("JarJar Management", message.toString(), options, cancelIndex);

        if (result.selectedIndex < 0 || result.selectedIndex == cancelIndex) {
            return ManagementAction.CANCEL;
        }

        String selected = options[result.selectedIndex];
        if (selected.equals("Install Direct RFB")) {
            return ManagementAction.INSTALL;
        } else if (selected.equals("Update Direct RFB")) {
            return ManagementAction.UPDATE;
        } else if (selected.equals("Uninstall Direct RFB")) {
            return ManagementAction.UNINSTALL;
        } else if (selected.equals("Disable Relaunch Mode")) {
            return ManagementAction.DISABLE_RELAUNCH;
        }
        return ManagementAction.CANCEL;
    }

    /**
     * Show the version mismatch dialog when installed RFB is outdated.
     *
     * @param currentVersion the current JarJar version
     * @return the selected action
     */
    public static VersionMismatchAction showVersionMismatchDialog(String currentVersion) {
        String message = "JarJar has been updated but the installed RFB component is outdated.\n\n" +
            "Current version: " + currentVersion + "\n\n" +
            "Would you like to update?";

        String[] options = {"Update Now", "Continue Anyway", "Uninstall"};

        OptionResult result = showOptions("JarJar Update Available", message, options, 0);

        switch (result.selectedIndex) {
            case 0: return VersionMismatchAction.UPDATE;
            case 2: return VersionMismatchAction.UNINSTALL;
            default: return VersionMismatchAction.CONTINUE;
        }
    }

    /**
     * Read an integer from stdin with timeout.
     *
     * @param min minimum valid value
     * @param max maximum valid value
     * @param defaultValue value to return on timeout
     * @return the read value, or null on timeout
     */
    private static Integer readIntWithTimeout(int min, int max, int defaultValue) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "JarJar-TUI-Input");
            t.setDaemon(true);
            return t;
        });

        try {
            Future<Integer> future = executor.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            // EOF - stdin closed
                            return defaultValue;
                        }
                        line = line.trim();
                        if (line.isEmpty()) {
                            return defaultValue;
                        }
                        try {
                            int value = Integer.parseInt(line);
                            if (value >= min && value <= max) {
                                return value;
                            }
                            System.out.println("Please enter a number between " + min + " and " + max + ": ");
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input. Please enter a number: ");
                        }
                    }
                }
            });

            return future.get(TUI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (Exception e) {
            return defaultValue;
        } finally {
            executor.shutdownNow();
        }
    }
}
