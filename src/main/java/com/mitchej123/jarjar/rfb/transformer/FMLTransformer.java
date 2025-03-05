package com.mitchej123.jarjar.rfb.transformer;

import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.jar.Manifest;

public class FMLTransformer implements RfbClassTransformer {
    public static final String CORE_MOD_MANAGER = "cpw/mods/fml/relauncher/CoreModManager";
    public static final String CORE_MOD_MANAGER_V2 = "com/mitchej123/jarjar/fml/relauncher/CoreModManagerV2";

    public static final String METADATA_COLLECTION = "cpw/mods/fml/common/MetadataCollection";
    public static final String METADATA_COLLECTION_V2 = "com/mitchej123/jarjar/fml/common/MetadataCollectionV2";

    private static final String[] CLASS_CONSTANTS = new String[] { CORE_MOD_MANAGER, METADATA_COLLECTION };
    private static final ClassConstantPoolParser cstPoolParser = new ClassConstantPoolParser(CLASS_CONSTANTS);

    @Override
    public @NotNull String id() {
        return "jarjar-fmltransformer-v2";
    }

    @Override
    public @NotNull String @Nullable [] sortBefore() {
        return new String[]{"*"};
    }

    @Override
    public @NotNull String @Nullable [] additionalExclusions() {
        return new String[]{"com.mitchej123.jarjar.fml."};
    }

    @Override
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        return cstPoolParser.find(classNode.getOriginalBytes(), false);
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final @Nullable ClassNode cn = classNode.getNode();
        if (cn == null || cn.methods == null) {
            return;
        }
        for (final MethodNode mn : cn.methods) {
            if (mn.instructions == null || mn.instructions.size() == 0) {
                continue;
            }
            for (int i = 0; i < mn.instructions.size(); i++) {
                final AbstractInsnNode aInsn = mn.instructions.get(i);
                if(aInsn.getOpcode() == Opcodes.INVOKESTATIC && aInsn instanceof MethodInsnNode mInsn) {
                    if(mInsn.owner.equals(CORE_MOD_MANAGER)) {
                        mInsn.owner = CORE_MOD_MANAGER_V2;
                    } else if(mInsn.owner.equals(METADATA_COLLECTION)) {
                        mInsn.owner = METADATA_COLLECTION_V2;
                    }
                }
            }
        }
    }
}
