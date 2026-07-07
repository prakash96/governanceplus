
package com.governanceplus.reviewer.ruleengine.model;

public class SwaggerRule implements ProjectScopedRule {

    private String id;
    private String category;
    private String severity;
    private String description;
    private String jsonPath;
    private String projectNamePattern;

    public SwaggerRule(String id, String category, String severity, String description, String jsonPath) {
        this(id, category, severity, description, jsonPath, null);
    }

    public SwaggerRule(String id, String category, String severity, String description, String jsonPath,
            String projectNamePattern) {
        this.id = id;
        this.category = category;
        this.severity = severity;
        this.description = description;
        this.jsonPath = jsonPath;
        this.projectNamePattern = projectNamePattern;
    }

    public String getProjectNamePattern() { return projectNamePattern; }
    public void setProjectNamePattern(String projectNamePattern) { this.projectNamePattern = projectNamePattern; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getJsonPath() { return jsonPath; }
    public void setJsonPath(String jsonPath) { this.jsonPath = jsonPath; }

    @Override
    public String toString() {
        return getId() + ":" + getJsonPath();
    }
}
