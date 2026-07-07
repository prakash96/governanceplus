package com.governanceplus.web.dto.assist;

public class RuleAssistExplainResponse {

    private final String explanation;

    public RuleAssistExplainResponse(String explanation) {
        this.explanation = explanation;
    }

    public String getExplanation() { return explanation; }
}
