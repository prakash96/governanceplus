package com.governanceplus.ai;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.Pair;

import java.nio.file.Path;
import java.util.List;

/**
 * Wraps a local GGUF model loaded via the JNI-bound llama.cpp library
 * (de.kherud:java-llama.cpp). The native library is loaded once into the
 * JVM process at construction time — no subprocess, no network port, no
 * separate executable for the user to manage.
 *
 * NOTE: the exact method/class names below (ModelParameters, InferenceParameters,
 * LlamaModel#complete, etc.) reflect the de.kherud/java-llama.cpp API as of
 * writing. This library's API has shifted across versions in the past — before
 * relying on this in production, confirm the method signatures against the
 * version declared in pom.xml (check the library's README/Javadoc on Maven
 * Central or GitHub) and adjust accordingly.
 */
public class ModelService implements ChatEngine, AutoCloseable {

    /** Kept modest since generation is CPU-only (see the forced setGpuLayers(0) below) — a big cap makes every response slow. */
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 150;

    private final LlamaModel model;
    private final int maxOutputTokens;

    /**
     * @param modelPath path to a local .gguf model file (e.g. a Q4_K_M quantized
     *                  Qwen2.5-7B-Instruct or Llama-3.1-8B-Instruct model)
     * @param contextSize context window size in tokens; pick based on how long a
     *                    conversation turn is expected to be, with headroom
     *                    (e.g. 4096 or 8192)
     */
    public ModelService(Path modelPath, int contextSize) {
        this(modelPath, contextSize, Runtime.getRuntime().availableProcessors());
    }

    /**
     * @param threads number of CPU threads llama.cpp uses for inference. Defaults
     *                to all available processors via the other constructor —
     *                pass a smaller number to leave headroom for other work on
     *                the same machine (e.g. the Spring Boot HTTP threads).
     */
    public ModelService(Path modelPath, int contextSize, int threads) {
        this(modelPath, contextSize, threads, DEFAULT_MAX_OUTPUT_TOKENS);
    }

    public ModelService(Path modelPath, int contextSize, int threads, int maxOutputTokens) {
        ModelParameters modelParams = new ModelParameters()
                .setModel(modelPath.toAbsolutePath().toString())
                .setCtxSize(contextSize)
                .setGpuLayers(0)    // force CPU-only; GPU auto-detection crashes on systems without CUDA
                .setThreads(threads)
                // Required for InferenceParameters#setUseChatTemplate to have any effect —
                // without this, the model's embedded chat template is never loaded, so the
                // per-request "use jinja template" flag is silently a no-op.
                .enableJinja();

        this.model = new LlamaModel(modelParams);
        this.maxOutputTokens = maxOutputTokens;
    }

    /**
     * Sends the user's message to the model as a chat turn, no rules wrapping
     * or extra prompt engineering. Uses the library's chat-template support
     * (setUseChatTemplate + setMessages) rather than handing the raw text to
     * the model as a plain completion prompt — an instruct-tuned model given
     * un-templated raw text often doesn't reliably emit its own stop token,
     * so it just keeps generating up to maxOutputTokens on every call. The
     * model's embedded chat template (from its GGUF metadata) fixes that.
     */
    @Override
    public String chat(String message) {
        InferenceParameters inferParams = new InferenceParameters(message)
                .setUseChatTemplate(true)
                .setMessages(null, List.of(new Pair<>("user", message)))
                .setTemperature(0.7f)
                .setNPredict(maxOutputTokens);

        StringBuilder output = new StringBuilder();
        for (LlamaOutput token : model.generate(inferParams)) {
            output.append(token.text);
        }
        return output.toString().trim();
    }

    @Override
    public void close() {
        model.close();
    }
}
