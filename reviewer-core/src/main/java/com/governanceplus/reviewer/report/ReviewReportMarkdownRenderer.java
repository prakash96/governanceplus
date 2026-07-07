package com.governanceplus.reviewer.report;

import com.governanceplus.reviewer.model.ReviewFinding;
import com.governanceplus.reviewer.model.ReviewReport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders a ReviewReport (structured findings) into readable markdown for the CLI's report file. */
public class ReviewReportMarkdownRenderer {

    public String render(ReviewReport report) {
        List<ReviewFinding> findings = report.getFindings();

        StringBuilder sb = new StringBuilder();

        if (report.getSummary() != null && !report.getSummary().isBlank()) {
            sb.append(report.getSummary()).append("\n\n");
        }
        sb.append("**").append(findings.size()).append(" rule(s) checked — ")
                .append(countByStatus(findings, ReviewFinding.Status.PASS)).append(" passed, ")
                .append(countByStatus(findings, ReviewFinding.Status.FAIL)).append(" failed, ")
                .append(countByStatus(findings, ReviewFinding.Status.WARNING)).append(" warning(s)**\n\n");

        for (Map.Entry<String, List<ReviewFinding>> entry : groupByCategory(findings).entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");
            for (ReviewFinding finding : entry.getValue()) {
                sb.append("### ").append(statusLabel(finding)).append(" — ").append(finding.getRule()).append("\n\n");
                appendIfPresent(sb, "File/flow", finding.getFile());
                appendIfPresent(sb, "Explanation", finding.getExplanation());
                appendIfPresent(sb, "Recommendation", finding.getRecommendation());
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private long countByStatus(List<ReviewFinding> findings, ReviewFinding.Status status) {
        return findings.stream().filter(f -> f.getStatus() == status).count();
    }

    private Map<String, List<ReviewFinding>> groupByCategory(List<ReviewFinding> findings) {
        Map<String, List<ReviewFinding>> byCategory = new LinkedHashMap<>();
        for (ReviewFinding finding : findings) {
            String category = (finding.getCategory() == null || finding.getCategory().isBlank())
                    ? "General" : finding.getCategory();
            byCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(finding);
        }
        return byCategory;
    }

    private String statusLabel(ReviewFinding finding) {
        String status = finding.getStatus() == null ? "UNKNOWN" : finding.getStatus().name();
        return finding.getSeverity() == null ? status : status + " (" + finding.getSeverity().name() + ")";
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }
}
