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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineReviewAdapterTest {

    private final RuleEngineReviewAdapter adapter = new RuleEngineReviewAdapter();

    @Test
    void mapsXmlAndPomViolationsIntoReviewFindings() throws Exception {
        File projectDir = fixtureFile("rule-engine-fixture");
        File rulesJson = fixtureFile("rule-engine-fixture/rules.json");

        ReviewReport report = adapter.review(projectDir, rulesJson.getAbsolutePath());

        assertNotNull(report.getFindings());
        assertEquals(3, report.getFindings().size());

        ReviewFinding xmlFinding = report.getFindings().stream()
                .filter(f -> "Security".equals(f.getCategory()))
                .findFirst()
                .orElseThrow();
        assertEquals(ReviewFinding.Status.FAIL, xmlFinding.getStatus());
        assertEquals(ReviewFinding.Severity.CRITICAL, xmlFinding.getSeverity());
        assertEquals("HTTP Listener config must not use plain HTTP protocol", xmlFinding.getRule());
        assertTrue(xmlFinding.getFile().contains("order-processing.xml"));

        ReviewFinding pomFinding = report.getFindings().stream()
                .filter(f -> "Dependencies".equals(f.getCategory()))
                .findFirst()
                .orElseThrow();
        assertEquals(ReviewFinding.Status.FAIL, pomFinding.getStatus());
        assertTrue(pomFinding.getRule().contains("mule-http-connector"));
        assertTrue(pomFinding.getExplanation().contains("1.5.0"));

        ReviewFinding swaggerFinding = report.getFindings().stream()
                .filter(f -> "API Design".equals(f.getCategory()))
                .findFirst()
                .orElseThrow();
        assertEquals(ReviewFinding.Status.FAIL, swaggerFinding.getStatus());
        assertEquals(ReviewFinding.Severity.MAJOR, swaggerFinding.getSeverity());
        assertEquals("GET operations should document a 500 response", swaggerFinding.getRule());
        assertTrue(swaggerFinding.getFile().contains("order-api.yaml"));
    }

    @Test
    void findsViolationsEvenWhenTheProjectIsWrappedInAnExtraTopLevelFolder() throws Exception {
        // Simulates zipping the project FOLDER itself (the default behavior of most
        // OS "compress"/"send to zip" actions) rather than its contents — the most
        // common real-world cause of "rule engine finds nothing" bug reports.
        File extractedDir = fixtureFile("rule-engine-fixture-wrapped");
        File rulesJson = fixtureFile("rule-engine-fixture/rules.json");

        ReviewReport report = adapter.review(extractedDir, rulesJson.getAbsolutePath());

        assertNotNull(report.getFindings());
        assertEquals(2, report.getFindings().size());
    }

    @Test
    void reviewAsJsonProducesTheSameFindingsAsAJsonString() throws Exception {
        File projectDir = fixtureFile("rule-engine-fixture");
        File rulesJson = fixtureFile("rule-engine-fixture/rules.json");

        String json = RuleEngineReviewAdapter.reviewAsJson(projectDir.getAbsolutePath(), rulesJson.getAbsolutePath());

        assertTrue(json.contains("\"findings\""));
        assertTrue(json.contains("HTTP Listener config must not use plain HTTP protocol"));
    }

    @Test
    void isDirectoryDistinguishesRealDirectoriesFromNonsensePaths() throws Exception {
        File projectDir = fixtureFile("rule-engine-fixture");

        assertTrue(RuleEngineReviewAdapter.isDirectory(projectDir.getAbsolutePath()));
        assertFalse(RuleEngineReviewAdapter.isDirectory("/definitely/not/a/real/path/xyz"));
    }

    @Test
    void reviewZipAsJsonExtractsUnderBaseDirAndProducesTheSameFindings(@TempDir Path baseDir) throws Exception {
        File projectDir = fixtureFile("rule-engine-fixture");
        File rulesJson = fixtureFile("rule-engine-fixture/rules.json");
        byte[] zipBytes = zip(projectDir);

        String json = RuleEngineReviewAdapter.reviewZipAsJson(zipBytes, rulesJson.getAbsolutePath(), baseDir.toString());

        assertTrue(json.contains("\"findings\""));
        assertTrue(json.contains("HTTP Listener config must not use plain HTTP protocol"));
        assertTrue(json.contains("mule-http-connector"));
        assertTrue(json.contains("GET operations should document a 500 response"));

        // The extracted temp directory should have been cleaned up — baseDir itself is still
        // there (ProjectZipExtractor's constructor ensures it exists), but nothing left inside it.
        try (var stream = Files.list(baseDir)) {
            assertEquals(0, stream.count(), "extracted temp dir should be cleaned up after review");
        }
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
