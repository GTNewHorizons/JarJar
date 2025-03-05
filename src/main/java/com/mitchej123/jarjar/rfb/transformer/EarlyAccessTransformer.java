package com.mitchej123.jarjar.rfb.transformer;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;
import java.util.jar.Manifest;

/**
 * We're using the source access transformers to allow access to some FML fields/methods/classes, however these load before the access transformers, so we need
 * to make the fields/methods/classes public in the bytecode.
 */
public class EarlyAccessTransformer implements RfbClassTransformer {

    @Override
    public @NotNull String id() {
        return "jarjar-earlyaccesstransformer";
    }

    @Override
    public @NotNull String @Nullable [] sortBefore() {
        return new String[] { "*" };
    }

    private static final Set<String> TARGET_CLASSES = ImmutableSet.of(
        "cpw.mods.fml.relauncher.CoreModManager",
        "cpw.mods.fml.relauncher.CoreModManager.FMLPluginWrapper",
        "cpw.mods.fml.relauncher.CoreModManager$FMLPluginWrapper",
        "cpw.mods.fml.relauncher.ModListHelper",
        "cpw.mods.fml.relauncher.FMLInjectionData",
        "cpw.mods.fml.common.discovery.ModCandidate",
        "cpw.mods.fml.common.MetadataCollection");

    @Override
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest,
        @NotNull String className, @NotNull ClassNodeHandle classNode) {
        return TARGET_CLASSES.contains(className);
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest,
        @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final @Nullable ClassNode cn = classNode.getNode();

        if (cn == null || cn.fields == null) {
            return;
        }

        cn.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
        cn.access |= Opcodes.ACC_PUBLIC;

        // Make all fields public
        for (FieldNode field : cn.fields) {
            field.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
            field.access |= Opcodes.ACC_PUBLIC;
        }
        // Make all methods public
        if (cn.methods != null) {
            for (MethodNode method : cn.methods) {
                method.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                method.access |= Opcodes.ACC_PUBLIC;
            }
        }
        // Make all inner classes public
        if (cn.innerClasses != null) {
            for (InnerClassNode innerClass : cn.innerClasses) {
                if (innerClass.name.endsWith("FMLPluginWrapper")) {
                    innerClass.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
                    innerClass.access |= Opcodes.ACC_PUBLIC;
                }
            }
        }
    }
}
