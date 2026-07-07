
package com.governanceplus.reviewer.ruleengine.model;

public class Rule implements ProjectScopedRule {

    private String id;
    private String category;
    private String severity;
    private String description;
    private String xpath;
    private String usageAttribute;
    private String usagePattern;
    private String projectNamePattern;

    public String getProjectNamePattern() {
		return projectNamePattern;
	}
	public void setProjectNamePattern(String projectNamePattern) {
		this.projectNamePattern = projectNamePattern;
	}

	public String getUsageAttribute() {
		return usageAttribute;
	}
	public void setUsageAttribute(String usageAttribute) {
		this.usageAttribute = usageAttribute;
	}
	public String getUsagePattern() {
		return usagePattern;
	}
	public void setUsagePattern(String usagePattern) {
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
    
    @Override
    public String toString() {
    	// TODO Auto-generated method stub
    	return getId() + ":" + getXpath();
    }
	public Rule(String id, String category, String severity, String description, String xpath, String usageAttribute, String usagePattern) {
		this(id, category, severity, description, xpath, usageAttribute, usagePattern, null);
	}

	public Rule(String id, String category, String severity, String description, String xpath, String usageAttribute,
			String usagePattern, String projectNamePattern) {
		super();
		this.id = id;
		this.category = category;
		this.severity = severity;
		this.description = description;
		this.xpath = xpath;
		this.usageAttribute = usageAttribute;
		this.usagePattern = usagePattern;
		this.projectNamePattern = projectNamePattern;
	}
}
