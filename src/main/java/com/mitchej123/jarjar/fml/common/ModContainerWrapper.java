package com.mitchej123.jarjar.fml.common;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.retrofuturabootstrap.versioning.DefaultArtifactVersion;
import com.mitchej123.jarjar.discovery.SortableCandidate;
import com.mitchej123.jarjar.fml.common.discovery.ModCandidateV2;
import com.mitchej123.jarjar.fml.common.discovery.asm.ASMModParserV2;
import cpw.mods.fml.common.ModContainer;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@Desugar
public record ModContainerWrapper(ModContainer mod, ModCandidateV2 candidate, ASMModParserV2 parser) implements SortableCandidate {
    @Override
    public @NotNull String getId() {
        return mod.getModId();
    }

    @Override
    public @NotNull DefaultArtifactVersion getVersion() {
        return new DefaultArtifactVersion(mod.getVersion());
    }

    @Override
    public @NotNull File getFile() {
        return mod.getSource();
    }

    public int getNestLevel() {
        return candidate.getNestLevel();
    }

}
