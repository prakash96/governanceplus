package com.governanceplus.reviewer.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts an uploaded project zip into a fresh temp directory under a configurable base
 * directory (e.g. /opt/mule/tmp for reviewer-mule's submit-review flow — see
 * {@link RuleEngineReviewAdapter#reviewZipAsJson}). Guards against zip-slip (entries escaping the
 * target directory) and zip-bombs (entry count / total uncompressed size caps), since this backs
 * an unauthenticated upload endpoint accepting arbitrary zip files.
 *
 * This mirrors reviewer-web's own ProjectZipExtractor (same protections, ported rather than
 * shared) — consistent with reviewer-mule's existing "mild duplication over a shared-module
 * refactor of a working app" precedent (see reviewer-mule/pom.xml's top comment).
 */
public class ProjectZipExtractor {

    private static final int MAX_ENTRIES = 5_000;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 200L * 1024 * 1024; // 200MB

    private final Path baseDir;

    public ProjectZipExtractor(String baseDir) throws IOException {
        this.baseDir = Paths.get(baseDir);
        Files.createDirectories(this.baseDir);
    }

    /**
     * DataWeave-callable convenience wrapper (`import * from java!...ProjectZipExtractor` in a
     * Mule flow) — DataWeave's native Java interop only supports static methods with
     * DataWeave-representable parameters/return types, so this wraps the instance-based
     * extract(byte[])/cleanup(Path) pair with a plain-string path in and out. This is one of only
     * two pieces of reviewer-core logic reviewer-mule's native rewrite still calls into Java for
     * (the other being SwaggerRuleEvaluator's JSONPath evaluation) — there's no Mule/DataWeave
     * component that walks multi-entry ZIP archives with zip-slip/zip-bomb guards the way this
     * class does; everything downstream of extraction (walking the resulting directory tree,
     * evaluating XML/POM rules, assembling the report) is native DataWeave in review-engine.xml.
     */
    public static String extractZipAsPath(byte[] zipBytes, String baseDir) throws IOException {
        return new ProjectZipExtractor(baseDir).extract(zipBytes).toAbsolutePath().toString();
    }

    /** DataWeave-callable counterpart to {@link #extractZipAsPath} — deletes the extracted temp directory. */
    public static void cleanupPath(String dirPath) {
        cleanup(Paths.get(dirPath));
    }

    public Path extract(byte[] zipBytes) throws IOException {
        Path targetDir = Files.createTempDirectory(baseDir, "reviewer-mule-upload-" + UUID.randomUUID());

        int entryCount = 0;
        long totalBytes = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
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

    /** Best-effort cleanup of an extracted temp directory after a review finishes. */
    public static void cleanup(Path dir) {
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount()) // delete children before parents
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best-effort
                        }
                    });
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
