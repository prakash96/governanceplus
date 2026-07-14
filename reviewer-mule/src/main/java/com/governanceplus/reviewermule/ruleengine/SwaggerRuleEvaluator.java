package com.governanceplus.reviewermule.ruleengine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.governanceplus.reviewermule.ruleengine.model.Violation;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates JSONPath-based rules against a Swagger/OpenAPI spec (YAML or JSON — JSON is a YAML
 * subset, so one parser handles both), mirroring how XPathEvaluator evaluates XPath rules against
 * Mule XML: a non-empty match is a violation, there's no "passing" concept. Before evaluating any
 * rule, SwaggerRefResolver runs once to resolve local $ref pointers.
 *
 * Called from Mule flows via DataWeave's native Java interop
 * (`import * from java!com::governanceplus::reviewermule::ruleengine::SwaggerRuleEvaluator`),
 * never the Mule Java Module. This is the piece a hand-rolled DataWeave JSONPath interpreter
 * (dwlib/JsonPath.dwl) previously stood in for — reverted back to the real, battle-tested Jayway
 * JsonPath library since arbitrary JSONPath (filter predicates like [?(@.type == 'string')]) is
 * genuinely hard to reimplement correctly by hand.
 */
public class SwaggerRuleEvaluator {

    private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS)
            .build();

    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    /**
     * Real-review bridge (review-engine.xml): evaluates every applicable swagger rule (already
     * filtered by projectNamePattern in DataWeave) against one already-read spec file's content.
     * swaggerRulesJson is a JSON array of {id, description, jsonPath} objects.
     */
    public static String evaluateSwaggerRulesAsJson(String specContent, String fileName, String swaggerRulesJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> ruleMaps = mapper.readValue(swaggerRulesJson, new TypeReference<List<Map<String, String>>>() {});

        JsonNode root = YAML_MAPPER.readTree(specContent);
        JsonNode resolved = new SwaggerRefResolver(root).resolve();
        DocumentContext document = JsonPath.using(JSON_PATH_CONFIG).parse(resolved);

        List<Violation> violations = new ArrayList<>();
        for (Map<String, String> rule : ruleMaps) {
            String jsonPathExpr = rule.get("jsonPath");
            if (jsonPathExpr == null || jsonPathExpr.trim().isEmpty()) {
                continue;
            }
            try {
                JsonNode result = document.read(jsonPathExpr);
                int matchCount = result != null ? result.size() : 0;
                for (int i = 0; i < matchCount; i++) {
                    violations.add(new Violation(rule.get("id"), rule.get("description"), fileName, -1));
                }
            } catch (Exception e) {
                // A malformed stored rule expression shouldn't fail the whole review.
            }
        }

        return mapper.writeValueAsString(violations);
    }

    /** "Test this rule" bridge (rules-test.xml) — one ad-hoc rule against one pasted sample spec. */
    public static String evaluateSwaggerSample(String jsonPathExpr, String sampleSpec) throws Exception {
        JsonNode root = YAML_MAPPER.readTree(sampleSpec);
        JsonNode resolved = new SwaggerRefResolver(root).resolve();
        DocumentContext document = JsonPath.using(JSON_PATH_CONFIG).parse(resolved);

        List<Violation> violations = new ArrayList<>();
        JsonNode result = document.read(jsonPathExpr);
        int matchCount = result != null ? result.size() : 0;
        for (int i = 0; i < matchCount; i++) {
            violations.add(new Violation("TEST", "Test rule matched", "sample", -1));
        }

        return new ObjectMapper().writeValueAsString(violations);
    }
}
