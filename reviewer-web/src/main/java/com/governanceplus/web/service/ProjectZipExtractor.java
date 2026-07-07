package com.governanceplus.web.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts an uploaded project zip into a fresh temp directory. Guards
 * against zip-slip (entries escaping the target directory) and zip-bombs
 * (entry count / total uncompressed size caps), since this backs an
 * unauthenticated upload endpoint accepting arbitrary zip files.
 */
@Component
public class ProjectZipExtractor {

    private static final int MAX_ENTRIES = 5_000;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 200L * 1024 * 1024; // 200MB

    public Path extract(MultipartFile zipFile) throws IOException {
        Path targetDir = Files.createTempDirectory("governanceplus-upload-" + UUID.randomUUID());

        int entryCount = 0;
        long totalBytes = 0;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException("Zip has more than " + MAX_ENTRIES + " entries; rejecting as a likely zip bomb");
                }

                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                    continue;
                }

                Files.createDirectories(entryPath.getParent());
                totalBytes += copyWithLimit(zis, entryPath, MAX_TOTAL_UNCOMPRESSED_BYTES - totalBytes);
            }
        }

        return targetDir;
    }

    private long copyWithLimit(InputStream in, Path destination, long remainingBudget) throws IOException {
        if (remainingBudget <= 0) {
            throw new IOException("Zip exceeds max total uncompressed size of "
                    + MAX_TOTAL_UNCOMPRESSED_BYTES + " bytes; rejecting as a likely zip bomb");
        }
        byte[] buffer = new byte[8192];
        long written = 0;
        try (OutputStream out = Files.newOutputStream(destination)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > remainingBudget) {
                    throw new IOException("Zip exceeds max total uncompressed size of "
                            + MAX_TOTAL_UNCOMPRESSED_BYTES + " bytes; rejecting as a likely zip bomb");
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }

    /** Best-effort cleanup of an extracted temp directory after a job finishes. */
    public void cleanup(Path dir) {
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount()) // delete children before parents
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best-effort; OS will clean temp dirs eventually regardless
                        }
                    });
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
