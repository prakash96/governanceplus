package com.governanceplus.reviewer.model;

/**
 * Abstraction over "given a user message, produce a model response". Lets
 * callers (e.g. the web module) swap in a different implementation — such as
 * a canned mock for testing — without depending on the concrete GGUF-backed
 * {@link ModelService}.
 */
public interface ChatEngine {

    String chat(String message);
}
