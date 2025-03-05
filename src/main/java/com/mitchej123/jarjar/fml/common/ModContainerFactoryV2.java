package com.mitchej123.jarjar.fml.common;

import com.mitchej123.jarjar.fml.common.discovery.ModCandidateV2;
import com.mitchej123.jarjar.fml.common.discovery.asm.ASMModParserV2;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.MetadataCollection;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModContainerFactory;
import cpw.mods.fml.common.discovery.asm.ModAnnotation;
import org.apache.logging.log4j.Level;

import java.io.File;

public class ModContainerFactoryV2 extends ModContainerFactory {

    @SuppressWarnings("unused")
    public ModContainerWrapper buildV2(ASMModParserV2 modParser, File modSource, ModCandidateV2 candidate) {
        final String className = modParser.getASMType().getClassName();
        for (ModAnnotation ann : modParser.getAnnotations()) {
            if (modTypes.containsKey(ann.getASMType())) {
                FMLLog.fine("Identified a mod of type %s (%s) - loading", ann.getASMType(), className);
                try {
                    final ModContainer mod = modTypes.get(ann.getASMType()).newInstance(className, candidate, ann.getValues());
                    final MetadataCollection metadataCollection = candidate.getMetadataCollection();
                    mod.bindMetadata(metadataCollection);

                    return new ModContainerWrapper(mod, candidate, modParser);
                } catch (Exception e) {
                    FMLLog.log(Level.ERROR, e, "Unable to construct %s container", ann.getASMType().getClassName());
                    return null;
                }
            }
        }
        return null;
    }
}
