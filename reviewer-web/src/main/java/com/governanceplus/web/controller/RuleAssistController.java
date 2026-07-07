package com.governanceplus.web.controller;

import com.governanceplus.reviewer.model.ChatEngine;
import com.governanceplus.web.dto.assist.RuleAssistExplainRequest;
import com.governanceplus.web.dto.assist.RuleAssistExplainResponse;
import com.governanceplus.web.dto.assist.RuleAssistGenerateRequest;
import com.governanceplus.web.dto.assist.RuleAssistGenerateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * AI assistance for AUTHORING rules — never for deciding pass/fail. The
 * review pipeline (RuleEngineReviewAdapter) never calls the model; this is
 * the model's only reachable surface, scoped to helping a human write or
 * understand a rule. Uses the same singleton ChatEngine bean as everywhere
 * else (mock or a real local GGUF model, see ModelConfig).
 */
@RestController
@RequestMapping("/api/rules/assist")
public class RuleAssistController {

    private final ChatEngine chatEngine;

    public RuleAssistController(ChatEngine chatEngine) {
        this.chatEngine = chatEngine;
    }

    @PostMapping("/generate")
    public RuleAssistGenerateResponse generate(@RequestBody RuleAssistGenerateRequest request) {
        if (request.getInstruction() == null || request.getInstruction().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "instruction must not be blank");
        }

        String prompt = "You are helping a developer author a " + describeCategory(request.getCategory())
                + " for a Mule/OpenAPI governance tool that checks projects with a deterministic rule engine "
                + "(no AI is involved in the actual review — only in helping write this rule).\n\n"
                + "Request: " + request.getInstruction() + "\n\n"
                + "Suggest a concrete rule (the expression/field values it needs, per the rule shape above) "
                + "and a short one-sentence description. Respond in plain text.";

        return new RuleAssistGenerateResponse(chatEngine.chat(prompt));
    }

    @PostMapping("/explain")
    public RuleAssistExplainResponse explain(@RequestBody RuleAssistExplainRequest request) {
        if (request.getRule() == null || request.getRule().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rule must not be empty");
        }

        String prompt = "You are explaining an existing " + describeCategory(request.getCategory())
                + " to a developer who didn't write it.\n\n"
                + "Rule fields:\n" + formatRule(request.getRule()) + "\n\n"
                + "Explain in plain English what this rule checks and why it might matter. Respond in plain text.";

        return new RuleAssistExplainResponse(chatEngine.chat(prompt));
    }

    private String describeCategory(String category) {
        if (category == null) {
            return "governance rule";
        }
        switch (category.toLowerCase()) {
            case "xml":
                return "XML/Mule-flow governance rule (an XPath expression evaluated against Mule flow XML)";
            case "pom":
                return "pom.xml dependency-version governance rule (a Maven artifactId plus its required minimum version)";
            case "swagger":
                return "Swagger/OpenAPI governance rule (a JSONPath expression evaluated against the OpenAPI spec)";
            default:
                return "governance rule";
        }
    }

    private String formatRule(Map<String, Object> rule) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : rule.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        return sb.toString();
    }
}
