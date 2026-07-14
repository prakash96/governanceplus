package com.governanceplus.web.dto.rules;

/** One rule in rules.json's "swaggerRules" array — evaluated as JSONPath against an OpenAPI spec. */
public class SwaggerRuleDto {

    private String id;
    private String category;
    private String severity;
    private String description;
    private String jsonPath;
    /** Optional glob (e.g. "*-api") matched against the target project's pom.xml artifactId; blank/absent = all projects. */
    private String projectNamePattern;

    /**
     * The fields below are only ever read/written by the Rules UI's Selection +
     * Assertion condition builder, to let it reconstruct that state when a rule
     * is reopened for editing. The rule engine itself (reviewer-core's
     * RuleLoader/SwaggerRuleEvaluator) only ever reads jsonPath — it has no
     * idea these exist, and a rule authored by hand (or before this builder
     * existed) simply leaves them null, which the UI treats as "jsonPath is a
     * compound/manually-written expression, don't try to decompose it".
     */
    private String selection;
    private String operator;
    private String value;

    public SwaggerRuleDto() {
    }

    public SwaggerRuleDto(String id, String category, String severity, String description, String jsonPath) {
        this.id = id;
        this.category = category;
        this.severity = severity;
        this.description = description;
        this.jsonPath = jsonPath;
    }

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

    public String getProjectNamePattern() { return projectNamePattern; }
    public void setProjectNamePattern(String projectNamePattern) { this.projectNamePattern = projectNamePattern; }

    public String getSelection() { return selection; }
    public void setSelection(String selection) { this.selection = selection; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
