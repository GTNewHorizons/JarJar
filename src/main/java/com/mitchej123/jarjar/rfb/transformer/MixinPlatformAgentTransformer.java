package com.mitchej123.jarjar.rfb.transformer;

import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.jar.Manifest;

/*
 * We're now handling tweakers + coremods/mods in JarJar, so we don't need the logic in Mixins to handle it.
 */
public class MixinPlatformAgentTransformer implements RfbClassTransformer  {
    public static final String MIXIN_PLATFORM_AGENT = "org/spongepowered/asm/launch/platform/MixinPlatformAgentFMLLegacy";

    private static final String[] CLASS_CONSTANTS = new String[] { MIXIN_PLATFORM_AGENT };
    private static final ClassConstantPoolParser cstPoolParser = new ClassConstantPoolParser(CLASS_CONSTANTS);

    @Override
    public @NotNull String id() {
        return "jarjar-mixinplatformmanager-transformer";
    }

    @Override
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull Context context, @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        return cstPoolParser.find(classNode.getOriginalBytes(), false);
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull Context context, @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final @Nullable ClassNode cn = classNode.getNode();
        if (cn == null || cn.methods == null) {
            return;
        }
        for (final MethodNode mn : cn.methods) {
            if (mn.instructions == null || mn.instructions.size() == 0) {
                continue;
            }
            if(mn.name.equals("initFMLCoreMod")) {
                // We're handling Tweakers + mods/coremods in JarJar
                mn.tryCatchBlocks.clear();
                mn.instructions.clear();
                mn.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                mn.instructions.add(new InsnNode(Opcodes.ARETURN));

            }
        }
    }
}
