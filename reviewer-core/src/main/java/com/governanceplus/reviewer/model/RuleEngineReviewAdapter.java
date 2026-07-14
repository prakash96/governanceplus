package com.governanceplus.reviewer.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.governanceplus.reviewer.ruleengine.MuleRuleValidatorMojo;
import com.governanceplus.reviewer.ruleengine.RuleLoader;
import com.governanceplus.reviewer.ruleengine.model.Rule;
import com.governanceplus.reviewer.ruleengine.model.RuleConfig;
import com.governanceplus.reviewer.ruleengine.model.SwaggerRule;
import com.governanceplus.reviewer.ruleengine.model.Violation;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridges the deterministic com.governanceplus.reviewer.ruleengine rule engine
 * (XPath-based Mule XML checks + pom.xml dependency-version checks) into this
 * project's ReviewReport/ReviewFinding shape, so the CLI and web app can
 * render its output.
 *
 * The rule engine only ever reports problems — it has no PASS concept — so
 * every mapped finding has status FAIL. pom.xml violations key by artifactId
 * rather than a configured rule id (that's how PomDependencyValidator builds
 * them), so those fall back to a fixed "Dependencies" category instead of a
 * rule lookup.
 */
public class RuleEngineReviewAdapter {

    /**
     * DataWeave-callable convenience wrapper (`import * from java!...RuleEngineReviewAdapter` in a
     * Mule flow) — see {@link com.governanceplus.reviewer.ruleengine.XPathEvaluator#evaluateXmlSample}
     * for why this takes/returns plain strings instead of File/ReviewReport objects.
     */
    public static String reviewAsJson(String projectDirPath, String rulesJsonPath) throws Exception {
        ReviewReport report = new RuleEngineReviewAdapter().review(new File(projectDirPath), rulesJsonPath);
        return new ObjectMapper().writeValueAsString(report);
    }

    /**
     * DataWeave-callable boundary check: File#isDirectory() is an instance method, so DataWeave's
     * Java interop (static methods only) can't call it directly on an arbitrary path string.
     */
    public static boolean isDirectory(String path) {
        return new File(path).isDirectory();
    }

    /**
     * DataWeave-callable convenience wrapper for zip-upload project submission (reviews.xml's
     * submit-review flow): extracts zipBytes into a fresh temp directory under
     * extractionBaseDir (e.g. /opt/mule/tmp), reviews it, deletes the temp directory
     * afterward, and returns the report as JSON. DataWeave's Binary type maps to byte[] for
     * this parameter automatically via its Java interop.
     */
    public static String reviewZipAsJson(byte[] zipBytes, String rulesJsonPath, String extractionBaseDir) throws Exception {
        ProjectZipExtractor extractor = new ProjectZipExtractor(extractionBaseDir);
        Path extractedDir = extractor.extract(zipBytes);
        try {
            ReviewReport report = new RuleEngineReviewAdapter().review(extractedDir.toFile(), rulesJsonPath);
            return new ObjectMapper().writeValueAsString(report);
        } finally {
            ProjectZipExtractor.cleanup(extractedDir);
        }
    }

    public ReviewReport review(File projectDir, String rulesJsonPath) throws Exception {
        File resolvedProjectDir = resolveProjectRoot(projectDir);

        RuleConfig config = new RuleLoader().load(rulesJsonPath);
        Map<String, RuleMeta> rulesById = new java.util.HashMap<>();
        for (Rule rule : config.getRules()) {
            rulesById.putIfAbsent(rule.getId(), new RuleMeta(rule.getDescription(), rule.getCategory(), rule.getSeverity()));
        }
        for (SwaggerRule rule : config.getSwaggerRules()) {
            rulesById.putIfAbsent(rule.getId(), new RuleMeta(rule.getDescription(), rule.getCategory(), rule.getSeverity()));
        }

        Set<Violation> violations = new MuleRuleValidatorMojo().execute(resolvedProjectDir, rulesJsonPath);

        List<ReviewFinding> findings = violations.stream()
                .map(v -> toFinding(v, rulesById))
                .collect(Collectors.toList());

        String summary = "Rule-engine review: " + findings.size()
                + " violation(s) found across Mule XML flows, pom.xml, and Swagger/OpenAPI specs.";

        return new ReviewReport(summary, findings);
    }

    /** Common (description, category, severity) shape shared by Rule and SwaggerRule for finding lookup. */
    private static final class RuleMeta {
        final String description;
        final String category;
        final String severity;

        RuleMeta(String description, String category, String severity) {
            this.description = description;
            this.category = category;
            this.severity = severity;
        }
    }

    private ReviewFinding toFinding(Violation violation, Map<String, RuleMeta> rulesById) {
        RuleMeta rule = rulesById.get(violation.getRuleId());

        String ruleText;
        String category;
        ReviewFinding.Severity severity;

        if (rule != null) {
            ruleText = rule.description;
            category = rule.category;
            severity = mapSeverity(rule.severity);
        } else {
            // pom.xml violations key by artifactId, not a configured rule id — see PomDependencyValidator.
            ruleText = "Dependency version requirement: " + violation.getRuleId();
            category = "Dependencies";
            severity = ReviewFinding.Severity.MAJOR;
        }

        String fileLabel = violation.getLine() >= 0
                ? violation.getFile() + ":" + violation.getLine()
                : violation.getFile();

        return new ReviewFinding(ruleText, category, ReviewFinding.Status.FAIL, severity, fileLabel,
                violation.getMessage(), "");
    }

    /**
     * RuleEngine looks for XML flows at exactly &lt;projectDir&gt;/src/main/mule and
     * pom.xml at exactly &lt;projectDir&gt;/pom.xml — no recursive search. That's fine
     * for a zip built with the project's contents at the zip root, but a zip built
     * by compressing the project FOLDER itself (the default behavior of most
     * OS "compress"/"send to zip" actions) wraps everything in one extra parent
     * directory, silently making the rule engine find nothing at all — no error,
     * just an empty findings list. Unwrap one level if that's what happened.
     */
    private File resolveProjectRoot(File extractedDir) {
        if (new File(extractedDir, "src/main/mule").isDirectory()) {
            return extractedDir;
        }

        File[] children = extractedDir.listFiles(File::isDirectory);
        if (children != null) {
            for (File child : children) {
                if (new File(child, "src/main/mule").isDirectory()) {
                    return child;
                }
            }
        }

        return extractedDir; // no match found at either level; let RuleEngine report zero findings as before
    }

    private ReviewFinding.Severity mapSeverity(String rawSeverity) {
        if (rawSeverity == null) {
            return ReviewFinding.Severity.MAJOR;
        }
        String upper = rawSeverity.toUpperCase();
        if (upper.contains("CRIT")) {
            return ReviewFinding.Severity.CRITICAL;
        }
        if (upper.contains("ERROR") || upper.contains("MAJOR")) {
            return ReviewFinding.Severity.MAJOR;
        }
        return ReviewFinding.Severity.MINOR;
    }
}
