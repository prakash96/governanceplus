package com.governanceplus.reviewermule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts an uploaded project zip into a fresh temp directory under a configurable base
 * directory (/opt/mule/tmp by default — see global.xml's zip.extraction.dir and reviews.xml's
 * submit-review flow). Guards against zip-slip (entries escaping the target directory) and
 * zip-bombs (entry count / total uncompressed size caps), since this backs an unauthenticated
 * upload endpoint accepting arbitrary zip files.
 *
 * This is a self-contained copy of reviewer-core's ProjectZipExtractor (same algorithm, ported
 * rather than shared) living directly in reviewer-mule so this module has NO Maven dependency on
 * reviewer-core at all — the only other thing reviewer-core was still supplying (JSONPath/Swagger
 * rule evaluation) moved to native DataWeave in dwlib::JsonPath. Multi-entry ZIP archive
 * extraction with these guards has no native Mule/DataWeave equivalent (Mule's Compression Module
 * only handles single-stream GZIP/Deflate), so this one small class is the sole piece of Java left
 * anywhere in reviewer-mule, called only via DataWeave's native Java interop
 * (`import * from java!com::governanceplus::reviewermule::ProjectZipExtractor`) — never the Mule
 * Java Module.
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
     * DataWeave-callable convenience wrapper — DataWeave's native Java interop only supports
     * static methods with DataWeave-representable parameters/return types, so this wraps the
     * instance-based extract(byte[])/cleanup(Path) pair with a plain-string path in and out.
     * DataWeave's Binary type maps directly to the byte[] parameter.
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

        try (Stream<Path> stream = Files.list(targetDir)) {
            Optional<Path> firstChildFolder = stream
                    .filter(Files::isDirectory)
                    .findFirst();
            
            targetDir = firstChildFolder.get();
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

    /**
     * Best-effort cleanup of an extracted temp directory after a review finishes. setWritable(true)
     * before each delete clears any read-only attribute a file might carry (Windows:
     * FILE_ATTRIBUTE_READONLY; POSIX: owner-write bit) — confirmed necessary the hard way via
     * GitFetcher's equivalent cleanup, where Git/JGit's deliberately-read-only .git/objects/pack/*
     * files caused Files.deleteIfExists to throw AccessDeniedException, silently swallowed by the
     * catch below, leaving them undeletable even from a separate process afterward. Also closes
     * the Files.walk Stream via try-with-resources, per its own Javadoc, to release its native
     * directory-handle resources promptly.
     */
    public static void cleanup(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()) // delete children before parents
                    .forEach(p -> {
                        try {
                            p.toFile().setWritable(true);
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
