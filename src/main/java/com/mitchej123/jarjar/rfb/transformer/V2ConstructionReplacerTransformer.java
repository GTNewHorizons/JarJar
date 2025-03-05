package com.mitchej123.jarjar.rfb.transformer;

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
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.jar.Manifest;

public class V2ConstructionReplacerTransformer implements RfbClassTransformer {
    private static final String LOADER_CLASS = "cpw/mods/fml/common/Loader";
    private static final String LOADER_CLASSNAME = LOADER_CLASS.replace("/", ".");
    private static final String LOADER_V2_CLASS = "com/mitchej123/jarjar/fml/common/LoaderV2";

    private static final String MOD_CONTAINER_FACTORY_CLASS = "cpw/mods/fml/common/ModContainerFactory";
    private static final String MOD_CONTAINER_FACTORY_CLASSNAME = MOD_CONTAINER_FACTORY_CLASS.replace("/", ".");
    private static final String MOD_CONTAINER_FACTORY_V2_CLASS = "com/mitchej123/jarjar/fml/common/ModContainerFactoryV2";

    private static final String MOD_DISCOVERER_CLASSNAME = "cpw.mods.fml.common.discovery.ModDiscoverer";
    private static final String ASM_DATA_TABLE_CLASS = "cpw/mods/fml/common/discovery/ASMDataTable";
    private static final String ASM_DATA_TABLE_V2_CLASS = "com/mitchej123/jarjar/fml/common/discovery/ASMDataTableV2";

    private static final String ASM_MOD_PARSER_CLASSNAME = "cpw.mods.fml.common.discovery.asm.ASMModParser";
    private static final String MOD_ANNOTATION_CLASS = "cpw/mods/fml/common/discovery/asm/ModClassVisitor";
    private static final String MOD_ANNOTATION_V2_CLASS = "com/mitchej123/jarjar/fml/common/discovery/asm/ModClassVisitorV2";

    @Override
    public @NotNull String id() {
        return "jarjar-v2-construction-replacer";
    }

    @Override
    public @NotNull String @Nullable [] sortBefore() {
        return new String[]{"*"};
    }

    @Override
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        return className.equals(LOADER_CLASSNAME) || className.equals(MOD_CONTAINER_FACTORY_CLASSNAME) || className.equals(MOD_DISCOVERER_CLASSNAME) || className.equals(ASM_MOD_PARSER_CLASSNAME);
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest, @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final @Nullable ClassNode cn = classNode.getNode();
        if (cn == null || cn.methods == null) {
            return;
        }


        if (className.equals(LOADER_CLASSNAME)) {
            transformClass(cn, "instance", "()Lcpw/mods/fml/common/Loader;", LOADER_CLASS, LOADER_V2_CLASS, "()V", null);
        } else if (className.equals(MOD_CONTAINER_FACTORY_CLASSNAME)) {
            // if (!mn.name.equals("<clinit>") && mn.desc.equals("()V")) continue;
            transformClass(cn, "<clinit>", "()V", MOD_CONTAINER_FACTORY_CLASS, MOD_CONTAINER_FACTORY_V2_CLASS, "()V", null);
        } else if (className.equals(MOD_DISCOVERER_CLASSNAME)) {
            // if (!mn.name.equals("<init>") && mn.desc.equals("()V")) continue;
            transformClass(cn, "<init>", "()V", ASM_DATA_TABLE_CLASS, ASM_DATA_TABLE_V2_CLASS, "()V", null);
        } else if (className.equals(ASM_MOD_PARSER_CLASSNAME)) {
            // "<init>", "(Ljava/io/InputStream;)V"
            transformClass(cn, "<init>", "(Ljava/io/InputStream;)V", MOD_ANNOTATION_CLASS, MOD_ANNOTATION_V2_CLASS, "(Lcpw/mods/fml/common/discovery/asm/ASMModParser;)V", "(Lcom/mitchej123/jarjar/fml/common/discovery/asm/ASMModParserV2;)V");
        }
    }

    private void transformClass(@NotNull ClassNode cn, @NotNull String methodName, @NotNull String desc, @NotNull String originalClass, @NotNull String newClass, @NotNull String newClassDesc, @Nullable String newNewClassDesc) {
        for (final MethodNode mn : cn.methods) {
            if (!(mn.name.equals(methodName) && mn.desc.equals(desc))) continue;
            for (int i = 0; i < mn.instructions.size(); i++) {
                final AbstractInsnNode aInsn = mn.instructions.get(i);
                if (aInsn.getOpcode() == Opcodes.NEW && aInsn instanceof TypeInsnNode tInsn) {
                    if (tInsn.desc.equals(originalClass)) {
                        tInsn.desc = newClass;
                    }
                } else if (aInsn.getOpcode() == Opcodes.INVOKESPECIAL && aInsn instanceof MethodInsnNode mInsn) {
                    if (mInsn.owner.equals(originalClass) && mInsn.name.equals("<init>") && mInsn.desc.equals(newClassDesc)) {
                        mInsn.owner = newClass;
                    }
                }
            }
        }
    }
}
