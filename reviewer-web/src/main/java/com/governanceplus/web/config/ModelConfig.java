package com.governanceplus.web.config;

import com.governanceplus.reviewer.model.ChatEngine;
import com.governanceplus.reviewer.model.ModelService;
import com.governanceplus.web.service.MockChatEngine;
import com.governanceplus.web.service.UnavailableChatEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads the chat engine ONCE at application startup as a singleton bean,
 * instead of per-request the way the CLI (reviewer-core's Main.java) does —
 * loading a GGUF model is too expensive to repeat per HTTP request.
 *
 * When governanceplus.model.mock=true, a canned MockChatEngine is used
 * instead so the HTTP/UI pipeline can be exercised without a real model file.
 * When mock=false but no model is actually usable (blank path, missing file,
 * or the model fails to load), this falls back to an UnavailableChatEngine
 * rather than failing application startup — AI assist is the one feature
 * that depends on this, and the rest of the app (review, rules, test-on-sample)
 * has no reason to go down over a missing multi-GB model file. Spring's
 * default destroy-method inference calls ModelService#close() automatically
 * on shutdown since it implements AutoCloseable.
 */
@Configuration
public class ModelConfig {

    private static final Logger log = LoggerFactory.getLogger(ModelConfig.class);

    @Value("${governanceplus.model.mock:false}")
    private boolean mock;

    @Value("${governanceplus.model.path:}")
    private String modelPath;

    @Value("${governanceplus.model.context-size:4096}")
    private int contextSize;

    /** 0 means "auto" — use all available processors. */
    @Value("${governanceplus.model.threads:0}")
    private int threads;

    /** Cap on generated tokens per chat response. */
    @Value("${governanceplus.model.max-output-tokens:1024}")
    private int maxOutputTokens;

    @Bean
    public ChatEngine chatEngine() {
        if (mock) {
            return new MockChatEngine();
        }
        if (modelPath == null || modelPath.isBlank()) {
            log.warn("governanceplus.model.path is not set — AI assist (Ask AI / Explain) will be unavailable.");
            return new UnavailableChatEngine("governanceplus.model.path is not set");
        }

        Path path = Paths.get(modelPath);
        if (!Files.isRegularFile(path)) {
            log.warn("governanceplus.model.path ({}) does not exist — AI assist (Ask AI / Explain) will be unavailable.",
                    path.toAbsolutePath());
            return new UnavailableChatEngine("model file not found at " + path.toAbsolutePath());
        }

        int effectiveThreads = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        try {
            return new ModelService(path, contextSize, effectiveThreads, maxOutputTokens);
        } catch (Exception e) {
            log.warn("Failed to load model at {} — AI assist (Ask AI / Explain) will be unavailable.",
                    path.toAbsolutePath(), e);
            return new UnavailableChatEngine("failed to load model: " + e.getMessage());
        }
    }
}
