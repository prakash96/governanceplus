package com.governanceplus.ai.dto;

public class RuleAssistGenerateResponse {

    private final String suggestion;

    public RuleAssistGenerateResponse(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getSuggestion() { return suggestion; }
}
