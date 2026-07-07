package com.governanceplus.web.dto.assist;

public class RuleAssistGenerateRequest {

    /** "xml" | "pom" | "swagger" */
    private String category;
    private String instruction;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
}
