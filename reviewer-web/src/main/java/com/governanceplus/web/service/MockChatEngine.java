package com.governanceplus.web.service;

import com.governanceplus.reviewer.model.ChatEngine;

/**
 * Canned ChatEngine used when governanceplus.model.mock=true, so the HTTP
 * and UI pipeline can be built and tested without loading a multi-GB GGUF
 * model on every backend restart.
 */
public class MockChatEngine implements ChatEngine {

    @Override
    public String chat(String message) {
        simulateInferenceLatency();
        return "Mock response (governanceplus.model.mock=true) — no model was loaded.\n\nYou said: " + message;
    }

    private void simulateInferenceLatency() {
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
