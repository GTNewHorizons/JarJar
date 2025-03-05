package com.mitchej123.jarjar.fml.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LoaderUtil {
    private static final ConcurrentMap<Path, Path> pathNormalizationCache = new ConcurrentHashMap<>();

    public static String getClassFileName(String className) {
        return className.replace('.', '/').concat(".class");
    }

    public static Path normalizePath(Path path) {
        if (Files.exists(path)) {
            return normalizeExistingPath(path);
        } else {
            return path.toAbsolutePath().normalize();
        }
    }

    public static Path normalizeExistingPath(Path path) {
        return pathNormalizationCache.computeIfAbsent(path, LoaderUtil::normalizeExistingPath0);
    }

    private static Path normalizeExistingPath0(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
