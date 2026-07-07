package com.governanceplus.web.dto;

import com.governanceplus.reviewer.model.ReviewReport;

/** Outward-facing shape returned by the review endpoints — the deterministic rule-engine result. */
public class ReviewJobResponse {

    private final String jobId;
    private final ReviewStatus status;
    private final ReviewReport report;
    private final String errorMessage;

    public ReviewJobResponse(String jobId, ReviewStatus status, ReviewReport report, String errorMessage) {
        this.jobId = jobId;
        this.status = status;
        this.report = report;
        this.errorMessage = errorMessage;
    }

    public String getJobId() {
        return jobId;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public ReviewReport getReport() {
        return report;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
