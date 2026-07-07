package com.governanceplus.web.controller;

import com.governanceplus.web.dto.rules.PomRuleTestRequest;
import com.governanceplus.web.dto.rules.RuleTestResult;
import com.governanceplus.web.dto.rules.SwaggerRuleTestRequest;
import com.governanceplus.web.dto.rules.XmlRuleTestRequest;
import com.governanceplus.web.service.RuleTestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Test this rule against sample input" — never persisted, just runs the same evaluator a real review would use. */
@RestController
@RequestMapping("/api/rules")
public class RuleTestController {

    private final RuleTestService ruleTestService;

    public RuleTestController(RuleTestService ruleTestService) {
        this.ruleTestService = ruleTestService;
    }

    @PostMapping("/xml/test")
    public RuleTestResult testXmlRule(@RequestBody XmlRuleTestRequest request) {
        return ruleTestService.testXmlRule(request);
    }

    @PostMapping("/pom/test")
    public RuleTestResult testPomRule(@RequestBody PomRuleTestRequest request) {
        return ruleTestService.testPomRule(request);
    }

    @PostMapping("/swagger/test")
    public RuleTestResult testSwaggerRule(@RequestBody SwaggerRuleTestRequest request) {
        return ruleTestService.testSwaggerRule(request);
    }
}
