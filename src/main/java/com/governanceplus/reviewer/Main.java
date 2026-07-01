package com.governanceplus.reviewer;

import com.governanceplus.reviewer.io.GitFetcher;
import com.governanceplus.reviewer.io.ProjectLoader;
import com.governanceplus.reviewer.model.ModelService;
import com.governanceplus.reviewer.report.ReportWriter;
import com.governanceplus.reviewer.rules.RulesLoader;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point. Two usage modes:
 *
 * Local directory:
 *   java -jar governanceplus.jar --local <projectDir> <rulesFile.md> <modelFile.gguf> [outputDir]
 *
 * Git repository (GitHub / Bitbucket / GitLab / any HTTPS git server):
 *   java -jar governanceplus.jar --git <repoUrl> <branch> <rulesFile.md> <modelFile.gguf> [outputDir]
 *
 * For private repos, set GIT_USERNAME and GIT_TOKEN environment variables
 * before running (see GitFetcher for details).
 *
 * Examples:
 *   java -jar governanceplus.jar --local ./project-sample ./rules/rules.md ./models/model.gguf ./out
 *   java -jar governanceplus.jar --git https://github.com/org/repo.git main ./rules/rules.md ./models/model.gguf ./out
 */
public class Main {

    private static final int DEFAULT_CONTEXT_SIZE = 2048;

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsageAndExit();
        }

        String mode = args[0];
        Path clonedDir = null;
        GitFetcher gitFetcher = null;

        try {
            Path projectDir;
            Path rulesFile;
            Path modelFile;
            Path outputDir;

            if ("--local".equals(mode)) {
                if (args.length < 4) printUsageAndExit();
                projectDir = Paths.get(args[1]);
                rulesFile = Paths.get(args[2]);
                modelFile = Paths.get(args[3]);
                outputDir = args.length > 4 ? Paths.get(args[4]) : Paths.get("./out");

            } else if ("--git".equals(mode)) {
                if (args.length < 5) printUsageAndExit();
                String repoUrl = args[1];
                String branch = args[2];
                rulesFile = Paths.get(args[3]);
                modelFile = Paths.get(args[4]);
                outputDir = args.length > 5 ? Paths.get(args[5]) : Paths.get("./out");

                System.out.println("Cloning " + repoUrl + " (branch: " + branch + ")...");
                gitFetcher = new GitFetcher();
                clonedDir = gitFetcher.cloneRepository(repoUrl, branch);
                projectDir = clonedDir;
                System.out.println("Cloned into: " + projectDir);

            } else {
                printUsageAndExit();
                return; // unreachable, keeps compiler happy
            }

            System.out.println("Loading project files from: " + projectDir);
            String projectText = new ProjectLoader().loadProjectAsText(projectDir);

            System.out.println("Loading rules from: " + rulesFile);
            String rulesMarkdown = new RulesLoader().loadRules(rulesFile);

            System.out.println("Loading model (this may take a moment): " + modelFile);
            try (ModelService modelService = new ModelService(modelFile, DEFAULT_CONTEXT_SIZE)) {

                System.out.println("Running review...");
                String review = modelService.review(rulesMarkdown, projectText);

                ReportWriter reportWriter = new ReportWriter();
                reportWriter.printToConsole(review);
                Path reportPath = reportWriter.writeReport(outputDir, review);

                System.out.println("Report written to: " + reportPath.toAbsolutePath());
            }

        } catch (Exception e) {
            System.err.println("Review failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (clonedDir != null && gitFetcher != null) {
                gitFetcher.cleanup(clonedDir);
            }
        }
    }

    private static void printUsageAndExit() {
        System.err.println("Usage:");
        System.err.println("  java -jar governanceplus.jar --local <projectDir> <rulesFile.md> <modelFile.gguf> [outputDir]");
        System.err.println("  java -jar governanceplus.jar --git <repoUrl> <branch> <rulesFile.md> <modelFile.gguf> [outputDir]");
        System.exit(1);
    }
}
