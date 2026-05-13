package com.mitchej123.jarjar.rfb.transformer;

import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.jar.Manifest;

public class FMLTransformer implements RfbClassTransformer {
    public static final String CORE_MOD_MANAGER = "cpw/mods/fml/relauncher/CoreModManager";
    public static final String CORE_MOD_MANAGER_V2 = "com/mitchej123/jarjar/fml/relauncher/CoreModManagerV2";
    public static final String CORE_MOD_MANAGER_CLASS_NAME = "cpw.mods.fml.relauncher.CoreModManager";

    public static final String METADATA_COLLECTION = "cpw/mods/fml/common/MetadataCollection";
    public static final String METADATA_COLLECTION_V2 = "com/mitchej123/jarjar/fml/common/MetadataCollectionV2";

    public static final String HANDLE_LAUNCH_METHOD = "handleLaunch";
    public static final String HANDLE_LAUNCH_DESC = "(Ljava/io/File;Lnet/minecraft/launchwrapper/LaunchClassLoader;Lcpw/mods/fml/common/launcher/FMLTweaker;)V";

    public static final String ASM_MOD_PARSER = "cpw/mods/fml/common/discovery/asm/ASMModParser";
    public static final String ASM_MOD_PARSER_CLASS_NAME = "cpw.mods.fml.common.discovery.asm.ASMModParser";
    private static final String CLASS_READER = "org/objectweb/asm/ClassReader";
    private static final String CLASS_VISITOR_ACCEPT_DESC = "(Lorg/objectweb/asm/ClassVisitor;I)V";
    private static final int ASM_PARSE_SKIP_FLAGS = ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;

    private static final String[] CLASS_CONSTANTS = new String[] { CORE_MOD_MANAGER, METADATA_COLLECTION, ASM_MOD_PARSER };
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

        final boolean isCoreModManager = className.equals(CORE_MOD_MANAGER_CLASS_NAME);
        final boolean isAsmModParser = className.equals(ASM_MOD_PARSER_CLASS_NAME);

        for (final MethodNode mn : cn.methods) {
            // Special handling for CoreModManager.handleLaunch method - replace entire method body
            if (isCoreModManager && HANDLE_LAUNCH_METHOD.equals(mn.name) && mn.desc.equals(HANDLE_LAUNCH_DESC)) {
                replaceHandleLaunchMethod(mn);
                continue; // Skip normal transformation for this method
            }

            if (isAsmModParser && "<init>".equals(mn.name) && "(Ljava/io/InputStream;)V".equals(mn.desc)) {
                patchAsmModParserCtor(mn);
                continue;
            }

            // Normal transformation - redirect method calls
            if (mn.instructions == null || mn.instructions.size() == 0) {
                continue;
            }
            for (int i = 0; i < mn.instructions.size(); i++) {
                final AbstractInsnNode aInsn = mn.instructions.get(i);
                if (aInsn.getOpcode() == Opcodes.INVOKESTATIC && aInsn instanceof MethodInsnNode mInsn) {
                    if (mInsn.owner.equals(CORE_MOD_MANAGER)) {
                        mInsn.owner = CORE_MOD_MANAGER_V2;
                    } else if (mInsn.owner.equals(METADATA_COLLECTION)) {
                        mInsn.owner = METADATA_COLLECTION_V2;
                    }
                }
            }
        }
    }

    private static void patchAsmModParserCtor(@NotNull MethodNode mn) {
        if (mn.instructions == null || mn.instructions.size() == 0) {
            return;
        }
        for (int i = 0; i < mn.instructions.size(); i++) {
            final AbstractInsnNode insn = mn.instructions.get(i);
            if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL || !(insn instanceof MethodInsnNode mInsn)) {
                continue;
            }
            if (!CLASS_READER.equals(mInsn.owner) || !"accept".equals(mInsn.name) || !CLASS_VISITOR_ACCEPT_DESC.equals(mInsn.desc)) {
                continue;
            }
            final AbstractInsnNode flagsInsn = insn.getPrevious();
            if (flagsInsn == null || flagsInsn.getOpcode() != Opcodes.ICONST_0) {
                return;
            }

            // Swap flags with SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES
            mn.instructions.set(flagsInsn, new IntInsnNode(Opcodes.BIPUSH, ASM_PARSE_SKIP_FLAGS));
            return;
        }
    }

    private void replaceHandleLaunchMethod(@NotNull MethodNode mn) {
        // Clear all method metadata that depends on the old instructions
        mn.instructions.clear();
        mn.tryCatchBlocks.clear();
        mn.localVariables = null;

        // Create new instruction list that just delegates to CoreModManagerV2
        final InsnList newInstructions = new InsnList();

        // Load the three parameters onto the stack
        newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); // mcDir (File)
        newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 1)); // classLoader (LaunchClassLoader)
        newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 2)); // tweaker (FMLTweaker)

        // Call CoreModManagerV2.handleLaunch
        newInstructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CORE_MOD_MANAGER_V2, HANDLE_LAUNCH_METHOD, mn.desc, false));

        // Return
        newInstructions.add(new InsnNode(Opcodes.RETURN));

        mn.instructions = newInstructions;

        // Reset max stack and locals for the simple delegation
        mn.maxStack = 3; // Three parameters on stack
        mn.maxLocals = 3; // Three parameters in locals
    }
}
