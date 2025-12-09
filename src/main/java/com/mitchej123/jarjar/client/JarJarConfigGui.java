package com.mitchej123.jarjar.client;

import com.mitchej123.jarjar.JarJar;
import com.mitchej123.jarjar.launch.EnvUtils;
import com.mitchej123.jarjar.launch.config.UserConfig;
import com.mitchej123.jarjar.launch.prism.PrismRfbInstaller;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)

public class JarJarConfigGui extends GuiConfig {

    private static final int BUTTON_MANAGE_RFB = 1000;
    private static final int BUTTON_MANAGE_RELAUNCH = 1001;
    private GuiButton manageRfbButton;
    private GuiButton manageRelaunchButton;

    public JarJarConfigGui(GuiScreen parent) {
        super(parent, getConfigElements(), JarJar.MODID, false, false, "JarJar Configuration");
    }

    private static List<IConfigElement> getConfigElements() {
        // Return empty list - we don't have traditional config options
        // The main purpose of this screen is the "Manage RFB" button
        return new ArrayList<>();
    }

    @Override
    public void initGui() {
        super.initGui();

        // Add "Manage Direct RFB" button at the top
        String buttonText = getManageButtonText();
        manageRfbButton = new GuiButton(BUTTON_MANAGE_RFB, this.width / 2 - 100, 30, buttonText);
        this.buttonList.add(manageRfbButton);

        // Add "Manage Relaunch Mode" button below (only on Java 8)
        if (EnvUtils.isJava8()) {
            String relaunchText = getRelaunchButtonText();
            manageRelaunchButton = new GuiButton(BUTTON_MANAGE_RELAUNCH, this.width / 2 - 100, 55, relaunchText);
            // Disable if Direct RFB is installed (relaunch mode not needed)
            manageRelaunchButton.enabled = !isDirectRfbInstalled();
            this.buttonList.add(manageRelaunchButton);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw tooltip for disabled relaunch button
        if (manageRelaunchButton != null && !manageRelaunchButton.enabled) {
            if (mouseX >= manageRelaunchButton.xPosition && mouseX < manageRelaunchButton.xPosition + manageRelaunchButton.width &&
                mouseY >= manageRelaunchButton.yPosition && mouseY < manageRelaunchButton.yPosition + manageRelaunchButton.height) {
                drawCreativeTabHoveringText("Direct RFB is installed - relaunch mode not needed", mouseX, mouseY);
            }
        }
    }

    private String getManageButtonText() {
        if (!EnvUtils.isJava8()) {
            return "Direct RFB (Java 9+ - Not Needed)";
        }

        File gameDir = Minecraft.getMinecraft().mcDataDir;
        File instanceDir = gameDir.getParentFile();
        if (instanceDir == null) {
            return "Manage Direct RFB (Error)";
        }

        PrismRfbInstaller installer = new PrismRfbInstaller(
            instanceDir, PrismRfbInstaller.getJarJarVersion());

        if (installer.isInstalled()) {
            if (!installer.isVersionCurrent()) {
                return "Update Direct RFB (Outdated)";
            }
            return "Uninstall Direct RFB";
        } else {
            return "Install Direct RFB";
        }
    }

    private String getRelaunchButtonText() {
        File gameDir = Minecraft.getMinecraft().mcDataDir;
        UserConfig config = UserConfig.load(gameDir);
        UserConfig.Choice savedChoice = config.getSavedChoice();

        if (savedChoice == UserConfig.Choice.RELAUNCH_NOW) {
            return "Disable Relaunch Mode";
        } else {
            return "Enable Relaunch Mode";
        }
    }

    private boolean isDirectRfbInstalled() {
        File gameDir = Minecraft.getMinecraft().mcDataDir;
        File instanceDir = gameDir.getParentFile();
        if (instanceDir == null) {
            return false;
        }
        PrismRfbInstaller installer = new PrismRfbInstaller(
            instanceDir, PrismRfbInstaller.getJarJarVersion());
        return installer.isInstalled();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_MANAGE_RFB) {
            handleManageRfb();
            return;
        }
        if (button.id == BUTTON_MANAGE_RELAUNCH) {
            handleManageRelaunch();
            return;
        }
        super.actionPerformed(button);
    }

    private void handleManageRfb() {
        if (!EnvUtils.isJava8()) {
            javax.swing.JOptionPane.showMessageDialog(null,
                "Direct RFB is only needed on Java 8.\n" +
                "You're running Java 9+ where RFB loads natively.",
                "Not Needed",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File gameDir = Minecraft.getMinecraft().mcDataDir;
        File instanceDir = gameDir.getParentFile();
        if (instanceDir == null) {
            javax.swing.JOptionPane.showMessageDialog(null,
                "Could not determine instance directory.",
                "Error",
                javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        PrismRfbInstaller installer = new PrismRfbInstaller(
            instanceDir, PrismRfbInstaller.getJarJarVersion());

        String[] options;
        String message;

        if (installer.isInstalled()) {
            if (!installer.isVersionCurrent()) {
                options = new String[]{"Update", "Uninstall", "Cancel"};
                message = "Direct RFB is installed but outdated.\n" +
                    "Current JarJar version: " + PrismRfbInstaller.getJarJarVersion();
            } else {
                options = new String[]{"Uninstall", "Cancel"};
                message = "Direct RFB is installed.\n" +
                    "Version: " + PrismRfbInstaller.getJarJarVersion();
            }
        } else {
            options = new String[]{"Install", "Cancel"};
            message = "Direct RFB is not installed.\n\n" +
                "Installing Direct RFB allows JarJar to work on Java 8\n" +
                "without restarting the JVM on every launch.";
        }

        int choice = javax.swing.JOptionPane.showOptionDialog(null, message,
            "JarJar - Direct RFB",
            javax.swing.JOptionPane.DEFAULT_OPTION,
            javax.swing.JOptionPane.QUESTION_MESSAGE,
            null, options, options[options.length - 1]);

        if (choice < 0 || options[choice].equals("Cancel")) {
            return;
        }

        boolean success = false;
        String resultMessage = "";

        if (options[choice].equals("Install")) {
            success = installer.install();
            resultMessage = success
                ? "Direct RFB installed.\nExit and relaunch for changes to take effect."
                : "Failed to install. Check logs.";
        } else if (options[choice].equals("Update")) {
            success = installer.update();
            resultMessage = success
                ? "Direct RFB updated.\nExit and relaunch for changes to take effect."
                : "Failed to update. Check logs.";
        } else if (options[choice].equals("Uninstall")) {
            success = installer.uninstall();
            resultMessage = success
                ? "Direct RFB uninstalled.\nExit and relaunch for changes to take effect."
                : "Failed to uninstall. Check logs.";
        }

        javax.swing.JOptionPane.showMessageDialog(null, resultMessage,
            success ? "Success" : "Error",
            success ? javax.swing.JOptionPane.INFORMATION_MESSAGE : javax.swing.JOptionPane.ERROR_MESSAGE);

        // Refresh button states
        manageRfbButton.displayString = getManageButtonText();
        if (manageRelaunchButton != null) {
            manageRelaunchButton.enabled = !isDirectRfbInstalled();
            manageRelaunchButton.displayString = getRelaunchButtonText();
        }
    }

    private void handleManageRelaunch() {
        File gameDir = Minecraft.getMinecraft().mcDataDir;
        UserConfig config = UserConfig.load(gameDir);
        UserConfig.Choice savedChoice = config.getSavedChoice();

        if (savedChoice == UserConfig.Choice.RELAUNCH_NOW) {
            // Currently enabled - offer to disable
            int choice = javax.swing.JOptionPane.showConfirmDialog(null,
                "Relaunch mode is currently enabled.\n\n" +
                "This causes JarJar to restart the game with RFB on every launch.\n" +
                "Consider using 'Install Direct RFB' instead for faster launches.\n\n" +
                "Disable relaunch mode?",
                "Disable Relaunch Mode",
                javax.swing.JOptionPane.YES_NO_OPTION);

            if (choice == javax.swing.JOptionPane.YES_OPTION) {
                config.clear();
                javax.swing.JOptionPane.showMessageDialog(null,
                    "Relaunch mode disabled.\n\n" +
                    "You'll see the setup dialog next time you launch on Java 8.",
                    "Disabled",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
                manageRelaunchButton.displayString = getRelaunchButtonText();
            }
        } else {
            // Not set - offer to enable
            int choice = javax.swing.JOptionPane.showConfirmDialog(null,
                "Relaunch mode is not currently enabled.\n\n" +
                "When enabled, JarJar will automatically restart the game\n" +
                "with RFB on every launch (adds a few seconds to startup).\n\n" +
                "Note: 'Install Direct RFB' is recommended for faster launches.\n\n" +
                "Enable relaunch mode?",
                "Enable Relaunch Mode",
                javax.swing.JOptionPane.YES_NO_OPTION);

            if (choice == javax.swing.JOptionPane.YES_OPTION) {
                config.setSavedChoice(UserConfig.Choice.RELAUNCH_NOW);
                config.save();
                javax.swing.JOptionPane.showMessageDialog(null,
                    "Relaunch mode enabled.\n\n" +
                    "JarJar will automatically relaunch with RFB on future launches.",
                    "Enabled",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
                manageRelaunchButton.displayString = getRelaunchButtonText();
            }
        }
    }
}
