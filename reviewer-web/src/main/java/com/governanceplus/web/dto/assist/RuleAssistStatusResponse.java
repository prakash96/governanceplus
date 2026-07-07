package com.governanceplus.web.dto.assist;

public class RuleAssistStatusResponse {

    private final boolean available;

    public RuleAssistStatusResponse(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() { return available; }
}
