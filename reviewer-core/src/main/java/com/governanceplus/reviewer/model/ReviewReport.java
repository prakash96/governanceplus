package com.governanceplus.reviewer.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Result of reviewing a project against a set of rules — a summary plus structured per-rule findings. */
public class ReviewReport {

    private final String summary;
    private final List<ReviewFinding> findings;

    @JsonCreator
    public ReviewReport(
            @JsonProperty("summary") String summary,
            @JsonProperty("findings") List<ReviewFinding> findings) {
        this.summary = summary;
        this.findings = findings;
    }

    public String getSummary() {
        return summary;
    }

    public List<ReviewFinding> getFindings() {
        return findings;
    }
}
