package com.governanceplus.web.service;

import com.governanceplus.reviewer.model.ReviewReport;
import com.governanceplus.web.dto.ReviewStatus;

import java.nio.file.Path;
import java.time.Instant;

/** In-memory record of one review run; never persisted, purged after a TTL. */
public class ReviewJob {

    private final String id;
    private final Path extractedProjectDir;
    private final Instant submittedAt;

    private volatile ReviewStatus status = ReviewStatus.QUEUED;
    private volatile ReviewReport report;
    private volatile String errorMessage;

    public ReviewJob(String id, Path extractedProjectDir, Instant submittedAt) {
        this.id = id;
        this.extractedProjectDir = extractedProjectDir;
        this.submittedAt = submittedAt;
    }

    public String getId() {
        return id;
    }

    public Path getExtractedProjectDir() {
        return extractedProjectDir;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    /** Deterministic rule-engine result (XML flows + pom.xml). */
    public ReviewReport getReport() {
        return report;
    }

    public void setReport(ReviewReport report) {
        this.report = report;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
