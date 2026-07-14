package com.governanceplus.ai;

/**
 * Abstraction over "given a user message, produce a model response". Lets
 * callers swap in a different implementation — such as a canned mock for
 * testing — without depending on the concrete GGUF-backed {@link ModelService}.
 */
public interface ChatEngine {

    String chat(String message);

    /** False when there's no usable model behind this engine (e.g. none configured/found) — {@link #chat} would just fail. */
    default boolean isAvailable() {
        return true;
    }
}
