package com.governanceplus.web.dto.rules;

/** One rule in rules.json's "rules" array — evaluated as XPath against Mule flow XML. */
public class XmlRuleDto {

    private String id;
    private String category;
    private String severity;
    private String description;
    private String xpath;
    private String usageAttribute;
    private String usagePattern;
    /** Optional glob (e.g. "*-api") matched against the target project's pom.xml artifactId; blank/absent = all projects. */
    private String projectNamePattern;

    public XmlRuleDto() {
    }

    public XmlRuleDto(String id, String category, String severity, String description, String xpath,
                       String usageAttribute, String usagePattern) {
        this.id = id;
        this.category = category;
        this.severity = severity;
        this.description = description;
        this.xpath = xpath;
        this.usageAttribute = usageAttribute;
        this.usagePattern = usagePattern;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getXpath() { return xpath; }
    public void setXpath(String xpath) { this.xpath = xpath; }

    public String getUsageAttribute() { return usageAttribute; }
    public void setUsageAttribute(String usageAttribute) { this.usageAttribute = usageAttribute; }

    public String getUsagePattern() { return usagePattern; }
    public void setUsagePattern(String usagePattern) { this.usagePattern = usagePattern; }

    public String getProjectNamePattern() { return projectNamePattern; }
    public void setProjectNamePattern(String projectNamePattern) { this.projectNamePattern = projectNamePattern; }
}
