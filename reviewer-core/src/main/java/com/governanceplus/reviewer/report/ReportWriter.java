package com.governanceplus.reviewer.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes the model's raw English review text to a markdown report file and
 * echoes it to the console. No re-formatting or re-structuring of the
 * model's output — it is already meant to be human-readable English.
 */
public class ReportWriter {

    public Path writeReport(Path outputDir, String reviewText) throws IOException {
        Files.createDirectories(outputDir);

        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now());
        Path reportFile = outputDir.resolve("review-" + timestamp + ".md");

        String content = "# Mulesoft Project Review\n\n"
                + "Generated: " + LocalDateTime.now() + "\n\n"
                + "---\n\n"
                + reviewText + "\n";

        Files.writeString(reportFile, content, StandardCharsets.UTF_8);
        return reportFile;
    }

    public void printToConsole(String reviewText) {
        System.out.println();
        System.out.println("=== REVIEW RESULT ===");
        System.out.println();
        System.out.println(reviewText);
        System.out.println();
    }
}
