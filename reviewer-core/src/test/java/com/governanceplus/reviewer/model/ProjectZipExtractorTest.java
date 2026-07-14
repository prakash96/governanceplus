package com.governanceplus.reviewer.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectZipExtractorTest {

    @Test
    void extractsUnderTheGivenBaseDirAndCleansUpAfterward(@TempDir Path baseDir) throws Exception {
        byte[] zipBytes = zip(fixtureFile("rule-engine-fixture"));

        ProjectZipExtractor extractor = new ProjectZipExtractor(baseDir.toString());
        Path extracted = extractor.extract(zipBytes);

        assertTrue(extracted.startsWith(baseDir), "extracted dir should live under the configured base dir");
        assertTrue(Files.isDirectory(extracted.resolve("src/main/mule")));
        assertTrue(Files.isRegularFile(extracted.resolve("pom.xml")));

        ProjectZipExtractor.cleanup(extracted);
        assertFalse(Files.exists(extracted), "cleanup should remove the extracted directory");
    }

    @Test
    void extractZipAsPathBridgeReturnsAStringPathAndCleanupPathRemovesIt(@TempDir Path baseDir) throws Exception {
        byte[] zipBytes = zip(fixtureFile("rule-engine-fixture"));

        String extractedPath = ProjectZipExtractor.extractZipAsPath(zipBytes, baseDir.toString());

        assertTrue(Path.of(extractedPath).startsWith(baseDir));
        assertTrue(Files.isDirectory(Path.of(extractedPath, "src/main/mule")));

        ProjectZipExtractor.cleanupPath(extractedPath);
        assertFalse(Files.exists(Path.of(extractedPath)), "cleanupPath should remove the extracted directory");
    }

    @Test
    void rejectsZipSlipEntries(@TempDir Path baseDir) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("../../evil.txt"));
            zos.write("gotcha".getBytes());
            zos.closeEntry();
        }

        ProjectZipExtractor extractor = new ProjectZipExtractor(baseDir.toString());
        IOException e = assertThrows(IOException.class, () -> extractor.extract(bos.toByteArray()));
        assertTrue(e.getMessage().contains("escapes target directory"));
    }

    private byte[] zip(File dir) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            addToZip(dir, dir, zos);
        }
        return bos.toByteArray();
    }

    private void addToZip(File root, File current, ZipOutputStream zos) throws IOException {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            String relativePath = root.toPath().relativize(child.toPath()).toString().replace('\\', '/');
            if (child.isDirectory()) {
                addToZip(root, child, zos);
            } else {
                zos.putNextEntry(new ZipEntry(relativePath));
                Files.copy(child.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    private File fixtureFile(String classpathRelativePath) {
        URL url = getClass().getClassLoader().getResource(classpathRelativePath);
        assertNotNull(url, "fixture not found on classpath: " + classpathRelativePath);
        return new File(url.getFile());
    }
}
