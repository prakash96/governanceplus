package com.governanceplus.ai.dto;

import java.util.Map;

public class RuleAssistExplainRequest {

    /** "xml" | "pom" | "swagger" */
    private String category;
    /** The rule's fields (id/category/severity/description/xpath, etc.) as sent by the UI's form. */
    private Map<String, Object> rule;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Map<String, Object> getRule() { return rule; }
    public void setRule(Map<String, Object> rule) { this.rule = rule; }
}
