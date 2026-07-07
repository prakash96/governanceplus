package com.governanceplus.reviewer.ruleengine;

import com.governanceplus.reviewer.ruleengine.model.SwaggerRule;
import com.governanceplus.reviewer.ruleengine.model.Violation;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private File fixtureFile(String classpathRelativePath) {
        URL url = getClass().getClassLoader().getResource(classpathRelativePath);
        assertNotNull(url, "fixture not found on classpath: " + classpathRelativePath);
        return new File(url.getFile());
    }
}
