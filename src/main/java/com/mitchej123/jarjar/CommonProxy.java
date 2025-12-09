package com.mitchej123.jarjar;

import com.mitchej123.jarjar.launch.EnvUtils;
import com.mitchej123.jarjar.launch.JarJarTweaker;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        String status = getStatus();
        JarJar.LOG.info("JarJar {} - Status: {}", Tags.VERSION, status);
    }

    public static String getStatus() {
        if (!EnvUtils.isJava8()) {
            return "Active (Java 9+ with RFB)";
        }
        if (EnvUtils.isRfbEnvironment()) {
            return "Active (Java 8 with RFB)";
        }
        if (JarJarTweaker.isDisabled()) {
            return "DISABLED (Java 8 - No RFB)";
        }
        return "Unknown";
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
