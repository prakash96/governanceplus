package com.governanceplus.reviewer.model;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private File fixtureFile(String classpathRelativePath) {
        URL url = getClass().getClassLoader().getResource(classpathRelativePath);
        assertNotNull(url, "fixture not found on classpath: " + classpathRelativePath);
        return new File(url.getFile());
    }
}
