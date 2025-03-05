package com.mitchej123.jarjar.rfb.plugin;

import com.gtnewhorizons.retrofuturabootstrap.api.PluginContext;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;
import com.mitchej123.jarjar.rfb.transformer.CodeChickenCoreTransformer;
import com.mitchej123.jarjar.rfb.transformer.EarlyAccessTransformer;
import com.mitchej123.jarjar.rfb.transformer.FMLTransformer;
import com.mitchej123.jarjar.rfb.transformer.MixinPlatformAgentTransformer;
import com.mitchej123.jarjar.rfb.transformer.V2ConstructionReplacerTransformer;
import net.minecraft.launchwrapper.Launch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JarJarRfbPlugin implements RfbPlugin {

    @Override
    public void onConstruction(@NotNull PluginContext ctx) {
        Launch.blackboard.put("jarjar.rfbPluginLoaded", Boolean.TRUE);
    }

    @Override
    public @NotNull RfbClassTransformer @Nullable [] makeTransformers() {
        return new RfbClassTransformer[] {
            new EarlyAccessTransformer(),
            new FMLTransformer(),
            new MixinPlatformAgentTransformer(),
            new V2ConstructionReplacerTransformer(),
            new CodeChickenCoreTransformer()
        };
    }

}
