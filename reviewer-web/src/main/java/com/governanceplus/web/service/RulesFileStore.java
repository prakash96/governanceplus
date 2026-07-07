package com.governanceplus.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.governanceplus.web.dto.rules.PomRuleDto;
import com.governanceplus.web.dto.rules.RulesDocument;
import com.governanceplus.web.dto.rules.SwaggerRuleDto;
import com.governanceplus.web.dto.rules.XmlRuleDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the single, authoritative rules.json that the rule engine
 * (see reviewer-core's RuleLoader) consumes at review time — this is the only
 * place that mutates it, backing the Rules management UI's add/edit/delete.
 *
 * `synchronized` methods are enough concurrency safety for this app's existing
 * "single trusted local user, no auth, no DB" design (same assumption already
 * documented on ReviewJobRegistry) — there's no multi-writer scenario to guard
 * against beyond avoiding a torn write from two nearly-simultaneous requests.
 */
@Component
public class RulesFileStore {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Value("${governanceplus.rules.path:../rules/rules.json}")
    private String rulesPath;

    public synchronized RulesDocument load() {
        Path path = Paths.get(rulesPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Rules file not found: " + path.toAbsolutePath());
        }
        try {
            RulesDocument doc = objectMapper.readValue(path.toFile(), RulesDocument.class);
            if (doc.getRules() == null) doc.setRules(new ArrayList<>());
            if (doc.getPomRules() == null) doc.setPomRules(new ArrayList<>());
            if (doc.getSwaggerRules() == null) doc.setSwaggerRules(new ArrayList<>());
            return doc;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read rules file: " + path.toAbsolutePath(), e);
        }
    }

    /** Writes via a temp file + atomic move so a crash mid-write can't corrupt rules.json. */
    private synchronized void save(RulesDocument doc) {
        Path path = Paths.get(rulesPath).toAbsolutePath();
        try {
            Path tempFile = Files.createTempFile(path.getParent(), "rules-", ".json.tmp");
            objectMapper.writeValue(tempFile.toFile(), doc);
            try {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not write rules file: " + path, e);
        }
    }

    // ---- XML rules (keyed by id) ----

    public synchronized XmlRuleDto addXmlRule(XmlRuleDto rule) {
        requireId(rule.getId());
        RulesDocument doc = load();
        if (doc.getRules().stream().anyMatch(r -> r.getId().equals(rule.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "XML rule already exists: " + rule.getId());
        }
        doc.getRules().add(rule);
        save(doc);
        return rule;
    }

    public synchronized XmlRuleDto updateXmlRule(String id, XmlRuleDto rule) {
        requireId(rule.getId());
        RulesDocument doc = load();
        List<XmlRuleDto> rules = doc.getRules();
        int index = indexOf(rules, r -> r.getId().equals(id));
        if (index < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "XML rule not found: " + id);
        }
        if (!rule.getId().equals(id) && rules.stream().anyMatch(r -> r.getId().equals(rule.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "XML rule already exists: " + rule.getId());
        }
        rules.set(index, rule);
        save(doc);
        return rule;
    }

    public synchronized void deleteXmlRule(String id) {
        RulesDocument doc = load();
        if (!doc.getRules().removeIf(r -> r.getId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "XML rule not found: " + id);
        }
        save(doc);
    }

    // ---- Pom rules (keyed by artifactId) ----

    public synchronized PomRuleDto addPomRule(PomRuleDto rule) {
        requireArtifactId(rule.getArtifactId());
        RulesDocument doc = load();
        if (doc.getPomRules().stream().anyMatch(r -> r.getArtifactId().equals(rule.getArtifactId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pom rule already exists: " + rule.getArtifactId());
        }
        doc.getPomRules().add(rule);
        save(doc);
        return rule;
    }

    public synchronized PomRuleDto updatePomRule(String artifactId, PomRuleDto rule) {
        requireArtifactId(rule.getArtifactId());
        RulesDocument doc = load();
        List<PomRuleDto> pomRules = doc.getPomRules();
        int index = indexOf(pomRules, r -> r.getArtifactId().equals(artifactId));
        if (index < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pom rule not found: " + artifactId);
        }
        if (!rule.getArtifactId().equals(artifactId)
                && pomRules.stream().anyMatch(r -> r.getArtifactId().equals(rule.getArtifactId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pom rule already exists: " + rule.getArtifactId());
        }
        pomRules.set(index, rule);
        save(doc);
        return rule;
    }

    public synchronized void deletePomRule(String artifactId) {
        RulesDocument doc = load();
        if (!doc.getPomRules().removeIf(r -> r.getArtifactId().equals(artifactId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pom rule not found: " + artifactId);
        }
        save(doc);
    }

    // ---- Swagger rules (keyed by id) ----

    public synchronized SwaggerRuleDto addSwaggerRule(SwaggerRuleDto rule) {
        requireId(rule.getId());
        RulesDocument doc = load();
        if (doc.getSwaggerRules().stream().anyMatch(r -> r.getId().equals(rule.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Swagger rule already exists: " + rule.getId());
        }
        doc.getSwaggerRules().add(rule);
        save(doc);
        return rule;
    }

    public synchronized SwaggerRuleDto updateSwaggerRule(String id, SwaggerRuleDto rule) {
        requireId(rule.getId());
        RulesDocument doc = load();
        List<SwaggerRuleDto> swaggerRules = doc.getSwaggerRules();
        int index = indexOf(swaggerRules, r -> r.getId().equals(id));
        if (index < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Swagger rule not found: " + id);
        }
        if (!rule.getId().equals(id) && swaggerRules.stream().anyMatch(r -> r.getId().equals(rule.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Swagger rule already exists: " + rule.getId());
        }
        swaggerRules.set(index, rule);
        save(doc);
        return rule;
    }

    public synchronized void deleteSwaggerRule(String id) {
        RulesDocument doc = load();
        if (!doc.getSwaggerRules().removeIf(r -> r.getId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Swagger rule not found: " + id);
        }
        save(doc);
    }

    private void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id must not be blank");
        }
    }

    private void requireArtifactId(String artifactId) {
        if (artifactId == null || artifactId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artifactId must not be blank");
        }
    }

    private <T> int indexOf(List<T> list, java.util.function.Predicate<T> predicate) {
        for (int i = 0; i < list.size(); i++) {
            if (predicate.test(list.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
