package com.mitchej123.jarjar.rfb.transformer;

import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.jar.Manifest;

/**
 * Adds SerializedName annotation to MetadataCollection.modList field.
 * This fixes GSON deserialization for mcmod.info files that use different casing for modList.
 */
public class MetadataCollectionTransformer implements RfbClassTransformer {
    private static final String TARGET_CLASS = "cpw.mods.fml.common.MetadataCollection";
    private static final String TARGET_FIELD = "modList";
    private static final String SERIALIZED_NAME_DESC = "Lcom/google/gson/annotations/SerializedName;";

    @Override
    public @NotNull String id() {
        return "jarjar-serializednametransformer";
    }

    @Override
    public @NotNull String @Nullable [] sortBefore() {
        return new String[] { "*" };
    }

    @Override
    public boolean shouldTransformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest,
        @NotNull String className, @NotNull ClassNodeHandle classNode) {
        return className.equals(TARGET_CLASS);
    }

    @Override
    public void transformClass(@NotNull ExtensibleClassLoader classLoader, @NotNull RfbClassTransformer.Context context, @Nullable Manifest manifest,
        @NotNull String className, @NotNull ClassNodeHandle classNode) {
        final @Nullable ClassNode cn = classNode.getNode();

        if (cn == null || cn.fields == null) {
            return;
        }

        // Find the modList field
        for (FieldNode field : cn.fields) {
            if (field.name.equals(TARGET_FIELD)) {
                // Check if the annotation already exists
                boolean hasAnnotation = false;
                if (field.visibleAnnotations != null) {
                    for (AnnotationNode annotation : field.visibleAnnotations) {
                        if (annotation.desc.equals(SERIALIZED_NAME_DESC)) {
                            hasAnnotation = true;
                            break;
                        }
                    }
                }

                if (!hasAnnotation) {
                    // Create the SerializedName annotation
                    AnnotationNode annotation = new AnnotationNode(SERIALIZED_NAME_DESC);
                    annotation.values = new ArrayList<>();
                    annotation.values.add("value");
                    annotation.values.add("modList");
                    annotation.values.add("alternate");
                    annotation.values.add(Arrays.asList("modlist", "ModList"));

                    // Add the annotation to the field
                    if (field.visibleAnnotations == null) {
                        field.visibleAnnotations = new ArrayList<>();
                    }
                    field.visibleAnnotations.add(annotation);
                }
                break;
            }
        }
    }
}
