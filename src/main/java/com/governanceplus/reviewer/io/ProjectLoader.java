package com.governanceplus.reviewer.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads a Mulesoft project directory as plain text. Deliberately does NOT parse
 * XML or Swagger/OpenAPI structure — every file is read as a raw string and
 * concatenated with clear file markers so the language model can resolve
 * cross-file flow references itself using full project context.
 */
public class ProjectLoader {

    private static final List<String> RELEVANT_EXTENSIONS =
            List.of(".xml", ".yaml", ".yml", ".json");

    /**
     * Walks the given project directory, reads every relevant file, and returns
     * a single concatenated string with "=== FILE: relative/path ===" markers
     * before each file's content.
     */
    public String loadProjectAsText(Path projectRoot) throws IOException {
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("Not a directory: " + projectRoot);
        }

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(this::isRelevantFile)
                .sorted(Comparator.comparing(Path::toString))
                .forEach(files::add);
        }

        if (files.isEmpty()) {
            throw new IllegalArgumentException(
                    "No .xml/.yaml/.yml/.json files found under " + projectRoot);
        }

        StringBuilder sb = new StringBuilder();
        for (Path file : files) {
            String relativePath = projectRoot.relativize(file).toString();
            String content = Files.readString(file, StandardCharsets.UTF_8);

            sb.append("=== FILE: ").append(relativePath).append(" ===\n");
            sb.append(content);
            if (!content.endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private boolean isRelevantFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return RELEVANT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
