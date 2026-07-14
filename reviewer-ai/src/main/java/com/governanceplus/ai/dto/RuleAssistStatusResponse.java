package com.governanceplus.ai.dto;

public class RuleAssistStatusResponse {

    private final boolean available;

    public RuleAssistStatusResponse(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() { return available; }
}
