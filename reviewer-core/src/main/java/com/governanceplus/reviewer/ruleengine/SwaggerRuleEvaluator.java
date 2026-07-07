
package com.governanceplus.reviewer.ruleengine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
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
 */
public class SwaggerRuleEvaluator {

    private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS)
            .build();

    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    public List<Violation> evaluate(File specFile, List<SwaggerRule> rules) throws Exception {
        List<Violation> violations = new ArrayList<>();

        JsonNode root = YAML_MAPPER.readTree(specFile);
        DocumentContext document = JsonPath.using(JSON_PATH_CONFIG).parse(root);

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
