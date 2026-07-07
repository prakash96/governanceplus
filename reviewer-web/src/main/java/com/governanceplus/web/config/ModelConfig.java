package com.governanceplus.web.config;

import com.governanceplus.reviewer.model.ChatEngine;
import com.governanceplus.reviewer.model.ModelService;
import com.governanceplus.web.service.MockChatEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * Loads the chat engine ONCE at application startup as a singleton bean,
 * instead of per-request the way the CLI (reviewer-core's Main.java) does —
 * loading a GGUF model is too expensive to repeat per HTTP request.
 *
 * When governanceplus.model.mock=true, a canned MockChatEngine is used
 * instead so the HTTP/UI pipeline can be exercised without a real model file.
 * Spring's default destroy-method inference calls ModelService#close()
 * automatically on shutdown since it implements AutoCloseable.
 */
@Configuration
public class ModelConfig {

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
            throw new IllegalStateException(
                    "governanceplus.model.path must be set when governanceplus.model.mock=false");
        }
        int effectiveThreads = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        return new ModelService(Paths.get(modelPath), contextSize, effectiveThreads, maxOutputTokens);
    }
}
