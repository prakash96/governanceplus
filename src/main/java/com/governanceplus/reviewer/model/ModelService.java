package com.governanceplus.reviewer.model;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;

import java.nio.file.Path;

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
public class ModelService implements AutoCloseable {

    private final LlamaModel model;

    /**
     * @param modelPath path to a local .gguf model file (e.g. a Q4_K_M quantized
     *                  Qwen2.5-7B-Instruct or Llama-3.1-8B-Instruct model)
     * @param contextSize context window size in tokens; pick based on how large
     *                    a typical "small project" concatenation + rules + output
     *                    is expected to be, with headroom (e.g. 8192 or 16384)
     */
    public ModelService(Path modelPath, int contextSize) {
        ModelParameters modelParams = new ModelParameters()
                .setModel(modelPath.toAbsolutePath().toString())
                .setCtxSize(contextSize)
                .setGpuLayers(0)    // force CPU-only; GPU auto-detection crashes on systems without CUDA
                .setThreads(4);

        this.model = new LlamaModel(modelParams);
    }

    /**
     * Runs a single review pass: gives the model the rules and the full
     * concatenated project text in one prompt, asks for an English review.
     */
    public String review(String rulesMarkdown, String projectText) {
        String prompt = buildPrompt(rulesMarkdown, projectText);

        InferenceParameters inferParams = new InferenceParameters(prompt)
                .setTemperature(0.2f)   // low temperature: favor consistent, literal rule-checking over creativity
                .setNPredict(256);     // cap on output length; raise if reviews are getting truncated

        StringBuilder output = new StringBuilder();
        System.out.println("Generating review...");
        for (LlamaOutput token : model.generate(inferParams)) {
            output.append(token.text);
        }
        return output.toString().trim();
    }

    private String buildPrompt(String rulesMarkdown, String projectText) {
        return "You are a senior Mulesoft integration reviewer. You will be given a set of "
                + "review rules written in English/Markdown, followed by the full contents of "
                + "every XML and Swagger/OpenAPI file in a Mulesoft project.\n\n"
                + "Flows defined in one file may be referenced by flows in another file. Use the "
                + "full set of files provided to resolve these cross-file references before judging "
                + "compliance with a rule.\n\n"
                + "For each rule, state clearly whether the project satisfies it. If it does not, "
                + "explain why, identify which file and flow/element is responsible, and describe "
                + "what should change. Write your full review in clear, well-structured English.\n\n"
                + "=== RULES ===\n"
                + rulesMarkdown
                + "\n\n=== PROJECT FILES ===\n"
                + projectText
                + "\n=== END OF PROJECT FILES ===\n\n"
                + "Now write the review.\n";
    }

    @Override
    public void close() {
        model.close();
    }
}
