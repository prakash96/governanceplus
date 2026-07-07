package com.governanceplus.reviewer.ruleengine;

import com.governanceplus.reviewer.ruleengine.model.Violation;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuleEngineProjectNamePatternTest {

    private final RuleEngine ruleEngine = new RuleEngine();

    @Test
    void onlyAppliesRulesWhoseProjectNamePatternMatchesTheProjectsArtifactId() throws Exception {
        File projectDir = fixtureFile("rule-engine-fixture-project-name");
        File rulesJson = fixtureFile("rule-engine-fixture-project-name/rules.json");

        Set<Violation> violations = ruleEngine.validate(projectDir, rulesJson.getAbsolutePath());

        Set<String> firedRuleIds = violations.stream().map(Violation::getRuleId).collect(Collectors.toSet());

        // The fixture's pom.xml artifactId is "orders-api": "*-api" matches, "*-batch" doesn't,
        // and a rule with no projectNamePattern always applies regardless of project name.
        assertEquals(Set.of("SCOPED-TO-API", "UNSCOPED"), firedRuleIds);
    }

    private File fixtureFile(String classpathRelativePath) {
        URL url = getClass().getClassLoader().getResource(classpathRelativePath);
        assertNotNull(url, "fixture not found on classpath: " + classpathRelativePath);
        return new File(url.getFile());
    }
}
