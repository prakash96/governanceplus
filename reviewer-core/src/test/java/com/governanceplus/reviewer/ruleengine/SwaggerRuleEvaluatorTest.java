package com.governanceplus.reviewer.ruleengine;

import com.governanceplus.reviewer.ruleengine.model.SwaggerRule;
import com.governanceplus.reviewer.ruleengine.model.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwaggerRuleEvaluatorTest {

    private final SwaggerRuleEvaluator evaluator = new SwaggerRuleEvaluator();

    @Test
    void reportsOneViolationPerJsonPathMatch() throws Exception {
        File specFile = fixtureFile("rule-engine-fixture/src/main/resources/api/order-api.yaml");
        SwaggerRule rule = new SwaggerRule("SWAGGER-001", "API Design", "MAJOR",
                "GET operations should document a 500 response",
                "$.paths[*].get[?(!@.responses['500'])]");

        List<Violation> violations = evaluator.evaluate(specFile, List.of(rule));

        assertEquals(1, violations.size());
        assertEquals("SWAGGER-001", violations.get(0).getRuleId());
        assertTrue(violations.get(0).getFile().contains("order-api.yaml"));
    }

    @Test
    void reportsNoViolationsWhenNothingMatches() throws Exception {
        File specFile = fixtureFile("rule-engine-fixture/src/main/resources/api/order-api.yaml");
        SwaggerRule rule = new SwaggerRule("SWAGGER-002", "API Design", "MINOR",
                "Every operation must declare a summary",
                "$.paths[*].get[?(!@.summary)]");

        List<Violation> violations = evaluator.evaluate(specFile, List.of(rule));

        assertTrue(violations.isEmpty());
    }

    @Test
    void recursiveDescentCatchesObjectSchemasMissingOrNotSetToFalseAdditionalProperties(@TempDir Path tempDir) throws Exception {
        String spec = "openapi: 3.0.0\n"
                + "components:\n"
                + "  schemas:\n"
                + "    Compliant:\n"
                + "      type: object\n"
                + "      additionalProperties: false\n"
                + "    Permissive:\n"
                + "      type: object\n"
                + "      additionalProperties: true\n"
                + "    Unspecified:\n"
                + "      type: object\n"
                + "      properties:\n"
                + "        id:\n"
                + "          type: string\n";
        File specFile = tempDir.resolve("spec.yaml").toFile();
        Files.writeString(specFile.toPath(), spec);

        SwaggerRule rule = new SwaggerRule("AP-001", "API Design", "MAJOR",
                "Object schemas must set additionalProperties: false",
                "$..[?(@.type == 'object' && @.additionalProperties != false)]");

        List<Violation> violations = evaluator.evaluate(specFile, List.of(rule));

        // Permissive (explicitly true) and Unspecified (absent) should both be flagged;
        // Compliant (explicitly false) should not.
        assertEquals(2, violations.size());
    }

    @Test
    void recursiveDescentCatchesHeaderParametersMissingSchemaOrNotRequired(@TempDir Path tempDir) throws Exception {
        String spec = "openapi: 3.0.0\n"
                + "paths:\n"
                + "  /orders:\n"
                + "    get:\n"
                + "      parameters:\n"
                + "        - name: X-Compliant\n"
                + "          in: header\n"
                + "          required: true\n"
                + "          schema:\n"
                + "            type: string\n"
                + "        - name: X-NoSchema\n"
                + "          in: header\n"
                + "          required: true\n"
                + "        - name: X-NotRequired\n"
                + "          in: header\n"
                + "          required: false\n"
                + "          schema:\n"
                + "            type: string\n"
                + "        - name: page\n"
                + "          in: query\n"
                + "          required: true\n";
        File specFile = tempDir.resolve("spec.yaml").toFile();
        Files.writeString(specFile.toPath(), spec);

        SwaggerRule schemaRule = new SwaggerRule("HDR-001", "API Design", "MAJOR",
                "Header parameters must declare a schema",
                "$..parameters[?(@.in == 'header' && !@.schema)]");
        SwaggerRule requiredRule = new SwaggerRule("HDR-002", "API Design", "MAJOR",
                "Header parameters must be required",
                "$..parameters[?(@.in == 'header' && @.required != true)]");

        List<Violation> schemaViolations = evaluator.evaluate(specFile, List.of(schemaRule));
        List<Violation> requiredViolations = evaluator.evaluate(specFile, List.of(requiredRule));

        // Only X-NoSchema lacks a schema; only X-NotRequired is explicitly not required.
        // The query parameter "page" must never be caught by either (in != 'header').
        assertEquals(1, schemaViolations.size());
        assertEquals(1, requiredViolations.size());
    }

    @Test
    void requestBodyScopedRuleFollowsRefIntoComponentSchemas(@TempDir Path tempDir) throws Exception {
        String spec = "openapi: 3.0.0\n"
                + "paths:\n"
                + "  /orders:\n"
                + "    post:\n"
                + "      requestBody:\n"
                + "        content:\n"
                + "          application/json:\n"
                + "            schema:\n"
                + "              $ref: '#/components/schemas/Order'\n"
                + "components:\n"
                + "  schemas:\n"
                + "    Order:\n"
                + "      type: object\n"
                + "      additionalProperties: true\n";
        File specFile = tempDir.resolve("spec.yaml").toFile();
        Files.writeString(specFile.toPath(), spec);

        // The exact expression the Rules UI's "Request Schema Validation" quick-check composes —
        // scoped to requestBody.*.schema, not a whole-document recursive check.
        SwaggerRule rule = new SwaggerRule("REQ-001", "API Design", "MAJOR",
                "Request body schemas must set additionalProperties: false",
                "$.paths.*.*.requestBody.content.*.schema[?(@.type == 'object' && @.additionalProperties != false)]");

        List<Violation> violations = evaluator.evaluate(specFile, List.of(rule));

        // Without $ref resolution this schema node is just {"$ref": "..."} — no "type" field to
        // match — so this assertion would fail (0 violations) before SwaggerRefResolver existed.
        assertEquals(1, violations.size());
    }

    @Test
    void requestBodyScopedRuleDoesNotFlagCompliantRefSchema(@TempDir Path tempDir) throws Exception {
        String spec = "openapi: 3.0.0\n"
                + "paths:\n"
                + "  /orders:\n"
                + "    post:\n"
                + "      requestBody:\n"
                + "        content:\n"
                + "          application/json:\n"
                + "            schema:\n"
                + "              $ref: '#/components/schemas/Order'\n"
                + "components:\n"
                + "  schemas:\n"
                + "    Order:\n"
                + "      type: object\n"
                + "      additionalProperties: false\n";
        File specFile = tempDir.resolve("spec.yaml").toFile();
        Files.writeString(specFile.toPath(), spec);

        SwaggerRule rule = new SwaggerRule("REQ-002", "API Design", "MAJOR",
                "Request body schemas must set additionalProperties: false",
                "$.paths.*.*.requestBody.content.*.schema[?(@.type == 'object' && @.additionalProperties != false)]");

        List<Violation> violations = evaluator.evaluate(specFile, List.of(rule));

        assertTrue(violations.isEmpty());
    }

    @Test
    void selfReferencingSchemaDoesNotCauseInfiniteRecursion(@TempDir Path tempDir) throws Exception {
        String spec = "openapi: 3.0.0\n"
                + "components:\n"
                + "  schemas:\n"
                + "    Category:\n"
                + "      type: object\n"
                + "      additionalProperties: false\n"
                + "      properties:\n"
                + "        subcategories:\n"
                + "          type: array\n"
                + "          items:\n"
                + "            $ref: '#/components/schemas/Category'\n";
        File specFile = tempDir.resolve("spec.yaml").toFile();
        Files.writeString(specFile.toPath(), spec);

        SwaggerRule rule = new SwaggerRule("REC-001", "API Design", "MAJOR",
                "Object schemas must set additionalProperties: false",
                "$..[?(@.type == 'object' && @.additionalProperties != false)]");

        // Only assertion that matters here is that this returns at all instead of hanging/StackOverflow;
        // Category itself is compliant, so no violations expected.
        List<Violation> violations = evaluator.evaluate(specFile, List.of(rule));

        assertTrue(violations.isEmpty());
    }

    @Test
    void evaluateSwaggerSampleBridgeReturnsViolationsAsJson() throws Exception {
        String spec = "openapi: 3.0.0\n"
                + "components:\n"
                + "  schemas:\n"
                + "    Thing:\n"
                + "      type: object\n"
                + "      additionalProperties: true\n";

        String json = SwaggerRuleEvaluator.evaluateSwaggerSample(
                "$..[?(@.type == 'object' && @.additionalProperties != false)]", spec);

        assertTrue(json.contains("\"message\""));
        assertTrue(json.startsWith("["));
    }

    @Test
    void evaluateSwaggerRulesAsJsonBridgeEvaluatesEachRuleInTheList() throws Exception {
        String spec = "openapi: 3.0.0\n"
                + "components:\n"
                + "  schemas:\n"
                + "    Compliant:\n"
                + "      type: object\n"
                + "      additionalProperties: false\n"
                + "    Permissive:\n"
                + "      type: object\n"
                + "      additionalProperties: true\n";

        String rulesJson = "["
                + "{\"id\":\"AP-001\",\"category\":\"API Design\",\"severity\":\"MAJOR\",\"description\":\"additionalProperties must be false\",\"jsonPath\":\"$..[?(@.type == 'object' \\u0026\\u0026 @.additionalProperties != false)]\"},"
                + "{\"id\":\"AP-002\",\"category\":\"API Design\",\"severity\":\"MINOR\",\"description\":\"never matches\",\"jsonPath\":\"$..[?(@.type == 'nonexistent')]\"}"
                + "]";

        String json = SwaggerRuleEvaluator.evaluateSwaggerRulesAsJson(spec, "spec.yaml", rulesJson);

        assertTrue(json.contains("\"ruleId\":\"AP-001\""));
        assertFalse(json.contains("\"ruleId\":\"AP-002\""));
        assertTrue(json.contains("\"file\":\"spec.yaml\""));
    }

    private File fixtureFile(String classpathRelativePath) {
        URL url = getClass().getClassLoader().getResource(classpathRelativePath);
        assertNotNull(url, "fixture not found on classpath: " + classpathRelativePath);
        return new File(url.getFile());
    }
}
