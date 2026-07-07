package com.governanceplus.web.service;

import com.governanceplus.reviewer.ruleengine.PomDependencyValidator;
import com.governanceplus.reviewer.ruleengine.SwaggerRuleEvaluator;
import com.governanceplus.reviewer.ruleengine.XPathEvaluator;
import com.governanceplus.reviewer.ruleengine.model.PomRule;
import com.governanceplus.reviewer.ruleengine.model.Rule;
import com.governanceplus.reviewer.ruleengine.model.SwaggerRule;
import com.governanceplus.reviewer.ruleengine.model.Violation;
import com.governanceplus.web.dto.rules.PomRuleTestRequest;
import com.governanceplus.web.dto.rules.RuleTestResult;
import com.governanceplus.web.dto.rules.SwaggerRuleTestRequest;
import com.governanceplus.web.dto.rules.XmlRuleTestRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs a single, not-yet-saved rule against pasted sample input, reusing the
 * same reviewer-core evaluators a real review run uses — so "test on sample"
 * in the Rules UI reflects exactly what saving the rule would produce.
 */
@Service
public class RuleTestService {

    public RuleTestResult testXmlRule(XmlRuleTestRequest request) {
        Rule rule = new Rule("TEST", "TEST", "INFO", "Test rule", request.getXpath(),
                request.getUsageAttribute(), request.getUsagePattern());

        return withTempFile(".xml", request.getSampleXml(), file -> {
            try {
                List<File> allFiles = List.of(file);
                return new XPathEvaluator().evaluate(file, List.of(rule), allFiles);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not evaluate XPath: " + e.getMessage());
            }
        });
    }

    public RuleTestResult testPomRule(PomRuleTestRequest request) {
        PomRule rule = new PomRule(request.getArtifactId(), request.getMinVersion());

        return withTempFile(".xml", request.getSamplePom(), file -> {
            try {
                return PomDependencyValidator.validate(file, List.of(rule));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not evaluate pom rule: " + e.getMessage());
            }
        });
    }

    public RuleTestResult testSwaggerRule(SwaggerRuleTestRequest request) {
        SwaggerRule rule = new SwaggerRule("TEST", "TEST", "INFO", "Test rule", request.getJsonPath());

        return withTempFile(".yaml", request.getSampleSpec(), file -> {
            try {
                return new SwaggerRuleEvaluator().evaluate(file, List.of(rule));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not evaluate JSONPath: " + e.getMessage());
            }
        });
    }

    private RuleTestResult withTempFile(String suffix, String content, java.util.function.Function<File, List<Violation>> evaluate) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sample content must not be blank");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("rule-test-", suffix);
            Files.writeString(tempFile, content);

            List<Violation> violations = evaluate.apply(tempFile.toFile());
            List<RuleTestResult.RuleTestViolation> violationDtos = violations.stream()
                    .map(v -> new RuleTestResult.RuleTestViolation(v.getMessage(), v.getLine()))
                    .collect(Collectors.toList());

            return new RuleTestResult(!violationDtos.isEmpty(), violationDtos);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create temp file for test: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // best-effort
                }
            }
        }
    }
}
