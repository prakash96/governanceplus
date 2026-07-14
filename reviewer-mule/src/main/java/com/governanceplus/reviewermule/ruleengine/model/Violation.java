package com.governanceplus.reviewermule.ruleengine.model;

/** One rule match against a project file — the rule engine only ever reports problems, no PASS concept. */
public class Violation {

    private final String ruleId;
    private final String message;
    private final String file;
    private final int line;

    public Violation(String ruleId, String message, String file, int line) {
        this.ruleId = ruleId;
        this.message = message;
        this.file = file;
        this.line = line;
    }

    public String getRuleId() { return ruleId; }
    public String getMessage() { return message; }
    public String getFile() { return file; }
    public int getLine() { return line; }
}
