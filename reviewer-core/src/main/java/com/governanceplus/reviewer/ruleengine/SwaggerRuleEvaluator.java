
package com.governanceplus.reviewer.ruleengine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.governanceplus.reviewer.ruleengine.model.SwaggerRule;
import com.governanceplus.reviewer.ruleengine.model.Violation;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

/**
 * Evaluates JSONPath-based rules against a Swagger/OpenAPI spec (YAML or
 * JSON — JSON is a YAML subset, so one parser handles both), mirroring how
 * {@link XPathEvaluator} evaluates XPath rules against Mule XML: a non-empty
 * match is a violation, there's no "passing" concept.
 *
 * Before evaluating any rule, {@link SwaggerRefResolver} runs once against
 * the parsed spec to resolve local $ref pointers (e.g. a requestBody schema
 * that's really just {"$ref": "#/components/schemas/Order"}) into the node
 * they point at — otherwise a rule targeting requestBody/response schemas or
 * parameters would only ever see bare $ref stubs and never match anything.
 */
public class SwaggerRuleEvaluator {

    private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS)
            .build();

    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    /**
     * DataWeave-callable convenience wrapper (`import * from java!...SwaggerRuleEvaluator` in a
     * Mule flow) — see {@link com.governanceplus.reviewer.ruleengine.XPathEvaluator#evaluateXmlSample}
     * for why this takes/returns plain strings instead of File/SwaggerRule/List&lt;Violation&gt;.
     */
    public static String evaluateSwaggerSample(String jsonPath, String sampleSpec) throws Exception {
        Path tempFile = Files.createTempFile("rule-test-", ".yaml");
        try {
            Files.writeString(tempFile, sampleSpec);
            File file = tempFile.toFile();
            SwaggerRule rule = new SwaggerRule("TEST", "TEST", "INFO", "Test rule", jsonPath);
            List<Violation> violations = new SwaggerRuleEvaluator().evaluate(file, List.of(rule));
            return new ObjectMapper().writeValueAsString(violations);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * DataWeave-callable convenience wrapper for a real review run (reviewer-mule's
     * review-engine.xml): unlike {@link #evaluateSwaggerSample}, this evaluates a whole list of
     * rules (as they come back from the H2 rules DB, already filtered by projectNamePattern in
     * DataWeave) against one already-in-hand spec file's content — no temp file needed since
     * Jackson's YAMLMapper can parse a String directly. swaggerRulesJson is a JSON array of
     * {id, category, severity, description, jsonPath} objects; this is the ONE piece of rule
     * evaluation reviewer-mule still delegates to Java, because JSONPath (with filter predicates
     * like [?(@.type == 'string')]) has no native DataWeave equivalent — everything else
     * (XPath rules via xpath3, POM version checks, file-tree walking, report assembly) is
     * implemented natively in review-engine.xml.
     */
    public static String evaluateSwaggerRulesAsJson(String specContent, String fileName, String swaggerRulesJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> ruleMaps = mapper.readValue(swaggerRulesJson, new TypeReference<List<Map<String, String>>>() {});
        List<SwaggerRule> rules = new ArrayList<>();
        for (Map<String, String> m : ruleMaps) {
            rules.add(new SwaggerRule(m.get("id"), m.get("category"), m.get("severity"), m.get("description"), m.get("jsonPath")));
        }

        JsonNode root = YAML_MAPPER.readTree(specContent);
        JsonNode resolved = new SwaggerRefResolver(root).resolve();
        DocumentContext document = JsonPath.using(JSON_PATH_CONFIG).parse(resolved);

        List<Violation> violations = new ArrayList<>();
        for (SwaggerRule rule : rules) {
            try {
                JsonNode result = document.read(rule.getJsonPath());
                int matchCount = result != null ? result.size() : 0;
                for (int i = 0; i < matchCount; i++) {
                    violations.add(new Violation(rule.getId(), rule.getDescription(), fileName, -1));
                }
            } catch (Exception e) {
                System.err.println("Error processing swagger rule: " + rule.getId());
            }
        }

        return mapper.writeValueAsString(violations);
    }

    public List<Violation> evaluate(File specFile, List<SwaggerRule> rules) throws Exception {
        List<Violation> violations = new ArrayList<>();

        JsonNode root = YAML_MAPPER.readTree(specFile);
        JsonNode resolved = new SwaggerRefResolver(root).resolve();
        DocumentContext document = JsonPath.using(JSON_PATH_CONFIG).parse(resolved);

        for (SwaggerRule rule : rules) {
            try {
                JsonNode result = document.read(rule.getJsonPath());
                int matchCount = result != null ? result.size() : 0;

                for (int i = 0; i < matchCount; i++) {
                    // No reliable source-line info once parsed into a JSON tree —
                    // same convention as PomDependencyValidator's pom.xml violations.
                    violations.add(new Violation(rule.getId(), rule.getDescription(), specFile.getName(), -1));
                }
            } catch (Exception e) {
                System.err.println("Error processing swagger rule: " + rule.getId());
                e.printStackTrace();
            }
        }

        return violations;
    }
}
