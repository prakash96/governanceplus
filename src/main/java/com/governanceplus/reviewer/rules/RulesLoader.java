package com.governanceplus.reviewer.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the English-language markdown rules file as-is. No parsing into
 * structured rule objects — the model reads and applies the rules directly,
 * the same way a human reviewer would read a markdown checklist.
 */
public class RulesLoader {

    public String loadRules(Path rulesFile) throws IOException {
        if (!Files.isRegularFile(rulesFile)) {
            throw new IllegalArgumentException("Rules file not found: " + rulesFile);
        }
        return Files.readString(rulesFile, StandardCharsets.UTF_8);
    }
}
