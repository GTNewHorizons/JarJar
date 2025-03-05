package com.mitchej123.jarjar.fml.common.discovery.finder;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@FunctionalInterface
public interface ModCandidateFinder {
    void findCandidates(ModCandidateConsumer out);

    interface ModCandidateConsumer {
        default void accept(Path path, boolean requiresRemap) {
            accept(Collections.singletonList(path), requiresRemap, false, false);
        }
        default void accept(List<Path> paths, boolean requiresRemap) {
            accept(paths, requiresRemap, false, false);
        }

        void accept(List<Path> paths, boolean requiresRemap, boolean isMinecraft, boolean isClasspath);
    }
}
