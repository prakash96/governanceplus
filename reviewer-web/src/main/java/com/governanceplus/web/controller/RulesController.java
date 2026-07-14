package com.governanceplus.web.controller;

import com.governanceplus.web.dto.rules.PomRuleDto;
import com.governanceplus.web.dto.rules.RulesDocument;
import com.governanceplus.web.dto.rules.SwaggerRuleDto;
import com.governanceplus.web.dto.rules.XmlRuleDto;
import com.governanceplus.web.service.RulesFileStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD over the server's single, authoritative rules.json (XML/pom/Swagger
 * rule categories) — backs the Rules management UI's list/add/edit/delete.
 * See RuleTestController for "test this rule against sample input". The
 * AI-assist endpoints (rule-authoring help) live in the separate reviewer-ai
 * module, not currently wired into this app.
 */
@RestController
@RequestMapping("/api/rules")
public class RulesController {

    private final RulesFileStore rulesFileStore;

    public RulesController(RulesFileStore rulesFileStore) {
        this.rulesFileStore = rulesFileStore;
    }

    @GetMapping
    public RulesDocument getRules() {
        return rulesFileStore.load();
    }

    @PostMapping("/xml")
    public XmlRuleDto addXmlRule(@RequestBody XmlRuleDto rule) {
        return rulesFileStore.addXmlRule(rule);
    }

    @PutMapping("/xml/{id}")
    public XmlRuleDto updateXmlRule(@PathVariable String id, @RequestBody XmlRuleDto rule) {
        return rulesFileStore.updateXmlRule(id, rule);
    }

    @DeleteMapping("/xml/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteXmlRule(@PathVariable String id) {
        rulesFileStore.deleteXmlRule(id);
    }

    @PostMapping("/pom")
    public PomRuleDto addPomRule(@RequestBody PomRuleDto rule) {
        return rulesFileStore.addPomRule(rule);
    }

    @PutMapping("/pom/{artifactId}")
    public PomRuleDto updatePomRule(@PathVariable String artifactId, @RequestBody PomRuleDto rule) {
        return rulesFileStore.updatePomRule(artifactId, rule);
    }

    @DeleteMapping("/pom/{artifactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePomRule(@PathVariable String artifactId) {
        rulesFileStore.deletePomRule(artifactId);
    }

    @PostMapping("/swagger")
    public SwaggerRuleDto addSwaggerRule(@RequestBody SwaggerRuleDto rule) {
        return rulesFileStore.addSwaggerRule(rule);
    }

    @PutMapping("/swagger/{id}")
    public SwaggerRuleDto updateSwaggerRule(@PathVariable String id, @RequestBody SwaggerRuleDto rule) {
        return rulesFileStore.updateSwaggerRule(id, rule);
    }

    @DeleteMapping("/swagger/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSwaggerRule(@PathVariable String id) {
        rulesFileStore.deleteSwaggerRule(id);
    }
}
