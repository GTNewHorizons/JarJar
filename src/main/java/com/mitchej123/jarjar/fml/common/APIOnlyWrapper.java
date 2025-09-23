package com.mitchej123.jarjar.fml.common;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.retrofuturabootstrap.versioning.DefaultArtifactVersion;
import com.mitchej123.jarjar.discovery.SortableCandidate;
import com.mitchej123.jarjar.fml.common.discovery.ModCandidateV2;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * A wrapper for API-only candidates that don't produce ModContainer objects
 * but still need to participate in sorting/resolution so their @API annotations
 * can be sent to the ASMDataTable.
 */
@Desugar
public record APIOnlyWrapper(ModCandidateV2 candidate) implements SortableCandidate {
    @Override
    public @NotNull String getId() {
        return candidate.getId();
    }

    @Override
    public DefaultArtifactVersion getVersion() {
        return candidate.getVersion();
    }

    @Override
    public File getFile() {
        return candidate.getFile();
    }

    public int getNestLevel() {
        return candidate.getNestLevel();
    }
}