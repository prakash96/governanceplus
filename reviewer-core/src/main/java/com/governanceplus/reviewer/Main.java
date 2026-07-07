package com.governanceplus.reviewer;

import com.governanceplus.reviewer.io.GitFetcher;
import com.governanceplus.reviewer.model.ReviewReport;
import com.governanceplus.reviewer.model.RuleEngineReviewAdapter;
import com.governanceplus.reviewer.report.ReportWriter;
import com.governanceplus.reviewer.report.ReviewReportMarkdownRenderer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point. Two usage modes:
 *
 * Local directory:
 *   java -jar governanceplus-cli.jar --local <projectDir> <rulesJsonFile> [outputDir]
 *
 * Git repository (GitHub / Bitbucket / GitLab / any HTTPS git server):
 *   java -jar governanceplus-cli.jar --git <repoUrl> <branch> <rulesJsonFile> [outputDir]
 *
 * For private repos, set GIT_USERNAME and GIT_TOKEN environment variables
 * before running (see GitFetcher for details).
 *
 * XML flows (under src/main/mule/) and pom.xml are reviewed by the
 * deterministic com.governanceplus.reviewer.ruleengine rule engine, driven by <rulesJsonFile>
 * (an XPath-rule JSON file — see rules/rules.json.example for the shape).
 *
 * Examples:
 *   java -jar governanceplus-cli.jar --local ./project-sample ./rules/rules.json.example ./out
 *   java -jar governanceplus-cli.jar --git https://github.com/org/repo.git main ./rules/rules.json.example ./out
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsageAndExit();
        }

        String mode = args[0];
        Path clonedDir = null;
        GitFetcher gitFetcher = null;

        try {
            Path projectDir;
            Path rulesJsonFile;
            Path outputDir;

            if ("--local".equals(mode)) {
                if (args.length < 3) printUsageAndExit();
                projectDir = Paths.get(args[1]);
                rulesJsonFile = Paths.get(args[2]);
                outputDir = args.length > 3 ? Paths.get(args[3]) : Paths.get("./out");

            } else if ("--git".equals(mode)) {
                if (args.length < 4) printUsageAndExit();
                String repoUrl = args[1];
                String branch = args[2];
                rulesJsonFile = Paths.get(args[3]);
                outputDir = args.length > 4 ? Paths.get(args[4]) : Paths.get("./out");

                System.out.println("Cloning " + repoUrl + " (branch: " + branch + ")...");
                gitFetcher = new GitFetcher();
                clonedDir = gitFetcher.cloneRepository(repoUrl, branch);
                projectDir = clonedDir;
                System.out.println("Cloned into: " + projectDir);

            } else {
                printUsageAndExit();
                return; // unreachable, keeps compiler happy
            }

            System.out.println("Running rule-engine review (XML flows + pom.xml)...");
            ReviewReport ruleEngineReport = new RuleEngineReviewAdapter()
                    .review(projectDir.toFile(), rulesJsonFile.toString());

            String renderedReview = "# XML & pom.xml Review (Rule Engine)\n\n"
                    + new ReviewReportMarkdownRenderer().render(ruleEngineReport);

            ReportWriter reportWriter = new ReportWriter();
            reportWriter.printToConsole(renderedReview);
            Path reportPath = reportWriter.writeReport(outputDir, renderedReview);

            System.out.println("Report written to: " + reportPath.toAbsolutePath());

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
        System.err.println("  java -jar governanceplus-cli.jar --local <projectDir> <rulesJsonFile> [outputDir]");
        System.err.println("  java -jar governanceplus-cli.jar --git <repoUrl> <branch> <rulesJsonFile> [outputDir]");
        System.exit(1);
    }
}
