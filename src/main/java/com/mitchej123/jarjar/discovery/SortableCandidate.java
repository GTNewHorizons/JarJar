package com.mitchej123.jarjar.discovery;

import com.gtnewhorizons.retrofuturabootstrap.versioning.DefaultArtifactVersion;

import java.io.File;

public interface SortableCandidate {

    /**
     * Get the unique identifier for this candidate
     * @return a unique identifier
     */
    String getId();

    /**
     * Get the version of this candidate
     * @return the version
     */
    DefaultArtifactVersion getVersion();

    /**
     * Get the file of this candidate
     * @return the file
     */
    File getFile();
}
