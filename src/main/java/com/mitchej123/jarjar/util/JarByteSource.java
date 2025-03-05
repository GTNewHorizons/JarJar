package com.mitchej123.jarjar.util;

import com.google.common.io.ByteSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarByteSource extends ByteSource {

    private final JarFile jar;
    private final JarEntry entry;

    public JarByteSource(JarFile jar, JarEntry entry) {
        this.jar = jar;
        this.entry = entry;
    }

    @Override
    public InputStream openStream() throws IOException {
        return jar.getInputStream(entry);
    }
}
