package com.mitchej123.jarjar.launch.bootstrap;

import com.mitchej123.jarjar.launch.EnvUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RfbExtractor {

    // Absolute path from JAR root - resource is placed at com/mitchej123/jarjar/launch/
    private static final String RFB_BUNDLE_RESOURCE = "/com/mitchej123/jarjar/launch/rfb-bundle.jar";

    public static Path extractRfb() throws IOException {
        Path cacheDir = EnvUtils.getCacheDir();
        Files.createDirectories(cacheDir);

        // Stream and hash simultaneously
        HashResult hashResult = loadAndHashBundle();
        String hash = hashResult.hash.substring(0, 12);
        Path targetPath = cacheDir.resolve("rfb-" + hash + ".jar");

        if (!Files.exists(targetPath)) {
            Path tempFile = cacheDir.resolve("rfb-" + hash + ".jar.tmp");
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                out.write(hashResult.data);
            }
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return targetPath;
    }

    private static class HashResult {
        final byte[] data;
        final String hash;

        HashResult(byte[] data, String hash) {
            this.data = data;
            this.hash = hash;
        }
    }

    /**
     * Load bundle from resources while computing hash in a single pass.
     */
    private static HashResult loadAndHashBundle() throws IOException {
        try (InputStream rawIs = RfbExtractor.class.getResourceAsStream(RFB_BUNDLE_RESOURCE)) {
            if (rawIs == null) {
                throw new IOException("RFB bundle not found in resources: " + RFB_BUNDLE_RESOURCE);
            }

            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 not available", e);
            }

            // Wrap input stream to compute hash while reading
            DigestInputStream dis = new DigestInputStream(rawIs, md);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = dis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            byte[] hashBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            return new HashResult(baos.toByteArray(), sb.toString());
        }
    }
}
