package com.governanceplus.web.service;

import com.governanceplus.reviewer.model.ChatEngine;

/**
 * Used when no real model is usable (mock=false, and governanceplus.model.path
 * is blank or doesn't point at a real file) — lets the app start up normally
 * with AI assist simply unavailable, instead of failing the whole backend
 * over a feature the review pipeline never depends on. RuleAssistController
 * checks {@link #isAvailable()} before calling {@link #chat}, and the
 * frontend hides the Ask AI nav item / Explain buttons based on the same
 * flag — {@link #chat} throwing is a last-resort guard, not the primary path.
 */
public class UnavailableChatEngine implements ChatEngine {

    private final String reason;

    public UnavailableChatEngine(String reason) {
        this.reason = reason;
    }

    @Override
    public String chat(String message) {
        throw new IllegalStateException("AI assist is not available: " + reason);
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
