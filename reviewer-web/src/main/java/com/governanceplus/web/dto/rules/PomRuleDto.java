package com.governanceplus.web.dto.rules;

/** One rule in rules.json's "pomRules" array — a minimum dependency version requirement. */
public class PomRuleDto {

    private String artifactId;
    private String minVersion;
    /** Optional glob (e.g. "*-api") matched against the target project's pom.xml artifactId; blank/absent = all projects. */
    private String projectNamePattern;

    public PomRuleDto() {
    }

    public PomRuleDto(String artifactId, String minVersion) {
        this.artifactId = artifactId;
        this.minVersion = minVersion;
    }

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public String getMinVersion() { return minVersion; }
    public void setMinVersion(String minVersion) { this.minVersion = minVersion; }

    public String getProjectNamePattern() { return projectNamePattern; }
    public void setProjectNamePattern(String projectNamePattern) { this.projectNamePattern = projectNamePattern; }
}
