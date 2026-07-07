package com.governanceplus.reviewer.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One rule's assessment against the project, as judged by the model. */
public class ReviewFinding {

    public enum Status {
        PASS, FAIL, WARNING
    }

    public enum Severity {
        CRITICAL, MAJOR, MINOR
    }

    private final String rule;
    private final String category;
    private final Status status;
    private final Severity severity;
    private final String file;
    private final String explanation;
    private final String recommendation;

    @JsonCreator
    public ReviewFinding(
            @JsonProperty("rule") String rule,
            @JsonProperty("category") String category,
            @JsonProperty("status") Status status,
            @JsonProperty("severity") Severity severity,
            @JsonProperty("file") String file,
            @JsonProperty("explanation") String explanation,
            @JsonProperty("recommendation") String recommendation) {
        this.rule = rule;
        this.category = category;
        this.status = status;
        this.severity = severity;
        this.file = file;
        this.explanation = explanation;
        this.recommendation = recommendation;
    }

    public String getRule() {
        return rule;
    }

    public String getCategory() {
        return category;
    }

    public Status getStatus() {
        return status;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getFile() {
        return file;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getRecommendation() {
        return recommendation;
    }
}
