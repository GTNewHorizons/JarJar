package com.mitchej123.jarjar.rfb.plugin;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizons.retrofuturabootstrap.api.PluginContext;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;
import com.mitchej123.jarjar.rfb.transformer.CodeChickenCoreTransformer;
import com.mitchej123.jarjar.rfb.transformer.EarlyAccessTransformer;
import com.mitchej123.jarjar.rfb.transformer.FMLTransformer;
import com.mitchej123.jarjar.rfb.transformer.MetadataCollectionTransformer;
import com.mitchej123.jarjar.rfb.transformer.MixinPlatformAgentTransformer;
import com.mitchej123.jarjar.rfb.transformer.V2ConstructionReplacerTransformer;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;

public class JarJarRfbPlugin implements RfbPlugin {
    public static final Logger LOG = LogManager.getLogger("JarJar");
    public static final String DISABLE_PROPERTY = "jarjar.disable";
    public static final boolean DISABLED = Boolean.getBoolean(DISABLE_PROPERTY);

    @Override
    public void onConstruction(@NotNull PluginContext ctx) {
        Launch.blackboard.put("jarjar.rfbPluginLoaded", Boolean.TRUE);
        Launch.blackboard.put("jarjar.disabled", DISABLED);
        if (DISABLED) {
            LOG.info("JarJar transformers disabled via jvm property");
        }
    }

    @Override
    public @NotNull RfbClassTransformer @Nullable [] makeTransformers() {
        if (DISABLED) {
            return new RfbClassTransformer[] {};
        }
        return new RfbClassTransformer[] {
            new EarlyAccessTransformer(),
            new FMLTransformer(),
            new MixinPlatformAgentTransformer(),
            new V2ConstructionReplacerTransformer(),
            new CodeChickenCoreTransformer(),
            new MetadataCollectionTransformer()
        };
    }

}
