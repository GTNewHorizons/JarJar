package com.mitchej123.jarjar.rfb.transformer;

import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.jar.Manifest;

public class CodeChickenCoreTransformer implements RfbClassTransformer {

    @Override
    public @NotNull String id() {
        return "jarjar-codechickencoretransformer";
    }

    @Override
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull Context context, @Nullable Manifest manifest,
        @NotNull String className, @NotNull ClassNodeHandle classNode) {
        return className.equals("codechicken.core.ClassDiscoverer");
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull Context context, @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final @Nullable ClassNode cn = classNode.getNode();

        if (cn == null || cn.fields == null) {
            return;
        }

        /*
         * Find the following code based on the `minecraftSource.getName()` call:
         * - if (!knownLibraries.contains(minecraftSource.getName()))
         * And then add the additional conditional && Hooks.shouldIgnore(minecraftSource))
         * This should prevent CCC's ClassDiscoverer from loading mods that are in the default libraries or have been disabled by LoaderV2.
         */
        for(final MethodNode mn : cn.methods) {
            if(!mn.name.equals("findClasspathMods")) continue;
            AbstractInsnNode insn = mn.instructions.getFirst();
            while (insn != null) {
                if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL && insn instanceof MethodInsnNode mInsn && mInsn.owner.equals("java/io/File") && mInsn.name.equals("getName")) {
                    final int fileVar = ((VarInsnNode) (insn.getPrevious())).var;
                    insn = insn.getNext();
                    if (!(insn instanceof MethodInsnNode)) continue;
                    mInsn = (MethodInsnNode) insn;
                    if (!mInsn.owner.equals("java/util/List") && mInsn.name.equals("contains")) continue;

                    insn = insn.getNext();
                    if (!(insn.getOpcode() == Opcodes.IFNE && insn instanceof JumpInsnNode jInsnNode)) continue;
                    final LabelNode ifneJumpLabel = jInsnNode.label;

                    insn = insn.getNext(); // label
                    mn.instructions.insertBefore(insn, new VarInsnNode(Opcodes.ALOAD, fileVar));
                    insn = insn.getNext(); // linenumber
                    insn = insn.getNext(); // LDC
                    mn.instructions.insertBefore(
                        insn,
                        new MethodInsnNode(Opcodes.INVOKESTATIC, "com/mitchej123/jarjar/util/Hooks", "shouldIgnore", "(Ljava/io/File;)Z", false));
                    mn.instructions.insertBefore(insn, new JumpInsnNode(Opcodes.IFEQ, ifneJumpLabel));
                    break;
                }
                insn = insn.getNext();
            }
        }
    }
}
